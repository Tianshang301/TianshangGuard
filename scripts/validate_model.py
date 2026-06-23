"""
TianshangGuard - Validate trained model on harder datasets
Tests: ChiFraud (Chinese fraud text), OpenPhish (live URLs), hard negatives
"""
import os, sys, io, csv
import numpy as np
import torch
import torch.nn as nn

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

# ── Add parent to path so we can import from train_phishing_model ──
sys.path.insert(0, os.path.dirname(__file__))
from train_phishing_model import BytePhishingTransformer, Config, ByteTokenizer, device

RESULTS_DIR = os.path.join(os.path.dirname(__file__), "validation_results")
os.makedirs(RESULTS_DIR, exist_ok=True)

# ── Load model ──────────────────────────────────────────────
def load_model(checkpoint_path):
    config = Config()
    model = BytePhishingTransformer(config).to(device)
    state = torch.load(checkpoint_path, map_location=device)
    model.load_state_dict(state)
    model.eval()
    print(f"Model loaded from {checkpoint_path}")
    return model, config

tokenizer = ByteTokenizer()

# ── Inference ──────────────────────────────────────────────
def predict(model, texts, batch_size=256):
    all_scores = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i:i+batch_size]
        tokens = np.stack([tokenizer.encode(t, Config.max_seq_len) for t in batch])
        tokens_t = torch.tensor(tokens, dtype=torch.long, device=device)
        with torch.no_grad():
            logits = model(tokens_t)
            scores = torch.sigmoid(logits).cpu().numpy()
        all_scores.extend(scores.tolist())
    return np.array(all_scores)

def print_metrics(name, scores, labels, threshold=0.5):
    preds = (scores > threshold).astype(int)
    tp = np.sum((preds == 1) & (labels == 1))
    fp = np.sum((preds == 1) & (labels == 0))
    tn = np.sum((preds == 0) & (labels == 0))
    fn = np.sum((preds == 0) & (labels == 1))
    acc = (tp + tn) / max(len(labels), 1)
    prec = tp / max(tp + fp, 1)
    rec = tp / max(tp + fn, 1)
    f1 = 2 * prec * rec / max(prec + rec, 1e-8)
    tpr = tp / max(tp + fn, 1)  # recall for fraud
    fpr = fp / max(fp + tn, 1)
    avg_score_legit = np.mean(scores[labels == 0]) if np.sum(labels == 0) > 0 else 0
    avg_score_fraud = np.mean(scores[labels == 1]) if np.sum(labels == 1) > 0 else 0

    print(f"\n{'='*60}")
    print(f"  {name}")
    print(f"{'='*60}")
    print(f"  Samples: {len(labels)} total | Fraud: {labels.sum():.0f} | Legit: {len(labels)-labels.sum():.0f}")
    print(f"  Accuracy:  {acc:.4f}")
    print(f"  Precision: {prec:.4f}")
    print(f"  Recall:    {rec:.4f}")
    print(f"  F1 Score:  {f1:.4f}")
    print(f"  TPR (fraud caught): {tpr:.4f}")
    print(f"  FPR (legit flagged): {fpr:.4f}")
    print(f"  Avg score (legit):   {avg_score_legit:.4f}")
    print(f"  Avg score (fraud):   {avg_score_fraud:.4f}")
    return {"acc": acc, "prec": prec, "rec": rec, "f1": f1, "tpr": tpr, "fpr": fpr}

# ── Test 1: ChiFraud (Chinese text, webpage content) ───────
def test_chifraud(model):
    base = os.path.join(os.path.dirname(__file__), "raw_data", "chifraud", "dataset")
    results = {}
    for split_name, filename in [("t2022", "ChiFraud_t2022.csv"), ("t2023", "ChiFraud_t2023.csv")]:
        path = os.path.join(base, filename)
        if not os.path.exists(path):
            print(f"  SKIP {filename} not found")
            continue
        texts, labels = [], []
        with open(path, "r", encoding="utf-8") as f:
            reader = csv.reader(f, delimiter="\t")
            header = next(reader)
            for row in reader:
                if len(row) >= 2:
                    label_id = int(row[0].strip())
                    label = 1 if label_id != 0 else 0  # binary: 0=legit, else=fraud
                    texts.append(row[1].strip())
                    labels.append(label)
        labels = np.array(labels)
        scores = predict(model, texts)
        metrics = print_metrics(f"ChiFraud {split_name} (binary: 0=legit, 1-10=fraud)", scores, labels)
        results[split_name] = metrics
    return results

