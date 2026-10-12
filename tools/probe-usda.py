"""按关键词查 USDA 条目名称，用于修人工命名表里匹配不上的规则。

用法：python tools/probe-usda.py "cream, heavy" "spaghetti" ...
"""
import csv
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
csv.field_size_limit(10 ** 7)

BASE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "data",
    "usda",
    "FoodData_Central_sr_legacy_food_csv_2018-04",
)


def descriptions():
    with open(os.path.join(BASE, "food.csv"), encoding="utf-8-sig", newline="") as fh:
        return [row["description"] for row in csv.DictReader(fh)]


def main():
    names = descriptions()
    for term in sys.argv[1:]:
        low = term.lower()
        hits = [n for n in names if low in n.lower()]
        hits.sort(key=len)
        print("=== %s  (%d hits)" % (term, len(hits)))
        for n in hits[:10]:
            print("    ", n)
        print()


if __name__ == "__main__":
    main()
