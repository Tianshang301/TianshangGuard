import csv
import os
import hashlib

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "raw_data", "sms_spam")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "english_sms_dataset.csv")
OUTPUT_FILE_DEDUPED = os.path.join(OUTPUT_DIR, "english_sms_deduped.csv")

SOURCE_PRIORITY = {
    "uci": 0,
    "ncsu": 1,
    "imc25": 2,
    "combined84k": 3,
    "syn_phishing": 4,
    "syn_legit": 5,
}

def normalize_text(text):
    return text.replace("\n", " ").replace("\r", "").strip()

def load_uci():
    rows = []
    path = os.path.join(OUTPUT_DIR, "SMSSpamCollection")
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if "\t" not in line:
                continue
            label, text = line.split("\t", 1)
            text = normalize_text(text)
            if text and len(text) > 3:
                rows.append((text, 1 if label == "spam" else 0, "uci"))
    return rows

def load_ncsu():
    rows = []
    path = os.path.join(OUTPUT_DIR, "sms-phishing-main", "phishing_messages.csv")
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = normalize_text(row.get("message", ""))
            if text and len(text) > 10:
                rows.append((text, 1, "ncsu"))
    return rows

def load_imc25_english():
    rows = []
    path = os.path.join(OUTPUT_DIR, "Smishing-Dataset-IMC25-main", "dataset", "final_dataset_output.csv")
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get("language", "").strip() != "English":
                continue
            text = normalize_text(row.get("translation", "") or row.get("text", ""))
            if text and len(text) > 10:
                rows.append((text, 1, "imc25"))
    return rows

def load_combined84k():
    rows = []
    path = os.path.join(OUTPUT_DIR, "combined_smishing", "Combined-Labeled-Dataset.csv")
    if not os.path.exists(path):
        return rows
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = normalize_text(row.get("message", ""))
            smishing = row.get("smishing label", "0").strip()
            if text and len(text) > 10:
                rows.append((text, 1 if smishing == "1" else 0, "combined84k"))
    return rows

def load_synthetic_legitimate():
    rows = []
    path = os.path.join(OUTPUT_DIR, "legitimate_sms_synthetic.csv")
    if not os.path.exists(path):
        return rows
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = normalize_text(row.get("text", ""))
            if text and len(text) > 10:
                rows.append((text, 0, "syn_legit"))
    return rows

def load_synthetic_phishing():
    rows = []
    path = os.path.join(OUTPUT_DIR, "phishing_sms_synthetic.csv")
    if not os.path.exists(path):
        return rows
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = normalize_text(row.get("text", ""))
            if text and len(text) > 10:
                rows.append((text, 1, "syn_phishing"))
    return rows

def main():
    loaders = [
        ("UCI SMS Spam Collection", load_uci),
        ("NCSU SMS Phishing", load_ncsu),
        ("IMC25 Smishing (English)", load_imc25_english),
        ("Combined 84K Smishing", load_combined84k),
        ("Synthetic phishing", load_synthetic_phishing),
        ("Synthetic legitimate", load_synthetic_legitimate),
    ]

    all_rows = []
    for name, loader in loaders:
        print(f"Loading {name}...")
        loaded = loader()
        phish = sum(1 for _, l, _ in loaded if l == 1)
        legit = sum(1 for _, l, _ in loaded if l == 0)
        print(f"  {len(loaded)} rows ({phish} phishing, {legit} legitimate)")
        all_rows.extend(loaded)

    total_before = len(all_rows)
    phish_before = sum(1 for _, l, _ in all_rows if l == 1)
    legit_before = sum(1 for _, l, _ in all_rows if l == 0)
    print(f"\nBefore dedup: {total_before} rows ({phish_before} phishing, {legit_before} legitimate)")

    text_best = {}
    stats = {"conflicts": 0, "conflict_resolved_phish": 0, "conflict_resolved_legit": 0}

    for text, label, source in all_rows:
        if text in text_best:
            existing_label, existing_source = text_best[text]
            if existing_label != label:
                stats["conflicts"] += 1
                current_prio = SOURCE_PRIORITY.get(source, 99)
                existing_prio = SOURCE_PRIORITY.get(existing_source, 99)
                if current_prio < existing_prio:
                    text_best[text] = (label, source)
                    if label == 0:
                        stats["conflict_resolved_legit"] += 1
                    else:
                        stats["conflict_resolved_phish"] += 1
            continue
        text_best[text] = (label, source)

    deduped = [(text, label, source) for text, (label, source) in text_best.items()]

    total_after = len(deduped)
    phish_after = sum(1 for _, l, _ in deduped if l == 1)
    legit_after = sum(1 for _, l, _ in deduped if l == 0)

    print(f"After dedup:  {total_after} rows ({phish_after} phishing, {legit_after} legitimate)")
    print(f"Duplicates removed: {total_before - total_after}")
    print(f"Label conflicts found: {stats['conflicts']}")
    print(f"  Resolved to phishing: {stats['conflict_resolved_phish']}")
    print(f"  Resolved to legitimate: {stats['conflict_resolved_legit']}")

    print(f"\nWriting full (deduped) output to {OUTPUT_FILE}...")
    with open(OUTPUT_FILE, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f, quoting=csv.QUOTE_ALL)
        writer.writerow(["text", "label"])
        for text, label, _ in deduped:
            writer.writerow([text, label])

    print("Done!")

if __name__ == "__main__":
    main()
