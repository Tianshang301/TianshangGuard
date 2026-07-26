"""
Calibrate SMS model decision threshold using v5 validation set (real SMS data)
"""
import os, sys, json, random, numpy as np
import torch
import torch.nn as nn
from sklearn.metrics import precision_score, recall_score, f1_score, roc_auc_score

sys.path.insert(0, os.path.dirname(__file__))
from train_phishing_model import BytePhishingTransformer, Config, ByteTokenizer, device

SEED = 42
random.seed(SEED)
np.random.seed(SEED)
torch.manual_seed(SEED)

MODE = "sms"
base_dir = os.path.dirname(__file__)
config = Config(MODE)

model = BytePhishingTransformer(config).to(device)
state_path = os.path.join(config.output_dir, "best_model.pt")
state = torch.load(state_path, map_location=device, weights_only=True)
model.load_state_dict(state)
model.eval()
print(f"Loaded {state_path} ({sum(p.numel() for p in model.parameters()):,} params)")

tokenizer = ByteTokenizer()

# ── 1. Load v5 SMS dataset, 90/10 split ──
import pandas as pd
csv_path = os.path.join(base_dir, "raw_data", "chinese_sms_training_v5.csv")
df = pd.read_csv(csv_path, encoding="utf-8")
df = df.dropna(subset=["text"])
df["text"] = df["text"].astype(str)
phish_texts = df[df["label"] == "phishing"]["text"].tolist()
legit_texts = df[df["label"] == "legitimate"]["text"].tolist()

rng = random.Random(SEED)
n = min(len(phish_texts), len(legit_texts))
phish_sample = rng.sample(phish_texts, n)
legit_sample = rng.sample(legit_texts, n)
texts = phish_sample + legit_sample
labels = [1.0] * n + [0.0] * n
combined = list(zip(texts, labels))
rng.shuffle(combined)
texts, labels = zip(*combined)
split_idx = int(len(texts) * 0.9)

val_texts = texts[split_idx:]
val_labels = labels[split_idx:]
print(f"v5 dataset: {len(texts)} total, {len(val_texts)} validation ({int(sum(val_labels))} phish, {len(val_labels)-int(sum(val_labels))} legit)")

def run_inference(texts, labels, batch_size=64):
    scores = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i:i+batch_size]
        tokens_np = np.stack([tokenizer.encode(t, config.max_seq_len) for t in batch]).astype(np.int64)
        tokens_t = torch.tensor(tokens_np, dtype=torch.long, device=device)
        with torch.no_grad():
            out = model(tokens_t)
        batch_scores = torch.sigmoid(out).cpu().numpy().flatten().tolist()
        scores.extend(batch_scores)
    return np.array(scores), np.array(labels[:len(scores)])

val_scores, val_labels_np = run_inference(val_texts, val_labels)

# ── 2. Threshold sweep on v5 validation set ──
print(f"\n{'='*70}")
print(f"{'CALIBRATION ON v5 VALIDATION SET':^70}")
print(f"{'='*70}")
print(f"{'Thresh':>8} {'Prec':>8} {'Recall':>8} {'F1':>8} {'FPR':>8} {'FNR':>8} {'P/FN':>6}")
print("-" * 70)
results = []
for th in np.arange(0.05, 0.96, 0.01):
    preds = (val_scores > th).astype(float)
    tp = ((preds == 1) & (val_labels_np == 1)).sum()
    fp = ((preds == 1) & (val_labels_np == 0)).sum()
    fn = ((preds == 0) & (val_labels_np == 1)).sum()
    tn = ((preds == 0) & (val_labels_np == 0)).sum()
    prec = tp / max(tp + fp, 1)
    rec = tp / max(tp + fn, 1)
    f1 = 2 * prec * rec / max(prec + rec, 1e-8)
    fpr = fp / max(fp + tn, 1)
    fnr = fn / max(fn + tp, 1)
    results.append((th, prec, rec, f1, fpr, fnr, int(fn)))
    if abs(th - round(th * 20) / 20) < 0.001 or th in (0.05, 0.95):
        print(f"{th:>8.2f} {prec:>8.4f} {rec:>8.4f} {f1:>8.4f} {fpr:>8.4f} {fnr:>8.4f} {int(fn):>6}")

# Best F1
best = max(results, key=lambda r: r[3])
print(f"\n>>> Best F1: thresh={best[0]:.2f} F1={best[3]:.4f} Prec={best[1]:.4f} Rec={best[2]:.4f} FPR={best[4]:.4f}")

