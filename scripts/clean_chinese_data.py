"""
v2 — Clean chinese_real_dataset.csv:
   1. Remove FBS source (no-op in v2 — FBS already excluded from pipeline)
   2. Fix mislabeled legit samples (escort/gambling/fake docs with contact info)
   3. Remove noise (control chars, too short, number-only, garbage tokens)
   4. Remove exact duplicates
   5. Balance dataset (downsample legit to match fraud)
   6. Save cleaned CSV
"""
import csv, os, re, sys

INPUT = os.path.join(os.path.dirname(__file__), "raw_data", "chinese_real_dataset.csv")
OUTPUT = os.path.join(os.path.dirname(__file__), "raw_data", "chinese_real_clean.csv")

# ── Keyword patterns for mislabeled detection ──────────────
ESCORT_KEYWORDS = [
    "找小姐", "酒店特殊服务", "上门服务", "上门全套", "酒店全套",
    "特殊服务", "小姐服务", "外围", "楼凤", "兼职妹", "商务模特",
    "夜总会", "桑拿", "洗浴中心", "会所", "私人伴游", "情人",
    "全套服务", "莞式", "大活", "口活", "波推",
]
ESCORT_PATTERNS = [
    r"微信.{0,5}[0-9a-zA-Z]{5,}",   # "微信XXX12345"
    r"加微.{0,5}[0-9a-zA-Z]{5,}",   # "加微XXX12345"
    r"微[:::]{1,3}[0-9a-zA-Z]{4,}",  # "微:::12345"
    r"[Vv]信.{0,5}[0-9a-zA-Z]{5,}",
]

GAMBLING_KEYWORDS = [
    "牛牛群", "微信红包群", "一分免押金", "赌博群", "彩票群",
    "北京赛车", "时时彩", "百家乐", "六合彩", "外围盘口",
    "真人视讯", "棋牌", "开元棋牌", "龙虎斗", "炸金花",
    "德州扑克群", "麻将群",
]

FAKE_DOC_KEYWORDS = [
    "代开票", "代开各类", "增值税发票", "发票代开", "开票",
    "代开医院", "代开病历", "代开诊断证明", "代办证件",
    "办证", "刻章", "假证",
]

CONTROL_CHAR_RE = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f]")
NUMBER_ONLY_RE = re.compile(r"^[\d\s\-\+\(\)]+$")
GARBAGE_TOKENS = {"URL", "DIGIT", "NAME", "PLACE", "CELLPHONE", "WECHAT",
                  "BANK", "微信同号", "手机号码"}

def has_contact_info(text):
    """Check if text contains WeChat/phone/QQ contact info."""
    if re.search(r"微信.{0,5}[0-9a-zA-Z]{4,}", text):
        return True
    if re.search(r"加微.{0,5}[0-9a-zA-Z]{4,}", text):
        return True
    if re.search(r"微[:::]{1,3}[0-9a-zA-Z]{3,}", text):
        return True
    if re.search(r"[Vv]信.{0,5}[0-9a-zA-Z]{4,}", text):
        return True
    if re.search(r"[Qq]{2}.{0,3}[0-9]{5,}", text):
        return True
    if re.search(r"电话.{0,5}1[3-9]\d{9}", text):
        return True
    if re.search(r"1[3-9]\d{9}", text):  # bare Chinese phone number
        return True
    return False

def is_mislabeled_legit(text):
    """Check if a label=0 (legit) sample is actually fraud."""
    # Escort with contact info
    escort_hit = any(kw in text for kw in ESCORT_KEYWORDS)
    escort_pat = any(re.search(p, text) for p in ESCORT_PATTERNS)
    if (escort_hit or escort_pat) and has_contact_info(text):
        return True, "escort"

    # Gambling
    if any(kw in text for kw in GAMBLING_KEYWORDS):
        if has_contact_info(text) or "群" in text:
            return True, "gambling"

    # Fake documents
    if any(kw in text for kw in FAKE_DOC_KEYWORDS):
        if has_contact_info(text) or "电话" in text:
            return True, "fake_doc"

    return False, None

