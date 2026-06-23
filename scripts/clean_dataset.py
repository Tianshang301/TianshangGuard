#!/usr/bin/env python3
"""
数据清洗脚本 - 第一轮清洗
Data cleaning pipeline for TianshangGuard ML model training.

Sources:
  1. PhiUSIIL Phishing URL Dataset (UCI) - 235,795 URLs with 54 features
  2. OpenPhish feed - 301 live phishing URLs

Output:
  - clean_dataset.csv     : 用于 ML 训练的文本-标签对 (URL+Title, label)
  - phishing_domains.csv  : 用于 DNS 引擎的钓鱼域名列表
  - legitimate_domains.csv: 用于 DNS 引擎的合法域名列表
  - cleaning_report.txt   : 清洗报告
"""

import pandas as pd
import os
import re
import hashlib
from datetime import datetime
from urllib.parse import urlparse

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "raw_data")
UCI_PATH = os.path.join(OUTPUT_DIR, "phiusiil", "PhiUSIIL_Phishing_URL_Dataset.csv")
OPENPHISH_PATH = os.path.join(OUTPUT_DIR, "openphish_feed.txt")
REPORT_PATH = os.path.join(OUTPUT_DIR, "cleaning_report.txt")

def load_uci() -> pd.DataFrame:
    """Load PhiUSIIL dataset from UCI."""
    print(f"[1/5] Loading UCI dataset from {UCI_PATH}")
    df = pd.read_csv(UCI_PATH, low_memory=False)
    print(f"      Raw shape: {df.shape}")
    return df

def load_openphish() -> pd.DataFrame:
    """Load OpenPhish phishing URLs."""
    print(f"[2/5] Loading OpenPhish from {OPENPHISH_PATH}")
    if not os.path.exists(OPENPHISH_PATH):
        print("      Not found, skipping.")
        return pd.DataFrame(columns=["URL", "label"])

    with open(OPENPHISH_PATH, "r", encoding="utf-8") as f:
        urls = [line.strip() for line in f if line.strip()]

    df = pd.DataFrame({"URL": urls, "label": 0})  # 0 = phishing
    print(f"      Loaded {len(df)} phishing URLs")
    return df

def clean(df: pd.DataFrame) -> pd.DataFrame:
    """
    Step 4 (user flow): Dedup, filter invalid URLs, extract domains, titles, labels.
    """
    print(f"[3/5] Cleaning dataset...")

    before = len(df)

    # 1. Drop rows without URL
    df = df.dropna(subset=["URL"])
    print(f"      After dropna(URL): {len(df)} (dropped {before - len(df)})")

    # 2. Standardize URL strings
    df["URL"] = df["URL"].astype(str).str.strip()

    # 3. Remove empty URLs
    empty = (df["URL"] == "") | (df["URL"] == "nan")
    before2 = len(df)
    df = df[~empty]
    print(f"      After empty URL filter: {len(df)} (dropped {before2 - len(df)})")

    # 4. Filter out URLs without scheme (invalid)
    def has_scheme(url):
        return bool(re.match(r"^https?://", url, re.IGNORECASE))

    valid = df["URL"].apply(has_scheme)
    before3 = len(df)
    df = df[valid]
    print(f"      After scheme filter: {len(df)} (dropped {before3 - len(df)})")

    # 5. Deduplicate by URL
    before4 = len(df)
    df = df.drop_duplicates(subset=["URL"])
    print(f"      After dedup: {len(df)} (dropped {before4 - len(df)})")

    # 6. Extract domain
    def extract_domain(url):
        try:
            return urlparse(url).netloc.lower()
        except Exception:
            return ""

    df["Domain"] = df["URL"].apply(extract_domain)

    # 7. Filter out empty domains
    no_domain = df["Domain"] == ""
    before5 = len(df)
    df = df[~no_domain]
    print(f"      After empty domain filter: {len(df)} (dropped {before5 - len(df)})")

    # 8. Standardize label: UCI has 1=legitimate, 0=phishing
    #    OpenPhish also uses 0=phishing
    #    Our model uses 1.0=phishing, 0.0=legitimate, so we need to INVERT
    if "label" in df.columns:
        # Invert: UCI/OpenPhish 0=phishing, 1=legitimate
        # Our model: 0=legitimate, 1=phishing
        # So label_model = 1 - label_uci
        # But first check which convention is used
        unique_labels = sorted(df["label"].dropna().unique())
        print(f"      Labels found: {unique_labels}")
        if set(unique_labels) == {0, 1}:
            df["label"] = 1 - df["label"]  # Invert to: 0=legit, 1=phish
            print(f"      Inverted labels to (0=legit, 1=phish)")
    else:
        df["label"] = 0  # Assume phishing if no label

    print(f"      Clean shape: {df.shape}")
    return df

