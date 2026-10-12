# -*- coding: utf-8 -*-
"""
搜索覆盖自测：按用户真正会输入的**中文日常叫法**查一遍，看能不能搜到条目。

比「库里有几条」更能说明问题 —— 库里有一万条，但用户搜「牛油果」搜不到，
那对这个用户来说这条数据等于不存在。
"""
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "assets", "foods_seed.json",
)

KEYWORDS = [
    # 中式主食
    "米饭", "馒头", "面条", "粥", "米粉", "油条", "烧饼", "花卷",
    # 中式菜肴（复算条目）
    "番茄炒蛋", "宫保鸡丁", "红烧肉", "鱼香肉丝", "麻婆豆腐", "地三鲜", "土豆丝", "排骨汤", "炒饭", "炸酱面",
    # 小吃点心
    "饺子", "包子", "馄饨", "锅贴", "煎饼", "汤圆", "粽子", "春卷",
    # 中式街头小吃 / 饮品（2026-10 新增的 10 道复算条目，见 docs/food-data/PROVENANCE.md）
    "肉夹馍", "凉皮", "麻辣烫", "烤冷面", "灌饼", "酸辣粉", "蛋挞", "奶茶", "炸串", "香锅",
    # 基础食材（用户常用叫法 vs 成分表条目名）
    "鸡蛋", "鸡胸肉", "鸡腿", "猪里脊", "五花肉", "牛腩", "牛肉", "虾", "三文鱼", "豆腐", "豆浆",
    "牛奶", "酸奶", "奶酪", "芝士", "黄油", "橄榄油", "花生油", "酱油", "白醋",
    "菠菜", "西兰花", "西红柿", "番茄", "土豆", "红薯", "胡萝卜", "香菇", "金针菇",
    "苹果", "香蕉", "橙子", "牛油果", "蓝莓", "葡萄", "西瓜",
    # 西式 / 加工食品
    "意面", "意大利面", "面包", "燕麦", "汉堡", "披萨", "薯条", "三明治", "炸鸡",
    "薯片", "巧克力", "饼干", "冰淇淋", "蛋糕", "可乐", "啤酒", "红酒", "咖啡", "果汁",
]

data = json.load(open(PATH, encoding="utf-8"))
foods = data["foods"]
print("食物库 %d 条" % len(foods))

missing = []
for term in KEYWORDS:
    # 口径与 App 内一致：名称或别名命中都算（别名只参与搜索、不显示）
    hits = [f for f in foods if term in f["name"] or term in (f.get("alias") or "")]
    if not hits:
        missing.append(term)
        print("  ✗ %-10s 搜不到" % term)
        continue
    hits.sort(key=lambda f: (0 if f["name"].startswith(term) else 1, len(f["name"])))
    sample = "、".join("%s %.0f" % (h["name"][:14], h["kcal"]) for h in hits[:3])
    print("  ✓ %-10s %2d 条  %s" % (term, len(hits), sample))

print()
print("搜不到的词 %d / %d：%s" % (len(missing), len(KEYWORDS), "、".join(missing) if missing else "无"))
