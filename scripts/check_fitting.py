"""
TianshangGuard - Check Model Fitting Quality
Evaluates URL and Chinese models for overfitting/underfitting/good fit
"""

import os, sys, json, math, random
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader

sys.path.insert(0, os.path.dirname(__file__))
from train_phishing_model import (
    BytePhishingTransformer, Config, ByteTokenizer, tokenizer, device,
    PhishingDataset, generate_phishing_text, generate_legitimate_text,
    _build_format_kwargs
)

RESULTS_FILE = os.path.join(os.path.dirname(__file__), "fitting_report.json")
os.makedirs(os.path.dirname(RESULTS_FILE), exist_ok=True)

# ── Evaluation helpers ─────────────────────────────────────
def compute_metrics(scores, labels, threshold=0.5):
    preds = (scores > threshold).astype(int)
    tp = np.sum((preds == 1) & (labels == 1))
    fp = np.sum((preds == 1) & (labels == 0))
    tn = np.sum((preds == 0) & (labels == 0))
    fn = np.sum((preds == 0) & (labels == 1))
    n = len(labels)
    acc = (tp + tn) / max(n, 1)
    prec = tp / max(tp + fp, 1)
    rec = tp / max(tp + fn, 1)
    f1 = 2 * prec * rec / max(prec + rec, 1e-8)
    tpr = rec
    fpr = fp / max(fp + tn, 1)
    avg_pos = np.mean(scores[labels == 1]) if np.sum(labels == 1) > 0 else 0.0
    avg_neg = np.mean(scores[labels == 0]) if np.sum(labels == 0) > 0 else 0.0
    margin = avg_pos - avg_neg
    
    # Best threshold by F1
    best_f1 = 0
    best_th = threshold
    for th in np.linspace(0.05, 0.95, 19):
        p = (scores > th).astype(int)
        tpp = np.sum((p == 1) & (labels == 1))
        fpp = np.sum((p == 1) & (labels == 0))
        fnn = np.sum((p == 0) & (labels == 1))
        prec_t = tpp / max(tpp + fpp, 1)
        rec_t = tpp / max(tpp + fnn, 1)
        f1_t = 2 * prec_t * rec_t / max(prec_t + rec_t, 1e-8)
        if f1_t > best_f1:
            best_f1 = f1_t
            best_th = th
    
    return {
        "accuracy": round(acc, 4),
        "precision": round(prec, 4),
        "recall": round(rec, 4),
        "f1": round(f1, 4),
        "tpr": round(tpr, 4),
        "fpr": round(fpr, 4),
        "avg_score_phishing": round(avg_pos, 4),
        "avg_score_legit": round(avg_neg, 4),
        "score_margin": round(margin, 4),
        "best_threshold": round(best_th, 2),
        "best_f1": round(best_f1, 4),
        "samples": int(n),
        "phishing_count": int(labels.sum()),
        "legit_count": int(n - labels.sum()),
    }


def score_distribution_health(scores, labels):
    s_pos = scores[labels == 1]
    s_neg = scores[labels == 0]
    result = {
        "phishing_score_mean": float(np.mean(s_pos)) if len(s_pos) > 0 else 0,
        "phishing_score_std": float(np.std(s_pos)) if len(s_pos) > 0 else 0,
        "phishing_score_min": float(np.min(s_pos)) if len(s_pos) > 0 else 0,
        "phishing_score_max": float(np.max(s_pos)) if len(s_pos) > 0 else 0,
        "legit_score_mean": float(np.mean(s_neg)) if len(s_neg) > 0 else 0,
        "legit_score_std": float(np.std(s_neg)) if len(s_neg) > 0 else 0,
        "legit_score_min": float(np.min(s_neg)) if len(s_neg) > 0 else 0,
        "legit_score_max": float(np.max(s_neg)) if len(s_neg) > 0 else 0,
        "overlap_ratio": float(np.sum(s_pos <= 0.5) + np.sum(s_neg > 0.5)) / max(len(scores), 1),
    }
    return result


