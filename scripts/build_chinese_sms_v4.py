"""
TianshangGuard v1.5.0 — 中文 SMS 训练数据集 v4

改进 vs v3:
1. 完全移除 ChiFraud 网页数据 (域漂移根因)
2. 扩充合成合法 SMS 模板: 12 → 55 种
3. 合法短信类型覆盖: 银行/快递/验证码/会议/预约/促销/通知/聊天
4. 只使用 SMS 级别的文本 (FBS + 合成)
"""
import os, csv, random

SEED = 42
random.seed(SEED)

BASE = os.path.dirname(__file__)
RAW = os.path.join(BASE, "raw_data")
OUTPUT = os.path.join(RAW, "chinese_sms_training_v4.csv")

# ── Bank & service names ──────────────────────────────────
BANKS = ["工商银行", "建设银行", "农业银行", "招商银行", "中国银行",
         "交通银行", "浦发银行", "平安银行", "中信银行", "光大银行",
         "民生银行", "兴业银行", "华夏银行", "邮储银行"]
SVCS = ["顺丰速运", "中通快递", "圆通速递", "韵达快递", "申通快递",
        "美团", "饿了么", "淘宝", "京东", "拼多多", "滴滴出行",
        "中国移动", "中国电信", "中国联通", "携程旅行", "飞猪"]
PLATFORMS = ["微信", "支付宝", "知乎", "哔哩哔哩", "CSDN", "GitHub"]
APPS = ["抖音", "快手", "微博", "QQ邮箱", "百度网盘", "网易云音乐"]
AGENCIES = ["社保局", "医保局", "税务局", "ETC服务中心",
            "人社局", "车管所", "市场监督管理局"]

# ── Phishing templates (20, intentionally mix bracket/no-bracket) ──
PHISH_TEMPLATES = [
    # With brackets - bank (break spurious correlation)
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
    "【{bank}】您的银行账户涉嫌洗钱，请配合调查 {url}",
    # With brackets - agency (more diversity)
    "【{agency}】您的社保卡已被暂停使用，请点击链接重新激活 {url}",
    "【{agency}】您有医保补贴未领取，请点击链接登记 {url}",
    "【{agency}】您的ETC已过期，请点击链接更新 {url}",
    "【{agency}】您的车辆违章未处理，请点击链接缴纳罚款 {url}",
    "【{agency}】您的税务申报异常，请立即处理 {url}",
    # No brackets (social engineering, casual)
    "您的账户存在异常，请立即联系客服处理 {phone}",
    "系统检测到您的银行卡在境外消费，若非本人操作请点击 {url}",
    "您有一笔退款待领取，请点击链接确认 {url}",
    "您的社保卡已被停用，请点击链接激活 {url}",
    "您的ETC已过期，请点击链接更新 {url}",
    "您有{amt}元贷款额度可申请，点击链接立即领取 {url}",
    "您的快递因地址不详被退回，请点击链接确认 {url}",
    "您的账号在陌生设备登录，立即冻结 {url}",
]

