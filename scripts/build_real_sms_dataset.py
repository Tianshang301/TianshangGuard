"""
Build real-only Chinese SMS training dataset v5.
- Phishing: FBS cleaned (real FBS scam SMS)
- Legitimate: mudou_spam ham (label 0 = real legitimate SMS)
No synthetic data used for training.
"""
import os, csv, random

SEED = 42
random.seed(SEED)

BASE = os.path.dirname(__file__)
RAW = os.path.join(BASE, "raw_data")
OUTPUT = os.path.join(RAW, "chinese_sms_training_v5.csv")


def load_fbs(path):
    texts = []
    with open(path, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            texts.append(row["text"])
    return texts


def load_mudou_ham(path):
    texts = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split("\t", 2)
            if len(parts) >= 2 and parts[0] == "0":
                texts.append(parts[1])
    return texts


def load_mudou_spam(path):
    texts = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split("\t", 2)
            if len(parts) >= 2 and parts[0] == "1":
                texts.append(parts[1])
    return texts


def main():
    fbs_path = os.path.join(RAW, "fbs_cleaned.csv")
    mudou_train = os.path.join(RAW, "mudou_spam", "mudou_spam.train")
    mudou_test = os.path.join(RAW, "mudou_spam", "mudou_spam.test")

    # Load real phishing (FBS)
    fbs_phish = load_fbs(fbs_path)
    print(f"FBS phishing: {len(fbs_phish)}")

    # Load mudou spam as phishing (real scam/spam SMS, diverse types)
    mudou_phish = load_mudou_spam(mudou_train)
    print(f"Mudou spam (phishing): {len(mudou_phish)}")

    # Load mudou ham as legitimate
    mudou_ham = load_mudou_ham(mudou_train)
    print(f"Mudou ham (legitimate): {len(mudou_ham)}")

    # Also load test set ham for extra legitimate variety
    mudou_test_ham = load_mudou_ham(mudou_test)
    print(f"Mudou test ham: {len(mudou_test_ham)}")

    # Phishing: FBS + mudou spam
    all_phish = fbs_phish + mudou_phish
    # Legitimate: mudou ham only
    all_legit = mudou_ham + mudou_test_ham

    print(f"\nTotal phishing: {len(all_phish)}")
    print(f"Total legitimate: {len(all_legit)}")

    # Balance to min class
    n = min(len(all_phish), len(all_legit))
    rng = random.Random(SEED)
    phish_sample = rng.sample(all_phish, n)
    legit_sample = rng.sample(all_legit, n)
    print(f"Balanced: {n} phishing, {n} legitimate")

    rows = []
    for t in phish_sample:
        rows.append([t, "phishing", "real"])
    for t in legit_sample:
        rows.append([t, "legitimate", "real"])
    rng.shuffle(rows)

    with open(OUTPUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["text", "label", "source"])
        writer.writerows(rows)

    lens_phish = [len(r[0]) for r in rows if r[1] == "phishing"]
    lens_legit = [len(r[0]) for r in rows if r[1] == "legitimate"]
    print(f"\nWritten: {len(rows)} rows to {OUTPUT}")
    print(f"  Phish len: avg={sum(lens_phish)/len(lens_phish):.0f}, "
          f"med={sorted(lens_phish)[len(lens_phish)//2]}")
    print(f"  Legit len: avg={sum(lens_legit)/len(lens_legit):.0f}, "
          f"med={sorted(lens_legit)[len(lens_legit)//2]}")
    print("  100% real data. No synthetic data.")


if __name__ == "__main__":
    main()