def check_overfitting(train_metrics, val_metrics):
    gap_acc = train_metrics["accuracy"] - val_metrics["accuracy"]
    gap_f1 = train_metrics["f1"] - val_metrics["f1"]
    verdict = "GOOD FIT"
    if gap_acc > 0.15 or gap_f1 > 0.15:
        verdict = "OVERFITTING (severe)"
    elif gap_acc > 0.08 or gap_f1 > 0.08:
        verdict = "OVERFITTING (moderate)"
    elif gap_acc < -0.05 or gap_f1 < -0.05:
        verdict = "UNDERFITTING (val better than train)"
    return {
        "gap_accuracy": round(gap_acc, 4),
        "gap_f1": round(gap_f1, 4),
        "verdict": verdict
    }


# ── Generate fresh holdout test set (never seen by model) ──
def generate_test_set(n=5000, seed=999):
    random.seed(seed)
    np.random.seed(seed)
    texts, labels = [], []
    half = n // 2
    for _ in range(half):
        texts.append(generate_phishing_text())
        labels.append(1.0)
    for _ in range(half):
        texts.append(generate_legitimate_text())
        labels.append(0.0)
    combined = list(zip(texts, labels))
    random.shuffle(combined)
    texts, labels = zip(*combined)
    return list(texts), np.array(labels)


# ── URL: load real held-out data ──────────────────────────────
def load_url_test_data():
    from train_phishing_model import PhishingCSVDataset
    csv_path = os.path.join(os.path.dirname(__file__), "raw_data", "clean_dataset.csv")
    if not os.path.exists(csv_path):
        return None, None, None
    val = PhishingCSVDataset(csv_path, split="val", max_seq_len=512)
    return val.texts, np.array(val.labels), f"PhiUSIIL val ({len(val.texts)} samples)"


# ── URL generalization check (synthetic) ─────────────────────
PHISH_TLD_DOMAINS = [
    "secure-login.xyz", "verify-account.top", "account-update.xyz",
    "payment-confirm.net", "banking-secure.xyz", "auth-verify.top",
    "password-reset.xyz", "documents-signed.com", "invoice-pending.xyz",
    "account-verify.org", "login-secure.net", "payment-pending.top",
    "alert-security.xyz", "confirm-identity.com", "verification-page.xyz",
    "secure-banking.xyz", "update-account.top", "free-gift.xyz",
    "win-prize.net", "claim-reward.top",
]

LEGIT_DOMAINS = [
    "github.com", "google.com", "youtube.com", "facebook.com",
    "stackoverflow.com", "wikipedia.org", "amazon.com", "reddit.com",
    "twitter.com", "linkedin.com", "baidu.com", "zhihu.com",
    "bilibili.com", "weibo.com", "taobao.com", "jd.com",
    "microsoft.com", "apple.com", "netflix.com", "medium.com",
    "dropbox.com", "drive.google.com", "docs.google.com",
    "mail.google.com", "httpbin.org", "example.com",
    "news.ycombinator.com", "spotify.com",
]

URL_PATHS = [
    "/about", "/contact", "/products", "/services", "/blog",
    "/faq", "/support", "/help", "/terms", "/privacy",
    "/careers", "/team", "/login", "/signup", "/search",
    "/profile", "/settings", "/dashboard", "/account",
    "/orders", "/cart", "/checkout",
]

PHISH_PATHS = [
    "/login", "/verify", "/update", "/confirm", "/reset",
    "/secure", "/auth", "/signin", "/password-reset",
    "/account/verify", "/payment/confirm", "/identity/verify",
    "/login.php", "/verify.php", "/secure/login",
]


def generate_url_text(phishing: bool, rng: random.Random) -> str:
    if phishing:
        domain = rng.choice(PHISH_TLD_DOMAINS)
        path = rng.choice(PHISH_PATHS) if rng.random() < 0.7 else ""
        title = rng.choice(["Login", "Verify", "Account Update", "Payment Confirmation", "Secure Login",
                            "0", "home", "Security Alert", "Your Account"])
    else:
        domain = rng.choice(LEGIT_DOMAINS)
        path = rng.choice(URL_PATHS) if rng.random() < 0.5 else ""
        title = rng.choice(["Home", "About", "Contact", "Products", "Blog", "Support",
                            "0", "home", "Welcome", "Dashboard"])
    url = f"https://{domain}{path}" if path else f"https://{domain}"
    return f"{url} [SEP] {title}"


