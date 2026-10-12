# -*- coding: utf-8 -*-
"""核对设备上真实落库的食物数据（从模拟器拉下来的 Room 数据库）。"""
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8")
db = sys.argv[1] if len(sys.argv) > 1 else r"e:\AI开发\tools\data\device_calorie.db"

conn = sqlite3.connect(db)
cur = conn.cursor()

print("tables:", [r[0] for r in cur.execute(
    "select name from sqlite_master where type='table' order by name")])
print("food_items:", cur.execute("select count(*) from food_items").fetchone()[0])
print("categories:", cur.execute("select count(distinct category) from food_items").fetchone()[0])
print("by category:", dict(cur.execute(
    "select category, count(*) from food_items group by category order by count(*) desc")))
print("goals:", cur.execute(
    "select date, target_kcal, target_carbs, target_protein, target_fat from daily_goals").fetchall())

print("--- 关键条目（每 100g） ---")
for row in cur.execute(
    "select name, kcal_per_100g, carbs_per_100g, protein_per_100g, fat_per_100g, "
    "common_serving, common_serving_grams, source_ref, alias from food_items "
    "where name in ('米饭（蒸，代表值）','番茄炒蛋','炒青菜','花生油','鸡蛋（代表值）','番茄［西红柿］')"
):
    print(row)

print("--- 搜索别名验证：白米饭 / 西红柿 / 青菜 ---")
for term in ("白米饭", "西红柿", "青菜"):
    rows = cur.execute(
        "select name, kcal_per_100g from food_items where name like ? or ifnull(alias,'') like ? limit 3",
        (f"%{term}%", f"%{term}%"),
    ).fetchall()
    print(term, "->", rows)

print("--- 数据完整性抽查：热量与 Atwater 一致性 ---")
bad = cur.execute(
    "select count(*) from food_items where kcal_per_100g <= 0 or kcal_per_100g > 950"
).fetchone()[0]
print("out-of-range rows:", bad)

# 口径与管线一致：相对偏差 > 40% 且**绝对偏差 > 20 kcal/100g** 才算不自洽。
# 只卡相对值会在「咖啡 1 kcal/100g」这类近零食物上出假阳性 ——
# 原始值本来是自洽的，是落库时保留 1 位小数才把相对偏差放大的（绝对误差不到 1 kcal）。
inconsistent = 0
flagged = []
for name, kcal, c, p, f in cur.execute(
    "select name, kcal_per_100g, carbs_per_100g, protein_per_100g, fat_per_100g from food_items"
):
    computed = 4 * c + 4 * p + 9 * f
    if kcal <= 0:
        continue
    diff = abs(computed - kcal)
    if diff / kcal > 0.40 and diff > 20:
        inconsistent += 1
        if len(flagged) < 5:
            flagged.append((name, kcal, round(computed, 1), round(diff, 1)))
print("atwater-inconsistent rows (rel>40% and abs>20kcal):", inconsistent)
for row in flagged:
    print("   ", row)

print("--- 记录表 ---")
print("meal_entries:", cur.execute("select count(*) from meal_entries").fetchone()[0])
print("entry_items:", cur.execute("select count(*) from entry_items").fetchone()[0])
for row in cur.execute(
    "select m.date, m.meal_type, m.source, i.food_name, i.grams, i.kcal, i.food_id "
    "from meal_entries m join entry_items i on i.meal_entry_id = m.id order by i.id desc limit 10"
):
    print(row)

conn.close()
