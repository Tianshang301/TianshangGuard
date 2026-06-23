"""
TianshangGuard - Train tiny byte-level Transformer for Chinese phishing detection
Output: ONNX with INT8 quantization for Android ONNX Runtime
"""
import os, math, random, argparse
from urllib.parse import urlparse
import numpy as np
from tqdm import tqdm
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
from torch.amp import autocast, GradScaler
import onnx
from onnxruntime.quantization import quantize_dynamic, QuantType

# ── Reproducibility ──────────────────────────────────────────
SEED = 42
random.seed(SEED)
np.random.seed(SEED)
torch.manual_seed(SEED)
torch.cuda.manual_seed_all(SEED)

# ── Config Factory ───────────────────────────────────────────
class Config:
    def __init__(self, mode="url"):
        base_dir = os.path.dirname(__file__)
        self.vocab_size = 256
        self.cls_token_id = 101
        self.sep_token_id = 102
        self.pad_token_id = 0
        self.max_seq_len = 512
        self.dropout = 0.1
        self.num_epochs = 10
        self.lr = 3e-4
        self.weight_decay = 1e-5
        self.grad_clip = 1.0
        self.mode = mode

        if mode == "url":
            self.d_model = 64
            self.n_heads = 2
            self.n_layers = 2
            self.d_ff = 128
            self.batch_size = 128
            self.output_subdir = "url"
            self.onnx_name = "url_phishing.onnx"
        else:  # chinese
            self.d_model = 128
            self.n_heads = 4
            self.n_layers = 4
            self.d_ff = 256
            self.batch_size = 64
            self.output_subdir = "chinese"
            self.onnx_name = "chinese_phishing.onnx"

        self.output_dir = os.path.join(base_dir, "output", self.output_subdir)
        self.onnx_path = os.path.join(base_dir, "..",
            "app", "src", "main", "assets", "model", self.onnx_name)
        self.checkpoint_path = os.path.join(self.output_dir, "checkpoint.pt")

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Using device: {device}")
if device.type == "cuda":
    print(f"  GPU: {torch.cuda.get_device_name(0)}")
    print(f"  Memory: {torch.cuda.get_device_properties(0).total_memory / 1e9:.1f} GB")

# ── Byte-level Tokenizer (matches Android BertTokenizer) ─────
class ByteTokenizer:
    def encode(self, text: str, max_len: int) -> np.ndarray:
        tokens = np.zeros(max_len, dtype=np.int64)
        tokens[0] = 101  # CLS
        text_bytes = text.encode("utf-8")[:max_len - 2]
        for i, b in enumerate(text_bytes):
            tokens[i + 1] = b & 0xFF
        tokens[min(len(text_bytes) + 1, max_len - 1)] = 102  # SEP
        return tokens

tokenizer = ByteTokenizer()

# ── Synthetic Chinese Phishing Dataset ──────────────────────
PHISHING_TEMPLATES = [
    ["您的{prefix}安全账户{action}，请{next}",
     "系统检测到您的{prefix}安全账户{action}，{warning}",
     "紧急通知：您的{prefix}安全账户将在24小时内{action}",
     "【{prefix}安全中心】您的账户存在异常，请{next}"],
    ["请开启屏幕共享功能{action}",
     "根据{prefix}要求，您需要开启屏幕共享{next}",
     "为了协助您{action}，请打开屏幕共享功能",
     "{prefix}客服要求您共享屏幕以{next}"],
    ["您的账户涉嫌洗钱{action}，请配合调查",
     "我局发现您的{prefix}账户{action}涉嫌洗钱犯罪",
     "【{prefix}反诈中心】您涉嫌洗钱{action}，请立即{next}",
     "您的银行卡因涉嫌洗钱将被冻结，请{next}"],
    ["请点击链接验证身份{action}",
     "您的{prefix}账户需要验证身份{next}",
     "【{prefix}】点击链接完成身份验证{action}",
     "身份验证未通过，请在24小时内{next}"],
    ["请输入您的银行卡密码{action}",
     "您的银行卡密码已过期，请{next}",
     "为了您的账户安全，请重新设置银行卡密码{action}"],
    ["请将资金转账到安全账户{action}",
     "立即转账到指定账户{next}",
     "为防止资金损失，请转账到以下安全账户{action}"],
    ["点击退款链接{action}领取赔偿",
     "您的退款申请已通过，请点击链接{next}",
     "【{prefix}退款】点击链接领取退款{action}"],
    ["点击验证{action}解除限制",
     "请点击验证链接{next}确认身份",
     "【{prefix}】点击验证{action}恢复正常使用"],
    # Additional phishing patterns
    ["恭喜您获得{prize}大奖！请点击链接领取奖金{amount}元",
     "您被抽中为幸运用户，获得{prize}，请填写个人信息领取",
     "【抽奖通知】您已中奖{prize}，请支付{amount}元手续费后领取"],
    ["您的快递{tracking}已丢失，请点击链接申请理赔",
     "【快递通知】您的包裹因{reason}无法送达，请点击链接重新填写地址",
     "您好，您的快件{tracking}在运输中受损，请添加客服微信{customer}办理赔付"],
    ["{prefix}贷款为您提供{amount}元额度，点击链接立即申请",
     "您的贷款申请已通过，额度{amount}元，请点击链接确认",
     "【{prefix}】您有{amount}元待领取，限时免息，点击链接立即提现"],
    ["您的社保账户异常，请点击链接更新信息避免停用",
     "【社保局】您的社保卡已被暂停使用，请点击链接重新激活",
     "医保补贴已发放，请点击链接登记领取补贴金{amount}元"],
    ["{broker}推荐股票涨停！加入群聊获取内幕消息",
     "跟著老师操作，日赚{amount}元！点击链接加入交流群",
     "【{broker}】推荐牛股，收益翻倍，点击链接了解详情"],
]