def generate_url_test_set(n=5000, seed=999):
    rng = random.Random(seed)
    texts, labels = [], []
    half = n // 2
    for _ in range(half):
        texts.append(generate_url_text(phishing=True, rng=rng))
        labels.append(1.0)
    for _ in range(half):
        texts.append(generate_url_text(phishing=False, rng=rng))
        labels.append(0.0)
    combined = list(zip(texts, labels))
    rng.shuffle(combined)
    texts, labels = zip(*combined)
    return list(texts), np.array(labels)


def load_model(mode, checkpoint_name="best_model.pt"):
    config = Config(mode)
    base = os.path.join(os.path.dirname(__file__), "output", config.output_subdir)
    ckpt_path = os.path.join(base, checkpoint_name)
    if not os.path.exists(ckpt_path):
        print(f"  Model not found: {ckpt_path}")
        return None, None
    model = BytePhishingTransformer(config).to(device)
    state = torch.load(ckpt_path, map_location=device, weights_only=True)
    model.load_state_dict(state)
    model.eval()
    return model, config


def predict(model, config, texts, batch_size=256):
    all_scores = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i:i+batch_size]
        tokens_np = np.stack([tokenizer.encode(t, config.max_seq_len) for t in batch])
        tokens_t = torch.tensor(tokens_np, dtype=torch.long, device=device)
        with torch.no_grad():
            logits = model(tokens_t)
            scores = torch.sigmoid(logits).cpu().numpy()
        all_scores.extend(scores.tolist())
    return np.array(all_scores)


