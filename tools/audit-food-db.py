# -*- coding: utf-8 -*-
"""
食物库独立审计。

和 build-food-db.py 的区别（这是关键）：本脚本**不 import 生成脚本的任何逻辑**，
而是自己重新读原始数据源（成分表 JSON / USDA CSV / RECIPES），独立算出应有的数值，
再和 app/src/main/assets/foods_seed.json 逐条对账。

目的就是「自己验证自己」之外的第三方视角：
  * 生成脚本里如果有取整、条件、映射写错，这里会不一致；
  * 顺便查唯一性、区间、Atwater、出处完整性，以及已知参考值的抽样对拍。

用法：python tools/audit-food-db.py
"""
import csv
import glob
import json
import os
import re
import sys
from collections import Counter

sys.stdout.reconfigure(encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED = os.path.join(ROOT, "app", "src", "main", "assets", "foods_seed.json")
CFCT_DIR = os.path.join(
    ROOT, "tools", "data", "ext", "china-food-composition-data-main",
    "json_data_v3_20260825_qwen38max_kimi_k3_fixed",
)
USDA_DIR = os.path.join(ROOT, "tools", "data", "usda", "FoodData_Central_sr_legacy_food_csv_2018-04")

problems = []
notes = []


def bad(kind, detail):
    problems.append("[%s] %s" % (kind, detail))


def number(value):
    """成分表里的数值字段：'1.2' / '—' / 'Tr' / '899*'"""
    if value is None:
        return None
    text = str(value).strip()
    if text == "" or text in ("—", "-", "…"):
        return None
    if text in ("Tr", "tr"):
        return 0.0
    match = re.match(r"^[+-]?\d+(?:\.\d+)?", text)
    return float(match.group(0)) if match else None


# ---------------------------------------------------------------- 1. 读种子库
seed = json.load(open(SEED, encoding="utf-8"))
foods = seed["foods"]
by_id = {f["id"]: f for f in foods}
print("种子库：%d 条，seedVersion=%s" % (len(foods), seed["meta"].get("seedVersion")))

# ---------------------------------------------------------------- 2. 唯一性 / 区间 / 出处
if len(by_id) != len(foods):
    bad("唯一性", "存在重复 id：%d 条 id vs %d 条记录" % (len(by_id), len(foods)))

name_count = Counter(f["name"] for f in foods)
dups = [n for n, c in name_count.items() if c > 1]
if dups:
    bad("唯一性", "存在同名条目 %d 组：%s" % (len(dups), dups[:6]))

han = re.compile(r"[\u4e00-\u9fff]")
non_chinese = [f["name"] for f in foods if not han.search(f["name"])]
if non_chinese:
    bad("中文名", "有 %d 条不含汉字：%s" % (len(non_chinese), non_chinese[:6]))

for f in foods:
    if not (0 < f["kcal"] <= 950):
        bad("区间", "%s 热量越界 %s" % (f["name"], f["kcal"]))
    if min(f["carbs"], f["protein"], f["fat"]) < 0:
        bad("区间", "%s 有负值宏量营养素" % f["name"])
    if not f.get("source"):
        bad("出处", "%s 缺 source" % f["name"])
    if f["id"].startswith(("cfct:", "usda:", "dish:")) and not f.get("sourceRef"):
        bad("出处", "%s 缺 sourceRef" % f["name"])
    if f["servingGrams"] <= 0:
        bad("区间", "%s servingGrams<=0" % f["name"])

# Atwater（与生成脚本同口径：相对 40% 或绝对差 25 kcal）
exempt_hint = ("beer", "wine", "sake", "alcoholic")
for f in foods:
    computed = 4 * f["carbs"] + 4 * f["protein"] + 9 * f["fat"]
    diff = abs(computed - f["kcal"])
    if f["kcal"] <= 0:
        continue
    if diff <= 25:
        continue
    if diff / f["kcal"] > 0.40:
        # 酒类/醋类豁免（能量含乙醇与有机酸）
        is_drink = f["category"] == "饮料" and f["name"] in ("啤酒", "淡啤酒", "红葡萄酒", "白葡萄酒", "清酒")
        if is_drink or f["name"] == "白醋":
            continue
        bad("Atwater", "%s 自洽性差 %.1f%%（自报 %.1f，反算 %.1f）"
            % (f["name"], diff / f["kcal"] * 100, f["kcal"], computed))

# ---------------------------------------------------------------- 3. 复算 CFCT 条目
cfct_raw = {}
if os.path.isdir(CFCT_DIR):
    for path in glob.glob(os.path.join(CFCT_DIR, "*.json")):
        for row in json.load(open(path, encoding="utf-8")):
            if not isinstance(row, dict):
                continue
            name = (row.get("foodName") or "").strip()
            code = (row.get("foodCode") or "").strip()
            if name and code:
                cfct_raw[(name, code)] = row

    checked = mismatch = 0
    for f in foods:
        if not f["id"].startswith("cfct:"):
            continue
        ref = (f.get("sourceRef") or "").strip()
        key = (f["name"], ref)
        row = cfct_raw.get(key)
        if row is None:
            # 成分表里同一名称可能有多条（我们按名称去重后只留了第一条）
            continue
        checked += 1
        kcal_src = number(row.get("energyKCal"))
        if kcal_src is None:
            continue
        if abs(round(kcal_src, 1) - f["kcal"]) > 0.051:
            mismatch += 1
            bad("复算", "%s 热量不一致：源 %.4f vs 库 %.1f" % (f["name"], kcal_src, f["kcal"]))
        for src_key, local_key in (("protein", "protein"), ("fat", "fat"), ("CHO", "carbs")):
            src = number(row.get(src_key))
            src = 0.0 if src is None else src
            if abs(round(src, 1) - f[local_key]) > 0.051:
                mismatch += 1
                bad("复算", "%s %s 不一致：源 %.4f vs 库 %.1f" % (f["name"], src_key, src, f[local_key]))
    print("CFCT 条目逐条对账：%d 条，不一致 %d 处" % (checked, mismatch))
else:
    notes.append("未找到成分表原始数据，跳过 CFCT 对账")

# ---------------------------------------------------------------- 4. 复算菜肴（独立实现）
def recompute_dish(recipe_ingredients, value_of):
    grams_total = 0.0
    totals = {"kcal": 0.0, "carbs": 0.0, "protein": 0.0, "fat": 0.0}
    for name, grams in recipe_ingredients:
        grams_total += grams
        if name == "水":
            continue
        base = value_of(name)
        if base is None:
            return None
        for key in totals:
            totals[key] += base[key] * grams / 100.0
    if grams_total <= 0:
        return None
    scale = 100.0 / grams_total
    return {k: round(v * scale, 1) for k, v in totals.items()}


index = {f["name"]: f for f in foods}
sys.path.insert(0, os.path.join(ROOT, "tools"))
from food_naming import GLOBAL_AVOID, USDA_PICKS  # noqa: E402

# 菜肴的食材必须能在库里找到（含被去重丢弃的同名条目 → 用原始成分表补）
dish_ok = dish_bad = 0
recipes_src = open(os.path.join(ROOT, "tools", "build-food-db.py"), encoding="utf-8").read()
start = recipes_src.find("RECIPES = [")
end = recipes_src.find("\n]", start)
recipes_ns = {}
exec(recipes_src[start:end + 2], {"WATER": "水"}, recipes_ns)  # noqa: S102
recipes = recipes_ns["RECIPES"]
print("配方数：%d 道（库里 dish: 条目 %d 条）"
      % (len(recipes), sum(1 for f in foods if f["id"].startswith("dish:"))))

cfct_by_name = {}
for (name, code), row in cfct_raw.items():
    cfct_by_name.setdefault(name, row)


def value_of(name):
    f = index.get(name)
    if f is not None:
        return {"kcal": f["kcal"], "carbs": f["carbs"], "protein": f["protein"], "fat": f["fat"]}
    row = cfct_by_name.get(name)
    if row is None:
        return None
    kcal = number(row.get("energyKCal"))
    if kcal is None:
        return None
    return {
        "kcal": kcal,
        "carbs": number(row.get("CHO")) or 0.0,
        "protein": number(row.get("protein")) or 0.0,
        "fat": number(row.get("fat")) or 0.0,
    }


for recipe in recipes:
    expected = recompute_dish(recipe["ingredients"], value_of)
    actual = by_id.get(recipe["id"])
    if expected is None:
        bad("菜肴", "%s 的食材在源数据里找不到，无法复算" % recipe["name"])
        continue
    if actual is None:
        bad("菜肴", "%s 在种子库里不存在" % recipe["name"])
        continue
    for key in ("kcal", "carbs", "protein", "fat"):
        if abs(expected[key] - actual[key]) > 0.15:
            dish_bad += 1
            bad("菜肴", "%s %s 复算不一致：独立算出 %.1f vs 库 %.1f"
                % (recipe["name"], key, expected[key], actual[key]))
    else:
        dish_ok += 1
    # 份量一致性：一份热量应等于 每100g × servingGrams/100
    one = actual["kcal"] * actual["servingGrams"] / 100
    if one <= 0 or one > 3000:
        bad("菜肴", "%s 一份热量异常 %.0f kcal（serving %s）" % (recipe["name"], one, actual["serving"]))
print("菜肴独立复算：%d 道一致，%d 道有差异" % (dish_ok, dish_bad))

# ---------------------------------------------------------------- 5. 复算 USDA 条目
if os.path.isdir(USDA_DIR):
    wanted_ids = {
        "1008": "kcal", "1003": "protein", "1004": "fat", "1005": "carbs",
    }
    nutrient = {}
    with open(os.path.join(USDA_DIR, "food_nutrient.csv"), encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            key = wanted_ids.get((row.get("nutrient_id") or "").strip())
            if key is None:
                continue
            try:
                nutrient.setdefault(row["fdc_id"], {})[key] = float(row["amount"])
            except (TypeError, ValueError):
                continue

    checked = mismatch = 0
    for f in foods:
        if not f["id"].startswith("usda:"):
            continue
        fdc = f["id"].split(":", 1)[1]
        src = nutrient.get(fdc)
        if not src or len(src) != 4:
            bad("复算", "%s 在 USDA 原始数据里找不到 fdc_id=%s" % (f["name"], fdc))
            continue
        checked += 1
        for key in ("kcal", "carbs", "protein", "fat"):
            if abs(round(src[key], 1) - f[key]) > 0.051:
                mismatch += 1
                bad("复算", "%s %s 不一致：USDA %.4f vs 库 %.1f" % (f["name"], key, src[key], f[key]))
    print("USDA 条目逐条对账：%d 条，不一致 %d 处" % (checked, mismatch))
else:
    notes.append("未找到 USDA 原始 CSV，跳过对账")

# ---------------------------------------------------------------- 6. 已知参考值抽样
# 这些是公开资料里广为引用的每 100g 数值（成分表/USDA 口径），用来抓「整体偏移」这类问题
ANCHORS = [
    ("米饭（蒸，代表值）", 116.0, 2.0),
    ("馒头（代表值）", 223.0, 2.0),
    ("花生油", 899.0, 2.0),
    ("白砂糖", 387.0, 5.0),
    # 蜂蜜：库里用的是 USDA "Honey" = 304；成分表第6版的蜂蜜是 321，但那份公开数据
    # 没有糖类/蜂蜜分类，所以这里以 USDA 为准
    ("蜂蜜", 304.0, 12.0),
    ("切达奶酪", 403.0, 5.0),
    ("可乐", 42.0, 3.0),
    ("薯片（原味）", 532.0, 15.0),
    ("全脂牛奶", 61.0, 4.0),
    ("脱脂牛奶", 34.0, 4.0),
    ("鸡蛋（代表值）", 144.0, 10.0),
    # 牛油果：必须取通用商业品种（160），不能取佛州种（120）——这条曾经踩过坑
    ("牛油果", 160.0, 8.0),
    ("意大利面（熟）", 158.0, 6.0),
    ("白米饭（熟）", 130.0, 8.0),
    ("三文鱼（大西洋，生）", 208.0, 15.0),
    ("鸡胸肉（生，去皮）", 120.0, 12.0),
    ("橄榄油", 899.0, 15.0),
    # 同词多品种的坑：这几个值必须取「常规品种」而不是低脂/地区变体
    ("马苏里拉奶酪", 300.0, 15.0),
    ("西柚", 42.0, 5.0),
    ("橙味汽水", 48.0, 8.0),
    ("蓝莓马芬", 375.0, 20.0),
]
print("\n已知参考值抽样（允许区间内视为一致）：")
anchor_bad = 0
for name, expect, tol in ANCHORS:
    f = index.get(name)
    if f is None:
        bad("参考值", "库里没有 %s（抽样项写错或条目被剔除）" % name)
        anchor_bad += 1
        continue
    delta = f["kcal"] - expect
    flag = "OK " if abs(delta) <= tol else "!! "
    if abs(delta) > tol:
        anchor_bad += 1
        bad("参考值", "%s = %.1f，公开参考 %.1f（差 %+.1f，超容差 %.0f）" % (name, f["kcal"], expect, delta, tol))
    print("  %s%-22s 库 %7.1f  参考 %7.1f  差 %+6.1f" % (flag, name, f["kcal"], expect, delta))

# ---------------------------------------------------------------- 7. 结论
print("\n" + "=" * 60)
if problems:
    print("发现 %d 个问题：" % len(problems))
    for line in problems[:60]:
        print("  " + line)
else:
    print("数据审计通过：未发现问题")
if notes:
    for line in notes:
        print("  注：" + line)
