"""
TianshangGuard v1.5.0 — D-2/D-3: 构建中文 SMS 训练数据集
产出:
  raw_data/chinese_sms_training.csv  (Phishing: ChiFraud 4,6,8 + 手写; Legit: ChiFraud 0短文本)
"""
import os, csv, random
from collections import Counter

SEED = 42
random.seed(SEED)

BASE = os.path.dirname(__file__)
RAW = os.path.join(BASE, "raw_data")
OUTPUT = os.path.join(RAW, "chinese_sms_training.csv")

def load_chifraud_labels(filepath, wanted_labels):
    """Load ChiFraud rows with specific labels, return list of (text, label)"""
    results = []
    with open(filepath, "r", encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        next(reader, None)  # skip header
        for row in reader:
            if len(row) < 2:
                continue
            label, text = row[0].strip(), row[1].strip()
            if label in wanted_labels:
                results.append((text, label))
    return results

# ── D-2: Extract ChiFraud label=4,6,8 (phishing) ──────────────
print("=== D-2: Extracting ChiFraud label=4,6,8 ===")
chifraud_dir = os.path.join(RAW, "chifraud", "dataset")
phishing_468 = []
for fname in ["ChiFraud_train.csv", "ChiFraud_t2022.csv"]:
    path = os.path.join(chifraud_dir, fname)
    rows = load_chifraud_labels(path, {"4", "6", "8"})
    phishing_468.extend(rows)
    print(f"  {fname}: {len(rows)} phishing rows")

print(f"  Total ChiFraud 4,6,8: {len(phishing_468)}")

# ── D-3: Hand-crafted Chinese phishing SMS ─────────────────────
print("\n=== D-3: Hand-crafted phishing SMS ===")
handcrafted_phishing = [
    # Bank / payment phishing
    ("【招商银行】尊敬的客户，您的信用卡存在异常消费，即将冻结。请联系 400-xxx-xxxx 或登录 http://cmb-verify.com 处理", "银行"),
    ("【建设银行】您的储蓄卡已过期，请登录 http://ccb-update.com 补录信息，否则将影响使用", "银行"),
    ("【农业银行】您的账户因安全风险被限制，请前往 http://abc-secure.com 解除限制", "银行"),
    ("【交通银行】系统检测到您的账户异地登录，请立即验证 http://bcm-verify.net", "银行"),
    ("【邮政储蓄】您的银行账户积分即将到期，兑换 http://psbc-points.top 领取好礼", "银行"),
    ("【浦发银行】您的信用额度提升至50万，立即申请 http://spdb-limit.com 领取", "银行"),
    ("【中信银行】您的银行卡被暂停使用，点击 http://citic-verify.com 重新激活", "银行"),
    ("【民生银行】您的账户存在安全风险，请立即升级安全等级 http://cmbc-secure.com", "银行"),
    ("【中国银联】您的银行卡出现异常交易，暂停使用，请核实 http://unionpay-check.com", "银行"),
    ("【支付宝】您的账户存在风险，将在24小时内冻结，请验证 http://alipay-secure.net", "支付"),
    ("【微信支付】您的账户被他人尝试登录，紧急冻结请点击 http://wechat-pay-safe.com", "支付"),
    ("【财付通】您的账户有不明设备登录，立即验证身份 http://tenpay-id.com", "支付"),

    # Social security / healthcare phishing
    ("【社保局】您的社保卡已被暂停使用，请在24小时内点击 http://sbj-activate.com 重新启用", "社保"),
    ("【医保中心】您的医保账户异常，将停止报销，请点击 http://ybj-update.com 补录信息", "医保"),
    ("【卫健委】您的电子健康卡出现异常，点击 http://health-card.cn 处理", "医保"),
    ("【国家医保局】您有一笔医保报销金待领取，金额2860元，申领 http://yb-refund.com", "医保"),

    # Express / logistics phishing
    ("【中通快递】您的包裹因地址不详无法派送，请点击 http://zto-redirect.com 补充地址", "快递"),
    ("【圆通速递】您好，您有一个国际包裹被海关扣留，需缴纳清关费 http://yto-customs.com", "快递"),
    ("【韵达快递】您的快递已滞留多日，重新派送请点击 http://yunda-arrange.com", "快递"),
    ("【申通快递】您的包裹在运输中损坏，理赔申请 http://sto-compensation.com", "快递"),
    ("【极兔速递】您的包裹已到达当地，但面单模糊，请确认 http://jtexpress-update.com", "快递"),
    ("【中国邮政】您有一个挂号信未签收，请点击 http://chinapost-letter.com 预约投递", "快递"),
    ("【京东物流】您的配送地址异常，请点击 http://jd-logistics.com 更新地址", "快递"),
    ("【菜鸟驿站】您的包裹已入库，但取件码异常，请重新获取 http://cainiao-code.com", "快递"),

    # Carrier phishing
    ("【中国移动】您的手机号积分将于明日清零，点击 http://10086-points.com 兑换好礼", "运营商"),
    ("【中国联通】恭喜您获得5G体验官资格，送话费100元，领取 http://10010-gift.top", "运营商"),
    ("【中国电信】您当前套餐可免费升级为5G畅享套餐，立即办理 http://189-upgrade.com", "运营商"),
    ("【中国广电】您的数字电视账户欠费，将停机处理，缴费 http://cbnet-topup.com", "运营商"),

    # Shopping / rewards phishing
    ("【京东】您购买的商品确认收货后可抽奖，一等奖iPhone16，抽奖 http://jd-lucky.com", "电商"),
    ("【淘宝】您在双11活动中中奖啦！获得免单资格，确认 http://taobao-free.com", "电商"),
    ("【拼多多】恭喜获得现金红包100元，提现 http://pdd-cash.top", "电商"),
    ("【唯品会】您的会员积分即将过期，兑换大牌好礼 http://vip-points.com", "电商"),
    ("【抖音商城】您有一个待发货订单异常，查看 http://douyin-order.com", "电商"),
    ("【快手小店】您购买的商品质检不合格，退款 http://kuaishou-refund.com", "电商"),
    ("【小米商城】您获得新品体验官资格，0元领小米15Pro，领取 http://mi-free.com", "电商"),
    ("【华为商城】您预约的Mate70今日开抢，优先购资格 http://huawei-priority.com", "电商"),
    ("【得物】您出售的商品存在纠纷，请处理 http://dew-dispute.com", "电商"),

    # Government / law enforcement phishing
    ("您好，这里是XX市公安局经侦支队，您涉嫌一起金融诈骗案，请配合调查，将资金转入安全账户进行核查", "公安"),
    ("【国家反诈中心】您的银行卡涉嫌洗钱，已立案侦查，请立即联系办案民警 010-xxxx-xxxx", "公安"),
    ("【税务局】您有退税金3680元未领取，申请 http://tax-refund-cn.com", "税务"),
    ("【市场监督管理局】您的营业执照已过期，需补办 http://gs-update.com", "政府"),
    ("【住建局】您的房产信息异常，请确认 http://housing-check.com", "政府"),

    # Service / subscription phishing
    ("【ETC中心】您的ETC已停用，需重新认证才能正常使用，认证 http://etc-reverify.com", "ETC"),
    ("【高速ETC】您的ETC卡被禁用，点击 http://etc-enable.com 重新激活", "ETC"),
    ("【交管12123】您有一条交通违章未处理，即将记分，查看 http://122gov-fine.com", "交通"),
    ("【12306】您的购票信息有误，车票将被取消，请确认 http://12306-ticket.com", "交通"),
    ("【滴滴出行】您的账户存在安全风险，暂停打车服务，验证 http://didiverify.com", "出行"),
    ("【美团】您的商家账户被投诉，请立即申诉 http://mt-appeal.com", "生活"),
    ("【携程旅行】您的订单发生变更，为保障行程，确认 http://ctrip-modify.com", "旅游"),
    ("【去哪儿网】您预订的酒店需要二次确认，否则取消，操作 http://qunar-confirm.com", "旅游"),
    ("【爱奇艺】您的会员自动续费失败，将停止服务，续费 http://iqiyi-renew.com", "娱乐"),
    ("【腾讯视频】您获得VIP体验周卡，仅限今日，领取 http://vip-qqvideo.com", "娱乐"),
    ("【QQ音乐】您的绿钻会员即将到期，续费享5折 http://qqmusic-discount.com", "娱乐"),
    ("【知乎】您的账号被多人举报，即将封禁，申诉 http://zhihu-appeal.com", "社交"),
    ("【百度网盘】您的会员到期，文件将被清理，续费 http://pan-baidu.com", "云服务"),
    ("【WPS】您的云文档存储空间不足，升级尊享会员 http://wps-premium.com", "办公"),

    # Investment / financial fraud
    ("【恒生金融】内幕消息：某科技股即将发布利好，收益预期30%，立即跟投 http://hsfund.com", "投资"),
    ("【慧理财】您的账户收益已到账11,234.56元，提现 http://hlicai.com", "投资"),
    ("【数字货币交易所】您有USDT充值和提现异常，请验证 http://ex-secure.com", "投资"),
    ("老师带单，月收益稳定50%以上，先盈利后收费，添加微信 xxx 了解详情", "投资"),
    ("【平安普惠】您的信用额度已提升至20万，随借随还，申请 http://paph-fund.com", "贷款"),

    # Pure scam tactics (no URL, pure social engineering)
    ("我是你领导，现在不方便说话，你先帮我转5万到这个账户，明天还你", "冒充领导"),
    ("爸，我手机掉水里了，用朋友手机发的，学校要交培训费，转这个账号", "冒充亲友"),
    ("你家人出车祸了，正在医院抢救，赶紧打钱到这个账户交手术费", "冒充亲友"),
    ("您好，我是物业的，您家漏水到楼下了，需要立即处理，先转500押金", "冒充物业"),
    ("你的快递在运输中丢失，我们双倍赔付，请提供银行卡号和验证码", "冒充客服"),
    ("【贷款平台】您申请的贷款已审批通过，但需先缴纳保证金5000元才能放款", "贷款诈骗"),
    ("恭喜您获得节目抽奖一等奖188万元，请先缴纳个人所得税和公证费", "中奖诈骗"),
    ("【交友】小姐姐加个微信呗，我这里有优质单身资源，先交会员费即可匹配", "交友诈骗"),
    ("招聘兼职刷单，日入300-500元，无需押金，一单一结，添加微信了解", "刷单诈骗"),
    ("在家就能做的手工活，日结工资200+，不收取任何费用，了解加V", "兼职诈骗"),

    # English phishing for Chinese users
    ("Your WeChat account has been logged in from an unrecognized device. Secure now: https://wechat-security.com", "英文"),
    ("Your Alibaba account has been restricted. Verify your identity: https://alibaba-verify.net", "英文"),
    ("【HSBC】Your account has been suspended due to unusual activity. Confirm: https://hsbc-alert.com", "英文"),
    ("Your SF-Express package is held at customs. Pay release fee: https://sf-customs.com", "英文"),
]

# Add variations by replacing URL domains and phone numbers
def make_variations(text, count=3):
    """Create minor variations of a phishing SMS"""
    variations = [text]
    urls = ["http://cn-x1.top", "http://cn-x2.com", "http://cn-x3.net",
            "http://cn-x4.cn", "http://cn-x5.org", "http://cn-x6.cc",
            "http://cn-x7.top", "http://cn-x8.com", "http://cn-x9.net"]
    phones = ["400-xxx-xxxx", "010-xxxx-xxxx", "021-xxxx-xxxx"]
    amounts = ["5000", "10000", "20000", "50000", "88000", "168000"]
    replacements = [
        ("http://[^\\s]+", urls),
        ("400-xxx-xxxx|010-xxxx-xxxx|021-xxxx-xxxx", phones),
        ("\\d{4,6}元", amounts),
    ]
    for _ in range(count - 1):
        var = text
        for pattern, choices in replacements:
            import re
            var = re.sub(pattern, lambda m: random.choice(choices), var)
        if var != text:
            variations.append(var)
    return variations

all_phishing = []
for text, category in handcrafted_phishing:
    all_phishing.append((text, "handcrafted", category))
    for var in make_variations(text, count=2):
        if var != text:
            all_phishing.append((var, "handcrafted_var", category))

print(f"  Handcrafted + variations: {len(all_phishing)}")

# ── Combine phishing data ─────────────────────────
print("\n=== Building training CSV ===")

# Legitimate: ChiFraud label=0 len<100, sample to balance
phishing_total = len(phishing_468) + len(all_phishing)
legit_needed = phishing_total  # 1:1 ratio
legit_short = []
for fname in ["ChiFraud_train.csv", "ChiFraud_t2022.csv"]:
    path = os.path.join(chifraud_dir, fname)
    with open(path, "r", encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        next(reader)
        for row in reader:
            if len(row) < 2:
                continue
            label, text = row[0].strip(), row[1].strip()
            if label == "0" and len(text) < 100:
                legit_short.append(text)

print(f"  Available legit (label=0 len<100): {len(legit_short)}")
random.shuffle(legit_short)
legit_sample = legit_short[:legit_needed]

with open(OUTPUT, "w", encoding="utf-8", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["text", "label", "source"])
    for text, label in phishing_468:
        writer.writerow([text, "phishing", f"chifraud_{label}"])
    for text, source, category in all_phishing:
        writer.writerow([text, "phishing", source])
    for text in legit_sample:
        writer.writerow([text, "legitimate", "chifraud_0_short"])

# Stats
with open(OUTPUT, "r", encoding="utf-8") as f:
    reader = csv.reader(f)
    next(reader)
    phishing_count = 0
    legit_count = 0
    for row in reader:
        if row[1] == "phishing":
            phishing_count += 1
        else:
            legit_count += 1

print(f"\n{'='*50}")
print(f"  Output: {OUTPUT}")
print(f"  Phishing: {phishing_count}")
print(f"  Legitimate: {legit_count}")
print(f"  Total: {phishing_count + legit_count}")
print(f"  Ratio: 1:{legit_count/phishing_count:.1f}")
print(f"{'='*50}")