def evaluate_model(mode, model, config, label_prefix=""):
    print(f"\n{'='*60}")
    print(f"  Evaluating {mode.upper()} model {label_prefix}")
    print(f"{'='*60}")
    
    result = {"mode": mode, "label": label_prefix.strip()}
    is_url = (mode == "url")
    
    # ── Primary: real held-out data (URL = PhiUSIIL val, Chinese = synthetic) ──
    if is_url:
        texts, labels, desc = load_url_test_data()
        if texts is not None:
            print(f"  Primary evaluation: {desc}")
            scores = predict(model, config, texts)
            metrics = compute_metrics(scores, labels)
            result["holdout_test"] = metrics
            result["holdout_test_label"] = desc
            print(f"  Holdout test: Acc={metrics['accuracy']:.4f} F1={metrics['f1']:.4f} "
                  f"P={metrics['precision']:.4f} R={metrics['recall']:.4f}")
            
            score_health = score_distribution_health(scores, labels)
            result["score_distribution"] = score_health
            print(f"  Score range: phishing [{score_health['phishing_score_min']:.3f}..{score_health['phishing_score_max']:.3f}] "
                  f"vs legit [{score_health['legit_score_min']:.3f}..{score_health['legit_score_max']:.3f}]")
            print(f"  Overlap ratio: {score_health['overlap_ratio']:.4f} "
                  f"({'BAD' if score_health['overlap_ratio'] > 0.2 else 'OK'})")
        else:
            print("  No PhiUSIIL val data found, fallthrough to synthetic URL tests")
    
    # ── Secondary: synthetic test set (generalization check) ──
    gen_set = generate_url_test_set if is_url else generate_test_set
    gen_name = "URL (synthetic)" if is_url else "Chinese text (synthetic)"
    print(f"  Synthetic holdout test ({gen_name}, seed=999)...")
    test_texts, test_labels = gen_set(5000, seed=999)
    test_scores = predict(model, config, test_texts)
    test_metrics = compute_metrics(test_scores, test_labels)
    result["synthetic_test"] = test_metrics
    print(f"  Synthetic: Acc={test_metrics['accuracy']:.4f} F1={test_metrics['f1']:.4f} "
          f"P={test_metrics['precision']:.4f} R={test_metrics['recall']:.4f}")
    
    # Score distribution on synthetic
    synth_health = score_distribution_health(test_scores, test_labels)
    result["synthetic_score_distribution"] = synth_health
    
    # 2. Second synthetic holdout with different seed
    print("  Synthetic holdout #2 (seed=42)...")
    test2_texts, test2_labels = gen_set(2000, seed=42)
    test2_scores = predict(model, config, test2_texts)
    test2_metrics = compute_metrics(test2_scores, test2_labels)
    result["synthetic_test2"] = test2_metrics
    print(f"  Synthetic #2: Acc={test2_metrics['accuracy']:.4f} F1={test2_metrics['f1']:.4f}")
    
    gap = abs(test_metrics["f1"] - test2_metrics["f1"])
    stability = "STABLE" if gap < 0.03 else "UNSTABLE"
    result["synthetic_stability"] = {"f1_gap": round(gap, 4), "verdict": stability}
    print(f"  Synthetic stability: {stability} (F1 gap={gap:.4f})")
    
    # 3. In-distribution test for overfitting check
    print("  In-distribution synthetic test (seed=123)...")
    ind_texts, ind_labels = gen_set(3000, seed=123)
    ind_scores = predict(model, config, ind_texts)
    ind_metrics = compute_metrics(ind_scores, ind_labels)
    result["in_distribution"] = ind_metrics
    
    of_check = check_overfitting(ind_metrics, test_metrics)
    result["overfitting_check"] = of_check
    print(f"  Overfitting check: {of_check['verdict']} (gap_acc={of_check['gap_accuracy']:.4f}, gap_f1={of_check['gap_f1']:.4f})")
    
    # 4. Test A: Domain-only (no path) — verify no path=phishing spurious correlation
    if is_url:
        domain_only_texts = []
        domain_only_labels = []
        phish_tld_domains = PHISH_TLD_DOMAINS + [
            "login-secure.xyz", "verify-account.top", "banking-secure.xyz",
            "payment-confirm.net", "secure-auth.xyz", "account-update.top",
        ]
        legit_tld_domains = LEGIT_DOMAINS + [
            "npmjs.com", "docker.com", "gitlab.com", "bitbucket.org",
            "cloudflare.com", "fastly.com", "jsdelivr.net", "cdnjs.com",
        ]
        for d in phish_tld_domains:
            domain_only_texts.append(f"https://{d} [SEP] 0")
            domain_only_labels.append(1.0)
        for d in legit_tld_domains:
            domain_only_texts.append(f"https://{d} [SEP] 0")
            domain_only_labels.append(0.0)
        do_scores = predict(model, config, domain_only_texts)
        do_metrics = compute_metrics(do_scores, np.array(domain_only_labels))
        result["test_A_domain_only"] = do_metrics
        do_pass = do_metrics["f1"] >= 0.85 and do_metrics["fpr"] <= 0.02
        print(f"  Test A (domain-only, {len(domain_only_texts)}): Acc={do_metrics['accuracy']:.4f} "
              f"F1={do_metrics['f1']:.4f} FPR={do_metrics['fpr']:.4f} "
              f"{'✅ PASS' if do_pass else '❌ FAIL'}")

        # Test B: Pure URL string (no features) — verify no feature dependency
        url_string_texts = []
        url_string_labels = []
        for domain in phish_tld_domains:
            for path in ["/login", "/verify", "/confirm"]:
                url_string_texts.append(f"https://{domain}{path} [SEP] 0")
                url_string_labels.append(1.0)
        for domain in legit_tld_domains:
            for path in ["/about", "/contact", "/blog"]:
                url_string_texts.append(f"https://{domain}{path} [SEP] 0")
                url_string_labels.append(0.0)
        us_scores = predict(model, config, url_string_texts)
        us_metrics = compute_metrics(us_scores, np.array(url_string_labels))
        result["test_B_url_string"] = us_metrics
        us_pass = us_metrics["f1"] >= 0.85
        print(f"  Test B (URL string only, {len(url_string_texts)}): Acc={us_metrics['accuracy']:.4f} "
              f"F1={us_metrics['f1']:.4f} {'✅ PASS' if us_pass else '❌ FAIL'}")

    # 5. Edge cases
    if is_url:
        edge_texts = [
            ("https://github.com/user/repo [SEP] GitHub", 0),
            ("https://www.google.com/search?q=test [SEP] Google", 0),
            ("https://stackoverflow.com/questions/123 [SEP] Stack Overflow", 0),
            ("https://en.wikipedia.org/wiki/Python [SEP] Python", 0),
            ("https://www.amazon.com/dp/B08X123 [SEP] Product", 0),
            ("https://www.baidu.com/s?wd=test [SEP] 百度", 0),
            ("https://secure-login.xyz/verify [SEP] Login", 1),
            ("https://banking-secure.top/login [SEP] Verify", 1),
            ("https://verify-account.xyz [SEP] 0", 1),
            ("https://github.com [SEP] 0", 0),
            ("https://google.com [SEP] home", 0),
            ("https://zhihu.com [SEP] home", 0),
            ("https://secure-login.xyz [SEP] Login", 1),
            ("https://verify-account.top [SEP] 0", 1),
        ]
    else:
        edge_texts = [
            ("安全账户", 1), ("验证身份", 1), ("恭喜中奖", 1),
            ("天气预报", 0), ("新品上市", 0), ("参加会议", 0),
            ("1234567890", 0), ("!!!!!!!", 0), ("......", 0),
            ("您的123456安全账户，请验证！", 1),
            ("今天的天气真好适合散步", 0),
            ("点击链接领取5000元奖金", 1),
            ("【支付宝】您的验证码123456，5分钟有效", 0),
        ]
    edge_texts_list = [t for t, _ in edge_texts]
    edge_labels = np.array([l for _, l in edge_texts])
    edge_scores = predict(model, config, edge_texts_list)
    edge_metrics = compute_metrics(edge_scores, edge_labels)
    result["edge_cases"] = edge_metrics
    print(f"  Edge cases ({len(edge_texts)}): Acc={edge_metrics['accuracy']:.4f}")
    
    for (text, label), score in zip(edge_texts, edge_scores):
        pred = score > 0.5
        status = "OK" if pred == label else "FAIL"
        print(f"    [{status}] score={score:.4f} pred={int(pred)} true={int(label)} | {text}")
    
    return result


