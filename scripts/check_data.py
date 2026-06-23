import pandas as pd

print("=== combined_dataset.csv ===")
try:
    df = pd.read_csv('raw_data/combined_dataset.csv', nrows=3)
    print('Columns:', list(df.columns))
    for _, row in df.iterrows():
        txt = str(row.get('text', ''))[:60]
        lbl = row.get('label', '?')
        print(f'  label={lbl} | {txt}')
except Exception as e:
    print(f'Error: {e}')

print("\n=== ChiFraud_train.csv ===")
try:
    cf = pd.read_csv('raw_data/chifraud/dataset/ChiFraud_train.csv', nrows=3)
    print('Columns:', list(cf.columns))
    for _, row in cf.iterrows():
        txt = str(row.get('text', ''))[:60]
        lbl = row.get('label', '?')
        print(f'  label={lbl} | {txt}')
except Exception as e:
    print(f'Error: {e}')
