"""按关键词列出中国成分表里的准确条目名，供写「复算菜肴」配比时引用。"""
import json
import glob
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "data", "ext", "china-food-composition-data-main",
    "json_data_v3_20260825_qwen38max_kimi_k3_fixed",
)

names = []
for path in glob.glob(os.path.join(DIR, "*.json")):
    try:
        rows = json.load(open(path, encoding="utf-8"))
    except Exception:
        continue
    for row in rows:
        if isinstance(row, dict) and row.get("foodName"):
            names.append(row["foodName"])

for keyword in sys.argv[1:]:
    hits = sorted({n for n in names if keyword in n})
    print("=== %s (%d)" % (keyword, len(hits)))
    for name in hits[:14]:
        print("    ", name)
    print()