def evaluate_onnx_model(mode):
    """Evaluate the deployed ONNX model vs the pytorch best model"""
    import onnxruntime as ort
    
    config = Config(mode)
    onnx_path = os.path.join(os.path.dirname(__file__), "..",
                             "app", "src", "main", "assets", "model",
                             config.onnx_name)
    
    if not os.path.exists(onnx_path):
        print(f"  ONNX not found: {onnx_path}")
        return None
    
    print(f"\n{'='*60}")
    print(f"  ONNX Model: {config.onnx_name} ({os.path.getsize(onnx_path)/1e3:.1f} KB)")
    print(f"{'='*60}")
    
    session = ort.InferenceSession(onnx_path)
    in_name = session.get_inputs()[0].name
    out_name = session.get_outputs()[0].name
    
    is_url = (mode == "url")
    
    # URL: use real held-out data; Chinese: synthetic
    if is_url:
        texts, labels, desc = load_url_test_data()
        if texts is not None:
            # Sample 2000 for speed
            rng = random.Random(777)
            indices = list(range(len(texts)))
            rng.shuffle(indices)
            indices = indices[:2000]
            texts = [texts[i] for i in indices]
            labels = labels[indices]
            print(f"  Testing on {desc} (sampled 2000)")
        else:
            texts, labels = generate_test_set(2000, seed=777)
    else:
        texts, labels = generate_test_set(2000, seed=777)
    
    all_scores = []
    for text in texts:
        tokens = tokenizer.encode(text, config.max_seq_len).reshape(1, -1).astype(np.int64)
        output = session.run([out_name], {in_name: tokens})[0]
        score = float(output.item() if hasattr(output, 'item') else output[0])
        all_scores.append(score)
    
    scores = np.array(all_scores)
    metrics = compute_metrics(scores, labels)
    print(f"  Holdout test (2000 samples):")
    print(f"    Acc={metrics['accuracy']:.4f} F1={metrics['f1']:.4f}")
    print(f"    P={metrics['precision']:.4f} R={metrics['recall']:.4f}")
    print(f"    Avg score phishing={metrics['avg_score_phishing']:.4f} legit={metrics['avg_score_legit']:.4f}")
    print(f"    Score margin={metrics['score_margin']:.4f}")
    
    return {"onnx_path": onnx_path, "size_kb": round(os.path.getsize(onnx_path)/1e3, 1), **metrics}


