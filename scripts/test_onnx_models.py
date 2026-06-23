import sys; sys.path.insert(0, '.')
import onnxruntime as ort
import numpy as np
from train_phishing_model import tokenizer, Config

config = Config('url')

test_texts = [
    'https://secure-bank.com/login/verify?token=abc123 [SEP] Secure Bank - Account Verification',
    'http://free-prize-winner.xyz/claim?amount=5000 [SEP] Congratulations! You Won!',
    'http://account-alert.phishing.com/refund?id=999 [SEP] Account Suspended - Verify Now',
    'https://en.wikipedia.org/wiki/Machine_learning [SEP] Machine learning - Wikipedia',
    'https://stackoverflow.com/questions/12345 [SEP] Python list comprehension example',
    'https://github.com/torvalds/linux [SEP] Linux kernel source tree',
    'https://www.alipay.com/ [SEP] 支付宝 - 全球领先的独立第三方支付平台',
]

tokens = tokenizer.encode(test_texts[0], config.max_seq_len).reshape(1, -1)

for label, path in [('FP32', 'output/url/onnx/model_fp32.onnx'), ('INT8', '../app/src/main/assets/model/url_phishing.onnx')]:
    sess = ort.InferenceSession(path)
    in_name = sess.get_inputs()[0].name
    out_name = sess.get_outputs()[0].name
    out = sess.run([out_name], {in_name: tokens})[0]
    print(f'{label}: type={type(out).__name__}')
    if hasattr(out, 'shape'):
        print(f'  shape={out.shape}, values={out}')
    elif hasattr(out, '__len__'):
        print(f'  len={len(out)}, values={out}')
    else:
        print(f'  value={out}')

print("\n--- Full test ---")
for label, path in [('FP32', 'output/url/onnx/model_fp32.onnx')]:
    sess = ort.InferenceSession(path)
    in_name = sess.get_inputs()[0].name
    out_name = sess.get_outputs()[0].name
    print(f'\n=== {label} ===')
    for text in test_texts:
        tok = tokenizer.encode(text, config.max_seq_len).reshape(1, -1)
        out = sess.run([out_name], {in_name: tok})[0]
        if hasattr(out, 'shape') and len(out.shape) > 0:
            score = out[0][0] if out.shape[-1] > 1 else out[0] if out.ndim > 1 else float(out)
        elif hasattr(out, '__len__'):
            score = out[0] if len(out) > 1 else float(out)
        else:
            score = float(out)
        print(f'  Score={score:.6f} | {text[:50]}')
