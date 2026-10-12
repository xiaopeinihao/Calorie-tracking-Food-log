# -*- coding: utf-8 -*-
"""往设备数据库里插一条「升级前就存在」的记录，用于验证迁移不丢数据。"""
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8")
db = sys.argv[1]

conn = sqlite3.connect(db)
cur = conn.cursor()

cur.execute(
    "INSERT INTO meal_entries (date, created_at, meal_type, source, photo_path) "
    "VALUES ('2026-10-08', 1759900000000, '午餐', '手动', NULL)"
)
meal_id = cur.lastrowid

cur.execute(
    "INSERT INTO entry_items (meal_entry_id, food_id, food_name, grams, kcal, carbs, protein, fat, user_edited) "
    "VALUES (?, 'cfct:012401x', '米饭（蒸，代表值）', 200, 232.0, 51.8, 5.2, 0.6, 0)",
    (meal_id,),
)
conn.commit()

print("inserted meal_id =", meal_id)
print("meals =", cur.execute("select count(*) from meal_entries").fetchone()[0])
print("items =", cur.execute("select count(*) from entry_items").fetchone()[0])
print("foods =", cur.execute("select count(*) from food_items").fetchone()[0])
conn.close()
