"""
TianshangGuard v1.5.0 — F-1: 重建中文 SMS 训练数据集 v2

改进:
1. 保留所有 ChiFraud 金融欺诈 (4,6,8) + 误标检测 (色情/赌博/假证)
2. 保留更多合法短信 (ChiFraud 0, 短文本 <200)
3. 使用 Chinese mode 的合成模板补充覆盖缺口
4. 输出 30K+ 均衡数据集
"""
import os, csv, json, re, random
from collections import Counter

SEED = 42
random.seed(SEED)

BASE = os.path.dirname(__file__)
RAW = os.path.join(BASE, "raw_data")
OUTPUT = os.path.join(RAW, "chinese_sms_training_v2.csv")

# ── 误标检测模式 (来自 clean_chinese_data.py) ──────────────
ESCORT_KW = ["找小姐","酒店特殊服务","上门服务","上门全套","特殊服务",
             "小姐服务","外围","楼凤","兼职妹","商务模特","会所","私人伴游",
             "全套服务","莞式","大活","波推"]
ESCORT_PAT = [r"微信.{0,5}[0-9a-zA-Z]{5,}", r"加微.{0,5}[0-9a-zA-Z]{5,}",
              r"[Vv]信.{0,5}[0-9a-zA-Z]{5,}", r"微[:::]{1,3}[0-9a-zA-Z]{3,}"]
GAMBLING_KW = ["牛牛群","微信红包群","一分免押金","赌博群","彩票群",
               "北京赛车","时时彩","百家乐","六合彩","棋牌"]
FAKE_DOC_KW = ["代开票","代开各类","增值税发票","发票代开","开票",
               "代办证件","办证","刻章","假证"]

def has_contact(text):
    if re.search(r"微信.{0,5}[0-9a-zA-Z]{4,}", text): return True
    if re.search(r"加微.{0,5}[0-9a-zA-Z]{4,}", text): return True
    if re.search(r"1[3-9]\d{9}", text): return True
    if re.search(r"[Qq]{2}.{0,3}[0-9]{5,}", text): return True
    return False

def is_mislabeled(text):
    escort = any(kw in text for kw in ESCORT_KW) or any(re.search(p, text) for p in ESCORT_PAT)
    if escort and has_contact(text): return True
    if any(kw in text for kw in GAMBLING_KW) and ("群" in text or has_contact(text)): return True
    if any(kw in text for kw in FAKE_DOC_KW) and has_contact(text): return True
    return False

def is_noise(text):
    if len(text.strip()) < 5: return True
    if re.match(r"^[\d\s\-\+\(\)]+$", text.strip()): return True
    # HTML/JS artifacts
    if re.search(r"<[a-z]+[^>]*>", text, re.I): return True
    if re.search(r"javascript:|function\s*\(|\.addEventListener", text, re.I): return True
    return False

