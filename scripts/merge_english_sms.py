import csv
import os

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "raw_data", "sms_spam")
OUTPUT_FILE = os.path.join(OUTPUT_DIR, "english_sms_dataset.csv")

def load_uci():
    """UCI SMS Spam Collection: ham=0, spam=1"""
    rows = []
    path = os.path.join(OUTPUT_DIR, "SMSSpamCollection")
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.strip()
            if "\t" not in line:
                continue
            label, text = line.split("\t", 1)
            rows.append((text.strip(), 1 if label == "spam" else 0))
    return rows

def load_ncsu():
    """NCSU SMS Phishing: all phishing (label=1)"""
    rows = []
    path = os.path.join(OUTPUT_DIR, "sms-phishing-main", "phishing_messages.csv")
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = row.get("message", "").strip()
            if text and len(text) > 10:
                rows.append((text, 1))
    return rows

def load_imc25_english():
    """IMC25 Smishing: English only, all phishing (label=1)"""
    rows = []
    path = os.path.join(OUTPUT_DIR, "Smishing-Dataset-IMC25-main", "dataset", "final_dataset_output.csv")
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get("language", "").strip() != "English":
                continue
            text = row.get("translation", "").strip()
            if not text:
                text = row.get("text", "").strip()
            if text and len(text) > 10:
                rows.append((text, 1))
    return rows

def load_synthetic_legitimate():
    """Synthetic legitimate SMS: all legitimate (label=0)"""
    rows = []
    path = os.path.join(OUTPUT_DIR, "legitimate_sms_synthetic.csv")
    if not os.path.exists(path):
        return rows
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = row.get("text", "").strip()
            if text and len(text) > 10:
                rows.append((text, 0))
    return rows

def load_synthetic_phishing():
    """Synthetic phishing SMS: all phishing (label=1)"""
    rows = []
    path = os.path.join(OUTPUT_DIR, "phishing_sms_synthetic.csv")
    if not os.path.exists(path):
        return rows
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = row.get("text", "").strip()
            if text and len(text) > 10:
                rows.append((text, 1))
    return rows

def main():
    print("Loading UCI SMS Spam Collection...")
    uci = load_uci()
    uci_ham = sum(1 for _, l in uci if l == 0)
    uci_spam = sum(1 for _, l in uci if l == 1)
    print(f"  UCI: {len(uci)} total ({uci_ham} ham, {uci_spam} spam)")

    print("Loading NCSU SMS Phishing...")
    ncsu = load_ncsu()
    print(f"  NCSU: {len(ncsu)} phishing messages")

    print("Loading IMC25 Smishing (English only)...")
    imc25 = load_imc25_english()
    print(f"  IMC25: {len(imc25)} English phishing messages")

    print("Loading synthetic phishing SMS...")
    syn_phishing = load_synthetic_phishing()
    print(f"  Synthetic phishing: {len(syn_phishing)} messages")

    print("Loading synthetic legitimate SMS...")
    syn_legit = load_synthetic_legitimate()
    print(f"  Synthetic legitimate: {len(syn_legit)} messages")

    all_rows = uci + ncsu + imc25 + syn_phishing + syn_legit
    total_phishing = sum(1 for _, l in all_rows if l == 1)
    total_legit = sum(1 for _, l in all_rows if l == 0)

    print(f"\nTotal: {len(all_rows)} messages ({total_phishing} phishing, {total_legit} legitimate)")

    print(f"Writing to {OUTPUT_FILE}...")
    with open(OUTPUT_FILE, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["text", "label"])
        for text, label in all_rows:
            text = text.replace("\n", " ").replace("\r", "").strip()
            if text:
                writer.writerow([text, label])

    print("Done!")

if __name__ == "__main__":
    main()
