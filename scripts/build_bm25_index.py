"""
Build BM25 inverted index for Android retrieval engine.

Input: ChiFraud + FBS SMS + English SMS datasets
Output: index.bin (pre-computed inverted index for Android)

Usage: python scripts/build_bm25_index.py
"""

import csv
import math
import struct
import os
import zlib
from collections import defaultdict

# Paths
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
RAW_DATA_DIR = os.path.join(BASE_DIR, "raw_data")
OUTPUT_DIR = os.path.join(BASE_DIR, "..", "app", "src", "main", "assets", "knowledge_base")

# BM25 parameters
K1 = 1.5
B = 0.75
MAX_POSTINGS_PER_TOKEN = 100  # Keep only top-K postings per token


def tokenize(text: str) -> list[str]:
    """N-gram tokenizer for Chinese + English."""
    tokens = []
    chars = []

    for ch in text:
        if '\u4e00' <= ch <= '\u9fff':  # CJK
            chars.append(ch)
        elif ch.isalnum():  # English/digits
            chars.append(ch.lower())
        else:
            if chars:
                _add_ngram_tokens(chars, tokens)
                chars = []
    if chars:
        _add_ngram_tokens(chars, tokens)

    return tokens


def _add_ngram_tokens(chars: list[str], tokens: list[str]):
    """Extract 2-gram and 3-gram tokens from character sequence."""
    s = ''.join(chars)
    if len(s) <= 2:
        tokens.append(s)
    else:
        # Bigrams
        for i in range(len(s) - 1):
            tokens.append(s[i:i+2])
        # Trigrams
        for i in range(len(s) - 2):
            tokens.append(s[i:i+3])