def load_chifraud(filepath):
    """Load ChiFraud tab-separated CSV, return (labeled_fraud, labeled_legit, mislabeled_fraud)"""
    labeled_fraud, labeled_legit, mislabeled_fraud = [], [], []
    with open(filepath, encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        next(reader)  # skip header
        for row in reader:
            if len(row) < 2:
                continue
            label_id = row[0].strip()
            text = row[1].strip()
            text_len = len(text)

            # Filter noise
            if is_noise(text):
                continue

            # Financial fraud labels (4=虚假办卡, 6=违规提现, 8=虚假手机卡)
            if label_id in ("4", "6", "8"):
                # Keep all financial fraud, no text length restriction
                labeled_fraud.append(text)
                continue

            # Label 0 (normal): keep as legitimate if SMS-like
            if label_id == "0":
                # Check for mislabeled fraud
                if is_mislabeled(text):
                    mislabeled_fraud.append(text)
                elif text_len < 200:
                    labeled_legit.append(text)
                # Texts >= 200 chars are too long for SMS, skip

    return labeled_fraud, labeled_legit, mislabeled_fraud


# ═══════════════════════════════════════════════════════════════
# Step 1: Load ChiFraud data
# ═══════════════════════════════════════════════════════════════

print("=" * 60)
print("Step 1: Loading ChiFraud data")
print("=" * 60)

chifraud_dir = os.path.join(RAW, "chifraud", "dataset")
all_fraud = []
all_legit = []
all_mislabeled = []

for fname in ["ChiFraud_train.csv", "ChiFraud_t2022.csv"]:
    path = os.path.join(chifraud_dir, fname)
    fraud, legit, mislabeled = load_chifraud(path)
    all_fraud.extend(fraud)
    all_legit.extend(legit)
    all_mislabeled.extend(mislabeled)
    print(f"  {fname}: fraud={len(fraud)}, legit={len(legit)}, mislabeled={len(mislabeled)}")

# Also load t2023 for validation (not training)
print(f"\n  Totals: fraud={len(all_fraud)}, legit={len(all_legit)}, mislabeled={len(all_mislabeled)}")

# ═══════════════════════════════════════════════════════════════
# Step 2: Generate synthetic SMS
# ═══════════════════════════════════════════════════════════════

print("\n" + "=" * 60)
print("Step 2: Generating synthetic SMS")
print("=" * 60)

# Phishing templates
bank_phish_templates = [
    "【{bank}】您的账户出现异常登录，请立即验证身份 {url}",
    "【{bank}】您的账户已被暂停使用，点击链接重新激活 {url}",
    "【{bank}】系统检测到您的账户存在安全风险，请验证 {url}",
    "【{bank}】您的银行卡已被冻结，请联系客服解冻 {phone}",
    "【{bank}】您的信用卡逾期未还，已上报征信，点击处理 {url}",
    "【{bank}】您的电子密码器即将过期，请更新 {url}",
    "【{bank}】您有积分即将过期，可兑换现金 {url}",
    "【{bank}】您的贷款申请已通过，点击链接确认 {url}",
    "【{bank}】您的账户在异地登录，若非本人操作请立即锁定 {url}",
    "【{bank}】您的账户被他人尝试登录，请验证身份 {url}",
    "【{bank}】检测到可疑交易，请立即处理 {url}",
    "【{bank}】您的银行账户积分即将到期，兑换 {url} 领取好礼",
    "【{bank}】您的信用额度提升至50万，立即申请 {url} 领取",
]

delivery_phish_templates = [
    "【{svc}】您的包裹因地址不详无法派送，请点击 {url} 补充地址",
    "【{svc}】您好，您有一个国际包裹被海关扣留，需缴纳清关费 {url}",
    "【{svc}】您的快递已滞留多日，重新派送请点击 {url}",
    "【{svc}】您的包裹在运输中损坏，理赔申请 {url}",
    "【{svc}】您的包裹已到达当地，但面单模糊，请确认 {url}",
    "【{svc}】您的配送地址异常，请点击 {url} 更新地址",
]

social_phish_templates = [
    "【社保局】您的社保卡已被暂停使用，请在24小时内点击 {url} 重新启用",
    "【医保中心】您的医保账户异常，将停止报销，请点击 {url} 补录信息",
    "【国家医保局】您有一笔医保报销金待领取，金额2860元，申领 {url}",
    "【税务局】您有退税金3680元未领取，申请 {url}",
    "【市场监督管理局】您的营业执照已过期，需补办 {url}",
    "【住建局】您的房产信息异常，请确认 {url}",
]

carrier_phish_templates = [
    "【中国移动】您的手机号积分将于明日清零，点击 {url} 兑换好礼",
    "【中国联通】恭喜您获得5G体验官资格，送话费100元，领取 {url}",
    "【中国电信】您当前套餐可免费升级为5G畅享套餐，立即办理 {url}",
    "【ETC中心】您的ETC已停用，需重新认证才能正常使用，认证 {url}",
    "【高速ETC】您的ETC卡被禁用，点击 {url} 重新激活",
]

gov_phish_templates = [
    "您好，这里是XX市公安局经侦支队，您涉嫌一起金融诈骗案，请配合调查",
    "【国家反诈中心】您的银行卡涉嫌洗钱，已立案侦查，请立即联系办案民警 {phone}",
    "您好，我是XX市检察院的，您有一张传票尚未领取，请尽快联系我们",
]

ecommerce_phish_templates = [
    "【{svc}】您购买的商品确认收货后可抽奖，一等奖iPhone16，抽奖 {url}",
    "【{svc}】恭喜获得现金红包100元，提现 {url}",
    "【{svc}】您的会员积分即将过期，兑换大牌好礼 {url}",
    "【{svc}】您有一个待发货订单异常，查看 {url}",
]

social_eng_templates = [
    "我是你领导，现在不方便说话，你先帮我转5万到这个账户，明天还你",
    "爸，我手机掉水里了，用朋友手机发的，学校要交培训费，转这个账号",
    "你家人出车祸了，正在医院抢救，赶紧打钱到这个账户交手术费",
    "你的快递在运输中丢失，我们双倍赔付，请提供银行卡号和验证码",
    "【贷款平台】您申请的贷款已审批通过，但需先缴纳保证金5000元才能放款",
    "恭喜您获得节目抽奖一等奖188万元，请先缴纳个人所得税和公证费",
    "招聘兼职刷单，日入300-500元，无需押金，一单一结，添加微信了解",
]

# Legitimate templates
bank_legit_templates = [
    "【{bank}】您的验证码是{code}，5分钟内有效，请勿泄露",
    "【{bank}】您正在修改登录密码，验证码{code}",
    "【{bank}】尊敬的用户，您尾号{card}的账户发生{amt}元交易",
    "【{bank}】您的信用卡账单已出，应还金额{amt}元",
    "【{bank}】您尾号{card}的账户收到转账{amt}元",
    "【{bank}】月度账单已生成，点击app查看详情",
]

delivery_legit_templates = [
    "【{svc}】您的快递已到达配送站，快递员正在派送中",
    "【{svc}】您的快递已被签收，感谢使用",
    "【{svc}】您的快递正在派送中，预计今日送达",
    "【{svc}】您的包裹已到达自提点，请凭取件码{code}领取",
    "【{svc}】您的预约已确认，请按时到达",
    "【{svc}】您的话费已充值成功，金额{amt}元",
]

otp_templates = [
    "您的验证码是{code}，5分钟内有效，请勿泄露给他人",
    "{code} 为您的登录验证码，如非本人操作请忽略",
    "验证码{code}，您正在注册成为新用户，请完成验证",
    "【{svc}】您的注册验证码为{code}，10分钟内有效",
]

sources = ["工商银行","建设银行","农业银行","招商银行","中国银行",
           "交通银行","浦发银行","平安银行","中信银行","光大银行",
           "民生银行","兴业银行","华夏银行","广发银行"]
services = ["顺丰速运","中通快递","圆通速递","韵达快递","申通快递",
            "美团外卖","饿了么","淘宝","京东","拼多多","抖音商城"]

_rng = random.Random(SEED)

def make_phish(templates, count, **kwargs):
    results = []
    for _ in range(count):
        tmpl = _rng.choice(templates)
        bank = _rng.choice(sources)
        svc = _rng.choice(services)
        url = _rng.choice([f"https://www.{bank[:2]}-verify.com/auth",
                          f"https://{bank[:2]}bank.safelink.net/verify",
                          f"http://cn-sms{_rng.randint(1,99)}.top",
                          f"http://cn-x{_rng.randint(1,99)}.com/verify"])
        phone = str(_rng.randint(400000000, 400999999))
        code = str(_rng.randint(100000, 999999))
        text = tmpl.format(bank=bank, svc=svc, url=url, phone=phone,
                          code=code, amt=str(_rng.randint(100,50000)),
                          card=str(_rng.randint(1000,9999)))
        results.append(text)
    return results

def make_legit(templates, count, **kwargs):
    results = []
    for _ in range(count):
        tmpl = _rng.choice(templates)
        bank = _rng.choice(sources)
        svc = _rng.choice(services)
        code = str(_rng.randint(100000, 999999))
        text = tmpl.format(bank=bank, svc=svc, code=code,
                          amt=str(_rng.randint(10,50000)),
                          card=str(_rng.randint(1000,9999)))
        results.append(text)
    return results

# Generate
syn_phish = []
syn_phish += make_phish(bank_phish_templates, 2500)
syn_phish += make_phish(delivery_phish_templates, 800)
syn_phish += make_phish(social_phish_templates, 600)
syn_phish += make_phish(carrier_phish_templates, 600)
syn_phish += make_phish(ecommerce_phish_templates, 600)
syn_phish += social_eng_templates.copy()  # add all social engineering templates directly

syn_legit = []
syn_legit += make_legit(bank_legit_templates, 3000)
syn_legit += make_legit(delivery_legit_templates, 1500)
syn_legit += make_phish(otp_templates, 1000)  # OTP is legitimate

print(f"  Synthetic phishing: {len(syn_phish)}")
print(f"  Synthetic legitimate: {len(syn_legit)}")

# ═══════════════════════════════════════════════════════════════
# Step 3: Assemble training dataset
# ═══════════════════════════════════════════════════════════════

print("\n" + "=" * 60)
print("Step 3: Assembling training dataset")
print("=" * 60)

# All fraud: ChiFraud financial + mislabeled + synthetic
all_fraud_sources = (
    [(t, "phishing", "chifraud_468") for t in all_fraud] +
    [(t, "phishing", "chifraud_mislabeled") for t in all_mislabeled] +
    [(t, "phishing", "synthetic") for t in syn_phish]
)

# All legit: ChiFraud short + synthetic
all_legit_sources = (
    [(t, "legitimate", "chifraud_0") for t in all_legit] +
    [(t, "legitimate", "synthetic") for t in syn_legit]
)

# Balance to 50/50
n_phish = len(all_fraud_sources)
n_legit = min(len(all_legit_sources), n_phish)

_rng.shuffle(all_legit_sources)
legit_sample = all_legit_sources[:n_legit]

print(f"  Fraud sources: {len(all_fraud_sources)}")
print(f"    ChiFraud 4,6,8: {len(all_fraud)}")
print(f"    Mislabeled: {len(all_mislabeled)}")
print(f"    Synthetic: {len(syn_phish)}")
print(f"  Legit sources: {len(legit_sample)}")
chifraud_0_in_sample = sum(1 for _, l, s in legit_sample if s == "chifraud_0")
syn_in_sample = sum(1 for _, l, s in legit_sample if s == "synthetic")
print(f"    ChiFraud 0: {chifraud_0_in_sample}")
print(f"    Synthetic: {syn_in_sample}")

combined = all_fraud_sources + legit_sample
_rng.shuffle(combined)

# Write
with open(OUTPUT, "w", encoding="utf-8", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["text", "label", "source"])
    for text, label_str, src in combined:
        writer.writerow([text, label_str, src])

print(f"\n  Output: {OUTPUT}")
print(f"  Total: {len(combined)} (fraud={n_phish}, legit={n_legit})")
print(f"  Ratio: 1:{n_legit/n_phish:.1f}")

# Verify
import pandas as pd
df = pd.read_csv(OUTPUT)
print(f"\n  Verification: {len(df)} rows, {df['label'].value_counts().to_dict()}")
print(f"  Source distribution:\n{df['source'].value_counts().to_string()}")
print(f"\nDone!")