# ── Main ────────────────────────────────────────────────────
if __name__ == "__main__":
    print("=" * 60)
    print("  TianshangGuard - Model Fitting Quality Check")
    print("=" * 60)
    
    all_results = {}
    
    for mode in ["url", "chinese"]:
        model, config = load_model(mode, "best_model.pt")
        if model is None:
            model, config = load_model(mode, "final_model.pt")
        if model is None:
            print(f"  No model found for mode={mode}, skipping")
            continue
        
        result = evaluate_model(mode, model, config)
        all_results[mode] = result
        
        # ONNX comparison
        onnx_result = evaluate_onnx_model(mode)
        if onnx_result:
            all_results[f"{mode}_onnx"] = onnx_result
    
    # ── Summary ─────────────────────────────────────────
    print(f"\n\n{'='*60}")
    print(f"  FITTING QUALITY SUMMARY")
    print(f"{'='*60}")
    
    for key, result in all_results.items():
        if "onnx" in key:
            print(f"\n  [{key}]")
            print(f"    Size: {result.get('size_kb', '?')} KB")
            print(f"    F1={result.get('f1', '?'):.4f} Acc={result.get('accuracy', '?'):.4f}")
        else:
            mode = result.get("mode", "?")
            label = result.get("label", "")
            of = result.get("overfitting_check", {})
            sd = result.get("synthetic_score_distribution", result.get("score_distribution", {}))
            ht = result.get("holdout_test", {})
            st = result.get("synthetic_test", {})
            
            print(f"\n  [{mode} {label}]")
            if ht:
                print(f"    Real holdout: Acc={ht.get('accuracy', '?'):.4f} F1={ht.get('f1', '?'):.4f} "
                      f"P={ht.get('precision', '?'):.4f} R={ht.get('recall', '?'):.4f}")
            print(f"    Synthetic:    Acc={st.get('accuracy', '?'):.4f} F1={st.get('f1', '?'):.4f} "
                  f"P={st.get('precision', '?'):.4f} R={st.get('recall', '?'):.4f}")
            print(f"    Score margin: {st.get('score_margin', '?'):.4f} "
                  f"(phish={st.get('avg_score_phishing', '?'):.3f} legit={st.get('avg_score_legit', '?'):.3f})")
            print(f"    Overlap ratio: {sd.get('overlap_ratio', '?'):.4f}")
            print(f"    Overfitting: {of.get('verdict', '?')} "
                  f"(gap_acc={of.get('gap_accuracy', '?'):.4f} gap_f1={of.get('gap_f1', '?'):.4f})")
            print(f"    Stability: {result.get('synthetic_stability', {}).get('verdict', '?')}")
    
    # Save full results
    with open(RESULTS_FILE, "w", encoding="utf-8") as f:
        json.dump(all_results, f, ensure_ascii=False, indent=2)
    print(f"\n\nFull results saved to: {RESULTS_FILE}")
    
    # ── Final verdict ──
    print(f"\n{'='*60}")
    print(f"  FINAL VERDICT")
    print(f"{'='*60}")
    
    all_ok = True
    for key, result in all_results.items():
        if "onnx" in key:
            continue
        of = result.get("overfitting_check", {})
        sd = result.get("synthetic_score_distribution", result.get("score_distribution", {}))
        st = result.get("synthetic_test", {})
        ht = result.get("holdout_test", {})
        ta = result.get("test_A_domain_only", {})
        tb = result.get("test_B_url_string", {})
        
        problems = []
        # Primary metric: real holdout data (URL: PhiUSIIL, Chinese: synthetic)
        primary = ht if ht else st
        if primary.get("f1", 1) < 0.85:
            problems.append(f"low F1={primary['f1']:.3f}")
            all_ok = False
        if primary.get("score_margin", 1) < 0.3:
            problems.append(f"narrow margin={primary['score_margin']:.3f}")
            all_ok = False
        if of.get("verdict", "").startswith("OVERFITTING"):
            problems.append(f"overfitting ({of['verdict']})")
            all_ok = False
        if sd.get("overlap_ratio", 1) > 0.2 and not ht:
            problems.append(f"score overlap {sd['overlap_ratio']:.2%}")
            all_ok = False
        # Test A/B for URL mode
        if ta.get("f1", 1) < 0.85:
            problems.append(f"Test A (domain-only) F1={ta['f1']:.3f}")
            all_ok = False
        if ta.get("fpr", 1) > 0.02:
            problems.append(f"Test A FPR={ta['fpr']:.4f}")
            all_ok = False
        if tb.get("f1", 1) < 0.85:
            problems.append(f"Test B (URL string) F1={tb['f1']:.3f}")
            all_ok = False
        
        if problems:
            print(f"  [{key}] Issues: {', '.join(problems)}")
        else:
            print(f"  [{key}] Good fit")
    
    if all_ok:
        print(f"\n  All models pass fitting quality check!")
    else:
        print(f"\n  Some models have issues - review above.")
