"""
TianshangGuard v1.5.0 — Clean FBS SMS placeholders for training data.

FBS (伪基站) data from sms_test_set_full.csv contains ~5,646 real SMS
phishing samples with placeholder tokens like PLACE, NAME, DIGIT, URL, etc.
This script replaces them with realistic Chinese values.
"""
import os, csv, re, random, argparse
from collections import defaultdict

SEED = 42
random.seed(SEED)

BASE = os.path.dirname(__file__)
FBS_SRC = os.path.join(BASE, "raw_data", "sms_test_set_full.csv")
OUTPUT = os.path.join(BASE, "raw_data", "fbs_cleaned.csv")

CITIES = ["北京", "上海", "深圳", "广州", "杭州", "成都", "武汉", "南京",
          "重庆", "西安", "长沙", "郑州", "东莞", "苏州", "天津", "厦门"]
BANKS = ["工商银行", "建设银行", "农业银行", "招商银行", "中国银行",
         "交通银行", "浦发银行", "平安银行", "中信银行"]
PLATFORMS = ["京东金融", "蚂蚁财富", "平安金所", "陆金所", "东方财富",
             "同花顺", "雪球", "富途证券", "老虎证券"]
SERVICES = ["顺丰速运", "中通快递", "圆通速递", "韵达快递", "美团", "饿了么", "淘宝", "京东"]
NAMES = ["先生", "女士", "用户", "朋友", "会员"]

def rand_phone():
    return f"1{random.choice(['38','39','50','58','86','89'])}{random.randint(10000000,99999999)}"

def rand_qq():
    return str(random.randint(100000, 99999999))

def rand_wechat():
    return random.choice(["wxid_" + "".join(random.choices("abcdefghijklmnopqrstuvwxyz0123456789", k=8)),
                          "v" + str(random.randint(100000, 999999))])

def rand_url():
    domains = ["www.example.com", "m.example.cn", "app.verify.com",
               "www.secure-check.cn", "login.safe.net"]
    return f"https://{random.choice(domains)}/{''.join(random.choices('abcdefghijklmnopqrstuvwxyz', k=6))}"

def rand_code():
    return str(random.randint(100000, 999999))

def rand_digit(min_l=2, max_l=6):
    return str(random.randint(10**(min_l-1), 10**max_l - 1))

def rand_amount():
    return f"{random.randint(100, 50000)}元"

PROCESSORS = {
    "PLACE": lambda m: random.choice(CITIES),
    "NAME": lambda m: random.choice(NAMES),
    "DIGIT": lambda m: rand_digit(),
    "CELLPHONE": lambda m: rand_phone(),
    "PHONE": lambda m: rand_phone(),
    "URL": lambda m: rand_url(),
    "QQ": lambda m: rand_qq(),
    "WECHAT": lambda m: rand_wechat(),
    "HOTLINE": lambda m: f"400-{random.randint(100,999)}-{random.randint(1000,9999)}",
    "BANK": lambda m: random.choice(BANKS),
    "BANKNAME": lambda m: random.choice(BANKS),
    "BANKNAMENAME": lambda m: random.choice(BANKS) + random.choice(NAMES),
    "BANKPLACE": lambda m: random.choice(BANKS) + random.choice(CITIES),
    "BANKNAMEPLACE": lambda m: random.choice(BANKS) + random.choice(NAMES) + random.choice(CITIES),
    "BANKPLACEPLACE": lambda m: random.choice(BANKS) + random.choice(CITIES) + random.choice(CITIES),
    "CELLPHONEBANK": lambda m: rand_phone() + random.choice(BANKS),
    "CELLPHONECELLPHONE": lambda m: rand_phone() + rand_phone(),
    "CELLPHONENAME": lambda m: rand_phone() + random.choice(NAMES),
    "CELLPHONENAMENAME": lambda m: rand_phone() + random.choice(NAMES) + random.choice(NAMES),
    "CELLPHONEQQ": lambda m: rand_phone() + rand_qq(),
    "CELLPHONEQQNAME": lambda m: rand_phone() + rand_qq() + random.choice(NAMES),
    "CELLPHONEWECHAT": lambda m: rand_phone() + rand_wechat(),
    "DIGITCELLPHONE": lambda m: rand_digit() + rand_phone(),
    "DIGITDIGIT": lambda m: rand_digit() + rand_digit(),
    "DIGITNAME": lambda m: rand_digit() + random.choice(NAMES),
    "DIGITPLACE": lambda m: rand_digit() + random.choice(CITIES),
    "NAMEQQ": lambda m: random.choice(NAMES) + rand_qq(),
    "NAMECELLPHONE": lambda m: random.choice(NAMES) + rand_phone(),
    "NAMEDIGIT": lambda m: random.choice(NAMES) + rand_digit(),
    "NAMENAMEPLACE": lambda m: random.choice(NAMES) + random.choice(NAMES) + random.choice(CITIES),
    "NAMEPLACE": lambda m: random.choice(NAMES) + random.choice(CITIES),
    "NAMEPLACEDIGIT": lambda m: random.choice(NAMES) + random.choice(CITIES) + rand_digit(),
    "PHONEPHONEPHONE": lambda m: rand_phone() + rand_phone() + rand_phone(),
    "PLACEDIGIT": lambda m: random.choice(CITIES) + rand_digit(),
    "PLACEPLACE": lambda m: random.choice(CITIES) + random.choice(CITIES),
    "PLACEPLACENAME": lambda m: random.choice(CITIES) + random.choice(CITIES) + random.choice(NAMES),
}

def clean_text(text):
    """Replace all known placeholders with realistic values."""
    # Sort by length descending to match longer compound placeholders first
    tokens = sorted(PROCESSORS.keys(), key=len, reverse=True)
    pattern = "|".join(re.escape(t) for t in tokens)
    def replacer(m):
        token = m.group(0)
        proc = PROCESSORS.get(token)
        if proc:
            return proc(token)
        return token
    text = re.sub(pattern, replacer, text)
    # Cleanup artifacts: double spaces, leading/trailing whitespace
    text = re.sub(r"  +", " ", text).strip()
    return text

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default=FBS_SRC)
    parser.add_argument("--output", default=OUTPUT)
    parser.add_argument("--dedup", action="store_true", help="Deduplicate by text")
    args = parser.parse_args()

    with open(args.input, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)
    fbs_rows = [r for r in rows if r.get("source", "") == "fbs"]
    print(f"FBS rows found: {len(fbs_rows)}")

    seen = set()
    cleaned = []
    for r in fbs_rows:
        text = r["text"]
        label = r.get("label", "1")
        source = "fbs_cleaned"

        if args.dedup:
            if text in seen:
                continue
            seen.add(text)

        ctext = clean_text(text)
        if len(ctext.strip()) < 5:
            continue
        cleaned.append([ctext, label, source])

    with open(args.output, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["text", "label", "source"])
        writer.writerows(cleaned)

    # Stats
    labels = defaultdict(int)
    for row in cleaned:
        labels[row[1]] += 1
    print(f"Written: {len(cleaned)} rows to {args.output}")
    print(f"  Labels: {dict(labels)}")
    print(f"  Unique placeholders handled: {len(PROCESSORS)}")

    # Show samples
    print("\nSample cleaned texts:")
    for row in cleaned[:5]:
        print(f"  {repr(row[0][:120])}")

if __name__ == "__main__":
    main()
