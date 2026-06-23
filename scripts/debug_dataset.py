import pandas as pd
import sys
import io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

df = pd.read_csv("F:/Projects/Project11/scripts/raw_data/clean_dataset.csv", nrows=30)
print("First 30 samples:")
for i, row in df.iterrows():
    label = "PHISH" if row["label"] == 1 else "LEGIT"
    text = str(row["text"])[:120]
    print(f"  [{label}] {text}")

phish = (df["label"] == 1).sum()
legit = (df["label"] == 0).sum()
print(f"\nTotal: {len(df)}, Phish: {phish}, Legit: {legit}")
print(f"Avg text length: {df['text'].str.len().mean():.0f}")
