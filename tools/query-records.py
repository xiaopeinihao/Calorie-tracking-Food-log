# -*- coding: utf-8 -*-
"""打印设备库里的记录（默认今天），用于核对「连点是否重复写入」「编辑是否真的改了餐次」。

用法: python tools/query-records.py [日期 2026-10-08]
"""
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8")

DB = r"tools\data\device_calorie.db"
date = sys.argv[1] if sys.argv[1:] else None

conn = sqlite3.connect(DB)
where = "where m.date = ?" if date else ""
params = (date,) if date else ()
rows = conn.execute(
    "select m.id, m.date, m.meal_type, m.source, i.food_name, i.grams, i.kcal, "
    "ifnull(i.source_label, '-') from meal_entries m "
    "left join entry_items i on i.meal_entry_id = m.id "
    + where
    + " order by m.id",
    params,
).fetchall()

print("记录明细 %d 行：" % len(rows))
for r in rows:
    print("  meal#%s %s %-3s %-4s | %-16s %5sg %7.1f kcal | %s" % r)

print()
print("按餐次计数：", dict(conn.execute(
    "select meal_type, count(*) from meal_entries" + (" where date=?" if date else "") + " group by meal_type",
    params,
).fetchall()))
print("记录总数：", conn.execute("select count(*) from meal_entries").fetchone()[0],
      " 明细总数：", conn.execute("select count(*) from entry_items").fetchone()[0])
# 孤儿明细（本该被级联删除）
orphan = conn.execute(
    "select count(*) from entry_items where meal_entry_id not in (select id from meal_entries)"
).fetchone()[0]
print("孤儿明细：", orphan)
