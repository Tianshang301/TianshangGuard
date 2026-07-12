"""
v2 — Prepare real Chinese training dataset (FBS discarded, correct ChiFraud labels):
   1. ChiFraud_train.csv  (192K) - real Chinese web text
   2. ChiFraud_t2022.csv  (96K)  - real Chinese web text
Only financial-fraud labels (4/6/8) are mapped to fraud=1.
Label 2 (prostitution) is excluded. Label 0 is legitimate.
Validation: ChiFraud_t2023.csv (115K) - held-out, never used in training
"""
import os, sys, csv, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

FRAUD_LABELS = {4, 6, 8}
DATA_DIR = os.path.join(os.path.dirname(__file__), "raw_data")
OUTPUT_CSV = os.path.join(os.path.dirname(__file__), "raw_data", "chinese_real_dataset.csv")

# ── Step 1: Load ChiFraud ──────────────────────────────────
def load_chifraud(path):
    texts, labels = [], []
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        header = next(reader)
        for row in reader:
            if len(row) >= 2:
                label_id = int(row[0].strip())
                # Only 4/6/8 are financial fraud; 0 is legitimate; all others excluded
                if label_id == 0:
                    label = 0
                elif label_id in FRAUD_LABELS:
                    label = 1
                else:
                    continue  # skip label 2 (prostitution), 3, 5, 7 etc.
                texts.append(row[1].strip())
                labels.append(label)
    return texts, labels

print("=" * 60)
print("  v2: Loading ChiFraud datasets (labels 4/6/8 only)...")
print("=" * 60)

train_texts, train_labels = load_chifraud(
    os.path.join(DATA_DIR, "chifraud", "dataset", "ChiFraud_train.csv"))
t2022_texts, t2022_labels = load_chifraud(
    os.path.join(DATA_DIR, "chifraud", "dataset", "ChiFraud_t2022.csv"))
t2023_texts, t2023_labels = load_chifraud(
    os.path.join(DATA_DIR, "chifraud", "dataset", "ChiFraud_t2023.csv"))

print(f"  ChiFraud_train: {len(train_texts)} samples " +
      f"(fraud={sum(train_labels)}, legit={len(train_labels)-sum(train_labels)})")
print(f"  ChiFraud_t2022: {len(t2022_texts)} samples " +
      f"(fraud={sum(t2022_labels)}, legit={len(t2022_labels)-sum(t2022_labels)})")
print(f"  ChiFraud_t2023: {len(t2023_texts)} samples " +
      f"(fraud={sum(t2023_labels)}, legit={len(t2023_labels)-sum(t2023_labels)})")

# Combine train + t2022 for training
train_texts_all = train_texts + t2022_texts
train_labels_all = train_labels + t2022_labels
print(f"\n  Combined train: {len(train_texts_all)} samples " +
      f"(fraud={sum(train_labels_all)}, legit={len(train_labels_all)-sum(train_labels_all)})")

# ── (FBS permanently discarded: encoding corruption + 14 template placeholders) ──

# ── Step 2: Merge & save ───────────────────────────────────
print("\n" + "=" * 60)
print("  Step 2: Saving merged CSV...")
print("=" * 60)

all_texts = train_texts_all
all_labels = train_labels_all
sources = (["chifraud"] * len(train_texts_all))

print(f"  Total training samples: {len(all_texts)}")
print(f"  Fraud (1):  {sum(all_labels)} ({100*sum(all_labels)/len(all_labels):.1f}%)")
print(f"  Legit  (0): {len(all_labels)-sum(all_labels)} ({100*(len(all_labels)-sum(all_labels))/len(all_labels):.1f}%)")

print(f"\n  Writing {OUTPUT_CSV}...")
with open(OUTPUT_CSV, "w", encoding="utf-8", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["text", "label", "source"])
    for text, label, source in zip(all_texts, all_labels, sources):
        writer.writerow([text, label, source])

print(f"  Done! {len(all_texts)} samples written.")
print(f"\n{'='*60}")
print(f"  Validation data: ChiFraud_t2023.csv")
print(f"    {len(t2023_texts)} samples (fraud={sum(t2023_labels)}, legit={len(t2023_labels)-sum(t2023_labels)})")
print(f"{'='*60}")
