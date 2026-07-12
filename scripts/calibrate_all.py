"""
TianshangGuard - Calibrate thresholds for all 4 models on their validation sets.
"""
import os, sys, csv, random
import numpy as np
import torch
import torch.nn as nn
import onnxruntime as ort
from sklearn.metrics import precision_score, recall_score, f1_score, roc_auc_score

sys.path.insert(0, os.path.dirname(__file__))
from train_phishing_model import BytePhishingTransformer, Config, ByteTokenizer, device, load_checkpoint

random.seed(42)
np.random.seed(42)
BASE = os.path.dirname(__file__)
tokenizer = ByteTokenizer()

MODEL_MODES = [
    {"mode": "url",     "name": "URL 检测",      "val_desc": "PhiUSIIL val"},
    {"mode": "english", "name": "英文 SMS",      "val_desc": "English SMS val"},
    {"mode": "sms",     "name": "中文 SMS",      "val_desc": "SMS val"},
    {"mode": "chinese", "name": "中文文本",      "val_desc": "ChiFraud val"},
]

def load_val_texts(mode):
    """Load validation texts and labels for each mode."""
    if mode == "url":
        csv_path = os.path.join(BASE, "raw_data", "clean_dataset.csv")
        import pandas as pd
        df = pd.read_csv(csv_path)
        df = df.sample(frac=1, random_state=42).reset_index(drop=True)
        split = int(len(df) * 0.9)
        val_df = df.iloc[split:]
        # Use URL column for URL mode, text for others
        texts = val_df["Url"].astype(str).tolist() if "Url" in val_df.columns else val_df["text"].astype(str).tolist()
        labels = val_df["label"].astype(float).tolist()
        return texts, labels, f"PhiUSIIL ({len(val_df)} val)"

    elif mode == "english":
        csv_path = os.path.join(BASE, "raw_data", "sms_spam", "english_sms_dataset.csv")
        import pandas as pd
        df = pd.read_csv(csv_path).dropna(subset=["text"])
        df["label"] = df["label"].astype(float)
        phishing = df[df["label"] == 1.0]; legit = df[df["label"] == 0.0]
        n = min(len(phishing), len(legit))
        df_bal = pd.concat([phishing.sample(n, random_state=42), legit.sample(n, random_state=42)])
        df_bal = df_bal.sample(frac=1, random_state=42).reset_index(drop=True)
        split = int(len(df_bal) * 0.9)
        val_df = df_bal.iloc[split:]
        return val_df["text"].tolist(), val_df["label"].tolist(), f"English SMS balanced ({len(val_df)} val)"

    elif mode == "sms":
        # Use ChiFraud validation for SMS mode
        val_path = os.path.join(BASE, "raw_data", "chifraud", "dataset", "ChiFraud_t2023.csv")
        texts, labels = [], []
        with open(val_path, encoding="utf-8") as f:
            reader = csv.reader(f, delimiter="\t")
            next(reader)
            for row in reader:
                if len(row) >= 2 and len(row[1]) < 200:
                    labels.append(1.0 if int(row[0].strip()) != 0 else 0.0)
                    texts.append(row[1].strip())
        # Sample 10K for speed
        idx = list(range(len(texts)))
        random.shuffle(idx)
        idx = idx[:min(10000, len(texts))]
        return [texts[i] for i in idx], [labels[i] for i in idx], f"ChiFraud SMS ({min(10000, len(texts))} sampled)"

    elif mode == "chinese":
        val_path = os.path.join(BASE, "raw_data", "chifraud", "dataset", "ChiFraud_t2023.csv")
        texts, labels = [], []
        with open(val_path, encoding="utf-8") as f:
            reader = csv.reader(f, delimiter="\t")
            next(reader)
            for row in reader:
                if len(row) >= 2:
                    labels.append(1.0 if int(row[0].strip()) != 0 else 0.0)
                    texts.append(row[1].strip())
        idx = list(range(len(texts)))
        random.shuffle(idx)
        idx = idx[:min(10000, len(texts))]
        return [texts[i] for i in idx], [labels[i] for i in idx], f"ChiFraud ({min(10000, len(texts))} sampled)"

    return [], [], "unknown"


