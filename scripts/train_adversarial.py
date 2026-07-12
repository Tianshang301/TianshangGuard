"""
TianshangGuard - Adversarial Training (FGSM on embeddings)
Improves model robustness against adversarial perturbations.

Usage:
  python train_adversarial.py --mode chinese --epsilon 0.5
  python train_adversarial.py --mode sms --epsilon 0.3
  python train_adversarial.py --mode english --epsilon 0.3
"""

import os, sys, math, random, argparse
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
from torch.amp import autocast, GradScaler

SEED = 42
random.seed(SEED)
np.random.seed(SEED)
torch.manual_seed(SEED)
torch.cuda.manual_seed_all(SEED)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {device}")
if device.type == "cuda":
    print(f"  GPU: {torch.cuda.get_device_name(0)}")
    print(f"  Memory: {torch.cuda.get_device_properties(0).total_memory / 1e9:.1f} GB")

base_dir = os.path.dirname(__file__)
sys.path.insert(0, base_dir)

from train_phishing_model import (
    Config, ByteTokenizer, BytePhishingTransformer,
    PhishingCSVDataset, PhishingDataset,
    FocalLoss,
    save_checkpoint, load_checkpoint,
)
from onnxruntime.quantization import quantize_dynamic, QuantType

tokenizer = ByteTokenizer()


def fgsm_attack(model, input_ids, labels, epsilon, criterion):
    """Fast Gradient Sign Method on embedding layer."""
    input_ids.requires_grad = True
    logits = model(input_ids)
    loss = criterion(logits, labels)
    model.zero_grad()
    loss.backward()
    grad = input_ids.grad.data
    perturbation = epsilon * grad.sign()
    perturbed_ids = input_ids + perturbation
    return perturbed_ids.detach()


def pgd_attack(model, input_ids, labels, epsilon, alpha, num_iter, criterion):
    """Projected Gradient Descent on embedding layer."""
    ori_embed = model.embedding(input_ids).detach()
    delta = torch.zeros_like(ori_embed).uniform_(-epsilon, epsilon)
    delta.requires_grad = True

    for _ in range(num_iter):
        emb = ori_embed + delta
        x = emb + model.pos_embedding[:, :input_ids.size(1), :]
        x = model.transformer(x)
        cls_token = x[:, 0, :]
        pooled = model.pooler(cls_token)
        logit = model.classifier(pooled).squeeze(-1)
        loss = criterion(logit, labels)
        loss.backward()
        grad = delta.grad.data
        delta.data = delta + alpha * grad.sign()
        delta.data = torch.clamp(delta.data, -epsilon, epsilon)
        delta.data = torch.clamp(ori_embed + delta.data, -1, 1) - ori_embed
        delta.grad.zero_()

    final_emb = ori_embed + delta.detach()
    x = final_emb + model.pos_embedding[:, :input_ids.size(1), :]
    x = model.transformer(x)
    cls_token = x[:, 0, :]
    pooled = model.pooler(cls_token)
    logit = model.classifier(pooled).squeeze(-1)
    return logit


