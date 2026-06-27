#!/usr/bin/env python3
"""
Train a BPE (Byte Pair Encoding) tokenizer for TianshangGuard.

Usage:
    python scripts/train_bpe_tokenizer.py --input scripts/raw_data/chifraud/chinese_real_dataset.csv --vocab_size 4096
    python scripts/train_bpe_tokenizer.py --input scripts/raw_data/chifraud/ --vocab_size 4096 --output app/src/main/assets/tokenizer/
"""

import argparse
import os
import sys
import csv
import json
from pathlib import Path

try:
    import sentencepiece as spm
except ImportError:
    print("Error: sentencepiece is required. Install with:")
    print("  pip install sentencepiece")
    sys.exit(1)


def collect_texts(input_path: str) -> list:
    """Collect all texts from CSV files."""
    texts = []
    input_path = Path(input_path)
    
    if input_path.is_file():
        csv_files = [input_path]
    elif input_path.is_dir():
        csv_files = list(input_path.glob("*.csv"))
    else:
        print(f"Error: {input_path} does not exist")
        return []
    
    for csv_file in csv_files:
        print(f"Reading: {csv_file.name}")
        with open(csv_file, "r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                if "text" in row and row["text"]:
                    texts.append(row["text"].strip())
    
    print(f"Total texts: {len(texts)}")
    return texts


def train_bpe(texts: list, vocab_size: int, output_dir: str, model_prefix: str = "bpe_tokenizer"):
    """Train BPE tokenizer using sentencepiece."""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Write texts to temporary file for sentencepiece
    temp_file = output_dir / "temp_texts.txt"
    with open(temp_file, "w", encoding="utf-8") as f:
        for text in texts:
            f.write(text + "\n")
    
    print(f"Training BPE tokenizer with vocab_size={vocab_size}...")
    
    # Train sentencepiece model
    spm.SentencePieceTrainer.train(
        input=str(temp_file),
        model_prefix=str(output_dir / model_prefix),
        vocab_size=vocab_size,
        character_coverage=0.9995,
        model_type="bpe",
        pad_id=0,
        unk_id=1,
        bos_id=2,
        eos_id=3,
        pad_piece="[PAD]",
        unk_piece="[UNK]",
        bos_piece="[CLS]",
        eos_piece="[SEP]",
        user_defined_symbols=["[MASK]"],
        byte_fallback=True,
        split_digits=True,
        normalization_rule_name="identity",
        add_dummy_prefix=False,
    )
    
    # Clean up temp file
    temp_file.unlink()
    
    print(f"Tokenizer saved to: {output_dir / model_prefix}.model")
    print(f"Vocab file saved to: {output_dir / model_prefix}.vocab")
    
    return output_dir / f"{model_prefix}.model"


def export_vocab(model_path: str, output_path: str):
    """Export vocabulary to JSON for Kotlin implementation."""
    sp = spm.SentencePieceProcessor()
    sp.load(model_path)
    
    vocab = {}
    for i in range(sp.get_piece_size()):
        piece = sp.id_to_piece(i)
        vocab[piece] = i
    
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(vocab, f, ensure_ascii=False, indent=2)
    
    print(f"Vocabulary exported to: {output_path}")
    print(f"Vocabulary size: {len(vocab)}")
    
    # Test tokenization
    test_texts = [
        "您的银行账户涉嫌洗钱",
        "Your account has been compromised",
        "点击链接领取奖品",
        "http://phishing-site.com/verify",
        "10086",
    ]
    
    print("\nTokenization tests:")
    for text in test_texts:
        tokens = sp.encode(text, out_type=int)
        pieces = sp.encode(text, out_type=str)
        print(f"  '{text}' -> {tokens}")
        print(f"    pieces: {pieces}")


def main():
    parser = argparse.ArgumentParser(description="Train BPE tokenizer for TianshangGuard")
    parser.add_argument("--input", required=True, help="Input CSV file or directory")
    parser.add_argument("--vocab_size", type=int, default=4096, help="Vocabulary size (default: 4096)")
    parser.add_argument("--output", default="app/src/main/assets/tokenizer/", help="Output directory")
    parser.add_argument("--model_prefix", default="bpe_tokenizer", help="Model file prefix")
    parser.add_argument("--export_vocab", action="store_true", help="Export vocabulary to JSON")
    args = parser.parse_args()
    
    # Collect texts
    texts = collect_texts(args.input)
    if not texts:
        print("Error: No texts found")
        return
    
    # Train tokenizer
    model_path = train_bpe(texts, args.vocab_size, args.output, args.model_prefix)
    
    # Export vocabulary if requested
    if args.export_vocab:
        vocab_path = Path(args.output) / f"{args.model_prefix}_vocab.json"
        export_vocab(str(model_path), str(vocab_path))
    
    print("\nDone!")


if __name__ == "__main__":
    main()