LEGITIMATE_TEMPLATES = [
    # News (longer, more varied)
    "今日{subject}{predicate}，相关报道称{detail}。",
    "最新消息：{subject}于昨日{predicate}，引发广泛关注。",
    "据报道，{subject}{predicate}，预计将{effect}。",
    "财经新闻：今日股市{market}，成交量{volume}亿元，{sector}板块表现强势。",
    "天气预报：明日{city}地区{weather}，气温{temp}度，出门请注意{advice}。",
    "体育快讯：{team}在昨日比赛中{result}，晋级下一轮。",
    "科技前沿：{company}发布新一代{tech_product}，性能提升{percent}%。",
    "健康养生：专家建议每天{activity}{duration}分钟，有助于{benefit}。",
    "教育资讯：{year}年高考报名人数达到{students}万人，较去年{trend}。",
    "生活小贴士：{household_tip}",
    # Product descriptions with numbers (hard negatives)
    "这款{product}采用优质材料制成，适合{usage}。限时优惠仅需{price}元。",
    "新品上市：{product}，限时优惠{price}元，全国包邮。",
    "{product}用户评价良好，好评率{rate}%，推荐购买。",
    "{product}今日特价{price}元，原价{original_price}元，立即抢购！",
    "【限时折扣】{product}买一送一，活动仅剩{hours}小时。",
    "累计销量突破{sales}件，{product}好评如潮。",
    # Service notifications that look like phishing but are real (HARD NEGATIVES)
    "【{prefix}】您的验证码是{code}，5分钟内有效，请勿泄露给他人。",
    "{prefix}提醒您：如非本人操作请忽略此短信。",
    "尊敬的用户，您的{prefix}账户登录验证：验证码{code}。",
    "【{prefix}】您正在修改登录密码，验证码{code}。",
    "{prefix}安全提示：检测到新设备登录，如非本人请及时修改密码。",
    "【{prefix}】您尾号{card_last4}的账户发生{amount}元交易。",
    "您的{prefix}信用卡账单已出，应还金额{amount}元，请按时还款。",
    "【{prefix}】交易提醒：消费{amount}元，余额{balance}元。",
    "尊敬的客户，您的{prefix}账户于{date}成功充值{amount}元。",
    # Legitimate bank/service communications (HARD NEGATIVES)
    "您好，这里是{prefix}客服中心，您反馈的问题已受理。如需帮助请拨打官方客服热线。",
    "{prefix}温馨提示：请通过官方渠道办理业务，切勿向陌生人转账。",
    "【{prefix}】您已成功开通网上银行服务，请妥善保管密码。",
    "尊敬的用户，您的{prefix}账户已完成实名认证。",
    "【{prefix}】检测到您的密码强度较低，建议修改为包含字母和数字的复杂密码。",
    "您的{prefix}账户安全等级：高。定期检查账户活动有助于保障资金安全。",
    # News/magazine articles (longer, realistic)
    "在{topic}领域，近年来的发展速度令人瞩目。据统计，{stat_detail}。业内人士表示，{expert_comment}。",
    "近日，{location}举办了一场关于{topic2}的研讨会。与会专家一致认为，{expert_opinion}。这一观点引发了{reaction}。",
    "根据最新研究显示，{research_finding}。这一发现对{daily_life}具有重要指导意义，值得{action_advice}。",
    "该{organization}成立于{year_started}年，经过多年发展，已成为{industry}领域的知名企业。其核心产品{product}在市场上{performance}。",
    "文化简讯：{cultural_event}于昨日开幕，吸引了大批{audience}前来参观。展览将持续至{end_date}。",
    # General chat / social
    "{greeting}，今天天气真不错，适合{activity}。",
    "周末一起去{place}吧？听说那边{description}。",
    "最近在看{show}，推荐给你！真的太精彩了。",
    "推荐一家不错的{cafe}，他们家的{recommendation}特别好吃。",
    "今天去{location}逛了逛，人还挺多的。",
    # Tech/how-to
    "如何{task}？以下是详细教程。",
    "分享一个小技巧：{tip}。",
    "学习{skill}需要持之以恒的努力。",
    "Python编程入门：从变量到循环，一步步带你{learning_goal}。",
    "摄影技巧分享：{photo_tip}",
]

PREFIXES = ["银行", "支付宝", "微信", "京东", "淘宝", "中国银联", "工商银行", "建设银行", "农业银行",
            "招商银行", "浦发银行", "中国移动", "中国电信", "美团", "滴滴", "携程"]
ACTIONS = ["出现异常", "已被冻结", "即将失效", "存在风险", "被他人登录", "状态异常", "已被锁定"]
WARNINGS = ["请立即处理", "否则将承担法律责任", "切勿泄露给他人", "以免影响个人征信"]
NEXTS = ["处理", "核实", "配合调查", "进行操作"]
PRIZES = ["iPhone16", "iPad", "现金红包", "笔记本电脑", "购物卡"]
AMOUNTS = ["5000", "10000", "20000", "50000", "8888", "18888"]
TRACKINGS = ["SF1234567890", "YT9876543210", "ZT4567890123"]
REASONS = ["地址不详", "收件人拒收", "超时未取", "面单破损"]
CUSTOMERS = ["kf2024", "kf888", "kf_cs01"]
BROKERS = ["资深投顾王老师", "涨停板哥", "牛散联盟", "私募大佬"]
SUBJECTS = ["国务院", "教育部", "科技部", "气象局", "体育总局", "卫健委",
            "中国科学院", "清华大学研究团队", "国家统计局"]
PREDICATES = ["发布新政策", "公布统计数据", "启动专项行动", "召开新闻发布会"]
DETAILS = ["引起社会各界高度关注", "获得国际媒体广泛报道", "相关领域专家给予积极评价"]
EFFECTS = ["促进经济发展", "改善民生条件", "推动行业进步"]
MARKETS = ["震荡上行", "小幅回调", "量价齐升", "窄幅震荡"]
VOLUMES = ["5000", "8000", "10000", "12000"]
SECTORS = ["新能源", "半导体", "医药", "消费电子", "人工智能", "金融"]
CITIES = ["北京", "上海", "广州", "深圳", "杭州", "成都", "武汉"]
WEATHERS = ["晴转多云", "小雨", "阴天", "多云转晴"]
TEMPS = ["15-22", "20-28", "10-18", "25-32"]
ADVICES = ["带好雨具", "注意防晒", "适当添衣"]
TEAMS = ["中国队", "广州恒大", "上海申花", "北京国安"]
RESULTS = ["3:0大胜对手", "2:1逆转获胜", "1:1握手言和", "5:0完胜"]
COMPANIES = ["华为", "小米", "比亚迪", "宁德时代", "大疆"]
TECH_PRODUCTS = ["芯片", "智能汽车", "折叠屏手机", "无人机"]
PERCENTS = ["30%", "50%", "80%", "200%"]
DURATIONS = ["30", "45", "60"]
BENEFITS = ["增强免疫力", "改善睡眠", "降低血压", "减轻压力"]
YEARS = ["2024", "2025", "2026"]
STUDENTS = ["1342", "1353", "1368"]
TRENDS = ["增加5%", "增加3%", "基本持平"]
HOUSEHOLD_TIPS = ["用白醋和小苏打清洁水龙头，光亮如新。",
                   "冰箱定期除霜可以节省30%的电量。",
                   "淘米水可以用来浇花，富含营养成分。",
                   "出门前检查水电煤气，确保安全。"]