def calibrate(mode_name):
    mode = mode_name["mode"]
    name = mode_name["name"]
    print(f"\n{'='*60}")
    print(f"  Calibrating: {name} ({mode})")
    print(f"{'='*60}")

    config = Config(mode)
    model = BytePhishingTransformer(config).to(device)

    # Load best model
    state_path = os.path.join(config.output_dir, "best_model.pt")
    if not os.path.exists(state_path):
        print(f"  [SKIP] best_model.pt not found at {state_path}")
        return None

    state = torch.load(state_path, map_location=device, weights_only=True)
    model.load_state_dict(state)
    model.eval()
    print(f"  Model: {sum(p.numel() for p in model.parameters()):,} params")

    # Export fresh ONNX
    class ModelWithSigmoid(nn.Module):
        def __init__(self, m): super().__init__(); self.m = m
        def forward(self, x): return torch.sigmoid(self.m(x))

    export_model = ModelWithSigmoid(model).to(device)
    dummy = torch.zeros(1, config.max_seq_len, dtype=torch.long, device=device)

    onnx_dir = os.path.join(config.output_dir, "onnx")
    os.makedirs(onnx_dir, exist_ok=True)
    fp32_path = os.path.join(onnx_dir, "model_fp32.onnx")
    int8_path = os.path.join(onnx_dir, "model_int8.onnx")

    torch.onnx.export(export_model, dummy, fp32_path,
        input_names=["input"], output_names=["output"],
        dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
        opset_version=17)

    from onnxruntime.quantization import quantize_dynamic, QuantType
    quantize_dynamic(fp32_path, int8_path, weight_type=QuantType.QInt8)

    # Copy to assets
    android_path = config.onnx_path
    os.makedirs(os.path.dirname(android_path), exist_ok=True)
    import shutil
    shutil.copy2(int8_path, android_path)
    print(f"  ONNX: {os.path.getsize(android_path)/1e3:.0f} KB -> {android_path}")

    # Validate
    import onnx
    onnx_model = onnx.load(int8_path)
    onnx.checker.check_model(onnx_model)
    print(f"  ONNX validation: OK")

    # ── Calibrate ──
    texts, labels, desc = load_val_texts(mode)
    labels_np = np.array(labels)
    n_fraud = int(labels_np.sum())

    if len(texts) < 100:
        print(f"  [SKIP] too few validation samples ({len(texts)})")
        return None

    print(f"  Validation: {desc} ({n_fraud} fraud, {len(texts)-n_fraud} legit)")

    session = ort.InferenceSession(int8_path)
    in_name = session.get_inputs()[0].name
    out_name = session.get_outputs()[0].name

    all_scores = []
    for i in range(0, len(texts), 256):
        batch = texts[i:i+256]
        tokens_np = np.stack([tokenizer.encode(t, config.max_seq_len) for t in batch]).astype(np.int64)
        output = session.run([out_name], {in_name: tokens_np})[0]
        all_scores.extend(output.flatten().tolist())
    y_score = np.array(all_scores)

    auc = roc_auc_score(labels_np, y_score)
    print(f"\n  AUC-ROC: {auc:.4f}")
    print(f"  {'Thresh':>8} {'Prec':>8} {'Recall':>8} {'F1':>8} {'FPR':>8} {'FNR':>8}")
    print(f"  {'-'*48}")
    for thresh in [0.05, 0.1, 0.12, 0.15, 0.18, 0.2, 0.22, 0.25, 0.28, 0.3, 0.32, 0.35, 0.4, 0.5]:
        y_pred = (y_score > thresh).astype(float)
        prec = precision_score(labels_np, y_pred, zero_division=0)
        rec = recall_score(labels_np, y_pred)
        f1 = f1_score(labels_np, y_pred, zero_division=0)
        fp = ((y_pred == 1) & (labels_np == 0)).sum()
        fn = ((y_pred == 0) & (labels_np == 1)).sum()
        tn = ((y_pred == 0) & (labels_np == 0)).sum()
        tp = ((y_pred == 1) & (labels_np == 1)).sum()
        fpr = fp / (fp + tn) if (fp + tn) > 0 else 0
        fnr = fn / (fn + tp) if (fn + tp) > 0 else 0
        print(f"  {thresh:>8.2f} {prec:>8.4f} {rec:>8.4f} {f1:>8.4f} {fpr:>8.4f} {fnr:>8.4f}")

    print(f"\n  === Current default thresholds ===")
    for label, t_val, op in [("SAFE       < 0.15", 0.15, None),
                             ("DANGEROUS >= 0.30", 0.30, None)]:
        yp = (y_score >= t_val).astype(float)
        rec = recall_score(labels_np, yp, zero_division=0)
        fpr_v = ((yp == 1) & (labels_np == 0)).sum() / max(((labels_np == 0)).sum(), 1)
        f1_v = f1_score(labels_np, yp, zero_division=0)
        print(f"    {label}: Recall={rec:.4f} FPR={fpr_v:.4f} F1={f1_v:.4f}")

    # Recommended: SUSPICIOUS threshold at Recall>=0.95, DANGEROUS at max-F1
    print(f"\n  === Recommended (Recall>=0.95 for SUSPICIOUS, max-F1 for DANGEROUS) ===")
    sus_t = 0.50
    for t in [round(x, 3) for x in np.arange(0.50, 0.01, -0.001)]:
        if recall_score(labels_np, (y_score > t).astype(float), zero_division=0) >= 0.95:
            sus_t = t
            break
    best_f1, dan_t = 0, 0.30
    for t in [round(x, 3) for x in np.arange(0.10, 0.80, 0.001)]:
        f1_v = f1_score(labels_np, (y_score > t).astype(float), zero_division=0)
        if f1_v > best_f1:
            best_f1 = f1_v
            dan_t = t
    safe_t = round(sus_t * 0.5, 3)

    yp_sus = (y_score > sus_t).astype(float)
    fpr_sus = ((yp_sus == 1) & (labels_np == 0)).sum() / max(((labels_np == 0)).sum(), 1)
    rec_sus = recall_score(labels_np, yp_sus, zero_division=0)

    yp_dan = (y_score > dan_t).astype(float)
    rec_dan = recall_score(labels_np, yp_dan, zero_division=0)
    fpr_dan = ((yp_dan == 1) & (labels_np == 0)).sum() / max(((labels_np == 0)).sum(), 1)
    f1_dan = f1_score(labels_np, yp_dan, zero_division=0)

    print(f"    SAFE       < {safe_t:.3f}")
    print(f"    SUSPICIOUS < {dan_t:.3f}  (Recall={rec_sus:.4f} FPR={fpr_sus:.4f})")
    print(f"    DANGEROUS >= {dan_t:.3f}  (Recall={rec_dan:.4f} FPR={fpr_dan:.4f} F1={f1_dan:.4f})")

    return {
        "mode": mode, "name": name,
        "auc": auc,
        "safe_threshold": safe_t,
        "suspicious_threshold": sus_t,
        "dangerous_threshold": dan_t,
        "recall_sus": rec_sus,
        "fpr_sus": fpr_sus,
        "recall_dan": rec_dan,
        "fpr_dan": fpr_dan,
        "f1_dan": f1_dan,
    }


if __name__ == "__main__":
    results = []
    for m in MODEL_MODES:
        r = calibrate(m)
        if r:
            results.append(r)
        print()

    print("=" * 60)
    print("  SUMMARY")
    print("=" * 60)
    print(f"  {'Model':<16} {'AUC':>8} {'SAFE<':>8} {'SUS<':>8} {'DAN>=':>8} {'F1(DAN)':>8}")
    print(f"  {'-'*56}")
    for r in results:
        print(f"  {r['name']:<16} {r['auc']:>8.4f} {r['safe_threshold']:>8.3f} "
              f"{r['suspicious_threshold']:>8.3f} {r['dangerous_threshold']:>8.3f} "
              f"{r['f1_dan']:>8.4f}")
    print(f"\n  All done! Models exported to app/src/main/assets/model/")
