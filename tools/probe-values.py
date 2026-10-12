"""打印指定食物条目的权威数值（每 100g），用于核对菜肴配比与选料。

用法:
  python tools/probe-values.py "猪肉（前臀尖"          # 按名称关键词查
  python tools/probe-values.py --dishes               # 列出全部复算菜肴
"""
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "assets", "foods_seed.json",
)

data = json.load(open(PATH, encoding="utf-8"))
foods = {f["name"]: f for f in data["foods"]}


def show(name, f):
    print("    %-30s %6.1f kcal  蛋白%5.1f 脂肪%5.1f 碳水%5.1f"
          % (name, f["kcal"], f["protein"], f["fat"], f["carbs"]))


if sys.argv[1:2] == ["--dishes"]:
    dishes = [f for f in data["foods"] if f["id"].startswith("dish:")]
    dishes.sort(key=lambda f: (f["category"], f["name"]))
    print("复算菜肴 %d 道（值为每 100g）" % len(dishes))
    print()
    for f in dishes:
        one = f["servingGrams"] * f["kcal"] / 100
        print("  [%s] %-16s %6.1f kcal/100g   一份 %s = %5.0f kcal"
              % (f["category"], f["name"], f["kcal"], f["serving"], one))
        print("        出处: %s" % f["sourceRef"])
else:
    for keyword in sys.argv[1:]:
        hits = [(n, f) for n, f in foods.items() if keyword in n]
        hits.sort(key=lambda kv: kv[1]["kcal"])
        print("=== %s (%d)" % (keyword, len(hits)))
        for name, f in hits[:12]:
            show(name, f)
        print()