# Best recall >= 0.85
high_rec = [r for r in results if r[2] >= 0.85]
if high_rec:
    best_hr = max(high_rec, key=lambda r: r[3])
    print(f">>> High Recall (>=0.85): thresh={best_hr[0]:.2f} F1={best_hr[3]:.4f} Prec={best_hr[1]:.4f} Rec={best_hr[2]:.4f} FPR={best_hr[4]:.4f}")

# Best recall >= 0.95
high_rec2 = [r for r in results if r[2] >= 0.95]
if high_rec2:
    best_hr2 = max(high_rec2, key=lambda r: r[3])
    print(f">>> High Recall (>=0.95): thresh={best_hr2[0]:.2f} F1={best_hr2[3]:.4f} Prec={best_hr2[1]:.4f} Rec={best_hr2[2]:.4f} FPR={best_hr2[4]:.4f}")

# Low FPR (<= 0.10)
low_fpr = [r for r in results if r[4] <= 0.10]
if low_fpr:
    best_lf = max(low_fpr, key=lambda r: r[3])
    print(f">>> Low FPR (<=0.10): thresh={best_lf[0]:.2f} F1={best_lf[3]:.4f} Prec={best_lf[1]:.4f} Rec={best_lf[2]:.4f} FPR={best_lf[4]:.4f}")

auc = roc_auc_score(val_labels_np, val_scores)
print(f"\nAUC-ROC on v5 validation: {auc:.4f}")

# ── 3. Test on hand-crafted 40 SMS test cases ──
print(f"\n{'='*70}")
print(f"{'HAND-CRAFTED TEST CASES (sms_test_cases.json)':^70}")
print(f"{'='*70}")
test_json = os.path.join(base_dir, "..", "app", "src", "main", "assets", "test_data", "sms_test_cases.json")
with open(test_json, encoding="utf-8") as f:
    test_cases = json.load(f)
test_texts = [tc["body"] for tc in test_cases]
test_labels = [1.0 if tc["label"] == "phishing" else 0.0 for tc in test_cases]
test_scores_np, test_labels_np = run_inference(test_texts, test_labels)
test_scores = test_scores_np  # keep for individual display
n_phish = int(test_labels_np.sum())
n_legit = len(test_labels_np) - n_phish
print(f"  {len(test_texts)} cases ({n_phish} phish, {n_legit} legit)  scores: {len(test_scores)}")

print(f"\n{'Thresh':>8} {'Prec':>8} {'Recall':>8} {'F1':>8} {'FPR':>8} {'FNR':>8} {'PhishOK':>8}")
print("-" * 70)
hand_results = []
for th in np.arange(0.05, 0.96, 0.01):
    preds = (test_scores > th).astype(float)
    tp = ((preds == 1) & (test_labels_np == 1)).sum()
    fp = ((preds == 1) & (test_labels_np == 0)).sum()
    fn = ((preds == 0) & (test_labels_np == 1)).sum()
    tn = ((preds == 0) & (test_labels_np == 0)).sum()
    prec = tp / max(tp + fp, 1)
    rec = tp / max(tp + fn, 1)
    f1 = 2 * prec * rec / max(prec + rec, 1e-8)
    fpr = fp / max(fp + tn, 1)
    fnr = fn / max(fn + tp, 1)
    hand_results.append((th, prec, rec, f1, fpr, fnr, int(tp)))
    if abs(th - round(th * 20) / 20) < 0.001 or th in (0.05, 0.95):
        print(f"{th:>8.2f} {prec:>8.4f} {rec:>8.4f} {f1:>8.4f} {fpr:>8.4f} {fnr:>8.4f} {int(tp):>8}/{n_phish}")

hand_best = max(hand_results, key=lambda r: r[3])
print(f"\n>>> Best F1 on hand-crafted: thresh={hand_best[0]:.2f} F1={hand_best[3]:.4f} Prec={hand_best[1]:.4f} Rec={hand_best[2]:.4f} FPR={hand_best[4]:.4f}")

