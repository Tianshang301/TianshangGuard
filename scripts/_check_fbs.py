import os, glob

fbs_files = glob.glob('scripts/raw_data/sms_spam/fbs_sms/*')
for f in sorted(fbs_files):
    with open(f, 'r', encoding='utf-8', errors='replace') as fh:
        lines = fh.readlines()
    name = os.path.basename(f)
    sample = repr(lines[0][:100]) if lines else '(empty)'
    print(f'{name:40s} {len(lines):>5d} lines  sample: {sample}')