PRODUCTS = ["智能手表", "蓝牙耳机", "笔记本电脑", "运动鞋", "保温杯",
            "扫地机器人", "空气净化器", "咖啡机", "电动牙刷", "降噪耳机"]
USAGES = ["日常使用", "户外运动", "办公学习", "旅行出差", "居家生活"]
PRICES = ["99", "199", "299", "499", "999", "1299", "1999", "2999"]
ORIGINAL_PRICES = ["199", "399", "599", "999", "1999", "2999", "3999"]
RATES = ["98%", "95%", "99%", "97%"]
HOURS = ["24小时", "48小时", "12小时"]
SALES = ["10万+", "50万+", "100万+", "500万+"]
CODES = ["123456", "654321", "382910", "847261", "592037", "701284"]
CARD_LAST4 = ["8888", "6666", "1234", "5678", "9999"]
DATES = ["2024年6月15日", "2025年1月20日", "2026年3月8日"]
BALANCES = ["888.50", "12345.67", "500.00", "99999.99"]
TOPICS = ["人工智能", "新能源", "生物医药", "量子计算", "航天技术"]
STAT_DETAILS = ["行业规模已突破万亿大关", "研发投入年均增长15%以上",
                "相关企业数量超过10万家", "专利数量居全球前列"]
EXPERT_COMMENTS = ["这个领域还有很大的发展空间", "技术创新是核心竞争力",
                   "未来五年将迎来爆发式增长", "中国在这一领域已处于国际领先地位"]
LOCATIONS = ["北京国家会议中心", "上海世博展览馆", "深圳会展中心", "杭州未来科技城"]
TOPICS2 = ["数字经济", "绿色能源", "智能制造", "生物科技"]
EXPERT_OPINIONS = ["数字化转型是企业发展的必然趋势",
                   "绿色低碳是未来发展方向",
                   "智能制造将重塑传统产业格局"]
REACTIONS = ["业界的广泛共鸣", "各方的高度评价", "社会各界的积极响应"]
RESEARCH_FINDINGS = ["每天步行8000步以上可以显著降低心血管疾病风险",
                     "充足的睡眠有助于提高记忆力和创造力",
                     "适量饮用绿茶对健康有益"]
DAILY_LIVES = ["日常饮食", "运动健身", "工作学习"]
ACTION_ADVICES = ["养成良好的生活习惯", "持续关注和学习"]
ORGANIZATIONS = ["华为技术有限公司", "阿里巴巴集团", "深圳大疆创新"]
YEAR_STARTED = ["1987", "1999", "2006"]
INDUSTRIES = ["通信设备", "电子商务", "无人机"]
PERFORMANCES = ["市场占有率位居第一", "年营收突破千亿", "产品远销海外"]
CULTURAL_EVENTS = ["国际摄影展", "当代艺术双年展", "非遗文化展"]
AUDIENCES = ["摄影爱好者", "艺术从业者", "市民游客"]
END_DATES = ["7月15日", "8月30日", "10月7日"]
GREETINGS = ["你好", "嗨", "早上好", "下午好", "晚上好"]
ACTIVITIES = ["散步", "跑步", "逛街", "看电影", "爬山", "骑自行车"]
PLACES = ["公园", "商场", "图书馆", "咖啡馆", "博物馆"]
DESCRIPTIONS = ["环境很好", "人不多", "特别漂亮", "值得一去"]
SHOWS = ["流浪地球", "三体", "繁花", "狂飙", "漫长的季节", "繁花"]
CAFES = ["星巴克", "瑞幸", "Manner", "%Arabica"]
RECOMMENDATIONS = ["生椰拿铁", "澳白", "dirty", "手冲咖啡"]
TASKS = ["搭建个人网站", "学习Python", "制作PPT", "剪辑视频", "搭建博客"]
TIPS = ["使用快捷键Ctrl+Shift+S可以快速保存所有文件",
        "定期备份数据很重要，建议使用云盘+本地双重备份",
        "多喝水有益健康，每天建议饮用1.5-2升水",
        "学习新技能时，先建立整体框架再深入学习效果更好"]
SKILLS = ["编程", "英语", "摄影", "烹饪", "绘画", "吉他"]
LEARNING_GOALS = ["学会基础语法", "写出第一个程序", "掌握常用库的使用"]
PHOTO_TIPS = ["使用三分法构图可以让照片更有层次感",
              "黄金时段（日出后和日落前1小时）的光线最美",
              "拍摄人像时，选择大光圈可以获得柔美的背景虚化效果"]