# ── Test 2: OpenPhish live feed ────────────────────────────
def test_openphish(model):
    path = os.path.join(os.path.dirname(__file__), "raw_data", "openphish_feed.txt")
    if not os.path.exists(path):
        print("  SKIP openphish_feed.txt not found")
        return {}
    urls = []
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            url = line.strip()
            if url:
                urls.append(url)
    # All OpenPhish URLs are phishing → label = 1
    labels = np.ones(len(urls))
    scores = predict(model, urls)
    metrics = print_metrics("OpenPhish live URLs (all phishing)", scores, labels)
    return {"openphish": metrics}

# ── Test 3: Hard negatives - legitimate texts with phishing-like patterns ──
def test_hard_negatives(model):
    hard_legit = [
        # Real bank notifications that look like phishing
        "【工商银行】您尾号8888的账户发生5000元交易，余额12345.67元。",
        "【支付宝】您的验证码是123456，5分钟内有效，请勿泄露给他人。",
        "尊敬的用户，您的微信账户登录验证：验证码654321。",
        "【建设银行】您正在修改登录密码，验证码382910。",
        "支付宝安全提示：检测到新设备登录，如非本人请及时修改密码。",
        "尊敬的客户，您的招商银行信用卡账单已出，应还金额1999元，请按时还款。",
        "【中国银联】交易提醒：消费299.00元，余额888.50元。",
        "您的工商银行账户于2024年6月15日成功充值5000元。",
        "您好，这里是支付宝客服中心，您反馈的问题已受理。如需帮助请拨打官方客服热线。",
        "【微信支付】温馨提示：请通过官方渠道办理业务，切勿向陌生人转账。",
        "【支付宝】您已成功开通网上银行服务，请妥善保管密码。",
        "您的微信账户安全等级：高。定期检查账户活动有助于保障资金安全。",
        # Chinese news that might trigger false positives
        "今日国务院发布新政策，促进数字经济发展，引发社会各界广泛关注。",
        "天气预报：明日北京地区晴转多云，气温15-22度，出门请注意适当添衣。",
        "华为发布新一代芯片，性能提升50%，在通信设备领域市场占有率位居第一。",
        "在人工智能领域，近年来的发展速度令人瞩目。据统计，行业规模已突破万亿大关。",
        "近日，北京国家会议中心举办了一场关于数字经济的研讨会。与会专家一致认为，数字化转型是企业发展的必然趋势。",
        "每天步行8000步以上可以显著降低心血管疾病风险，这一发现对日常健身具有重要指导意义。",
        "该华为技术有限公司成立于1987年，经过多年发展，已成为通信设备领域的知名企业。",
        # Normal product descriptions (contain numbers, similar to phishing)
        "这款智能手表采用优质材料制成，适合日常使用。限时优惠仅需999元。",
        "新品上市：蓝牙耳机，限时优惠199元，全国包邮。",
        "【限时折扣】扫地机器人买一送一，活动仅剩24小时。",
        # Legitimate URLs (our training domain)
        "https://en.wikipedia.org/wiki/Machine_learning [SEP] Machine learning - Wikipedia",
        "https://stackoverflow.com/questions/12345 [SEP] Python list comprehension example",
        "https://github.com/torvalds/linux [SEP] Linux kernel source tree",
        "https://www.alipay.com/ [SEP] 支付宝 - 全球领先的独立第三方支付平台",
    ]
    hard_phish = [
        # Chinese phishing texts that look very similar to legitimate
        "【工商银行】您的账户出现异常，请立即点击链接验证身份，否则将被冻结。",
        "【支付宝安全中心】您的账户存在安全风险，请登录验证解除限制。",
        "尊敬的微信用户，您的账户已被限制使用，请点击链接重新实名认证。",
        "【建设银行】您的银行卡因涉嫌洗钱将被冻结，请配合调查。",
        "系统检测到您的银行安全账户存在风险，请立即处理。",
        "紧急通知：您的支付宝安全账户将在24小时内被锁定。",
        "我局发现您的银行账户涉嫌洗钱犯罪，请配合调查并将资金转入安全账户。",
        "您的账户涉嫌洗钱犯罪，已被立案调查，请立即配合调查。",
        "恭喜您获得iPhone16大奖！请点击链接支付5000元手续费后领取奖品。",
        "您被抽中为幸运用户，获得iPad，请填写个人信息领取。",
        "您的快递SF1234567890已丢失，请点击链接申请理赔。",
        "【快递通知】您的包裹因地址不详无法送达，请点击链接重新填写地址。",
        "银行安全账户出现异常，请点击链接验证身份。",
        "您的社保账户异常，请点击链接更新信息避免停用。",
        "【社保局】您的社保卡已被暂停使用，请点击链接重新激活。",
        "医保补贴已发放，请点击链接登记领取补贴金8888元。",
        # URL-based phishing (our training domain)
        "https://secure-bank.com/login/verify?token=abc123 [SEP] Secure Bank - Account Verification",
        "http://free-prize-winner.xyz/claim?amount=5000 [SEP] Congratulations! You Won!",
        "http://account-alert.phishing.com/refund?id=999 [SEP] Account Suspended - Verify Now",
    ]
    texts = hard_legit + hard_phish
    labels = np.array([0]*len(hard_legit) + [1]*len(hard_phish))
    scores = predict(model, texts)
    metrics = print_metrics("Hard Negatives (phishing-like legit + hard phish)", scores, labels)

    # Print individual results for hard negatives
    print(f"\n  --- Individual Hard Negative Results ---")
    for i, (text, label, score) in enumerate(zip(texts, labels, scores)):
        pred = score > 0.5
        status = "OK" if pred == label else "FAIL"
        tag = "PHISH" if label == 1 else "LEGIT"
        print(f"  [{status}] {tag} score={score:.4f} | {text[:60]}")
    return {"hard_negatives": metrics}