def load_chifraud() -> list[tuple[str, int]]:
    """Load ChiFraud dataset. Returns (text, label) pairs."""
    samples = []
    for fname in ["chinese_real_clean.csv"]:
        fpath = os.path.join(RAW_DATA_DIR, fname)
        if not os.path.exists(fpath):
            print(f"  Skipping {fname} (not found)")
            continue
        with open(fpath, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                text = row.get("text", row.get("content", "")).strip()
                label = int(row.get("label", 0))
                if text:
                    samples.append((text, label))
    return samples


def load_fbs_sms() -> list[tuple[str, int]]:
    """Load FBS SMS dataset. Returns (text, label=1) pairs."""
    samples = []
    fbs_dir = os.path.join(RAW_DATA_DIR, "sms_spam", "fbs_sms")
    if not os.path.exists(fbs_dir):
        print(f"  Skipping FBS (not found)")
        return samples
    for fname in os.listdir(fbs_dir):
        if not fname.endswith(".txt"):
            continue
        fpath = os.path.join(fbs_dir, fname)
        with open(fpath, "r", encoding="utf-8") as f:
            for line in f:
                text = line.strip()
                if text:
                    samples.append((text, 1))
    return samples


def load_english_sms() -> list[tuple[str, int]]:
    """Load English SMS dataset. Returns (text, label) pairs."""
    samples = []
    fpath = os.path.join(RAW_DATA_DIR, "sms_spam", "english_sms_dataset.csv")
    if not os.path.exists(fpath):
        print(f"  Skipping English SMS (not found)")
        return samples
    with open(fpath, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            text = row.get("text", row.get("content", "")).strip()
            label = int(row.get("label", 0))
            if text:
                samples.append((text, label))
    return samples


def build_bm25_index(documents: list[tuple[str, int]]):
    """
    Build BM25 inverted index.
    
    Returns:
        index: dict[token] -> list[(doc_id, score)]
        doc_labels: list[int] - label for each doc
        doc_count: int
    """
    doc_count = len(documents)
    if doc_count == 0:
        return {}, [], 0

    # Step 1: Compute document frequencies and average document length
    doc_freqs = defaultdict(int)  # token -> number of documents containing it
    doc_lengths = []
    doc_token_counts = []  # list of dict[token -> count] per document

    for text, _ in documents:
        tokens = tokenize(text)
        doc_lengths.append(len(tokens))
        token_counts = defaultdict(int)
        for t in tokens:
            token_counts[t] += 1
        doc_token_counts.append(token_counts)
        for t in set(tokens):
            doc_freqs[t] += 1

    avg_dl = sum(doc_lengths) / doc_count if doc_count > 0 else 1

    # Step 2: Build inverted index with BM25 scores
    # For each token, store (doc_id, bm25_score) for documents containing it
    index = defaultdict(list)

    for doc_id, token_counts in enumerate(doc_token_counts):
        dl = doc_lengths[doc_id]
        for token, tf in token_counts.items():
            df = doc_freqs[token]
            # BM25 score for this term in this document
            idf = math.log((doc_count - df + 0.5) / (df + 0.5) + 1.0)
            tf_norm = (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * dl / avg_dl))
            score = idf * tf_norm
            # Store as fixed-point (multiply by 1000, store as uint16)
            score_int = min(int(score * 1000), 65535)
            index[token].append((doc_id, score_int))

    doc_labels = [label for _, label in documents]
    return index, doc_labels, doc_count


def export_index(index, doc_labels, doc_count, output_path):
    """
    Export BM25 index as compressed binary file.
    
    Format (before zlib compression):
    - Header: uint32 doc_count
    - Doc labels: uint8[doc_count] (0=legit, 1=phishing)
    - Index entries: for each token:
        - uint16 token_bytes_len
        - bytes[token_bytes_len] (UTF-8 token)
        - uint32 postings_count
        - postings: (uint32 doc_id, uint16 score) * postings_count
    """
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    # Build raw bytes
    raw = bytearray()
    raw.extend(struct.pack("<I", doc_count))

    for label in doc_labels:
        raw.extend(struct.pack("<B", label))

    for token, postings in sorted(index.items()):
        # Keep only top-K postings by score
        top_postings = sorted(postings, key=lambda x: x[1], reverse=True)[:MAX_POSTINGS_PER_TOKEN]
        token_bytes = token.encode("utf-8")
        raw.extend(struct.pack("<H", len(token_bytes)))
        raw.extend(token_bytes)
        raw.extend(struct.pack("<I", len(top_postings)))
        for doc_id, score in top_postings:
            raw.extend(struct.pack("<IH", doc_id, score))

    # Compress with zlib
    compressed = zlib.compress(raw, level=9)

    with open(output_path, "wb") as f:
        f.write(compressed)

    print(f"  Exported to {output_path}")
    print(f"  Documents: {doc_count}")
    print(f"  Unique tokens: {len(index)}")
    print(f"  Max postings per token: {MAX_POSTINGS_PER_TOKEN}")
    raw_kb = len(raw) / 1024
    compressed_kb = len(compressed) / 1024
    print(f"  Raw size: {raw_kb:.1f} KB")
    print(f"  Compressed size: {compressed_kb:.1f} KB")


def main():
    print("=== BM25 Index Builder ===")

    print("\n1. Loading datasets...")
    samples = []
    samples.extend(load_chifraud())
    print(f"  ChiFraud: {len(samples)} samples")
    fbs = load_fbs_sms()
    samples.extend(fbs)
    print(f"  FBS SMS: {len(fbs)} samples")
    english = load_english_sms()
    samples.extend(english)
    print(f"  English SMS: {len(english)} samples")
    print(f"  Total: {len(samples)} samples")

    if len(samples) == 0:
        print("ERROR: No data loaded. Check paths.")
        return

    print("\n2. Building BM25 index...")
    index, doc_labels, doc_count = build_bm25_index(samples)

    print("\n3. Exporting index...")
    output_path = os.path.join(OUTPUT_DIR, "index.bin")
    export_index(index, doc_labels, doc_count, output_path)

    print("\nDone!")


if __name__ == "__main__":
    main()