def _build_format_kwargs() -> dict:
    """Build a comprehensive kwargs dict covering ALL possible template placeholders."""
    return {
        "prefix": random.choice(PREFIXES),
        "action": random.choice(ACTIONS),
        "warning": random.choice(WARNINGS),
        "next": random.choice(NEXTS),
        # phishing extras
        "prize": random.choice(PRIZES),
        "amount": random.choice(AMOUNTS),
        "tracking": random.choice(TRACKINGS),
        "reason": random.choice(REASONS),
        "customer": random.choice(CUSTOMERS),
        "broker": random.choice(BROKERS),
        # legitimate news
        "subject": random.choice(SUBJECTS),
        "predicate": random.choice(PREDICATES),
        "detail": random.choice(DETAILS),
        "effect": random.choice(EFFECTS),
        "market": random.choice(MARKETS),
        "volume": random.choice(VOLUMES),
        "sector": random.choice(SECTORS),
        "city": random.choice(CITIES),
        "weather": random.choice(WEATHERS),
        "temp": random.choice(TEMPS),
        "advice": random.choice(ADVICES),
        "team": random.choice(TEAMS),
        "result": random.choice(RESULTS),
        "company": random.choice(COMPANIES),
        "tech_product": random.choice(TECH_PRODUCTS),
        "percent": random.choice(PERCENTS),
        "duration": random.choice(DURATIONS),
        "benefit": random.choice(BENEFITS),
        "year": random.choice(YEARS),
        "students": random.choice(STUDENTS),
        "trend": random.choice(TRENDS),
        "household_tip": random.choice(HOUSEHOLD_TIPS),
        # legitimate products
        "product": random.choice(PRODUCTS),
        "usage": random.choice(USAGES),
        "price": random.choice(PRICES),
        "original_price": random.choice(ORIGINAL_PRICES),
        "rate": random.choice(RATES),
        "hours": random.choice(HOURS),
        "sales": random.choice(SALES),
        # legitimate service notifications
        "code": random.choice(CODES),
        "card_last4": random.choice(CARD_LAST4),
        "balance": random.choice(BALANCES),
        "date": random.choice(DATES),
        # legitimate general
        "greeting": random.choice(GREETINGS),
        "activity": random.choice(ACTIVITIES),
        "place": random.choice(PLACES),
        "description": random.choice(DESCRIPTIONS),
        "show": random.choice(SHOWS),
        "cafe": random.choice(CAFES),
        "recommendation": random.choice(RECOMMENDATIONS),
        "location": random.choice(LOCATIONS),
        # legitimate how-to
        "task": random.choice(TASKS),
        "tip": random.choice(TIPS),
        "skill": random.choice(SKILLS),
        "learning_goal": random.choice(LEARNING_GOALS),
        "photo_tip": random.choice(PHOTO_TIPS),
        # legitimate articles
        "topic": random.choice(TOPICS),
        "stat_detail": random.choice(STAT_DETAILS),
        "expert_comment": random.choice(EXPERT_COMMENTS),
        "topic2": random.choice(TOPICS2),
        "expert_opinion": random.choice(EXPERT_OPINIONS),
        "reaction": random.choice(REACTIONS),
        "research_finding": random.choice(RESEARCH_FINDINGS),
        "daily_life": random.choice(DAILY_LIVES),
        "action_advice": random.choice(ACTION_ADVICES),
        "organization": random.choice(ORGANIZATIONS),
        "year_started": random.choice(YEAR_STARTED),
        "industry": random.choice(INDUSTRIES),
        "performance": random.choice(PERFORMANCES),
        "cultural_event": random.choice(CULTURAL_EVENTS),
        "audience": random.choice(AUDIENCES),
        "end_date": random.choice(END_DATES),
    }

def generate_phishing_text() -> str:
    templates = random.choice(PHISHING_TEMPLATES)
    template = random.choice(templates)
    return template.format(**_build_format_kwargs())

def generate_legitimate_text() -> str:
    template = random.choice(LEGITIMATE_TEMPLATES)
    return template.format(**_build_format_kwargs())

class PhishingDataset(Dataset):
    def __init__(self, num_samples: int, max_seq_len: int = 512):
        self.max_seq_len = max_seq_len
        self.samples = []
        half = num_samples // 2

        for _ in range(half):
            self.samples.append((generate_phishing_text(), 1.0))
        for _ in range(half):
            self.samples.append((generate_legitimate_text(), 0.0))

        random.shuffle(self.samples)

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        text, label = self.samples[idx]
        tokens = tokenizer.encode(text, self.max_seq_len)
        return torch.tensor(tokens, dtype=torch.long), torch.tensor(label, dtype=torch.float)

# ── Tiny Transformer Model ─────────────────────────────────
class BytePhishingTransformer(nn.Module):
    def __init__(self, config: Config):
        super().__init__()
        self.config = config

        self.embedding = nn.Embedding(config.vocab_size, config.d_model, padding_idx=0)
        self.pos_embedding = nn.Parameter(torch.randn(1, config.max_seq_len, config.d_model) * 0.02)

        encoder_layer = nn.TransformerEncoderLayer(
            d_model=config.d_model,
            nhead=config.n_heads,
            dim_feedforward=config.d_ff,
            dropout=config.dropout,
            activation="gelu",
            batch_first=True,
            norm_first=True,
        )
        self.transformer = nn.TransformerEncoder(encoder_layer, num_layers=config.n_layers)

        self.pooler = nn.Sequential(
            nn.Linear(config.d_model, config.d_model),
            nn.Tanh(),
        )
        self.classifier = nn.Linear(config.d_model, 1)

        self._init_weights()

    def _init_weights(self):
        for p in self.parameters():
            if p.dim() > 1:
                nn.init.normal_(p, mean=0.0, std=0.02)

    def forward(self, input_ids: torch.Tensor) -> torch.Tensor:
        seq_len = input_ids.size(1)
        pos = self.pos_embedding[:, :seq_len, :]

        x = self.embedding(input_ids) + pos
        x = self.transformer(x)

        cls_token = x[:, 0, :]
        pooled = self.pooler(cls_token)
        logit = self.classifier(pooled).squeeze(-1)
        return logit

# ── CSV-based Dataset (from cleaned real data) ──────────────
class PhishingCSVDataset(Dataset):
    def __init__(self, csv_path: str, split: str = "train", val_ratio: float = 0.1, seed: int = 42, max_seq_len: int = 512, use_raw_text: bool = False):
        self.max_seq_len = max_seq_len
        import pandas as pd
        cols = ["text", "label", "URL", "Title", "source"] if use_raw_text else ["text", "label", "URL", "Title"]
        try:
            df = pd.read_csv(csv_path, usecols=cols)
        except ValueError:
            df = pd.read_csv(csv_path, usecols=["text", "label"])
            use_raw_text = True
        df = df.dropna(subset=["text", "label"])
        if use_raw_text:
            # Use the text column directly (for Chinese text data)
            df["text"] = df["text"].astype(str)
        else:
            # Use URL + Title as combined text (stronger signal than domain-only)
            df["URL"] = df["URL"].fillna("").astype(str)
            df["Title"] = df["Title"].fillna("").astype(str)
            df["text"] = df["URL"] + " [SEP] " + df["Title"]
        df["label"] = df["label"].astype(float)

        # Shuffle deterministically
        df = df.sample(frac=1.0, random_state=seed).reset_index(drop=True)

        split_idx = int(len(df) * (1 - val_ratio))
        if split == "train":
            df = df.iloc[:split_idx]
        else:
            df = df.iloc[split_idx:]

        self.texts = df["text"].tolist()
        self.labels = df["label"].tolist()
        print(f"      {split}: {len(self.texts)} samples ({sum(self.labels):.0f} phishing, {len(self.labels) - sum(self.labels):.0f} legit)")

    def __len__(self):
        return len(self.texts)

    def __getitem__(self, idx):
        tokens = tokenizer.encode(self.texts[idx], self.max_seq_len)
        return torch.tensor(tokens, dtype=torch.long), torch.tensor(self.labels[idx], dtype=torch.float)

