"""
Merge PhiUSIIL (URL) + ChiFraud (Chinese text) into combined training dataset
"""
import os, sys, io, csv
import pandas as pd
import numpy as np

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

BASE = os.path.join(os.path.dirname(__file__), "raw_data")
OUTPUT = os.path.join(BASE, "combined_dataset.csv")

# ── 1. Load ChiFraud ──────────────────────────────────────
chifraud_dir = os.path.join(BASE, "chifraud", "dataset")
chifraud_rows = []

for split_name, filename in [("train", "ChiFraud_train.csv"), ("t2022", "ChiFraud_t2022.csv"), ("t2023", "ChiFraud_t2023.csv")]:
    path = os.path.join(chifraud_dir, filename)
    if not os.path.exists(path):
        print(f"  SKIP {path} not found")
        continue
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        header = next(reader)
        for row in reader:
            if len(row) >= 2:
                label_id = int(row[0].strip())
                label = 1.0 if label_id != 0 else 0.0
                text = row[1].strip()
                if len(text) > 10:  # filter very short texts
                    chifraud_rows.append({"text": text, "label": label, "URL": "", "Title": text[:100], "source": f"ChiFraud_{split_name}"})

print(f"ChiFraud: {len(chifraud_rows)} rows loaded")

# ── 2. Load existing clean_dataset (PhiUSIIL) ──────────────
clean_path = os.path.join(BASE, "clean_dataset.csv")
phiusiil_rows = []
if os.path.exists(clean_path):
    df = pd.read_csv(clean_path, usecols=["text", "label", "URL", "Title"])
    df["source"] = "PhiUSIIL"
    phiusiil_rows = df.to_dict("records")
    print(f"PhiUSIIL: {len(phiusiil_rows)} rows loaded")
else:
    print(f"  SKIP {clean_path} not found")

# ── 3. Balance: downsample majority classes ───────────────
combined = chifraud_rows + phiusiil_rows
df_all = pd.DataFrame(combined)

labels = df_all["label"].values
n_legit = int((labels == 0).sum())
n_fraud = int((labels == 1).sum())
print(f"\nCombined raw: {len(df_all)} total | Legit: {n_legit} | Fraud: {n_fraud}")

# Balance to 50/50 by downsampling the majority class
min_count = min(n_legit, n_fraud)
print(f"  Balancing to {min_count} each...")

legit_df = df_all[df_all["label"] == 0.0].sample(n=min_count, random_state=42)
fraud_df = df_all[df_all["label"] == 1.0].sample(n=min_count, random_state=42)

balanced = pd.concat([legit_df, fraud_df]).sample(frac=1.0, random_state=42).reset_index(drop=True)

# Rebuild URL + Title combined text for ML training
balanced["text"] = balanced["URL"].fillna("").astype(str) + " [SEP] " + balanced["Title"].fillna("").astype(str)

print(f"\nBalanced combined: {len(balanced)} total | Legit: {(balanced['label']==0).sum()} | Fraud: {(balanced['label']==1).sum()}")
print(f"  PhiUSIIL: {(balanced['source']=='PhiUSIIL').sum()}")
print(f"  ChiFraud: {(balanced['source']!='PhiUSIIL').sum()}")

# ── 4. Save ────────────────────────────────────────────────
balanced.to_csv(OUTPUT, index=False, encoding="utf-8")
print(f"\nSaved combined dataset to {OUTPUT}")
print(f"  Size: {os.path.getsize(OUTPUT) / 1e6:.1f} MB")

# Show samples
print("\n--- Sample rows ---")
for _, row in balanced.head(10).iterrows():
    src = row["source"]
    label = "FRAUD" if row["label"] == 1 else "LEGIT"
    text_short = row["text"][:80]
    print(f"  [{src}] [{label}] {text_short}")
