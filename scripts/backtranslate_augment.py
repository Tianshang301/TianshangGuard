#!/usr/bin/env python3
"""
Back-translation data augmentation for Chinese phishing SMS.
Translates Chinese → English → Chinese to generate diverse paraphrases.

Usage:
    python scripts/backtranslate_augment.py --input scripts/raw_data/chifraud/ --output scripts/raw_data/augmented/
    python scripts/backtranslate_augment.py --input scripts/raw_data/chifraud/chinese_real_dataset.csv --output scripts/raw_data/augmented/chinese_augmented.csv
"""

import argparse
import os
import sys
import csv
import time
from pathlib import Path

try:
    from transformers import MarianMTModel, MarianTokenizer
    import torch
except ImportError:
    print("Error: transformers and torch are required. Install with:")
    print("  pip install transformers torch")
    sys.exit(1)


def load_model(model_name: str, device: str = "cuda"):
    """Load MarianMT model and tokenizer."""
    print(f"Loading model: {model_name}")
    tokenizer = MarianTokenizer.from_pretrained(model_name)
    model = MarianMTModel.from_pretrained(model_name)
    model = model.to(device)
    model.eval()
    return model, tokenizer


def translate_batch(texts: list, model, tokenizer, device: str = "cuda", max_length: int = 512) -> list:
    """Translate a batch of texts."""
    inputs = tokenizer(texts, return_tensors="pt", padding=True, truncation=True, max_length=max_length)
    inputs = {k: v.to(device) for k, v in inputs.items()}
    
    with torch.no_grad():
        outputs = model.generate(**inputs, max_length=max_length, num_beams=4)
    
    translated = tokenizer.batch_decode(outputs, skip_special_tokens=True)
    return translated


def backtranslate(texts: list, zh_en_model, zh_en_tokenizer, en_zh_model, en_zh_tokenizer, 
                  device: str = "cuda", batch_size: int = 16) -> list:
    """Back-translate: Chinese → English → Chinese."""
    augmented = []
    total = len(texts)
    
    for i in range(0, total, batch_size):
        batch = texts[i:i + batch_size]
        
        # Chinese → English
        en_texts = translate_batch(batch, zh_en_model, zh_en_tokenizer, device)
        
        # English → Chinese
        zh_texts = translate_batch(en_texts, en_zh_model, en_zh_tokenizer, device)
        
        augmented.extend(zh_texts)
        
        if (i + batch_size) % 100 == 0 or i + batch_size >= total:
            print(f"  Progress: {min(i + batch_size, total)}/{total} ({100 * min(i + batch_size, total) / total:.1f}%)")
    
    return augmented