# ── Test 4: Model sanity - should output varied scores ──────
def test_score_distribution(model):
    """Verify model outputs scores in [0,1] range and are not all ~0.5 or all ~1.0"""
    texts = [
        "您的银行安全账户出现异常，请立即点击链接验证身份，否则账户将被冻结",
        "今日国务院发布新政策，促进数字经济发展，引发社会各界广泛关注",
        "恭喜您获得iPhone15大奖！请点击链接支付手续费后领取奖品",
        "天气预报：明日北京地区晴转多云，气温15-22度，出门请注意适当添衣",
    ]
    scores = predict(model, texts)
    print(f"\n{'='*60}")
    print(f"  Sanity Check: Score Distribution")
    print(f"{'='*60}")
    for text, score in zip(texts, scores):
        s = float(score)
        print(f"  Score={s:.4f} | {text[:50]}")
    min_s, max_s = scores.min(), scores.max()
    score_range = max_s - min_s
    print(f"  Score range: {min_s:.4f} - {max_s:.4f} (width={score_range:.4f})")
    if score_range < 0.1:
        print(f"  ⚠ WARNING: Score range too narrow ({score_range:.4f}) - model may not discriminate!")
    elif score_range > 0.5:
        print(f"  ✓ Good score variation.")
    else:
        print(f"  ⚠ Moderate variation - monitor.")
    return {"score_range": float(score_range), "min": float(min_s), "max": float(max_s)}

# ── Main ────────────────────────────────────────────────────
if __name__ == "__main__":
    model, config = load_model(os.path.join(os.path.dirname(__file__), "output", "best_model.pt"))

    all_results = {}

    print("\n\n========== TEST 1: ChiFraud Chinese Text ==========")
    all_results["chifraud"] = test_chifraud(model)

    print("\n\n========== TEST 2: OpenPhish Live URLs ==========")
    all_results["openphish"] = test_openphish(model)

    print("\n\n========== TEST 3: Hard Negatives ==========")
    all_results["hard_negatives"] = test_hard_negatives(model)

    print("\n\n========== TEST 4: Score Distribution ==========")
    all_results["score_dist"] = test_score_distribution(model)

    # ── Summary ────────────────────────────────────────────
    print(f"\n\n{'='*60}")
    print(f"  VALIDATION SUMMARY")
    print(f"{'='*60}")
    for test_name, metrics in all_results.items():
        if isinstance(metrics, dict):
            for sub, m in metrics.items():
                if isinstance(m, dict) and "f1" in m:
                    print(f"  {test_name}/{sub}: Acc={m['acc']:.4f} F1={m['f1']:.4f} TPR={m['tpr']:.4f} FPR={m['fpr']:.4f}")
                elif isinstance(m, (int, float)):
                    print(f"  {test_name}/{sub}: {m:.4f}")
        elif isinstance(metrics, dict) and "f1" in metrics:
            print(f"  {test_name}: Acc={metrics['acc']:.4f} F1={metrics['f1']:.4f} TPR={metrics['tpr']:.4f} FPR={metrics['fpr']:.4f}")