def build_text(df: pd.DataFrame) -> pd.DataFrame:
    """
    Build text content for ML training from URL and Title.
    This simulates the "visible text extraction" step without actually crawling.
    """
    print(f"[4/5] Building text features for ML...")

    # Use Title if available, otherwise extract meaningful text from URL
    if "Title" in df.columns:
        df["Title"] = df["Title"].fillna("").astype(str)
    else:
        df["Title"] = ""

    # Build text: domain + path + title
    def make_text(row):
        url = row["URL"]
        title = row["Title"] if pd.notna(row.get("Title")) else ""
        # Extract path from URL
        try:
            parsed = urlparse(url)
            path = parsed.path.replace("/", " ").replace("-", " ").replace("_", " ")
            domain = parsed.netloc
            # Remove TLD from domain
            domain_parts = domain.split(".")
            if len(domain_parts) > 2:
                domain_name = " ".join(domain_parts[:-2])
            else:
                domain_name = domain_parts[0] if domain_parts else ""
            text = f"{domain_name} {path} {title}".strip()
        except Exception:
            text = title if title else url
        return text[:1000]  # Limit to 1000 chars

    df["text"] = df.apply(make_text, axis=1)

    # Remove rows with empty text
    empty_text = df["text"].str.strip() == ""
    print(f"      Empty text rows: {empty_text.sum()}")

    return df

def save_outputs(df: pd.DataFrame):
    """Save cleaned outputs to CSV files."""
    print(f"[5/5] Saving outputs...")

    # 1. ML training dataset (text + label)
    ml_cols = [c for c in ["text", "label", "URL", "Domain", "Title"] if c in df.columns]
    ml_path = os.path.join(OUTPUT_DIR, "clean_dataset.csv")
    df[ml_cols].to_csv(ml_path, index=False, encoding="utf-8")
    size_mb = os.path.getsize(ml_path) / 1e6
    print(f"      clean_dataset.csv: {len(df)} rows, {size_mb:.1f} MB")

    # 2. Phishing domains for DNS engine
    phish = df[df["label"] == 1]["Domain"].drop_duplicates().reset_index(drop=True)
    phish_path = os.path.join(OUTPUT_DIR, "phishing_domains.csv")
    phish.to_csv(phish_path, index=False, header=["domain"], encoding="utf-8")
    print(f"      phishing_domains.csv: {len(phish)} unique domains")

    # 3. Legitimate domains for DNS engine
    legit = df[df["label"] == 0]["Domain"].drop_duplicates().reset_index(drop=True)
    legit_path = os.path.join(OUTPUT_DIR, "legitimate_domains.csv")
    legit.to_csv(legit_path, index=False, header=["domain"], encoding="utf-8")
    print(f"      legitimate_domains.csv: {len(legit)} unique domains")

    # 4. Write cleaning report
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        f.write("=" * 60 + "\n")
        f.write("TianshangGuard - Dataset Cleaning Report\n")
        f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write("=" * 60 + "\n\n")
        f.write(f"Total samples: {len(df)}\n")
        f.write(f"  Phishing (label=1):    {(df['label'] == 1).sum()}\n")
        f.write(f"  Legitimate (label=0):  {(df['label'] == 0).sum()}\n")
        f.write(f"  Unique domains:       {df['Domain'].nunique()}\n\n")
        f.write(f"Output files:\n")
        f.write(f"  clean_dataset.csv       : {len(df)} rows, {size_mb:.1f} MB\n")
        f.write(f"  phishing_domains.csv    : {len(phish)} domains\n")
        f.write(f"  legitimate_domains.csv  : {len(legit)} domains\n")
        f.write("=" * 60 + "\n")

    print(f"      cleaning_report.txt written")


def main():
    print("=" * 60)
    print("TianshangGuard - Dataset Cleaning Pipeline")
    print("=" * 60)

    # Load
    uci = load_uci()
    openphish = load_openphish()

    # Merge
    print(f"\nMerging {len(uci)} (UCI) + {len(openphish)} (OpenPhish)...")
    df = pd.concat([uci, openphish], ignore_index=True)

    # Ensure label column
    if "label" not in df.columns:
        df["label"] = 0

    # Clean
    df = clean(df)

    # Build text
    df = build_text(df)

    # Final cleaning pass: remove rows with empty text
    before = len(df)
    df = df[df["text"].str.strip() != ""]
    print(f"      Final rows after empty text removal: {len(df)} (dropped {before - len(df)})")

    # Save
    save_outputs(df)

    print(f"\n{'=' * 60}")
    print(f"Done! Check {OUTPUT_DIR} for outputs.")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