def compute_edit_distance(s1: str, s2: str) -> float:
    """Compute normalized edit distance between two strings."""
    if len(s1) == 0 and len(s2) == 0:
        return 0.0
    
    m, n = len(s1), len(s2)
    dp = [[0] * (n + 1) for _ in range(m + 1)]
    
    for i in range(m + 1):
        dp[i][0] = i
    for j in range(n + 1):
        dp[0][j] = j
    
    for i in range(1, m + 1):
        for j in range(1, n + 1):
            if s1[i-1] == s2[j-1]:
                dp[i][j] = dp[i-1][j-1]
            else:
                dp[i][j] = 1 + min(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
    
    max_len = max(m, n)
    return dp[m][n] / max_len if max_len > 0 else 0.0


def filter_quality(original: list, augmented: list, min_dist: float = 0.1, max_dist: float = 0.7) -> list:
    """Filter augmented samples by edit distance quality."""
    filtered = []
    for orig, aug in zip(original, augmented):
        dist = compute_edit_distance(orig, aug)
        if min_dist <= dist <= max_dist:
            filtered.append(aug)
    return filtered


def main():
    parser = argparse.ArgumentParser(description="Back-translation data augmentation for Chinese phishing SMS")
    parser.add_argument("--input", required=True, help="Input CSV file or directory")
    parser.add_argument("--output", required=True, help="Output CSV file or directory")
    parser.add_argument("--batch-size", type=int, default=16, help="Batch size for translation")
    parser.add_argument("--device", default="cuda", help="Device (cuda or cpu)")
    parser.add_argument("--min-dist", type=float, default=0.1, help="Minimum edit distance threshold")
    parser.add_argument("--max-dist", type=float, default=0.7, help="Maximum edit distance threshold")
    parser.add_argument("--sample-size", type=int, default=None, help="Limit number of samples to augment")
    args = parser.parse_args()
    
    # Check device
    if args.device == "cuda" and not torch.cuda.is_available():
        print("CUDA not available, falling back to CPU")
        args.device = "cpu"
    
    # Load models
    print("Loading translation models...")
    zh_en_model, zh_en_tokenizer = load_model("Helsinki-NLP/opus-mt-zh-en", args.device)
    en_zh_model, en_zh_tokenizer = load_model("Helsinki-NLP/opus-mt-en-zh", args.device)
    
    # Determine input/output paths
    input_path = Path(args.input)
    output_path = Path(args.output)
    
    if input_path.is_dir():
        # Process all CSV files in directory
        csv_files = list(input_path.glob("*.csv"))
        if not csv_files:
            print(f"No CSV files found in {input_path}")
            return
        
        output_path.mkdir(parents=True, exist_ok=True)
        
        for csv_file in csv_files:
            print(f"\nProcessing: {csv_file.name}")
            output_file = output_path / f"{csv_file.stem}_augmented.csv"
            process_csv(csv_file, output_file, zh_en_model, zh_en_tokenizer, 
                       en_zh_model, en_zh_tokenizer, args)
    else:
        # Process single file
        output_path.parent.mkdir(parents=True, exist_ok=True)
        process_csv(input_path, output_path, zh_en_model, zh_en_tokenizer,
                   en_zh_model, en_zh_tokenizer, args)
    
    print("\nDone!")


def process_csv(input_file: Path, output_file: Path, zh_en_model, zh_en_tokenizer,
                en_zh_model, en_zh_tokenizer, args):
    """Process a single CSV file."""
    # Read input
    texts = []
    labels = []
    
    with open(input_file, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if "text" in row and "label" in row:
                texts.append(row["text"])
                labels.append(row["label"])
    
    print(f"  Loaded {len(texts)} samples")
    
    # Limit sample size if specified
    if args.sample_size and len(texts) > args.sample_size:
        texts = texts[:args.sample_size]
        labels = labels[:args.sample_size]
        print(f"  Limited to {args.sample_size} samples")
    
    # Back-translate
    print("  Back-translating...")
    start_time = time.time()
    augmented_texts = backtranslate(texts, zh_en_model, zh_en_tokenizer,
                                   en_zh_model, en_zh_tokenizer, args.device, args.batch_size)
    elapsed = time.time() - start_time
    print(f"  Translation completed in {elapsed:.1f}s ({len(texts)/elapsed:.1f} samples/sec)")
    
    # Filter by quality
    print("  Filtering by quality...")
    filtered_texts = filter_quality(texts, augmented_texts, args.min_dist, args.max_dist)
    print(f"  Kept {len(filtered_texts)}/{len(augmented_texts)} samples after filtering")
    
    # Write output
    with open(output_file, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["text", "label"])
        writer.writeheader()
        
        # Original samples
        for text, label in zip(texts, labels):
            writer.writerow({"text": text, "label": label})
        
        # Augmented samples (use same labels as originals)
        for text in filtered_texts:
            writer.writerow({"text": text, "label": "1"})  # Assume phishing for augmented
    
    total_output = len(texts) + len(filtered_texts)
    print(f"  Output: {total_output} samples to {output_file}")


if __name__ == "__main__":
    main()
