import sqlite3
import re

path = r"C:\Users\caioc\AppData\Roaming\Notion\notion.db"
c = sqlite3.connect(path)
cur = c.cursor()
cols = [r[1] for r in cur.execute("PRAGMA table_info(records)")]
print("records columns:", cols)
needle = re.compile(r"Desvira|desvira|livro|O_DESVIRA", re.I)
found: list[str] = []
blob_cols = ["record_value"] if "record_value" in cols else [x for x in cols if x not in ("id",)]
for col in blob_cols:
    try:
        for row in cur.execute(f'SELECT "{col}" FROM records'):
            blob = row[0]
            if blob is None:
                continue
            if isinstance(blob, bytes):
                s = blob.decode("utf-8", errors="ignore")
            else:
                s = str(blob)
            if needle.search(s):
                found.append(s[:800])
                if len(found) >= 5:
                    break
    except Exception as ex:
        print("skip col", col, ex)
    if len(found) >= 5:
        break
print("matches", len(found))
for i, s in enumerate(found):
    print("---", i + 1, "---")
    print(s[:800])