# ── Checkpoint Save / Resume ───────────────────────────────
def save_checkpoint(config, model, optimizer, scheduler, epoch, best_val_loss, scaler=None):
    ckpt = {
        "epoch": epoch,
        "best_val_loss": best_val_loss,
        "model_state_dict": model.state_dict(),
        "optimizer_state_dict": optimizer.state_dict(),
        "scheduler_state_dict": scheduler.state_dict(),
        "random_state": random.getstate(),
        "np_random_state": np.random.get_state(),
        "torch_random_state": torch.get_rng_state(),
        "torch_cuda_random_state": torch.cuda.get_rng_state_all() if torch.cuda.is_available() else [],
    }
    if scaler is not None:
        ckpt["scaler_state_dict"] = scaler.state_dict()
    torch.save(ckpt, config.checkpoint_path)
    print(f"  -> Checkpoint saved (epoch {epoch})")

def load_checkpoint(config, model, optimizer, scheduler, scaler=None):
    ckpt = torch.load(config.checkpoint_path, map_location=device, weights_only=False)
    model.load_state_dict(ckpt["model_state_dict"])
    optimizer.load_state_dict(ckpt["optimizer_state_dict"])
    scheduler.load_state_dict(ckpt["scheduler_state_dict"])
    random.setstate(ckpt["random_state"])
    np.random.set_state(ckpt["np_random_state"])
    try:
        torch.set_rng_state(ckpt["torch_random_state"])
    except Exception:
        pass
    if torch.cuda.is_available() and ckpt.get("torch_cuda_random_state"):
        try:
            torch.cuda.set_rng_state_all(ckpt["torch_cuda_random_state"])
        except Exception:
            pass
    if scaler is not None and "scaler_state_dict" in ckpt:
        scaler.load_state_dict(ckpt["scaler_state_dict"])
    print(f"  -> Resumed from epoch {ckpt['epoch']} (best_val_loss={ckpt['best_val_loss']:.4f})")
    return ckpt["epoch"], ckpt["best_val_loss"]

def parse_args():
    parser = argparse.ArgumentParser(description="Train phishing detection model")
    parser.add_argument("--fresh", action="store_true",
                        help="Ignore existing checkpoint and train from scratch")
    parser.add_argument("--mode", choices=["url", "chinese"], default="url",
                        help="Model mode: url (small, PhiUSIIL) or chinese (full, ChiFraud+synthetic)")
    return parser.parse_args()

# ── URL Data Augmentation ─────────────────────────────────────
LEGIT_PATHS = [
    "/about", "/contact", "/products", "/services", "/blog",
    "/faq", "/support", "/help", "/terms", "/privacy",
    "/careers", "/team", "/portfolio", "/gallery", "/news",
    "/events", "/login", "/signup", "/search", "/profile",
    "/settings", "/dashboard", "/account", "/orders", "/cart",
    "/checkout", "/category/electronics", "/product/123",
    "/article/how-to-guide", "/post/welcome", "/page/2",
    "/2024/01/15/news", "/tag/technology", "/author/john",
    "/downloads", "/resources", "/documentation", "/api",
    "/status", "/pricing", "/compare", "/reviews", "/testimonials",
    "/community/forum", "/blog/post-1", "/knowledge-base",
    "/user/profile", "/index.php?id=1", "/?page=home",
    "/en/about", "/zh-CN/products", "/fr/contact",
    "/images/logo.png", "/assets/css/style.css",
    "/js/app.js", "/wp-content/themes/main/style.css",
    "/node_modules/lodash/index.js",
    "/.well-known/security.txt",
    "/robots.txt", "/sitemap.xml", "/favicon.ico",
    "/api/v1/users", "/api/v2/products?page=1",
    "/oauth/authorize?client_id=123&redirect_uri=https://example.com/callback",
]

