import pandas as pd
df = pd.read_csv('raw_data/combined_dataset.csv')
print('Total rows:', len(df))
print('Columns:', list(df.columns))
print('Label distribution:', df['label'].value_counts().to_dict())
print('Source distribution:', df['source'].value_counts().to_dict())
print()
chinese = df[df['source'].str.contains('ChiFraud', na=False)]
print('ChiFraud samples:', len(chinese))
for _, row in chinese.head(5).iterrows():
    print('  label=%s URL=%s Title=%s | text=%s' % (
        row['label'], str(row.get('URL',''))[:30], str(row.get('Title',''))[:30],
        str(row['text'])[:80]))
print()
phi = df[~df['source'].str.contains('ChiFraud', na=False)]
print('PhiUSIIL samples:', len(phi))
for _, row in phi.head(2).iterrows():
    print('  label=%s URL=%s Title=%s | text=%s' % (
        row['label'], str(row.get('URL',''))[:30], str(row.get('Title',''))[:30],
        str(row['text'])[:80]))
