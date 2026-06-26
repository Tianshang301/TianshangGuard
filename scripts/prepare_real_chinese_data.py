"""
Prepare real-only Chinese training dataset:
  1. ChiFraud_train.csv  (192K) - real Chinese web text
  2. ChiFraud_t2022.csv  (96K)  - real Chinese web text
  3. FBS SMS Dataset     (~20K) - real Chinese phishing SMS from fake base stations
Validation: ChiFraud_t2023.csv (115K) - held-out, never used in training
"""
import os, sys, csv, io, urllib.request, urllib.parse, random
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

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
                label = 1 if label_id != 0 else 0
                texts.append(row[1].strip())
                labels.append(label)
    return texts, labels

print("=" * 60)
print("  Step 1: Loading ChiFraud datasets...")
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

# ── Step 2: Download & load FBS SMS Dataset ────────────────
FBS_CATEGORIES = {
    "FR:Financial": 1,
    "FR:Other": 1,
    "FR:Phishing(Bank)": 1,
    "FR:Phishing(Other)": 1,
    "IL:Gambling": 1,
    "IL:Fake_ID_and_invoice": 1,
    "IL:Escort_service": 1,
    "IL:Political_propaganda": 1,
}
FBS_CACHE_DIR = os.path.join(DATA_DIR, "sms_spam", "fbs_sms")

def download_file(url, dest):
    if os.path.exists(dest):
        return True
    try:
        urllib.request.urlretrieve(url, dest)
        return True
    except Exception as e:
        print(f"    WARNING: download failed: {e}")
        return False

def load_fbs_file(path):
    texts = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                line = line.replace(" ", "")  # FBS data is space-tokenized
                texts.append(line)
    return texts

print("\n" + "=" * 60)
print("  Step 2: Downloading & loading FBS SMS Dataset...")
print("=" * 60)

os.makedirs(FBS_CACHE_DIR, exist_ok=True)

fbs_texts, fbs_labels = [], []
for category, label in FBS_CATEGORIES.items():
    encoded_cat = urllib.parse.quote(category, safe='')
    url = f"https://cdn.jsdelivr.net/gh/Cypher-Z/FBS_SMS_Dataset@master/{encoded_cat}"
    safe_name = category.replace(":", "_").replace("(", "_").replace(")", "_").replace(",", "_")
    dest = os.path.join(FBS_CACHE_DIR, safe_name)
    if download_file(url, dest):
        texts = load_fbs_file(dest)
        fbs_texts.extend(texts)
        fbs_labels.extend([label] * len(texts))
        print(f"  {category}: {len(texts)} lines")
    else:
        print(f"  {category}: DOWNLOAD FAILED, skipping")

print(f"\n  Total FBS fraud samples: {len(fbs_texts)}")

# ── Step 3: Merge all training data ─────────────────────────
print("\n" + "=" * 60)
print("  Step 3: Merging datasets...")
print("=" * 60)

all_texts = train_texts_all + fbs_texts
all_labels = train_labels_all + fbs_labels
sources = (["chifraud"] * len(train_texts_all)) + (["fbs"] * len(fbs_texts))

print(f"  Total training samples: {len(all_texts)}")
print(f"  Fraud (1):  {sum(all_labels)} ({100*sum(all_labels)/len(all_labels):.1f}%)")
print(f"  Legit  (0): {len(all_labels)-sum(all_labels)} ({100*(len(all_labels)-sum(all_labels))/len(all_labels):.1f}%)")

# Verify FBS data doesn't overlap with ChiFraud
print(f"\n  Checking for overlaps...")
fbs_set = set(fbs_texts)
overlap = fbs_set.intersection(set(train_texts_all))
if overlap:
    print(f"    WARNING: {len(overlap)} overlapping texts found!")
else:
    print(f"    No overlap between FBS and ChiFraud ✓")

# Save merged CSV (training data only, t2023 is held for validation)
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