WELL_KNOWN_LEGIT = [
    # github - with and without www, various paths
    ("https://github.com", "/user/repo", "GitHub repository"),
    ("https://github.com", "/user/repo/issues/1", "Issue tracker"),
    ("https://github.com", "/", "0"),
    ("https://github.com", "/about", "GitHub About"),
    ("https://github.com", "", "GitHub"),
    ("https://www.github.com", "/", "0"),
    ("https://www.github.com", "/user/repo", "GitHub repository"),
    # wikipedia
    ("https://en.wikipedia.org", "/wiki/Main_Page", "Wikipedia"),
    ("https://en.wikipedia.org", "/wiki/Machine_learning", "Machine learning - Wikipedia"),
    ("https://en.wikipedia.org", "/", "home"),
    ("https://en.wikipedia.org", "/wiki/Python", "Python"),
    ("https://www.wikipedia.org", "/", "Wikipedia portal"),
    ("https://www.wikipedia.org", "/", "home"),
    # stackoverflow
    ("https://stackoverflow.com", "/questions/12345", "Stack Overflow question"),
    ("https://stackoverflow.com", "/users/123/user", "User profile"),
    ("https://stackoverflow.com", "/", "0"),
    ("https://stackoverflow.com", "/tags/python", "Stack Overflow tags"),
    ("https://stackoverflow.com", "/", "Stack Overflow"),
    ("https://www.stackoverflow.com", "/", "0"),
    # google - with and without www
    ("https://www.google.com", "/search?q=test", "Google Search"),
    ("https://www.google.com", "/maps/place/Beijing", "Google Maps"),
    ("https://www.google.com", "/", "0"),
    ("https://www.google.com", "/", "Google"),
    ("https://google.com", "/search?q=test", "Google Search"),
    ("https://google.com", "/", "0"),
    # amazon
    ("https://www.amazon.com", "/dp/B08X123", "Amazon product page"),
    ("https://www.amazon.com", "/gp/cart/view.html", "Amazon cart"),
    ("https://www.amazon.com", "/", "0"),
    ("https://www.amazon.com", "/", "Amazon"),
    ("https://amazon.com", "/dp/B08X123", "Amazon product"),
    ("https://amazon.com", "/", "0"),
    # other common sites
    ("https://www.youtube.com", "/watch?v=abc123", "YouTube video"),
    ("https://www.youtube.com", "/channel/UC123", "YouTube channel"),
    ("https://www.youtube.com", "/", "0"),
    ("https://www.reddit.com", "/r/programming", "Reddit programming"),
    ("https://www.reddit.com", "/user/test", "Reddit user"),
    ("https://www.reddit.com", "/", "0"),
    ("https://twitter.com", "/user/status/123", "Tweet"),
    ("https://twitter.com", "/user", "Twitter profile"),
    ("https://twitter.com", "/", "0"),
    ("https://www.facebook.com", "/user/posts/123", "Facebook post"),
    ("https://www.facebook.com", "/", "0"),
    ("https://www.linkedin.com", "/in/user", "LinkedIn profile"),
    ("https://www.linkedin.com", "/", "0"),
    ("https://medium.com", "/@user/article", "Medium article"),
    ("https://medium.com", "/", "0"),
    # Chinese services
    ("https://www.baidu.com", "/s?wd=test", "百度搜索"),
    ("https://www.baidu.com", "/", "0"),
    ("https://www.zhihu.com", "/question/123", "知乎问题"),
    ("https://www.zhihu.com", "/people/user", "知乎用户"),
    ("https://www.zhihu.com", "/", "0"),
    ("https://www.bilibili.com", "/video/BV123", "B站视频"),
    ("https://www.bilibili.com", "/", "0"),
    ("https://www.weibo.com", "/u/123", "微博主页"),
    ("https://www.weibo.com", "/", "0"),
    ("https://www.taobao.com", "/item/123.htm", "淘宝商品"),
    ("https://www.taobao.com", "/", "0"),
    ("https://www.jd.com", "/product/123.html", "京东商品"),
    ("https://www.jd.com", "/", "0"),
    ("https://www.alipay.com", "/", "支付宝"),
    ("https://www.alipay.com", "/", "0"),
    ("https://www.wechat.com", "/", "微信"),
    ("https://www.wechat.com", "/", "0"),
    # tech/services
    ("https://httpbin.org", "/get", "HTTP test"),
    ("https://httpbin.org", "/", "0"),
    ("https://example.com", "/about", "Example site"),
    ("https://example.com", "/", "0"),
    ("https://news.ycombinator.com", "/item?id=123", "Hacker News"),
    ("https://news.ycombinator.com", "/", "0"),
    ("https://www.microsoft.com", "/en-us/software", "Microsoft"),
    ("https://www.microsoft.com", "/", "0"),
    ("https://www.apple.com", "/iphone", "Apple iPhone"),
    ("https://www.apple.com", "/", "0"),
    ("https://www.netflix.com", "/browse", "Netflix browse"),
    ("https://www.netflix.com", "/", "0"),
    ("https://www.spotify.com", "/playlist/123", "Spotify playlist"),
    ("https://www.spotify.com", "/", "0"),
    ("https://www.dropbox.com", "/home", "Dropbox home"),
    ("https://www.dropbox.com", "/", "0"),
    ("https://drive.google.com", "/drive/my-drive", "Google Drive"),
    ("https://drive.google.com", "/", "0"),
    ("https://mail.google.com", "/mail", "Gmail"),
    ("https://mail.google.com", "/", "0"),
    ("https://docs.google.com", "/document/d/123", "Google Docs"),
    ("https://docs.google.com", "/", "0"),
]

def augment_url_texts(texts, labels):
    """Break the spurious 'path=phishing' correlation by:
    - Adding realistic paths to legitimate URLs
    - Adding well-known legitimate domains with paths
    - Removing paths from some phishing URLs (domain-only)"""
    new_texts = list(texts)
    new_labels = list(labels)
    added_legit = 0
    added_legit_well_known = 0
    added_phish = 0
    rng = random.Random(42)

    # 1. Augment existing legitimate URLs with paths
    for text, label in zip(texts, labels):
        parts = text.split(" [SEP] ", 1)
        url_part = parts[0]
        title_part = parts[1] if len(parts) > 1 else ""

        if label == 0.0 and rng.random() < 0.6:
            path = rng.choice(LEGIT_PATHS)
            new_url = url_part.rstrip("/") + path
            new_texts.append(f"{new_url} [SEP] {title_part}")
            new_labels.append(0.0)
            added_legit += 1

        elif label == 1.0 and rng.random() < 0.3:
            try:
                parsed = urlparse(url_part)
                domain = parsed.netloc or parsed.path.split("/")[0]
                if domain:
                    scheme = parsed.scheme if parsed.scheme else "https"
                    new_url = f"{scheme}://{domain}"
                    new_texts.append(f"{new_url} [SEP] {title_part}")
                    new_labels.append(1.0)
                    added_phish += 1
            except Exception:
                pass

    # 2. Add well-known legitimate domains (generalization booster)
    for domain, path, title in WELL_KNOWN_LEGIT:
        for _ in range(3):  # Each well-known entry replicated 3x
            new_texts.append(f"{domain}{path} [SEP] {title}")
            new_labels.append(0.0)
            added_legit_well_known += 1

    combined = list(zip(new_texts, new_labels))
    rng.shuffle(combined)
    new_texts, new_labels = zip(*combined)
    print(f"  URL Augmentation: +{added_legit} legit (paths), +{added_legit_well_known} well-known legit, +{added_phish} phish (domain-only)")
    print(f"  Total training samples after augmentation: {len(new_texts)}")
    return list(new_texts), list(new_labels)