def is_noisy(text):
    """Check if text is garbage/noise."""
    # Too short
    if len(text.strip()) < 5:
        return True, "too_short"
    # Control characters
    if CONTROL_CHAR_RE.search(text):
        return True, "control_char"
    # Number-only
    if NUMBER_ONLY_RE.match(text.strip()):
        return True, "number_only"
    # Pure garbage tokens
    cleaned = text.strip()
    if cleaned in GARBAGE_TOKENS:
        return True, "garbage_token"
    return False, None

# ── Main cleaning ──────────────────────────────────────────
print("=" * 60)
print("  Data Cleaning: chinese_real_dataset.csv")
print("=" * 60)

rows = []
with open(INPUT, "r", encoding="utf-8") as f:
    reader = csv.DictReader(f)
    for row in reader:
        rows.append(row)

print(f"\n  Original: {len(rows)} rows")

# Step 1: Filter out FBS source
rows_chifraud = [r for r in rows if r.get("source", "") != "fbs"]
n_fbs = len(rows) - len(rows_chifraud)
print(f"  Step 1 - Remove FBS: -{n_fbs} → {len(rows_chifraud)} rows")

# Step 2: Fix mislabeled legit samples
fixed = 0
fixed_categories = {"escort": 0, "gambling": 0, "fake_doc": 0}
for row in rows_chifraud:
    if row["label"] == "0" or row["label"] == "0.0":
        is_bad, category = is_mislabeled_legit(row["text"])
        if is_bad:
            row["label"] = "1"
            fixed += 1
            fixed_categories[category] += 1
print(f"  Step 2 - Fix labels: {fixed} mislabeled legit → fraud")
for cat, count in fixed_categories.items():
    print(f"    {cat}: {count}")

# Step 3: Remove noise
clean_rows = []
removed_noise = {"too_short": 0, "control_char": 0, "number_only": 0, "garbage_token": 0}
for row in rows_chifraud:
    is_bad, reason = is_noisy(row["text"])
    if is_bad:
        removed_noise[reason] += 1
    else:
        clean_rows.append(row)
total_noise = sum(removed_noise.values())
print(f"  Step 3 - Remove noise: -{total_noise} → {len(clean_rows)} rows")
for reason, count in removed_noise.items():
    print(f"    {reason}: {count}")

# Step 4: Remove exact duplicates
seen = set()
deduped = []
for row in clean_rows:
    key = row["text"]
    if key not in seen:
        seen.add(key)
        deduped.append(row)
n_dup = len(clean_rows) - len(deduped)
print(f"  Step 4 - Dedup: -{n_dup} → {len(deduped)} rows")

# Step 5: Balance dataset
fraud_rows = [r for r in deduped if float(r["label"]) == 1.0]
legit_rows = [r for r in deduped if float(r["label"]) == 0.0]
print(f"\n  After cleaning:")
print(f"    Fraud: {len(fraud_rows)}")
print(f"    Legit: {len(legit_rows)}")

import random
random.seed(42)
n_balanced = min(len(fraud_rows), len(legit_rows))
if len(legit_rows) > n_balanced:
    legit_sampled = random.sample(legit_rows, n_balanced)
else:
    legit_sampled = legit_rows

balanced = fraud_rows + legit_sampled
random.shuffle(balanced)

print(f"\n  Balanced: {len(balanced)} rows ({len(fraud_rows)} fraud + {len(legit_sampled)} legit)")

# Step 6: Save
with open(OUTPUT, "w", encoding="utf-8", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=["text", "label", "source"])
    writer.writeheader()
    for row in balanced:
        writer.writerow(row)

print(f"\n  Saved to: {OUTPUT}")
print(f"  {len(balanced)} rows written")

# Summary
print(f"\n{'='*60}")
print(f"  Summary:")
print(f"    Original:      {len(rows)} rows")
print(f"    FBS removed:   -{n_fbs}")
print(f"    Labels fixed:  +{fixed}")
print(f"    Noise removed: -{total_noise}")
print(f"    Duplicates:    -{n_dup}")
print(f"    Balanced:      {len(balanced)} rows (50/50)")
print(f"{'='*60}")