def load_dataset(config):
    """Reuse data loading from train_phishing_model."""
    mode = config.mode
    base_path = os.path.join(base_dir, "raw_data")

    if mode == "url":
        csv_path = os.path.join(base_path, "clean_dataset.csv")
        if os.path.exists(csv_path):
            train_ds = PhishingCSVDataset(csv_path, split="train", max_seq_len=config.max_seq_len)
            val_ds = PhishingCSVDataset(csv_path, split="val", max_seq_len=config.max_seq_len)
        else:
            train_ds = PhishingDataset(20000, max_seq_len=config.max_seq_len)
            val_ds = PhishingDataset(2000, max_seq_len=config.max_seq_len)

    elif mode == "english":
        import pandas as pd
        csv_path = os.path.join(base_path, "sms_spam", "english_sms_dataset.csv")
        df = pd.read_csv(csv_path).dropna(subset=["text"])
        df["text"] = df["text"].astype(str)
        df["label"] = df["label"].astype(float)
        phishing = df[df["label"] == 1.0]
        legit = df[df["label"] == 0.0]
        n = min(len(phishing), len(legit))
        df_bal = pd.concat([phishing.sample(n, random_state=42), legit.sample(n, random_state=42)])
        df_bal = df_bal.sample(frac=1, random_state=42).reset_index(drop=True)
        split = int(len(df_bal) * 0.9)

        class DFDS(Dataset):
            def __init__(self, df, max_len):
                self.texts = df["text"].tolist()
                self.labels = df["label"].tolist()
                self.max_len = max_len
            def __len__(self): return len(self.texts)
            def __getitem__(self, i):
                t = tokenizer.encode(self.texts[i], self.max_len)
                return torch.tensor(t, dtype=torch.long), torch.tensor(self.labels[i], dtype=torch.float)

        train_ds = DFDS(df_bal.iloc[:split], config.max_seq_len)
        val_ds = DFDS(df_bal.iloc[split:], config.max_seq_len)

    elif mode == "sms":
        import pandas as pd
        clean_csv = os.path.join(base_path, "chinese_real_clean.csv")
        df = pd.read_csv(clean_csv).dropna(subset=["text"])
        df["text"] = df["text"].astype(str)
        df["label"] = df["label"].astype(float)
        df_short = df[df["text"].str.len() < 200]
        phishing = df_short[df_short["label"] == 1.0]
        legit = df_short[df_short["label"] == 0.0]
        n_phish = len(phishing)
        legit_sampled = legit.sample(min(len(legit), n_phish * 3), random_state=42)
        df_bal = pd.concat([phishing, legit_sampled]).sample(frac=1, random_state=42).reset_index(drop=True)
        split = int(len(df_bal) * 0.9)

        class DFDS(Dataset):
            def __init__(self, df, max_len):
                self.texts = df["text"].tolist()
                self.labels = df["label"].tolist()
                self.max_len = max_len
            def __len__(self): return len(self.texts)
            def __getitem__(self, i):
                t = tokenizer.encode(self.texts[i], self.max_len)
                return torch.tensor(t, dtype=torch.long), torch.tensor(self.labels[i], dtype=torch.float)

        train_ds = DFDS(df_bal.iloc[:split], config.max_seq_len)
        val_ds = DFDS(df_bal.iloc[split:], config.max_seq_len)

    else:  # chinese
        import pandas as pd
        csv_path = os.path.join(base_path, "chinese_real_clean.csv")
        df = pd.read_csv(csv_path).dropna(subset=["text"])
        df["text"] = df["text"].astype(str)
        df["label"] = df["label"].astype(float)
        phishing = df[df["label"] == 1.0]
        legit = df[df["label"] == 0.0]
        n_phish = len(phishing)
        legit_sampled = legit.sample(min(len(legit), n_phish * 4), random_state=42)
        df_bal = pd.concat([phishing, legit_sampled]).sample(frac=1, random_state=42).reset_index(drop=True)
        split = int(len(df_bal) * 0.9)

        class DFDS(Dataset):
            def __init__(self, df, max_len):
                self.texts = df["text"].tolist()
                self.labels = df["label"].tolist()
                self.max_len = max_len
            def __len__(self): return len(self.texts)
            def __getitem__(self, i):
                t = tokenizer.encode(self.texts[i], self.max_len)
                return torch.tensor(t, dtype=torch.long), torch.tensor(self.labels[i], dtype=torch.float)

        train_ds = DFDS(df_bal.iloc[:split], config.max_seq_len)
        val_ds = DFDS(df_bal.iloc[split:], config.max_seq_len)

    print(f"  {mode}: {len(train_ds)} train, {len(val_ds)} val")
    return train_ds, val_ds


