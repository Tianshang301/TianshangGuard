"""Sample 100 records from each training dataset for quality inspection"""
import csv, os, re, sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'raw_data')

def sample_csv(path, n=100, label_col='label'):
    rows = []
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        reader = csv.DictReader(f)
        for r in reader:
            rows.append(r)
    total = len(rows)
    if total < n:
        sampled = rows
    else:
        random.seed(42)
        sampled = random.sample(rows, n)
    return total, sampled

import random
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

def inspect_url_dataset():
    print('=' * 70)
    print('PhiUSIIL: clean_dataset.csv — 100 条抽样')
    print('=' * 70)
    path = os.path.join(BASE, 'clean_dataset.csv')
    total, sampled = sample_csv(path, 100)
    print(f'总数据量: {total}')

    # Full label count
    all_rows = []
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        reader = csv.DictReader(f)
        for r in reader:
            all_rows.append(r)
    labels = {}
    for r in all_rows:
        lab = r.get('label', '?')
        labels[lab] = labels.get(lab, 0) + 1
    print(f'全体标签分布: {labels}')
    print(f'列名: {list(sampled[0].keys()) if sampled else "EMPTY"}')
    print()

    issues = []
    www_count = 0
    bare_domain = 0
    placeholder = 0
    for i, r in enumerate(sampled):
        text = r.get('text', '')
        label = r.get('label', '?')
        url = r.get('URL', '')
        domain = r.get('Domain', '')

        if 'www.' in url.lower():
            www_count += 1
        if url and url.count('/') <= 2:
            bare_domain += 1
        if re.search(r'(PHONE|DIGIT|CELLPHONE|NAME|NAMEDIGIT|PLACE|XXX|TEMPLATE|MASK|REDACTED)', text + url, re.I):
            placeholder += 1
            issues.append(f'  #{i} PLACEHOLDER [{label}] {text[:80]}')

        if i < 10:
            print(f'  #{i:3d} [{label}] url={url[:60]:60s} | text={text[:60]}')

    print()
    print(f'--- 100 samples stats ---')
    print(f'With www.:  {www_count}')
    print(f'Bare domain: {bare_domain}')
    print(f'Placeholder: {placeholder}')
    if issues:
        print('Placeholder issues:')
        for iss in issues[:15]:
            print(iss)
    print()

def inspect_sms_dataset():
    print('=' * 70)
    print('Chinese SMS: chinese_sms_training.csv — 100 条抽样')
    print('=' * 70)
    path = os.path.join(BASE, 'chinese_sms_training.csv')
    total, sampled = sample_csv(path, 100)
    print(f'总数据量: {total}')

    all_rows = []
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        reader = csv.DictReader(f)
        for r in reader:
            all_rows.append(r)
    labels = {}
    sources = {}
    for r in all_rows:
        lab = r.get('label', '?')
        labels[lab] = labels.get(lab, 0) + 1
        src = r.get('source', '?')
        sources[src] = sources.get(src, 0) + 1
    print(f'全体标签分布: {labels}')
    print(f'数据来源: {sources}')

    fbs_remnant = 0
    encode_err = 0
    for i, r in enumerate(sampled):
        text = r.get('text', '')
        label = r.get('label', '?')
        source = r.get('source', '?')
        if '\ufffd' in text:
            encode_err += 1
        for pat in ['平PLACE', 'NAME邀请', 'DIGITCELLPHONE', 'NAMEDIGIT', 'PLACE', 'TEMPLATE']:
            if pat in text:
                fbs_remnant += 1
                print(f'  FBS REMNANT #{i} [{label}] [{source}] {text[:80]}')

        if i < 10:
            print(f'  #{i:3d} [{label:>10}] [{source:>15}] {text[:70]}')

    print(f'\n--- 100 samples stats ---')
    print(f'FBS remnants: {fbs_remnant}')
    print(f'Encoding errors: {encode_err}')
    print()

    # Show last 5 samples
    print('Last 5 samples:')
    for r in sampled[-5:]:
        print(f'  [{r.get("label","?"):>10}] [{r.get("source","?"):>15}] {r.get("text","")[:70]}')
    print()

def inspect_chifraud():
    print('=' * 70)
    print('ChiFraud: chinese_real_clean.csv — 100 条抽样')
    print('=' * 70)
    path = os.path.join(BASE, 'chinese_real_clean.csv')
    total, sampled = sample_csv(path, 100)
    print(f'总数据量: {total}')

    all_rows = []
    with open(path, 'r', encoding='utf-8', errors='replace') as f:
        reader = csv.DictReader(f)
        for r in reader:
            all_rows.append(r)
    labels = {}
    for r in all_rows:
        lab = r.get('label', '?')
        labels[lab] = labels.get(lab, 0) + 1
    print(f'全体标签分布: {labels}')

    lengths = []
    url_extracted = 0
    for r in sampled:
        text = r.get('text', '')
        lengths.append(len(text))
        if re.search(r'https?://', text):
            url_extracted += 1

    print(f'文本长度: min={min(lengths)}, max={max(lengths)}, avg={sum(lengths)/len(lengths):.0f}')
    print(f'包含URL: {url_extracted}')

    print('Sample texts:')
    for i, r in enumerate(sampled[:10]):
        text = r.get('text', '')
        label = r.get('label', '?')
        print(f'  #{i:3d} [{label}] [{len(text):4d}ch] {text[:70]}')
    print('Last 5:')
    for r in sampled[-5:]:
        label = r.get('label', '?')
        text = r.get('text', '')
        print(f'  [{label}] [{len(text):4d}ch] {text[:70]}')

    # Check for duplicate texts with label 0 samples vs label 4,6,8
    print()
    print('Checking label=0 texts for phishing content...')
    suspicious_legit = 0
    phishing_keywords = ['转账', '验证', '安全账户', '涉嫌', '冻结', '退款', '链接', '中奖', '积分']
    for r in all_rows:
        if r.get('label') == '0':
            text = r.get('text', '')
            for kw in phishing_keywords:
                if kw in text:
                    suspicious_legit += 1
                    break
    print(f'Label=0 with phishing keywords: {suspicious_legit}')

if __name__ == '__main__':
    inspect_url_dataset()
    inspect_sms_dataset()
    inspect_chifraud()
