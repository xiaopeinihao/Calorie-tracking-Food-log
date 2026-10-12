"""打印设备上 Room 库的食物条目数与分类分布（验证种子升级是否生效）。"""
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8")
conn = sqlite3.connect(sys.argv[1] if sys.argv[1:] else r"tools\data\device_calorie.db")
print("food_items:", conn.execute("select count(*) from food_items").fetchone()[0])
print("分类:", dict(conn.execute("select category,count(*) from food_items group by category").fetchall()))
print("菜肴(复算):", conn.execute("select count(*) from food_items where id like 'dish:%'").fetchone()[0])
print("含英文名的条目:",
      conn.execute("select count(*) from food_items where name not glob '*[一-龥]*'").fetchone()[0])
