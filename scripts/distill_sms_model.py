#!/usr/bin/env python3
"""
Knowledge distillation for SMS phishing model.

Trains a smaller student model (d_model=64) using knowledge from a larger
teacher model (d_model=128, Chinese model).

Usage:
    python scripts/distill_sms_model.py --teacher chinese --student sms --epochs 10
    python scripts/distill_sms_model.py --teacher chinese --student sms --fresh
"""

import argparse
import os
import sys
import csv
import json
import time
from pathlib import Path

try:
    import torch
    import torch.nn as nn
    import torch.nn.functional as F
    import numpy as np
except ImportError:
    print("Error: torch and numpy are required. Install with:")
    print("  pip install torch numpy")
    sys.exit(1)

# Import training script components
sys.path.insert(0, str(Path(__file__).parent))
from train_phishing_model import (
    Config,
    BytePhishingTransformer,
    ByteTokenizer,
    PhishingCSVDataset,
    collate_fn,
    evaluate,
    export_to_onnx,
)


class DistillationConfig(Config):
    """Configuration for knowledge distillation."""
    
    def __init__(self, mode: str = "sms"):
        super().__init__(mode)
        
        # Distillation parameters
        self.temperature = 3.0  # Temperature for soft targets
        self.alpha = 0.5  # Weight for hard loss (1-alpha for soft loss)
        
        # Teacher model config (Chinese model)
        self.teacher_d_model = 128
        self.teacher_n_layers = 4
        self.teacher_n_heads = 4
        self.teacher_d_ff = 256
        
        # Student model config (SMS model)
        self.student_d_model = 64
        self.student_n_layers = 2
        self.student_n_heads = 2
        self.student_d_ff = 128


def distillation_loss(
    student_logits: torch.Tensor,
    teacher_logits: torch.Tensor,
    labels: torch.Tensor,
    temperature: float = 3.0,
    alpha: float = 0.5
) -> torch.Tensor:
    """
    Compute distillation loss.
    
    Args:
        student_logits: Raw logits from student model
        teacher_logits: Raw logits from teacher model
        labels: Ground truth labels
        temperature: Temperature for soft targets
        alpha: Weight for hard loss (1-alpha for soft loss)
    
    Returns:
        Combined distillation loss
    """
    # Hard loss: standard cross-entropy with ground truth
    hard_loss = F.binary_cross_entropy_with_logits(student_logits, labels)
    
    # Soft loss: KL divergence between student and teacher soft targets
    soft_student = F.log_softmax(student_logits / temperature, dim=0)
    soft_teacher = F.softmax(teacher_logits / temperature, dim=0)
    soft_loss = F.kl_div(soft_student, soft_teacher, reduction='batchmean') * (temperature ** 2)
    
    # Combined loss
    return alpha * hard_loss + (1 - alpha) * soft_loss


def train_distillation(
    teacher_model: BytePhishingTransformer,
    student_model: BytePhishingTransformer,
    train_texts: list,
    train_labels: list,
    val_texts: list,
    val_labels: list,
    config: DistillationConfig,
    device: str = "cuda",
    output_dir: str = "scripts/output/sms_distilled"
):
    """Train student model using knowledge distillation."""
    
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Move models to device
    teacher_model = teacher_model.to(device)
    student_model = student_model.to(device)
    
    # Freeze teacher model
    teacher_model.eval()
    for param in teacher_model.parameters():
        param.requires_grad = False
    
    # Create datasets
    tokenizer = ByteTokenizer()
    train_dataset = PhishingCSVDataset(
        train_texts, train_labels, tokenizer,
        max_len=config.max_seq_len, augment=False
    )
    val_dataset = PhishingCSVDataset(
        val_texts, val_labels, tokenizer,
        max_len=config.max_seq_len, augment=False
    )
    
    train_loader = torch.utils.data.DataLoader(
        train_dataset,
        batch_size=config.batch_size,
        shuffle=True,
        collate_fn=collate_fn
    )
    val_loader = torch.utils.data.DataLoader(
        val_dataset,
        batch_size=config.batch_size,
        shuffle=False,
        collate_fn=collate_fn
    )
    
    # Optimizer
    optimizer = torch.optim.AdamW(
        student_model.parameters(),
        lr=config.lr,
        weight_decay=config.weight_decay
    )
    
    # Training loop
    best_val_acc = 0.0
    best_epoch = 0
    
    print(f"\n{'='*60}")
    print(f"Knowledge Distillation Training")
    print(f"{'='*60}")
    print(f"Teacher: d_model={config.teacher_d_model}, layers={config.teacher_n_layers}")
    print(f"Student: d_model={config.student_d_model}, layers={config.student_n_layers}")
    print(f"Temperature: {config.temperature}")
    print(f"Alpha: {config.alpha}")
    print(f"{'='*60}\n")
    
    for epoch in range(config.num_epochs):
        # Training
        student_model.train()
        total_loss = 0
        num_batches = 0
        
        for batch in train_loader:
            input_ids = batch['input_ids'].to(device)
            labels = batch['labels'].to(device)
            
            # Forward pass through both models
            with torch.no_grad():
                teacher_logits = teacher_model(input_ids).squeeze()
            student_logits = student_model(input_ids).squeeze()
            
            # Compute distillation loss
            loss = distillation_loss(
                student_logits, teacher_logits, labels,
                temperature=config.temperature,
                alpha=config.alpha
            )
            
            # Backward pass
            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(student_model.parameters(), config.grad_clip)
            optimizer.step()
            
            total_loss += loss.item()
            num_batches += 1
        
        avg_loss = total_loss / num_batches if num_batches > 0 else 0
        
        # Validation
        val_acc, val_loss, val_f1 = evaluate(student_model, val_loader, device)
        
        print(f"Epoch {epoch+1}/{config.num_epochs} | "
              f"Loss: {avg_loss:.4f} | "
              f"Val Acc: {val_acc:.4f} | "
              f"Val Loss: {val_loss:.4f} | "
              f"Val F1: {val_f1:.4f}")
        
        # Save best model
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            best_epoch = epoch + 1
            
            # Save checkpoint
            checkpoint = {
                'epoch': epoch + 1,
                'model_state_dict': student_model.state_dict(),
                'optimizer_state_dict': optimizer.state_dict(),
                'val_acc': val_acc,
                'val_loss': val_loss,
                'val_f1': val_f1,
                'config': {
                    'd_model': config.student_d_model,
                    'n_layers': config.student_n_layers,
                    'n_heads': config.student_n_heads,
                    'd_ff': config.student_d_ff,
                    'vocab_size': config.vocab_size,
                    'max_seq_len': config.max_seq_len,
                }
            }
            torch.save(checkpoint, output_dir / "best_model.pt")
            print(f"  -> Saved best model (val_acc={val_acc:.4f})")
    
    print(f"\n{'='*60}")
    print(f"Training complete!")
    print(f"Best epoch: {best_epoch} (val_acc={best_val_acc:.4f})")
    print(f"{'='*60}\n")
    
    return best_val_acc


