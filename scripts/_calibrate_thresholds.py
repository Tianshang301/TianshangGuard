"""
TianshangGuard - Calibrate thresholds (10K sample)
"""
import onnxruntime as ort, csv, numpy as np, random
from sklearn.metrics import precision_score, recall_score, f1_score, roc_auc_score
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from train_phishing_model import tokenizer

random.seed(42)
onnx_path = os.path.join(os.path.dirname(__file__), "output", "chinese", "onnx", "model_int8.onnx")
session = ort.InferenceSession(onnx_path)
in_name = session.get_inputs()[0].name
out_name = session.get_outputs()[0].name
print(f"ONNX loaded: {os.path.getsize(onnx_path)/1e3:.0f} KB")

texts, labels = [], []
val_path = os.path.join(os.path.dirname(__file__), "raw_data", "chifraud", "dataset", "ChiFraud_t2023.csv")
with open(val_path, encoding="utf-8") as f:
    reader = csv.reader(f, delimiter="\t")
    next(reader)
    for row in reader:
        if len(row) >= 2:
            labels.append(1.0 if int(row[0].strip()) != 0 else 0.0)
            texts.append(row[1].strip())

idx = list(range(len(texts)))
random.shuffle(idx)
sample_n = 10000
idx = idx[:sample_n]
texts_s = [texts[i] for i in idx]
labels_s = np.array([labels[i] for i in idx])
print(f"Sampled {sample_n} texts ({int(labels_s.sum())} fraud)")

all_scores = []
for i in range(0, len(texts_s), 256):
    batch = texts_s[i:i+256]
    tokens_np = np.stack([tokenizer.encode(t, 512) for t in batch]).astype(np.int64)
    output = session.run([out_name], {in_name: tokens_np})[0]
    all_scores.extend(output.flatten().tolist())
y_score = np.array(all_scores)

auc = roc_auc_score(labels_s, y_score)
print(f"\nAUC-ROC: {auc:.4f} (sampled)")
print(f"{'Thresh':>8} {'Prec':>8} {'Recall':>8} {'F1':>8} {'FPR':>8} {'FNR':>8}")
print("-" * 56)
for thresh in [0.05, 0.1, 0.12, 0.15, 0.18, 0.2, 0.22, 0.25, 0.28, 0.3, 0.32, 0.35, 0.4, 0.5]:
    y_pred = (y_score > thresh).astype(float)
    prec = precision_score(labels_s, y_pred, zero_division=0)
    rec = recall_score(labels_s, y_pred)
    f1 = f1_score(labels_s, y_pred, zero_division=0)
    fp = ((y_pred == 1) & (labels_s == 0)).sum()
    fn = ((y_pred == 0) & (labels_s == 1)).sum()
    tn = ((y_pred == 0) & (labels_s == 0)).sum()
    tp = ((y_pred == 1) & (labels_s == 1)).sum()
    fpr = fp / (fp + tn) if (fp + tn) > 0 else 0
    fnr = fn / (fn + tp) if (fn + tp) > 0 else 0
    print(f"{thresh:>8.2f} {prec:>8.4f} {rec:>8.4f} {f1:>8.4f} {fpr:>8.4f} {fnr:>8.4f}")

print("\n=== Current Thresholds ===")
for label, t_val, op in [("SAFE       < 0.15", 0.15, "lt"), ("DANGEROUS >= 0.30", 0.30, "ge")]:
    yp = (y_score >= t_val) if op == "ge" else (y_score > t_val)
    rec = recall_score(labels_s, yp.astype(float), zero_division=0)
    fpr_v = ((yp.astype(float) == 1) & (labels_s == 0)).sum() / max(((labels_s == 0)).sum(), 1)
    f1_v = f1_score(labels_s, yp.astype(float), zero_division=0)
    print(f"  {label}: Recall={rec:.4f} FPR={fpr_v:.4f} F1={f1_v:.4f}")

print("\n=== Recommended (Recall>=0.95 for SUSPICIOUS, max-F1 for DANGEROUS) ===")
sus_t = 0.50
for t in np.arange(0.50, 0.01, -0.001):
    if recall_score(labels_s, (y_score > t).astype(float), zero_division=0) >= 0.95:
        sus_t = t
        break
best_f1, dan_t = 0, 0.30
for t in np.arange(0.10, 0.80, 0.001):
    f1_v = f1_score(labels_s, (y_score > t).astype(float), zero_division=0)
    if f1_v > best_f1:
        best_f1 = f1_v
        dan_t = t
safe_t = round(sus_t * 0.5, 3)
yp_sus = (y_score > sus_t).astype(float)
fpr_sus = ((yp_sus == 1) & (labels_s == 0)).sum() / max(((labels_s == 0)).sum(), 1)
yp_dan = (y_score > dan_t).astype(float)
rec_sus = recall_score(labels_s, yp_sus, zero_division=0)
rec_dan = recall_score(labels_s, yp_dan, zero_division=0)
fpr_dan = ((yp_dan == 1) & (labels_s == 0)).sum() / max(((labels_s == 0)).sum(), 1)
f1_dan = f1_score(labels_s, yp_dan, zero_division=0)
print(f"  SAFE       < {safe_t:.3f}")
print(f"  SUSPICIOUS < {dan_t:.3f}  (Recall={rec_sus:.4f} FPR={fpr_sus:.4f})")
print(f"  DANGEROUS >= {dan_t:.3f}  (Recall={rec_dan:.4f} FPR={fpr_dan:.4f} F1={f1_dan:.4f})")