# ── Training ────────────────────────────────────────────────
def train(fresh=False, mode="url"):
    config = Config(mode)
    os.makedirs(config.output_dir, exist_ok=True)

    base_path = os.path.join(os.path.dirname(__file__), "raw_data")

    if mode == "url":
        csv_path = os.path.join(base_path, "clean_dataset.csv")
        if os.path.exists(csv_path):
            print(f"[URL Mode] Loading PhiUSIIL dataset from {csv_path}...")
            train_dataset = PhishingCSVDataset(csv_path, split="train", max_seq_len=config.max_seq_len)
            val_dataset = PhishingCSVDataset(csv_path, split="val", max_seq_len=config.max_seq_len)
            # No synthetic augmentation for URL model
            print(f"  Using PhiUSIIL: {len(train_dataset)} train, {len(val_dataset)} val")
            print("  Augmenting URL training data to break path=phishing spurious correlation...")
            aug_texts, aug_labels = augment_url_texts(train_dataset.texts, train_dataset.labels)
            train_dataset.texts = aug_texts
            train_dataset.labels = aug_labels
        else:
            print("CSV not found, generating synthetic dataset...")
            train_dataset = PhishingDataset(20000, max_seq_len=config.max_seq_len)
            val_dataset = PhishingDataset(2000, max_seq_len=config.max_seq_len)

    else:  # chinese
        csv_path = os.path.join(base_path, "combined_dataset.csv")

        if os.path.exists(csv_path):
            print(f"[Chinese Mode] Loading dataset from {csv_path}...")
            import pandas as pd
            df_all = pd.read_csv(csv_path)
            # Filter to only ChiFraud data (Chinese text), exclude PhiUSIIL URLs
            if "source" in df_all.columns:
                df_all = df_all[df_all["source"].str.contains("ChiFraud", na=False)]
            # Drop rows with empty text
            df_all = df_all.dropna(subset=["text"])
            df_all["text"] = df_all["text"].astype(str)
            # Clean up text: remove leading " [SEP] " artifact
            df_all["text"] = df_all["text"].str.replace(r"^ \[SEP\] ", "", regex=True)
            # Balance phishing/legitimate
            phishing = df_all[df_all["label"] == 1.0]
            legit = df_all[df_all["label"] == 0.0]
            n = min(len(phishing), len(legit))
            df_balanced = pd.concat([phishing.sample(n, random_state=42), legit.sample(n, random_state=42)])
            df_balanced = df_balanced.sample(frac=1, random_state=42).reset_index(drop=True)

            split_idx = int(len(df_balanced) * 0.9)
            train_df = df_balanced.iloc[:split_idx]
            val_df = df_balanced.iloc[split_idx:]

            print(f"  ChiFraud: {len(df_balanced)} balanced samples ({len(train_df)} train, {len(val_df)} val)")

            # Wrapper Dataset for pandas DataFrame
            class DataFrameDataset(Dataset):
                def __init__(self, df, max_seq_len):
                    self.texts = df["text"].tolist()
                    self.labels = df["label"].astype(float).tolist()
                    self.max_seq_len = max_seq_len
                def __len__(self):
                    return len(self.texts)
                def __getitem__(self, idx):
                    tokens = tokenizer.encode(self.texts[idx], self.max_seq_len)
                    return torch.tensor(tokens, dtype=torch.long), torch.tensor(self.labels[idx], dtype=torch.float)

            train_dataset = DataFrameDataset(train_df, config.max_seq_len)
            val_dataset = DataFrameDataset(val_df, config.max_seq_len)

            # Augment with synthetic Chinese phishing/legitimate texts
            print("  Augmenting training data with synthetic Chinese texts...")
            syn_phishing = [generate_phishing_text() for _ in range(10000)]
            syn_legitimate = [generate_legitimate_text() for _ in range(10000)]
            syn_texts = syn_phishing + syn_legitimate
            syn_labels = [1.0] * 10000 + [0.0] * 10000
            combined = list(zip(syn_texts, syn_labels))
            random.shuffle(combined)
            syn_texts, syn_labels = zip(*combined)
            train_dataset.texts.extend(list(syn_texts))
            train_dataset.labels.extend(list(syn_labels))
            print(f"    Added 20K synthetic Chinese texts")
            print(f"    Total train: {len(train_dataset)} samples")
        else:
            print("CSV not found, using synthetic only...")
            train_dataset = PhishingDataset(20000, max_seq_len=config.max_seq_len)
            val_dataset = PhishingDataset(2000, max_seq_len=config.max_seq_len)

    train_loader = DataLoader(train_dataset, batch_size=config.batch_size, shuffle=True, num_workers=0)
    val_loader = DataLoader(val_dataset, batch_size=config.batch_size, shuffle=False, num_workers=0)

    model = BytePhishingTransformer(config).to(device)
    total_params = sum(p.numel() for p in model.parameters())
    print(f"Model parameters: {total_params:,}")

    optimizer = torch.optim.AdamW(
        model.parameters(), lr=config.lr,
        weight_decay=config.weight_decay
    )
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(
        optimizer, T_max=config.num_epochs
    )
    criterion = nn.BCEWithLogitsLoss()
    scaler = GradScaler(enabled=(device.type == "cuda"))

    # ── Resume from checkpoint ─────────────────────────────
    start_epoch = 1
    best_val_loss = float("inf")
    if not fresh and os.path.exists(config.checkpoint_path):
        print(f"Checkpoint found at {config.checkpoint_path}")
        start_epoch, best_val_loss = load_checkpoint(
            config, model, optimizer, scheduler, scaler
        )
        start_epoch += 1  # resume from next epoch
        print(f"  Resuming training from epoch {start_epoch}")
    else:
        if fresh:
            print("  --fresh: starting from scratch")
        else:
            print("  No checkpoint found, starting from scratch")

    for epoch in range(start_epoch, config.num_epochs + 1):
        model.train()
        total_loss = 0.0
        correct = 0
        total = 0

        pbar = tqdm(train_loader, desc=f"Epoch {epoch}/{config.num_epochs}", unit="batch")
        for tokens, labels in pbar:
            tokens, labels = tokens.to(device), labels.to(device)
            optimizer.zero_grad()

            with autocast(device_type=device.type, enabled=(device.type == "cuda")):
                outputs = model(tokens)
                loss = criterion(outputs, labels)

            scaler.scale(loss).backward()
            scaler.unscale_(optimizer)
            torch.nn.utils.clip_grad_norm_(model.parameters(), config.grad_clip)
            scaler.step(optimizer)
            scaler.update()

            total_loss += loss.item()
            predicted = (torch.sigmoid(outputs) > 0.5).float()
            correct += (predicted == labels).sum().item()
            total += labels.size(0)
            pbar.set_postfix(loss=loss.item(), acc=f"{correct/total:.4f}")

        train_loss = total_loss / len(train_loader)
        train_acc = correct / total
        scheduler.step()

        # Validation
        model.eval()
        val_loss = 0.0
        val_correct = 0
        val_total = 0
        with torch.no_grad():
            for tokens, labels in val_loader:
                tokens, labels = tokens.to(device), labels.to(device)
                with autocast(device_type=device.type, enabled=(device.type == "cuda")):
                    outputs = model(tokens)
                    loss = criterion(outputs, labels)
                val_loss += loss.item()
                predicted = (torch.sigmoid(outputs) > 0.5).float()
                val_correct += (predicted == labels).sum().item()
                val_total += labels.size(0)

        val_loss /= len(val_loader)
        val_acc = val_correct / val_total

        print(f"Epoch {epoch}/{config.num_epochs} | "
              f"Train Loss: {train_loss:.4f} Acc: {train_acc:.4f} | "
              f"Val Loss: {val_loss:.4f} Acc: {val_acc:.4f} | "
              f"LR: {scheduler.get_last_lr()[0]:.6f}")

        if val_loss < best_val_loss:
            best_val_loss = val_loss
            torch.save(model.state_dict(), os.path.join(config.output_dir, "best_model.pt"))
            print(f"  -> Saved best model (val_loss={val_loss:.4f})")

        # Save checkpoint after every epoch
        save_checkpoint(config, model, optimizer, scheduler, epoch, best_val_loss, scaler)

    # Save final model
    torch.save(model.state_dict(), os.path.join(config.output_dir, "final_model.pt"))
    print("Training complete!")
    return model, config