def main():
    parser = argparse.ArgumentParser(description="Knowledge distillation for SMS model")
    parser.add_argument("--teacher", default="chinese", help="Teacher model mode (default: chinese)")
    parser.add_argument("--student", default="sms", help="Student model mode (default: sms)")
    parser.add_argument("--epochs", type=int, default=10, help="Number of epochs")
    parser.add_argument("--fresh", action="store_true", help="Train from scratch (ignore checkpoints)")
    parser.add_argument("--device", default="cuda", help="Device (cuda or cpu)")
    parser.add_argument("--alpha", type=float, default=0.5, help="Weight for hard loss")
    parser.add_argument("--temperature", type=float, default=3.0, help="Temperature for soft targets")
    args = parser.parse_args()
    
    # Check device
    if args.device == "cuda" and not torch.cuda.is_available():
        print("CUDA not available, falling back to CPU")
        args.device = "cpu"
    
    # Load teacher model
    print(f"Loading teacher model ({args.teacher})...")
    teacher_config = Config(args.teacher)
    teacher_model = BytePhishingTransformer(teacher_config)
    
    teacher_checkpoint_path = Path(f"scripts/output/{args.teacher}/best_model.pt")
    if not teacher_checkpoint_path.exists():
        print(f"Error: Teacher model not found at {teacher_checkpoint_path}")
        print(f"Please train the teacher model first:")
        print(f"  python scripts/train_phishing_model.py --mode {args.teacher} --fresh")
        return
    
    teacher_checkpoint = torch.load(teacher_checkpoint_path, map_location=args.device)
    teacher_model.load_state_dict(teacher_checkpoint['model_state_dict'])
    teacher_model.eval()
    print(f"Teacher model loaded (val_acc={teacher_checkpoint.get('val_acc', 'N/A')})")
    
    # Create student model
    print(f"Creating student model ({args.student})...")
    student_config = DistillationConfig(args.student)
    student_model = BytePhishingTransformer(student_config)
    
    # Load student checkpoint if exists
    student_checkpoint_path = Path(f"scripts/output/{args.student}_distilled/best_model.pt")
    if not args.fresh and student_checkpoint_path.exists():
        print(f"Loading student checkpoint from {student_checkpoint_path}...")
        student_checkpoint = torch.load(student_checkpoint_path, map_location=args.device)
        student_model.load_state_dict(student_checkpoint['model_state_dict'])
        print(f"Student model loaded (val_acc={student_checkpoint.get('val_acc', 'N/A')})")
    
    # Load data
    print("Loading training data...")
    # Use SMS training data
    data_path = Path("scripts/raw_data/sms_spam/fbs_sms/fbs_sms_clean.csv")
    if not data_path.exists():
        print(f"Error: Training data not found at {data_path}")
        return
    
    texts = []
    labels = []
    with open(data_path, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if "text" in row and "label" in row:
                texts.append(row["text"])
                labels.append(float(row["label"]))
    
    print(f"Loaded {len(texts)} samples ({sum(labels):.0f} phishing, {len(labels) - sum(labels):.0f} legit)")
    
    # Split data
    split_idx = int(len(texts) * 0.9)
    train_texts, val_texts = texts[:split_idx], texts[split_idx:]
    train_labels, val_labels = labels[:split_idx], labels[split_idx:]
    
    # Train
    print("\nStarting knowledge distillation training...")
    best_acc = train_distillation(
        teacher_model, student_model,
        train_texts, train_labels,
        val_texts, val_labels,
        student_config,
        device=args.device,
        output_dir=f"scripts/output/{args.student}_distilled"
    )
    
    print(f"\nDistillation complete! Best validation accuracy: {best_acc:.4f}")
    print(f"Model saved to: scripts/output/{args.student}_distilled/best_model.pt")
    print(f"\nTo export to ONNX:")
    print(f"  python scripts/train_phishing_model.py --mode {args.student} --export")


if __name__ == "__main__":
    main()