# ── 4. Test on sms_test_set_clean.csv (external test set) ──
print(f"\n{'='*70}")
print(f"{'EXTERNAL SMS TEST SET (sms_test_set_clean.csv)':^70}")
print(f"{'='*70}")
ext_csv = os.path.join(base_dir, "raw_data", "sms_test_set_clean.csv")
if os.path.exists(ext_csv):
    ext_df = pd.read_csv(ext_csv)
    ext_texts = ext_df["text"].tolist()
    ext_labels = ext_df["label"].tolist()
    ext_scores, ext_labels_np = run_inference(ext_texts, ext_labels)
    ext_n_phish = int(sum(ext_labels))
    ext_n_legit = len(ext_labels) - ext_n_phish
    print(f"  {len(ext_texts)} samples ({ext_n_phish} phish, {ext_n_legit} legit)")

    print(f"\n{'Thresh':>8} {'Prec':>8} {'Recall':>8} {'F1':>8} {'FPR':>8} {'FNR':>8}")
    print("-" * 70)
    ext_results = []
    for th in np.arange(0.05, 0.96, 0.01):
        preds = (ext_scores > th).astype(float)
        tp = ((preds == 1) & (ext_labels_np == 1)).sum()
        fp = ((preds == 1) & (ext_labels_np == 0)).sum()
        fn = ((preds == 0) & (ext_labels_np == 1)).sum()
        tn = ((preds == 0) & (ext_labels_np == 0)).sum()
        prec = tp / max(tp + fp, 1)
        rec = tp / max(tp + fn, 1)
        f1 = 2 * prec * rec / max(prec + rec, 1e-8)
        fpr = fp / max(fp + tn, 1)
        fnr = fn / max(fn + tp, 1)
        ext_results.append((th, prec, rec, f1, fpr, fnr))
        if abs(th - round(th * 20) / 20) < 0.001 or th in (0.05, 0.95):
            print(f"{th:>8.2f} {prec:>8.4f} {rec:>8.4f} {f1:>8.4f} {fpr:>8.4f} {fnr:>8.4f}")

    ext_best = max(ext_results, key=lambda r: r[3])
    print(f"\n>>> Best F1 on external set: thresh={ext_best[0]:.2f} F1={ext_best[3]:.4f} Prec={ext_best[1]:.4f} Rec={ext_best[2]:.4f} FPR={ext_best[4]:.4f}")

    ext_auc = roc_auc_score(ext_labels_np, ext_scores)
    print(f">>> AUC-ROC on external set: {ext_auc:.4f}")

# ── 5. Individual score breakdown for hand-crafted test cases ──
print(f"\n{'='*70}")
print(f"{'INDIVIDUAL SCORES (hand-crafted test cases)':^70}")
print(f"{'='*70}")
for i, tc in enumerate(test_cases):
    marker = "PHISH" if tc["label"] == "phishing" else "LEGIT"
    score = test_scores[i]
    body = tc["body"][:60]
    print(f"  {marker:>6} | {score:.4f} | {body}")

print(f"\n{'='*70}")
print(f"{'RECOMMENDED THRESHOLD':^70}")
print(f"{'='*70}")

# Recommend: pick a threshold that balances v5 val and hand-crafted
print(f"\n  Based on v5 val F1=best:               thresh={best[0]:.2f}")
print(f"  Based on hand-crafted F1=best:        thresh={hand_best[0]:.2f}")
if high_rec:
    print(f"  Based on v5 recall>=0.85:             thresh={best_hr[0]:.2f}")
if high_rec2:
    print(f"  Based on v5 recall>=0.95:             thresh={best_hr2[0]:.2f}")
if low_fpr:
    print(f"  Based on v5 FPR<=0.10:                thresh={best_lf[0]:.2f}")

# Check current 0.30 threat level threshold
print(f"\n  Current SUSPICIOUS threshold (0.30):  F1={[r[3] for r in results if abs(r[0]-0.30)<0.005][0]:.4f} Rec={[r[2] for r in results if abs(r[0]-0.30)<0.005][0]:.4f} FPR={[r[4] for r in results if abs(r[0]-0.30)<0.005][0]:.4f}")
print(f"  Current DANGEROUS threshold (0.50):   F1={[r[3] for r in results if abs(r[0]-0.50)<0.005][0]:.4f} Rec={[r[2] for r in results if abs(r[0]-0.50)<0.005][0]:.4f} FPR={[r[4] for r in results if abs(r[0]-0.50)<0.005][0]:.4f}")
print(f"  Current DANGEROUS threshold (0.70):   F1={[r[3] for r in results if abs(r[0]-0.70)<0.005][0]:.4f} Rec={[r[2] for r in results if abs(r[0]-0.70)<0.005][0]:.4f} FPR={[r[4] for r in results if abs(r[0]-0.70)<0.005][0]:.4f}")

print("\nDone!")