def train_adversarial(mode="chinese", epsilon=0.5, adv_ratio=0.5, num_epochs=5):
    config = Config(mode)
    config.num_epochs = num_epochs
    os.makedirs(config.output_dir, exist_ok=True)

    model = BytePhishingTransformer(config).to(device)
    criterion = FocalLoss(alpha=0.25, gamma=2.0)
    optimizer = torch.optim.AdamW(model.parameters(), lr=1e-4, weight_decay=config.weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=num_epochs)
    scaler = GradScaler("cuda")

    start_epoch = 0
    best_val_loss = float("inf")

    if os.path.exists(config.checkpoint_path):
        print(f"Loading checkpoint: {config.checkpoint_path}")
        start_epoch, best_val_loss = load_checkpoint(config, model, optimizer, scheduler, scaler)
        print(f"  Resumed from epoch {start_epoch}, best_val_loss={best_val_loss:.4f}")
    else:
        print("No checkpoint found, starting from scratch")

    train_ds, val_ds = load_dataset(config)
    train_loader = DataLoader(train_ds, batch_size=config.batch_size, shuffle=True, num_workers=0, pin_memory=True)
    val_loader = DataLoader(val_ds, batch_size=config.batch_size * 2, shuffle=False, num_workers=0, pin_memory=True)

    total_batches = len(train_loader)

    for epoch in range(start_epoch, num_epochs):
        model.train()
        total_loss = 0
        correct = 0
        total = 0

        pbar = tqdm(train_loader, desc=f"Epoch {epoch+1}/{num_epochs}")
        for batch_idx, (input_ids, labels) in enumerate(pbar):
            input_ids, labels = input_ids.to(device), labels.to(device)
            batch_size = input_ids.size(0)
            adv_batch_size = max(1, int(batch_size * adv_ratio))

            optimizer.zero_grad()

            # ── Clean forward ──
            with autocast("cuda"):
                logits = model(input_ids[:batch_size - adv_batch_size])
                loss_clean = criterion(logits, labels[:batch_size - adv_batch_size])

            if adv_batch_size > 0 and batch_size - adv_batch_size > 0:
                # ── Adversarial forward (FGSM on embeddings) ──
                adv_ids = input_ids[batch_size - adv_batch_size:]
                adv_labels = labels[batch_size - adv_batch_size:]

                emb = model.embedding(adv_ids)
                emb.requires_grad_(True)

                pos = model.pos_embedding[:, :adv_ids.size(1), :]
                x = emb + pos
                x = model.transformer(x)
                cls_token = x[:, 0, :]
                pooled = model.pooler(cls_token)
                adv_logits = model.classifier(pooled).squeeze(-1)
                loss_adv = criterion(adv_logits, adv_labels)
                loss_adv.backward(retain_graph=True)

                grad = emb.grad.data
                perturbation = epsilon * grad.sign()
                emb_adv = emb + perturbation

                with autocast("cuda"):
                    x_adv = emb_adv + pos
                    x_adv = model.transformer(x_adv)
                    cls_adv = x_adv[:, 0, :]
                    pooled_adv = model.pooler(cls_adv)
                    adv_logits_adv = model.classifier(pooled_adv).squeeze(-1)
                    loss_adv_final = criterion(adv_logits_adv, adv_labels)

                loss = loss_clean + loss_adv_final
            else:
                loss = loss_clean

            scaler.scale(loss).backward()
            scaler.unscale_(optimizer)
            torch.nn.utils.clip_grad_norm_(model.parameters(), config.grad_clip)
            scaler.step(optimizer)
            scaler.update()

            total_loss += loss.item()
            preds = (torch.sigmoid(logits.detach()) > 0.5).float()
            correct += (preds == labels[:batch_size - adv_batch_size]).sum().item()
            total += (batch_size - adv_batch_size)

            pbar.set_postfix({
                "loss": f"{loss.item():.4f}",
                "acc": f"{correct/max(total,1):.3f}",
                "lr": f"{scheduler.get_last_lr()[0]:.2e}"
            })

        scheduler.step()

        avg_loss = total_loss / total_batches
        acc = correct / max(total, 1)
        print(f"  Train: loss={avg_loss:.4f} acc={acc:.3f}")

        # ── Validation ──
        model.eval()
        val_loss = 0
        val_correct = 0
        val_total = 0
        with torch.no_grad():
            for input_ids, labels in val_loader:
                input_ids, labels = input_ids.to(device), labels.to(device)
                with autocast("cuda"):
                    logits = model(input_ids)
                    loss = criterion(logits, labels)
                val_loss += loss.item()
                preds = (torch.sigmoid(logits) > 0.5).float()
                val_correct += (preds == labels).sum().item()
                val_total += labels.size(0)

        val_loss /= len(val_loader)
        val_acc = val_correct / max(val_total, 1)
        print(f"  Val:   loss={val_loss:.4f} acc={val_acc:.3f}")

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            save_checkpoint(config, model, optimizer, scheduler, epoch + 1, best_val_loss, scaler)
            print(f"  -> New best checkpoint saved")

    # ── Export to ONNX ──
    print(f"\nExporting {mode} model to ONNX...")
    model.eval()
    dummy = torch.randint(0, 256, (1, config.max_seq_len), dtype=torch.long).to(device)
    onnx_path = config.onnx_path

    torch.onnx.export(
        model,
        dummy,
        onnx_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
        opset_version=17,
        do_constant_folding=True,
    )

    # Validate
    onnx_model = onnx.load(onnx_path)
    onnx.checker.check_model(onnx_model)
    print(f"  ONNX exported: {onnx_path} ({os.path.getsize(onnx_path)} bytes)")

    # Quantize
    quant_path = onnx_path.replace(".onnx", "_quant.onnx")
    quantize_dynamic(onnx_path, quant_path, weight_type=QuantType.QUInt8)
    qsize = os.path.getsize(quant_path)
    print(f"  INT8 quantized: {quant_path} ({qsize} bytes)")

    return best_val_loss


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", default="chinese", choices=["url", "english", "sms", "chinese"])
    parser.add_argument("--epsilon", type=float, default=0.5, help="FGSM perturbation magnitude")
    parser.add_argument("--adv-ratio", type=float, default=0.5, help="Fraction of batch used for adversarial")
    parser.add_argument("--epochs", type=int, default=5, help="Number of adversarial training epochs")
    args = parser.parse_args()

    print(f"\n{'='*60}")
    print(f"  Adversarial Training: {args.mode}")
    print(f"  Epsilon={args.epsilon}, AdvRatio={args.adv_ratio}, Epochs={args.epochs}")
    print(f"{'='*60}\n")

    best_loss = train_adversarial(
        mode=args.mode,
        epsilon=args.epsilon,
        adv_ratio=args.adv_ratio,
        num_epochs=args.epochs
    )

    print(f"\nDone! Best val loss: {best_loss:.4f}")