# ── Legitimate templates (55 types covering real SMS) ──────
LEGIT_TEMPLATES = [
    # ── Bank transaction alerts (8) ──
    "【{bank}】您的验证码是{code}，5分钟内有效，请勿泄露",
    "【{bank}】您正在修改登录密码，验证码{code}",
    "【{bank}】尊敬的用户，您尾号{card}的账户发生{amt}元交易",
    "【{bank}】您的信用卡账单已出，应还金额{amt}元",
    "【{bank}】您尾号{card}的账户收到转账{amt}元",
    "【{bank}】月度账单已生成，点击APP查看详情",
    "【{bank}】您尾号{card}的账户支取{amt}元，余额{bal}元",
    "【{bank}】您尾号{card}的信用卡已出账，到期还款日{date}",
    # ── Delivery notifications (6) ──
    "【{svc}】您的快递已到达配送站，快递员正在派送中",
    "【{svc}】您的快递已被签收，感谢使用{svc}服务",
    "【{svc}】您的快递正在派送中，预计今日{time}前送达",
    "【{svc}】您的包裹已到达自提点{addr}，请凭取件码{code}领取",
    "【{svc}】您的包裹已从{city}发出，预计{day}天到达",
    "【{svc}】快递员{name}正在为您派件，联系电话{phone2}",
    # ── Verification codes (8) ──
    "【{plt}】您的登录验证码是{code}，请勿泄露给他人",
    "【{plt}】您正在使用{code}进行身份验证，有效时间5分钟",
    "【{plt}】您的账号正在异地登录，验证码{code}，如非本人请忽略",
    "【{app}】您的注册验证码：{code}，10分钟内有效",
    "【{app}】您正在修改密码，验证码{code}，如非本人操作请忽略",
    "【{app}】您的手机号绑定验证码：{code}",
    "【{plt}】您正在使用{code}登录{plt}账号",
    "【{plt}】您的账号正在重置密码，验证码{code}",
    # ── Appointment / meeting reminders (6) ──
    "【{svc}】您的预约已确认，就诊时间{date} {time}，请提前{min}分钟到达",
    "【{svc}】温馨提醒：您明天{time}在{addr}有预约",
    "明天{time}的会议已改期至{date}，请注意查收邮件",
    "【{svc}】您的订单已预约成功，服务时间{date} {time}",
    "【{svc}】您已成功预约{date}的{service}服务",
    "明日{time}在{addr}召开季度总结会议，请准时参加",
    # ── Bill / payment reminders (5) ──
    "【{bank}】您尾号{card}的信用卡账单{amt}元将于{date}到期",
    "【{svc}】您本月话费账单{amt}元，已从余额中扣除",
    "【{svc}】您的水电费账单{amt}元已生成，请及时缴纳",
    "【{svc}】您的{svc}会员已续费成功，金额{amt}元",
    "【{svc}】您购买的{product}已扣款{amt}元，订单号{order}",
    # ── Security / real service notifications (8) ──
    "【{bank}】安全提醒：检测到新设备登录，如非本人请修改密码",
    "【{plt}】您的账号在{addr}登录，如非本人请立即修改密码",
    "【{plt}】您的{plt}账号安全等级：高，定期检查有助于保障安全",
    "【{bank}】温馨提示：请通过官方渠道办理业务，切勿向陌生人转账",
    "【{plt}】您已成功开通{service}服务，请妥善保管账户信息",
    "感谢您使用{svc}服务，如有问题请拨打官方客服热线",
    "您的{svc}会员已自动续费，本月扣款{amt}元",
    "安全提醒：您的{plt}账号在新设备登录，如非本人请忽略",
    # ── Promotional / marketing (hard negatives, 12) ──
    "【{svc}】您有{amt}元优惠券即将过期，点击APP使用",
    "【{bank}】您有{credit}信用额度可申请，年利率{rate}%起",
    "【{svc}】您已获得{product}体验资格，限时免费",
    "【{svc}】本月会员日特惠，{product}买一送一",
    "【{svc}】您的积分可兑换{product}，点击APP查看",
    "【{svc}】新品上市{product}，会员专享价{amt}元",
    "您关注的{product}已降价，限时优惠{amt}元",
    "恭喜您获得{amt}元红包，点击APP领取",
    "亲爱的会员，本月消费满{amt}元送{product}",
    "特惠通知：{product}今日秒杀价{amt}元，仅限今日",
    "您的账户获得{amt}元体验金，可用于购买{product}",
    "好消息！{svc}推出会员专享价，最低{amt}元起",
    # ── Friend / family / chat (20) ──
    "好的，明天{time}老地方见",
    "收到了，我马上到",
    "今天开会的材料我发你邮箱了",
    "验证码：{code}，请查收",
    "我到了，你在哪？",
    "好的收到，谢谢！",
    "今天加班，晚点回去",
    "周末一起去{city}玩吗？",
    "生日快乐！祝你天天开心",
    "帮我带份午饭，谢谢",
    "明天考试加油！",
    "新年快乐，万事如意！",
    "妈，今晚不回来吃饭了",
    "你的快递我帮你收了",
    "到了记得回个消息",
    "刚才开会没看手机，怎么了？",
    "明天几点出发？我来安排",
    "好的没问题，交给我了",
    "你推荐的{city}那家店确实不错",
    "路上堵车，可能要晚到{min}分钟",
    # ── Travel / ticket notifications (4) ──
    "【{svc}】您预订的{date} {city}至{city2}的航班已出票",
    "【{svc}】您购买的{date} {time}火车票已出票，座位{seat}",
    "【{svc}】您的酒店订单已确认，入住{date}离店{date2}",
    "【{svc}】您的景点门票已出票，游览日期{date}",
    # ── School / parent notifications (4) ──
    "【{svc}】您的孩子{date}的课程安排已发送，请查收",
    "【{svc}】家长您好，{date}将举行家长会，届时请准时参加",
    "家长您好，您的孩子{date}课程安排已更新，请查看APP",
    "温馨提示：{date}学校将举行家长开放日，欢迎参加",

def gen_synthetic_phish(n=2500):
    texts = []
    schemes = ["http", "https"]
    tlds = [".com", ".cn", ".net", ".xyz", ".top", ".info"]
    for _ in range(n):
        tmpl = random.choice(PHISH_TEMPLATES)
        bank = random.choice(BANKS)
        agency = random.choice(AGENCIES)
        scheme = random.choice(schemes)
        tld = random.choice(tlds)
        if random.random() < 0.5:
            url = f"{scheme}://www.{bank}-safe.com/auth"
        else:
            url = f"{scheme}://{bank[:2]}bank.{random.choice(['verify', 'secure', 'check'])}.com"
        phone = f"400-{random.randint(100,999)}-{random.randint(1000,9999)}"
        code = f"{random.randint(100000, 999999)}"
        text = tmpl.format(bank=bank, url=url, phone=phone, code=code,
                           agency=agency,
                           amt=str(random.randint(100, 50000)),
                           card=str(random.randint(1000, 9999)))
        texts.append(inject_noise(text))
    return texts


def gen_synthetic_legit(n):
    texts = []
    cities = ["北京", "上海", "广州", "深圳", "杭州", "成都", "武汉",
              "南京", "西安", "重庆", "长沙", "苏州"]
    names = ["张师傅", "李师傅", "王师傅", "刘师傅", "陈师傅"]
    addresses = ["A区3号楼", "B区1单元", "C座大厅", "D栋前台",
                 "丰巢柜A01", "菜鸟驿站#03"]
    products = ["蓝牙耳机", "充电宝", "数据线", "手机壳", "保温杯",
                "零食礼包", "洗面奶", "面膜", "笔记本", "鼠标"]
    services = ["洗车服务", "家政保洁", "空调清洗", "上门维修",
                "美甲服务", "理发预约"]
    orders = [f"JD{random.randint(10000000, 99999999)}" for _ in range(100)]
    seats = ["3A", "5F", "12C", "8D", "2B", "16A"]
    rates = ["3.5", "4.2", "5.0", "2.8", "6.0"]

    for _ in range(n):
        tmpl = random.choice(LEGIT_TEMPLATES)
        ent = random.choice(BANKS + SVCS + PLATFORMS + APPS)
        code = f"{random.randint(100000, 999999)}"
        amt = f"{random.randint(1, 50000)}"
        card = f"{random.randint(1000, 9999)}"
        bal = f"{random.randint(100, 99999)}"
        date = f"{random.randint(1, 12)}月{random.randint(1, 28)}日"
        date2 = f"{random.randint(1, 12)}月{random.randint(1, 28)}日"
        time = f"{random.randint(8, 20)}:{random.randint(0, 59):02d}"
        day = str(random.randint(2, 5))
        min = str(random.randint(10, 30))
        addr = random.choice(addresses)
        city = random.choice(cities)
        city2 = random.choice([c for c in cities if c != city])
        name = random.choice(names)
        phone2 = f"1{random.randint(30, 99)}{random.randint(10000000, 99999999)}"
        product = random.choice(products)
        service = random.choice(services)
        order = random.choice(orders)
        seat = random.choice(seats)
        credit = f"{random.randint(10000, 50000)}"
        rate = random.choice(rates)

        text = tmpl.format(
            bank=ent, svc=ent, plt=ent, app=ent,
            code=code, amt=amt, card=card, bal=bal,
            date=date, date2=date2, time=time, day=day, min=min,
            addr=addr, city=city, city2=city2,
            name=name, phone2=phone2,
            product=product, service=service,
            order=order, seat=seat,
            credit=credit, rate=rate,
        )
        texts.append(inject_noise(text))
    return texts


def load_fbs_cleaned(filepath):
    texts = []
    with open(filepath, encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            texts.append(row["text"])
    return texts


# ── Noise injection for robustness ─────────────────────────
TYPOS = {
    "的": "地得", "在": "再", "了": "啦", "吗": "嘛吧",
    "你": "您", "我": "俄", "是": "式",
}
TYPO_PROB = 0.05

def inject_noise(text: str) -> str:
    """Add small random noise to prevent formatting overfitting."""
    chars = list(text)
    # Random typos
    for i in range(len(chars)):
        if random.random() < TYPO_PROB and chars[i] in TYPOS:
            chars[i] = random.choice(TYPOS[chars[i]])
    # Random extra space before punctuation
    if random.random() < 0.03:
        chars.insert(random.randint(1, len(chars)-1), " ")
    # Random space removal
    if random.random() < 0.03:
        for i in range(len(chars)-1, 0, -1):
            if chars[i] == " " and random.random() < 0.3:
                chars.pop(i)
                break
    return "".join(chars)


def main():
    # 1. Load FBS cleaned data (REAL phishing SMS)
    fbs_path = os.path.join(RAW, "fbs_cleaned.csv")
    print(f"Loading FBS cleaned from {fbs_path}...")
    fbs_phish = load_fbs_cleaned(fbs_path)
    print(f"  FBS phishing: {len(fbs_phish)}")

    # 2. Generate synthetic phishing
    syn_phish = gen_synthetic_phish(2500)
    print(f"  Synthetic phishing: {len(syn_phish)}")

    # 3. All phishing: FBS + synthetic
    all_phish = fbs_phish + syn_phish
    n_phish = len(all_phish)
    print(f"  Total phishing: {n_phish}")

    # 4. Generate synthetic legitimate to match phishing count
    # Use 55 templates with diverse entities → ~55*30 = 1650 unique per batch
    # Need n_phish legitimate samples
    syn_legit = gen_synthetic_legit(n_phish)
    n_legit = len(syn_legit)
    print(f"  Synthetic legitimate: {n_legit}")

    # 5. Balance and shuffle
    n = min(n_phish, n_legit)
    rng = random.Random(SEED)
    phish_sample = rng.sample(all_phish, n)
    legit_sample = rng.sample(syn_legit, n)
    print(f"\nBalanced: {n} phishing, {n} legitimate")

    rows = []
    for t in phish_sample:
        rows.append([t, "phishing", "fbs_syn"])
    for t in legit_sample:
        rows.append([t, "legitimate", "synth"])
    rng.shuffle(rows)

    with open(OUTPUT, "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["text", "label", "source"])
        writer.writerows(rows)

    # Verify
    phish_cnt = sum(1 for r in rows if r[1] == "phishing")
    legit_cnt = sum(1 for r in rows if r[1] == "legitimate")
    lens_phish = [len(r[0]) for r in rows if r[1] == "phishing"]
    lens_legit = [len(r[0]) for r in rows if r[1] == "legitimate"]
    print(f"\nWritten: {len(rows)} rows to {OUTPUT}")
    print(f"  phishing: {phish_cnt}, legitimate: {legit_cnt}")
    print(f"  Phish len: avg={sum(lens_phish)/len(lens_phish):.0f}, "
          f"med={sorted(lens_phish)[len(lens_phish)//2]}")
    print(f"  Legit len: avg={sum(lens_legit)/len(lens_legit):.0f}, "
          f"med={sorted(lens_legit)[len(lens_legit)//2]}")
    print("  No ChiFraud web data used.")


if __name__ == "__main__":
    main()