# ── ONNX Export + INT8 Quantization ───────────────────────
def export_onnx(model, config):
    model.eval()

    class ModelWithSigmoid(nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m
        def forward(self, x):
            return torch.sigmoid(self.m(x))

    export_model = ModelWithSigmoid(model).to(device)
    dummy_input = torch.zeros(1, config.max_seq_len, dtype=torch.long, device=device)

    onnx_dir = os.path.join(config.output_dir, "onnx")
    os.makedirs(onnx_dir, exist_ok=True)

    int8_path = os.path.join(onnx_dir, "model_int8.onnx")

    # Export directly to FP32, then quantize
    fp32_path = os.path.join(onnx_dir, "model_fp32.onnx")

    torch.onnx.export(
        export_model,
        dummy_input,
        fp32_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={"input": {0: "batch_size"}, "output": {0: "batch_size"}},
        opset_version=17,
    )
    print(f"FP32 ONNX exported: {fp32_path} ({os.path.getsize(fp32_path) / 1e6:.1f} MB)")

    # INT8 dynamic quantization
    quantize_dynamic(
        fp32_path,
        int8_path,
        weight_type=QuantType.QInt8,
    )

    # Copy to Android assets
    android_path = config.onnx_path
    os.makedirs(os.path.dirname(android_path), exist_ok=True)
    import shutil
    shutil.copy2(int8_path, android_path)
    print(f"INT8 ONNX copied to assets: {android_path} ({os.path.getsize(android_path) / 1e6:.1f} MB)")

    # Validate
    onnx_model = onnx.load(int8_path)
    onnx.checker.check_model(onnx_model)
    print("ONNX model validation passed!")
    return int8_path

# ── Test Inference on ONNX ────────────────────────────────
def test_onnx_inference(onnx_path, mode="url", max_seq_len=512):
    import onnxruntime as ort

    session = ort.InferenceSession(onnx_path)
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name

    if mode == "url":
        test_texts = [
            ("https://secure-bank.com/login/verify?token=abc123 [SEP] Secure Bank - Account Verification", True),
            ("http://free-prize-winner.xyz/claim?amount=5000 [SEP] Congratulations! You Won!", True),
            ("http://account-alert.phishing.com/refund?id=999 [SEP] Account Suspended - Verify Now", True),
            ("https://en.wikipedia.org/wiki/Machine_learning [SEP] Machine learning - Wikipedia", False),
            ("https://stackoverflow.com/questions/12345 [SEP] Python list comprehension example", False),
            ("https://github.com/torvalds/linux [SEP] Linux kernel source tree", False),
            ("https://www.alipay.com/ [SEP] 支付宝 - 全球领先的独立第三方支付平台", False),
        ]
    else:
        test_texts = [
            ("您的银行安全账户出现异常，请立即点击链接验证身份，否则账户将被冻结", True),
            ("我局发现您涉嫌洗钱犯罪，请配合调查并将资金转入安全账户", True),
            ("恭喜您获得iPhone15大奖！请点击链接支付手续费后领取奖品", True),
            ("【社保局】您的社保卡已被暂停使用，请点击链接重新激活", True),
            ("今日国务院发布新政策，促进数字经济发展，引发社会各界广泛关注", False),
            ("天气预报：明日北京地区晴转多云，气温15-22度，出门请注意适当添衣", False),
            ("华为发布新一代芯片，性能提升50%，在通信设备领域市场占有率位居第一", False),
            ("尊敬的客户，您的工商银行账户于2024年6月15日成功充值5000元", False),
        ]

    print(f"\n=== [{mode.upper()}] ONNX Inference Test ===")
    correct = 0
    for text, expected_phishing in test_texts:
        tokens = tokenizer.encode(text, max_seq_len).reshape(1, -1)
        output = session.run([output_name], {input_name: tokens})[0]
        score = output.item() if hasattr(output, 'item') else output[0] if output.ndim == 1 else output[0][0]
        predicted = score > 0.5
        status = "OK" if predicted == expected_phishing else "FAIL"
        confidence = score if predicted else 1 - score
        if predicted == expected_phishing:
            correct += 1
        print(f"  {status} Score={score:.4f} Conf={confidence:.4f} | {text[:50]}")
    acc = correct / len(test_texts)
    print(f"  Accuracy: {correct}/{len(test_texts)} = {acc:.2%}")
    print("===========================\n")

# ── Main ────────────────────────────────────────────────────
if __name__ == "__main__":
    args = parse_args()
    print(f"Mode: {args.mode} | Fresh: {args.fresh}")
    model, config = train(fresh=args.fresh, mode=args.mode)
    onnx_path = export_onnx(model, config)
    test_onnx_inference(onnx_path, mode=args.mode, max_seq_len=config.max_seq_len)
    print(f"\nAll done! Model ready at: {config.onnx_path}")
