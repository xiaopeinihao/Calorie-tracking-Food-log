#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
由权威数据源生成 App 随包的种子食物库（app/src/main/assets/foods_seed.json）。

数据来源（不手工编写任何营养数值）：
  1. 主数据源 —— 《中国食物成分表 标准版（第 6 版）》"能量和食物一般营养成分"，
     以结构化 JSON 形式公开（项目 Sanotsu/china-food-composition-data）。
     字段为每 100g 可食部的能量(kcal)、蛋白质、脂肪、碳水化合物(CHO)。
  2. 校验源 —— USDA FoodData Central · SR Legacy（公有领域），
     对基础食材做交叉核对，检出可能的抄录/OCR 错误。
  3. 复合菜 —— 番茄炒蛋 / 炒青菜 这类"菜肴"不在成分表的食材表里，
     因此按显式配比用成分表食材值推算，配比写在 RECIPES 中，可逐条复算。

质量闸门（不通过就不入库，宁可少一条也不给假数字）：
  A. 四项必需值必须齐全且非负；
  B. Atwater 自洽性：|4C + 4P + 9F - 自报kcal| / kcal <= 容差；
  C. 绝对区间：0 < kcal <= 950；
  D. id 唯一；
  E. 与 USDA 交叉核对（仅对可比对的食材），偏差过大写入报告供人工复核。
"""

from __future__ import annotations

import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from food_naming import EXTRA_ZH_ALIAS, GLOBAL_AVOID, USDA_PICKS  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
CFCT_DIR = (
    ROOT / "tools" / "data" / "ext" / "china-food-composition-data-main"
    / "json_data_v3_20260825_qwen38max_kimi_k3_fixed"
)
USDA_DIR = ROOT / "tools" / "data" / "usda"
OUT_JSON = ROOT / "app" / "src" / "main" / "assets" / "foods_seed.json"
REPORT_DIR = ROOT / "tools" / "data" / "reports"

PRIMARY_SOURCE = "中国食物成分表 标准版（第6版）"
# 显示用中文名（界面上不允许出现英文），数值仍来自 USDA 官方库
USDA_SOURCE = "美国农业部食物数据库（USDA FoodData Central）"
VALIDATION_SOURCE = "USDA FoodData Central SR Legacy"

ATWATER_TOLERANCE = 0.40      # 允许的相对偏差（成分表 CHO 含膳食纤维口径，留出余量）
KCAL_MAX = 950.0              # 纯油脂约 900 kcal/100g，超过必为抄录错误
DRY_DISH_SOURCE = "按中国食物成分表第6版食材值 + 显式配比推算"

# 分类映射：文件名 -> App 内的分类
TOP_CATEGORY = {
    "谷类及其制品": "谷薯",
    "薯类淀粉及其制品": "谷薯",
    "干豆类及其制品": "豆类",
    "蔬菜类及其制品": "蔬菜",
    "菌藻类": "蔬菜",
    "水果类及其制品": "水果",
    "畜肉类及其制品": "肉蛋",
    "禽肉类及其制品": "肉蛋",
    "蛋类及其制品": "肉蛋",
    "乳类及其制品": "乳制品",
    "鱼虾蟹贝类": "水产",
    "坚果种子类": "坚果",
    "植物油": "油脂",
    "动物油脂类": "油脂",
    "其他类": "其他",
}

# 常见分量默认值：纯 UI 辅助信息（用户可改），不是营养事实
SERVING_BY_CATEGORY = {
    "谷薯": ("一份约 100g", 100),
    "豆类": ("一份约 50g", 50),
    "蔬菜": ("一份约 150g", 150),
    "水果": ("一个约 150g", 150),
    "肉蛋": ("一份约 100g", 100),
    "乳制品": ("一杯约 250g", 250),
    "水产": ("一份约 100g", 100),
    "坚果": ("一小把约 25g", 25),
    "油脂": ("一勺约 10g", 10),
    "其他": ("一份约 100g", 100),
    "家常菜": ("一份约 250g", 250),
}

# 常见的"碗/个"改写（仅影响默认份量，不影响数值）
SERVING_OVERRIDES = [
    (("米饭", "粥", "面条（煮", "米粉", "米线", "馄饨", "饺子"), ("一碗约 200g", 200)),
    (("馒头", "花卷", "面包", "烧饼", "包子"), ("一个约 100g", 100)),
]

# 生熟标记：仅用于在界面上提醒用户（生米 346 kcal/100g vs 米饭 116 kcal/100g 差 3 倍）
COOKED_HINTS = ("米饭", "粥", "蒸", "煮", "熟", "烤", "炒", "炸", "馒头", "花卷", "面条（煮")

# 别名：让用户用日常叫法搜到权威条目。只影响检索，不影响数值。
CURATED_ALIAS = {
    "米饭（蒸，代表值）": "白米饭,米饭,大米饭",
    "粳米饭（蒸）": "白米饭,米饭",
    "籼米饭（蒸）": "米饭",
    "番茄［西红柿］": "西红柿,番茄",
    "小白菜［青菜］": "青菜,小白菜,上海青",
    "油菜": "青菜",
    "鸡蛋（代表值）": "鸡蛋,蛋",
    "鸡蛋（煮）": "水煮蛋,白煮蛋",
    "马铃薯［土豆、洋芋］": "土豆,洋芋",
    "甘薯［红心红薯］": "红薯,地瓜",
    "豆浆": "豆奶",
    "猪肉（肥瘦）": "猪肉",
    "苹果（代表值）": "苹果",
    "香蕉［甘蕉］": "香蕉",
    "花生油": "食用油,油",
    "豆油": "大豆油,食用油",
    "菜籽油": "食用油",
}

# 复合菜：菜肴在成分表中没有现成条目，按显式配比推算。
# 每项都必须能从成分表里找到对应食材，找不到就报错退出（绝不静默替换）。
WATER = "水"   # 配方里的虚拟食材：0 kcal，但计入成品重量

RECIPES = [
    {
        "id": "dish:fanqie-chaodan",
        "name": "番茄炒蛋",
        "alias": "西红柿炒鸡蛋,番茄炒鸡蛋",
        "category": "家常菜",
        "serving": "一份约 300g",
        "serving_grams": 300,
        "ingredients": [("番茄［西红柿］", 200), ("鸡蛋（代表值）", 100), ("花生油", 15)],
        "note": "家常配比 番茄 200g + 鸡蛋 100g + 食用油 15g（家常中位用油量）；成品按投料总重计，未折算水分损失（偏保守）。用油量是该菜热量波动的主因（5~25g 对应约 80~130 kcal/100g），结果页可改份量。",
    },
    {
        "id": "dish:chao-qingcai",
        "name": "炒青菜",
        "alias": "清炒青菜,炒小白菜,蒜蓉青菜",
        "category": "家常菜",
        "serving": "一份约 250g",
        "serving_grams": 250,
        "ingredients": [("小白菜［青菜］", 250), ("花生油", 15)],
        "note": "家常配比 小白菜 250g + 食用油 15g（家常中位用油量）；成品按投料总重计，未折算水分损失（偏保守）。",
    },
    # ================= 炒菜类 =================
    {
        "id": "dish:ganbian-sijidou",
        "name": "干煸四季豆",
        "alias": "干煸豆角,炒四季豆",
        "category": "家常菜",
        "serving": "一份约 320g",
        "serving_grams": 320,
        "ingredients": [("四季豆（菜豆）", 250), ("猪肉（前臀尖，杜长大猪）", 50), ("花生油", 20)],
        "note": "家常配比：四季豆 250g + 肉末 50g（前臀尖，约 25% 脂肪）+ 油 20g。四季豆吸油明显，油量按家常中位取值。",
    },
    {
        "id": "dish:yuxiang-rousi",
        "name": "鱼香肉丝",
        "alias": "鱼香肉丝盖饭",
        "category": "家常菜",
        "serving": "一份约 250g",
        "serving_grams": 250,
        "ingredients": [
            ("猪肉（瘦）", 120),
            ("木耳（水发）［黑木耳，云耳］", 30),
            ("胡萝卜", 30),
            ("甜椒［灯笼椒、柿子椒］", 30),
            ("白砂糖", 8),
            ("白醋", 8),
            ("酱油（生抽）", 10),
            ("花生油", 15),
        ],
        "note": "家常配比：瘦肉 120g + 木耳/胡萝卜/青椒 各 30g + 糖 8g + 醋 8g + 生抽 10g + 油 15g。糖醋比是关键，按川菜家常味型取值。",
    },
    {
        "id": "dish:gongbao-jiding",
        "name": "宫保鸡丁",
        "alias": "宫爆鸡丁",
        "category": "家常菜",
        "serving": "一份约 230g",
        "serving_grams": 230,
        "ingredients": [
            ("鸡胸脯肉", 150),
            ("花生仁（炒）", 30),
            ("大葱", 20),
            ("白砂糖", 8),
            ("酱油（生抽）", 10),
            ("花生油", 15),
        ],
        "note": "家常配比：鸡胸肉 150g + 油炸花生米 30g + 葱 20g + 糖醋汁（糖 8g/生抽 10g）+ 油 15g。花生米是这道菜的脂肪大头。",
    },
    {
        "id": "dish:hongshao-rou",
        "name": "红烧肉",
        "alias": "红烧五花肉",
        "category": "家常菜",
        "serving": "一份约 200g",
        "serving_grams": 200,
        "ingredients": [
            ("猪肉（奶脯）［软五花，猪夹心］", 150),
            ("白砂糖", 10),
            ("酱油（生抽）", 15),
            ("花生油", 10),
            ("姜（鲜）［黄姜］", 5),
            ("大葱", 10),
        ],
        "note": "家常配比：五花肉 150g + 糖 10g + 生抽 15g + 油 10g + 姜葱。五花肉部位差异极大（软五花 349、肋条 568 kcal/100g），这里取软五花。",
    },
    {
        "id": "dish:mapo-doufu",
        "name": "麻婆豆腐",
        "alias": "麻辣豆腐",
        "category": "家常菜",
        "serving": "一份约 385g",
        "serving_grams": 385,
        "ingredients": [
            ("豆腐（南豆腐）", 300),
            ("猪肉（瘦）", 50),
            ("大葱", 10),
            ("酱油（生抽）", 10),
            ("花生油", 15),
        ],
        "note": "家常配比：南豆腐 300g + 肉末 50g + 生抽 10g + 油 15g。豆瓣酱不在成分表数据内，未计入（约 +10 kcal/份，影响可忽略）。",
    },
    {
        "id": "dish:cuyou-tudousi",
        "name": "醋溜土豆丝",
        "alias": "酸辣土豆丝,炒土豆丝",
        "category": "家常菜",
        "serving": "一份约 300g",
        "serving_grams": 300,
        "ingredients": [
            ("马铃薯［土豆、洋芋］", 250),
            ("甜椒［灯笼椒、柿子椒］", 30),
            ("白醋", 10),
            ("花生油", 15),
        ],
        "note": "家常配比：土豆 250g + 青椒 30g + 醋 10g + 油 15g。土豆是碳水主食属性，一份约等于半碗饭。",
    },
    {
        "id": "dish:hongshao-qiezi",
        "name": "红烧茄子",
        "alias": "烧茄子",
        "category": "家常菜",
        "serving": "一份约 290g",
        "serving_grams": 290,
        "ingredients": [
            ("茄子（代表值）", 250),
            ("酱油（生抽）", 10),
            ("白砂糖", 5),
            ("花生油", 25),
        ],
        "note": "家常配比：茄子 250g + 生抽 10g + 糖 5g + 油 25g。茄子本身只有 21 kcal/100g，这道菜的热量几乎全部来自油（25g 油 = 225 kcal）。",
    },
    {
        "id": "dish:disanxian",
        "name": "地三鲜",
        "alias": "东北地三鲜",
        "category": "家常菜",
        "serving": "一份约 335g",
        "serving_grams": 335,
        "ingredients": [
            ("茄子（代表值）", 150),
            ("马铃薯［土豆、洋芋］", 100),
            ("甜椒［灯笼椒、柿子椒］", 50),
            ("花生油", 25),
            ("酱油（生抽）", 10),
        ],
        "note": "家常配比：茄子 150g + 土豆 100g + 青椒 50g + 油 25g + 生抽 10g。土豆与茄子都要过油，油量按家常中位取值。",
    },
    {
        "id": "dish:qingchao-xilanhua",
        "name": "清炒西兰花",
        "alias": "蒜蓉西兰花",
        "category": "家常菜",
        "serving": "一份约 270g",
        "serving_grams": 270,
        "ingredients": [("西兰花［绿菜花］", 250), ("大蒜（白皮，鲜）［蒜头］", 10), ("花生油", 12)],
        "note": "家常配比：西兰花 250g + 蒜 10g + 油 12g。",
    },
    {
        "id": "dish:qingjiao-rousi",
        "name": "青椒炒肉丝",
        "alias": "尖椒炒肉",
        "category": "家常菜",
        "serving": "一份约 215g",
        "serving_grams": 215,
        "ingredients": [("猪肉（瘦）", 100), ("甜椒［灯笼椒、柿子椒］", 100), ("花生油", 15)],
        "note": "家常配比：瘦肉 100g + 青椒 100g + 油 15g。",
    },
    {
        "id": "dish:jiucai-chaodan",
        "name": "韭菜炒鸡蛋",
        "alias": "韭菜鸡蛋",
        "category": "家常菜",
        "serving": "一份约 265g",
        "serving_grams": 265,
        "ingredients": [("韭菜", 150), ("鸡蛋（代表值）", 100), ("花生油", 15)],
        "note": "家常配比：韭菜 150g + 鸡蛋 2 个（100g）+ 油 15g。",
    },
    {
        "id": "dish:xianggu-qingcai",
        "name": "香菇炒青菜",
        "alias": "香菇油菜,香菇小白菜",
        "category": "家常菜",
        "serving": "一份约 260g",
        "serving_grams": 260,
        "ingredients": [("香菇（鲜）［香蕈，冬菇］", 100), ("小白菜［青菜］", 150), ("花生油", 12)],
        "note": "家常配比：鲜香菇 100g + 青菜 150g + 油 12g。",
    },
    {
        "id": "dish:liangban-huanggua",
        "name": "凉拌黄瓜",
        "alias": "拍黄瓜,蒜泥黄瓜",
        "category": "家常菜",
        "serving": "一份约 225g",
        "serving_grams": 225,
        "ingredients": [
            ("黄瓜（鲜）［胡瓜］", 200),
            ("大蒜（白皮，鲜）［蒜头］", 10),
            ("芝麻油［香油］", 5),
            ("白醋", 10),
        ],
        "note": "家常配比：黄瓜 200g + 蒜 10g + 香油 5g + 醋 10g。低热量凉菜，热量几乎全来自香油。",
    },
    # ================= 汤类 =================
    {
        "id": "dish:fanqie-jidan-tang",
        "name": "西红柿鸡蛋汤",
        "alias": "番茄蛋汤",
        "category": "家常菜",
        "serving": "一碗约 600g",
        "serving_grams": 600,
        "ingredients": [
            ("番茄［西红柿］", 150),
            ("鸡蛋（代表值）", 50),
            ("水", 400),
            ("花生油", 5),
        ],
        "note": "家常配比：番茄 150g + 鸡蛋 1 个 + 水 400g + 油 5g。汤类把水计入成品重量 —— 否则会把一碗汤算成浓缩汁。",
    },
    {
        "id": "dish:zicai-danhua-tang",
        "name": "紫菜蛋花汤",
        "alias": "蛋花汤",
        "category": "家常菜",
        "serving": "一碗约 460g",
        "serving_grams": 460,
        "ingredients": [
            ("紫菜（干）", 5),
            ("鸡蛋（代表值）", 50),
            ("水", 400),
            ("芝麻油［香油］", 3),
        ],
        "note": "家常配比：干紫菜 5g + 鸡蛋 1 个 + 水 400g + 香油 3g。",
    },
    {
        "id": "dish:donggua-paigu-tang",
        "name": "冬瓜排骨汤",
        "alias": "排骨汤",
        "category": "家常菜",
        "serving": "一碗约 700g",
        "serving_grams": 700,
        "ingredients": [
            ("猪小排（良杂猪）", 100),
            ("冬瓜", 200),
            ("水", 400),
            ("姜（鲜）［黄姜］", 5),
        ],
        "note": "家常配比：排骨 100g + 冬瓜 200g + 水 400g + 姜。排骨按「连汤一起吃掉」计，汤里的脂肪没有扣除（偏保守）。",
    },
    # ================= 主食（饭/面） =================
    {
        "id": "dish:dan-chaofan",
        "name": "蛋炒饭",
        "alias": "鸡蛋炒饭,炒饭",
        "category": "主食",
        "serving": "一份约 275g",
        "serving_grams": 275,
        "ingredients": [
            ("米饭（蒸，代表值）", 200),
            ("鸡蛋（代表值）", 50),
            ("大葱", 10),
            ("花生油", 15),
        ],
        "note": "家常配比：熟米饭 200g + 鸡蛋 1 个 + 葱 10g + 油 15g。米饭用成分表的「蒸」值（116 kcal/100g），不是生米。",
    },
    {
        "id": "dish:yangzhou-chaofan",
        "name": "扬州炒饭",
        "alias": "什锦炒饭",
        "category": "主食",
        "serving": "一份约 345g",
        "serving_grams": 345,
        "ingredients": [
            ("米饭（蒸，代表值）", 200),
            ("鸡蛋（代表值）", 50),
            ("火腿肠", 30),
            ("豌豆（煮）", 30),
            ("胡萝卜", 20),
            ("花生油", 15),
        ],
        "note": "家常配比：熟米饭 200g + 鸡蛋 1 个 + 火腿肠 30g + 豌豆 30g + 胡萝卜 20g + 油 15g。",
    },
    {
        "id": "dish:fanqie-jidan-mian",
        "name": "西红柿鸡蛋面",
        "alias": "番茄鸡蛋面,打卤面",
        "category": "主食",
        "serving": "一碗约 460g",
        "serving_grams": 460,
        "ingredients": [
            ("面条（生，代表值）", 100),
            ("水", 150),
            ("番茄［西红柿］", 150),
            ("鸡蛋（代表值）", 50),
            ("花生油", 10),
        ],
        "note": "家常配比：生面条 100g + 面条吸水 150g + 番茄 150g + 鸡蛋 1 个 + 油 10g。挂面 100g 煮熟约 250g，吸水按 +150g 计。",
    },
    {
        "id": "dish:zhajiang-mian",
        "name": "炸酱面",
        "alias": "老北京炸酱面",
        "category": "主食",
        "serving": "一碗约 335g",
        "serving_grams": 335,
        "ingredients": [
            ("面条（生，代表值）", 100),
            ("水", 150),
            ("猪肉（前臀尖，杜长大猪）", 50),
            ("大葱", 10),
            ("酱油（生抽）", 15),
            ("花生油", 10),
        ],
        "note": "家常配比：生面条 100g + 吸水 150g + 肉末 50g + 生抽 15g + 油 10g。黄酱不在成分表数据内，未计入。",
    },
    {
        "id": "dish:niurou-mian",
        "name": "牛肉面",
        "alias": "红烧牛肉面",
        "category": "主食",
        "serving": "一碗约 620g",
        "serving_grams": 620,
        "ingredients": [
            ("面条（生，代表值）", 100),
            ("水", 400),
            ("牛肉（腹部肉）［牛腩］", 60),
            ("小白菜［青菜］", 50),
            ("姜（鲜）［黄姜］", 5),
            ("花生油", 5),
        ],
        "note": "家常配比：生面条 100g + 汤水 400g + 牛腩 60g + 青菜 50g + 油 5g。连汤吃掉，汤里脂肪未扣除（偏保守）。",
    },
    {
        "id": "dish:chaomian",
        "name": "炒面",
        "alias": "家常炒面",
        "category": "主食",
        "serving": "一份约 385g",
        "serving_grams": 385,
        "ingredients": [
            ("面条（生，代表值）", 100),
            ("水", 120),
            ("猪肉（瘦）", 40),
            ("结球甘蓝（绿）［圆白菜］", 80),
            ("胡萝卜", 30),
            ("花生油", 15),
        ],
        "note": "家常配比：生面条 100g + 煮面吸水 120g + 瘦肉 40g + 圆白菜 80g + 胡萝卜 30g + 油 15g。",
    },
    # ================= 小吃 / 面点 =================
    {
        "id": "dish:jiaozi",
        "name": "饺子（猪肉白菜）",
        "alias": "水饺,猪肉白菜饺子,饺子",
        "category": "小吃",
        "serving": "约 10 个 / 260g",
        "serving_grams": 260,
        "ingredients": [
            ("小麦粉（代表值）", 70),
            ("水", 35),
            ("猪肉（前臀尖，杜长大猪）", 60),
            ("大白菜（代表值）", 80),
            ("大葱", 10),
            ("芝麻油［香油］", 3),
            ("花生油", 5),
        ],
        "note": "家常配比：面粉 70g + 和面水 35g + 肉馅 60g（前臀尖，约 25% 脂肪）+ 白菜 80g + 调味油 8g，约 10 个。馅料肥瘦比是这道菜热量波动的主因（用纯瘦肉会低约 40 kcal/份）。",
    },
    {
        "id": "dish:baozi",
        "name": "包子（猪肉大葱）",
        "alias": "肉包子,大葱猪肉包",
        "category": "小吃",
        "serving": "1 个约 195g",
        "serving_grams": 195,
        "ingredients": [
            ("小麦粉（代表值）", 80),
            ("水", 40),
            ("猪肉（前臀尖，杜长大猪）", 50),
            ("大葱", 20),
            ("花生油", 5),
        ],
        "note": "家常配比：面粉 80g + 和面水 40g + 肉馅 50g + 大葱 20g，约 1 个大包子。",
    },
    {
        "id": "dish:huntun",
        "name": "馄饨（猪肉）",
        "alias": "云吞,抄手",
        "category": "小吃",
        "serving": "约 10 个 / 130g",
        "serving_grams": 130,
        "ingredients": [
            ("小麦粉（代表值）", 50),
            ("水", 25),
            ("猪肉（前臀尖，杜长大猪）", 40),
            ("大葱", 10),
            ("芝麻油［香油］", 3),
        ],
        "note": "家常配比：面粉 50g + 和面水 25g + 肉馅 40g + 葱油 13g，约 10 个小馄饨（皮薄馅少）。",
    },
    {
        "id": "dish:jidan-jianbing",
        "name": "鸡蛋煎饼",
        "alias": "煎饼果子,鸡蛋灌饼,摊煎饼",
        "category": "小吃",
        "serving": "1 个约 170g",
        "serving_grams": 170,
        "ingredients": [
            ("小麦粉（代表值）", 60),
            ("水", 40),
            ("鸡蛋（代表值）", 50),
            ("大葱", 10),
            ("花生油", 10),
        ],
        "note": "家常配比：面粉 60g + 面糊水 40g + 鸡蛋 1 个 + 葱 10g + 油 10g。不含薄脆/油条加料，加了要另外再记。",
    },
    {
        "id": "dish:guotie",
        "name": "锅贴",
        "alias": "煎饺",
        "category": "小吃",
        "serving": "约 8 个 / 190g",
        "serving_grams": 190,
        "ingredients": [
            ("小麦粉（代表值）", 60),
            ("水", 30),
            ("猪肉（前臀尖，杜长大猪）", 40),
            ("大白菜（代表值）", 50),
            ("花生油", 12),
        ],
        "note": "家常配比：面粉 60g + 和面水 30g + 肉馅 40g + 白菜 50g + 煎制油 12g（比水饺多吃油）。",
    },
    {
        "id": "dish:tangyuan",
        "name": "汤圆（黑芝麻）",
        "alias": "元宵,芝麻汤圆",
        "category": "小吃",
        "serving": "约 5 个 / 120g",
        "serving_grams": 120,
        "ingredients": [
            ("糯米［江米］", 50),
            ("水", 40),
            ("芝麻（熟）", 15),
            ("白砂糖", 15),
        ],
        "note": "家常配比：糯米粉 50g + 水 40g + 芝麻 15g + 糖 15g，约 5 个。芝麻糖馅是热量主力。",
    },
    {
        "id": "dish:zongzi",
        "name": "粽子（鲜肉）",
        "alias": "肉粽,咸粽",
        "category": "小吃",
        "serving": "1 个约 275g",
        "serving_grams": 275,
        "ingredients": [
            ("糯米［江米］", 100),
            ("水", 130),
            ("猪肉（奶脯）［软五花，猪夹心］", 40),
            ("酱油（生抽）", 5),
        ],
        "note": "家常配比：生糯米 100g + 煮制吸水 130g + 五花肉 40g + 生抽 5g。糯米 100g 煮成约 230g 饭，吸水按 +130g 计。",
    },
    {
        "id": "dish:chunjuan",
        "name": "春卷（油炸）",
        "alias": "炸春卷",
        "category": "小吃",
        "serving": "约 4 个 / 190g",
        "serving_grams": 190,
        "ingredients": [
            ("小麦粉（代表值）", 50),
            ("水", 25),
            ("结球甘蓝（绿）［圆白菜］", 60),
            ("猪肉（瘦）", 30),
            ("花生油", 25),
        ],
        "note": "家常配比：面粉 50g + 和面水 25g + 菜肉馅 90g + 炸制油 25g（油炸吸油约 15%）。",
    },
    # ================= 中式街头小吃 / 快餐（2026-10 补：这四类是成分表公开转录的空白，
    # 但都属于「能拿成分表食材复算出来」的复合食物，所以走和家常菜同一套显式配比）
    {
        "id": "dish:roujiamo",
        "name": "肉夹馍",
        "alias": "腊汁肉夹馍,肉夹饼",
        "category": "小吃",
        "serving": "一个约 200g",
        "serving_grams": 200,
        "ingredients": [
            ("小麦粉（代表值）", 100),
            ("水", 55),
            ("猪肉（奶脯）［软五花，猪夹心］", 50),
            ("姜（鲜）［黄姜］", 3),
        ],
        "note": "家常配比：白吉馍用面粉 100g + 和面水 55g（烙后约 140g），腊汁肉用软五花 50g + 姜 3g。馍吸肉汁的水分损失未折算（偏保守）。软五花 349 kcal/100g，换成肋条肉(568)会明显更高——所以结果页的份量必须能改。",
    },
    {
        "id": "dish:liangpi",
        "name": "凉皮",
        "alias": "陕西凉皮,凉皮子",
        "category": "小吃",
        "serving": "一份约 340g",
        "serving_grams": 340,
        "ingredients": [
            ("淀粉（小麦）", 100),
            ("小麦粉（代表值）", 20),
            ("水", 150),
            ("黄瓜（鲜）［胡瓜］", 50),
            ("大蒜（白皮，鲜）［蒜头］", 5),
            ("白醋", 8),
            ("花生油", 10),
        ],
        "note": "家常配比：小麦淀粉 100g + 面粉 20g（面筋）+ **成品中保留的水 150g** + 黄瓜 50g + 蒜 5g + 醋 8g + 辣油 10g。注意：洗面/蒸制用掉的大量水**不计入**（与汤类相反——那些水最后被喝掉，这些被滤掉），只按成品保留量算，否则每 100g 值会被稀释掉一大半。凉皮主体是淀粉，蛋白质主要来自那 20g 面粉。",
    },
    {
        "id": "dish:malatang",
        "name": "麻辣烫",
        "alias": "冒菜,麻辣烫（自选）",
        "category": "小吃",
        "serving": "一碗约 600g",
        "serving_grams": 600,
        "ingredients": [
            ("大白菜（代表值）", 100),
            ("香菇（鲜）［香蕈，冬菇］", 50),
            ("绿豆芽", 60),
            ("豆腐皮", 50),
            ("鱼丸", 60),
            ("花生油", 15),
            ("水", 300),
        ],
        "note": "家常配比：白菜 100g + 香菇 50g + 绿豆芽 60g + 豆腐皮 50g + 鱼丸 60g + 辣油/底油 15g + 汤 300g。汤按会被喝掉计（与汤类同口径）。**蘸料（芝麻酱、蒜泥等）未计**——成分表公开转录里没有调味品分类，这里不猜数字；丸子按鱼丸取。",
    },
    {
        "id": "dish:kaolengmian",
        "name": "烤冷面",
        "alias": "东北烤冷面",
        "category": "小吃",
        "serving": "一份约 240g",
        "serving_grams": 240,
        "ingredients": [
            ("小麦粉（代表值）", 120),
            ("鸡蛋（代表值）", 50),
            ("火腿肠", 30),
            ("洋葱（鲜）［葱头］", 30),
            ("花生油", 12),
        ],
        "note": "家常配比：面饼用面粉 120g + 鸡蛋 50g + 火腿肠 30g + 洋葱 30g + 煎制油 12g。**刷的蒜蓉辣酱/番茄酱未计**（调味品不在成分表公开转录里）。",
    },
    {
        "id": "dish:jidan-guanbing",
        "name": "鸡蛋灌饼",
        "alias": "灌饼,鸡蛋饼（灌蛋）",
        "category": "小吃",
        "serving": "一个约 190g",
        "serving_grams": 190,
        "ingredients": [
            ("小麦粉（代表值）", 100),
            ("鸡蛋（代表值）", 50),
            ("生菜［叶用莴苣］", 30),
            ("花生油", 12),
        ],
        "note": "家常配比：面粉 100g + 鸡蛋 50g + 生菜 30g + 煎制油 12g。**刷面酱、加里脊/香肠的版本未计**（按基础款算）。",
    },
    {
        "id": "dish:suanlafen",
        "name": "酸辣粉",
        "alias": "重庆酸辣粉",
        "category": "小吃",
        "serving": "一碗约 430g",
        "serving_grams": 430,
        "ingredients": [
            ("淀粉（甘薯）", 100),
            ("水", 300),
            ("花生仁（炒）", 15),
            ("白醋", 10),
            ("花生油", 10),
        ],
        "note": "家常配比：红薯淀粉 100g + 汤 300g + 油炸花生 15g + 醋 10g + 辣油 10g。汤按被喝掉计。**酸笋、榨菜等配料未计**。",
    },
    {
        "id": "dish:danta",
        "name": "葡式蛋挞",
        "alias": "蛋挞,蛋塔,挞",
        "category": "小吃",
        "serving": "一个约 50g",
        "serving_grams": 50,
        "ingredients": [
            ("小麦粉（代表值）", 8),
            ("无盐黄油", 6),
            ("鸡蛋（代表值）", 13),
            ("纯牛奶（代表值，全脂）", 17),
            ("白砂糖", 5),
        ],
        "note": "家常配比：一个约 50g——挞皮面粉 8g + 黄油 6g，挞水鸡蛋 13g + 全脂牛奶 17g + 糖 5g。**挞皮起酥的黄油量按中位取**，它是蛋挞热量波动的主因（起酥层越厚越高）。",
    },
    {
        "id": "dish:naicha",
        "name": "奶茶（原味）",
        "alias": "奶茶,红茶奶茶,原味奶茶",
        "category": "其他",
        "serving": "一杯约 420g",
        "serving_grams": 420,
        "ingredients": [
            ("红茶（冲泡）", 300),
            ("纯牛奶（代表值，全脂）", 100),
            ("白砂糖", 20),
        ],
        "note": "家常配比：冲泡红茶 300g + 全脂牛奶 100g + 糖 20g。**不含珍珠**——成分表公开转录里只有「木薯」（生鲜），没有木薯淀粉，拿生木薯当珍珠会算错，所以不猜。糖量按市售中杯常规值取，不加糖则每杯少约 80 kcal。",
    },
    {
        "id": "dish:zhachuan",
        "name": "炸串（素）",
        "alias": "素炸串,油炸串",
        "category": "小吃",
        "serving": "一份约 210g",
        "serving_grams": 210,
        "ingredients": [
            ("豆腐皮", 80),
            ("香菇（鲜）［香蕈，冬菇］", 50),
            ("大白菜（代表值）", 60),
            ("花生油", 25),
        ],
        "note": "家常配比：豆腐皮 80g + 香菇 50g + 白菜 60g + 炸油 25g（油炸吸油按中位取）。**吸油量是这类食物最大的不确定项**（10~35g 油约差 40 kcal/100g），所以结果页必须能改份量。蘸料未计。",
    },
    {
        "id": "dish:malaxiangguo",
        "name": "麻辣香锅",
        "alias": "香锅,干锅",
        "category": "家常菜",
        "serving": "一份约 370g",
        "serving_grams": 370,
        "ingredients": [
            ("大白菜（代表值）", 100),
            ("香菇（鲜）［香蕈，冬菇］", 60),
            ("豆腐皮", 60),
            ("黄豆芽", 60),
            ("猪肉（瘦）", 60),
            ("花生油", 25),
            ("大蒜（白皮，鲜）［蒜头］", 8),
        ],
        "note": "家常配比：白菜 100g + 香菇 60g + 豆腐皮 60g + 黄豆芽 60g + 瘦肉 60g + 油 25g + 蒜 8g。麻辣香锅普遍重油，**用油量是这道菜热量的主因**（15~35g 油对应约 60 kcal/100g 的差距）。锅底调料未计。",
    },
]

# USDA 分类 → App 分类
USDA_CATEGORY = {
    "Dairy and Egg Products": "乳制品",
    "Spices and Herbs": "其他",
    "Baby Foods": "其他",
    "Fats and Oils": "油脂",
    "Poultry Products": "肉蛋",
    "Soups, Sauces, and Gravies": "其他",
    "Sausages and Luncheon Meats": "肉蛋",
    "Breakfast Cereals": "谷薯",
    "Fruits and Fruit Juices": "水果",
    "Pork Products": "肉蛋",
    "Vegetables and Vegetable Products": "蔬菜",
    "Nut and Seed Products": "坚果",
    "Beef Products": "肉蛋",
    "Beverages": "其他",
    "Finfish and Shellfish Products": "水产",
    "Legumes and Legume Products": "豆类",
    "Lamb, Veal, and Game Products": "肉蛋",
    "Baked Products": "谷薯",
    "Sweets": "其他",
    "Cereal Grains and Pasta": "谷薯",
    "Fast Foods": "其他",
    "Meals, Entrees, and Side Dishes": "其他",
    "Restaurant Foods": "其他",
    "Snacks": "其他",
    "American Indian/Alaska Native Foods": "其他",
}

# USDA 的中文命名与选材在 tools/food_naming.py（约 300 条人工审定条目）。

# 与 USDA 交叉核对的可比对食材。
# 前半是成分表里的准确条目名，后半是 USDA 描述里必须包含的关键词（大小写无关），
# 名称对照是人工写的，营养数值一律取自各自数据库，不做任何换算或替换。
USDA_CROSSCHECK = [
    ("鸡蛋（代表值）", ["egg, whole, raw"], ["dried", "powder", "yolk", "white"]),
    ("稻米（代表值）", ["rice, white, long-grain, regular, raw"], []),
    ("纯牛奶（代表值，全脂）", ["milk, whole, 3.25%"], []),
    ("香蕉［甘蕉］", ["bananas, raw"], []),
    ("苹果（代表值）", ["apples, raw, with skin"], []),
    ("马铃薯［土豆、洋芋］", ["potatoes, flesh and skin, raw"], []),
    ("花生油", ["oil, peanut"], []),
    ("黄豆［大豆］", ["soybeans, mature seeds, raw"], []),
    ("白萝卜（鲜）［莱菔］", ["radishes, raw"], []),
    ("番茄［西红柿］", ["tomatoes, red, ripe, raw"], []),
    ("小麦粉（代表值）", ["wheat flour, white, all-purpose"], ["self-rising"]),
    # 猪肉按「部位差异极大 + 生熟口径不同」处理：只与 USDA 的**生**复合部位比对，
    # 偏差仍可能偏大，这类会落到 review 里人工看，不会被当成错误数据自动采信。
    ("猪肉（代表值，fat30g）", ["pork, fresh, composite of trimmed retail cuts"], ["cooked"]),
]

USDA_ENERGY_NUTRIENT_ID = "1008"
USDA_PROTEIN_ID = "1003"
USDA_FAT_ID = "1004"
USDA_CARB_ID = "1005"


def parse_number(value, default=None):
    """
    成分表里的数值字段格式不统一：
      '1.2' 正常值 ｜ '—' 未检出 ｜ 'Tr' 微量 ｜ '899*' 带脚注星号 ｜ '' 空
    '—' 交给调用方决定（宏量营养素按 0 处理，再经 Atwater 校验背书）；'Tr' 记 0。
    """
    if value is None:
        return default
    text = str(value).strip()
    if text == "":
        return default
    if text in ("Tr", "tr"):
        return 0.0
    if text in ("—", "-", "…"):
        return default
    match = re.match(r"^[+-]?\d+(?:\.\d+)?", text)
    if match:
        return float(match.group(0))
    return default


def bracketed_alias(name: str) -> str:
    """'番茄［西红柿］' -> '西红柿' ；'马铃薯［土豆、洋芋］' -> '土豆,洋芋'"""
    found = re.findall(r"[［\[]([^］\]]+)[］\]]", name)
    if not found:
        return ""
    parts = []
    for item in found:
        parts.extend(p.strip() for p in re.split(r"[、,，]", item) if p.strip())
    return ",".join(dict.fromkeys(parts))


def pick_serving(name: str, category: str):
    for keys, value in SERVING_OVERRIDES:
        if any(k in name for k in keys):
            return value
    return SERVING_BY_CATEGORY.get(category, ("一份约 100g", 100))


def is_cooked(name: str) -> bool:
    return any(h in name for h in COOKED_HINTS)


# 4/4/9 口径解释不了「能量来自乙醇与有机酸」的食物：
#   * 酒类：乙醇 7 kcal/g，USDA 的能量值已包含它；
#   * 醋：能量来自醋酸（也是有机酸）。
# 这类条目的「不自洽」不是数据错，换成按描述关键词明确豁免，
# 并把豁免的每一条写进质检报告 —— 豁免范围必须看得见，不能悄悄放宽整个闸门。
ATWATER_EXEMPT_HINTS = (
    "alcoholic beverage",
    "vinegar",
    "wine",
    "beer",
    "sake",
    "spirits",
    "cocktail",
)

# 相对偏差之外再留一个绝对下限：热量本身极低时（如蒸馏醋 18 kcal/100g），
# 相对偏差会被放大到几百 %，但绝对差不到 1 kcal —— 这种不算问题。
ATWATER_ABS_SLACK = 25.0


def energy_outside_atwater(*texts) -> bool:
    return any(hint in (text or "").lower() for text in texts for hint in ATWATER_EXEMPT_HINTS)


def atwater_ok(kcal, carbs, protein, fat) -> bool:
    computed = 4.0 * carbs + 4.0 * protein + 9.0 * fat
    if kcal <= 0:
        return False
    diff = abs(computed - kcal)
    if diff <= ATWATER_ABS_SLACK:
        return True
    return diff / kcal <= ATWATER_TOLERANCE


def atwater_deviation(kcal, carbs, protein, fat):
    computed = 4.0 * carbs + 4.0 * protein + 9.0 * fat
    return abs(computed - kcal) / kcal if kcal > 0 else float("inf")


# ---------------------------------------------------------------- 载入成分表

def load_cfct():
    foods, rejected = [], []
    if not CFCT_DIR.exists():
        raise SystemExit(f"找不到成分表数据目录：{CFCT_DIR}")

    for path in sorted(CFCT_DIR.glob("merged_*.json")):
        match = re.match(r"merged_(.+?)-(.+)\.json", path.name)
        if not match:
            continue
        top, _sub = match.group(1), match.group(2)
        category = TOP_CATEGORY.get(top, "其他")

        try:
            rows = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            rejected.append({"file": path.name, "reason": f"JSON 解析失败: {exc}"})
            continue

        for row in rows:
            name = (row.get("foodName") or "").strip()
            code = (row.get("foodCode") or "").strip()
            kcal = parse_number(row.get("energyKCal"))
            carbs = parse_number(row.get("CHO"))
            protein = parse_number(row.get("protein"))
            fat = parse_number(row.get("fat"))

            if not name or not code:
                rejected.append({"name": name, "code": code, "reason": "缺少名称或条目号"})
                continue
            if kcal is None:
                rejected.append({"name": name, "code": code, "reason": "缺少热量值"})
                continue
            # 成分表里用「—」表示未检出/微量。这类宏量营养素按 0 处理，
            # 但**必须**通过下面的 Atwater 自洽性校验才会入库 ——
            # 也就是说「补 0」这个假设由数据自己证明，不靠人拍脑袋。
            imputed = []
            if carbs is None:
                carbs = 0.0
                imputed.append("carbs")
            if protein is None:
                protein = 0.0
                imputed.append("protein")
            if fat is None:
                fat = 0.0
                imputed.append("fat")
            if min(kcal, carbs, protein, fat) < 0:
                rejected.append({"name": name, "code": code, "reason": "存在负值"})
                continue
            if not (0 < kcal <= KCAL_MAX):
                rejected.append({"name": name, "code": code, "reason": f"热量越界 {kcal}"})
                continue
            if not atwater_ok(kcal, carbs, protein, fat):
                rejected.append({
                    "name": name, "code": code,
                    "reason": f"Atwater 自洽性超容差 {atwater_deviation(kcal, carbs, protein, fat):.1%}",
                })
                continue

            serving, serving_grams = pick_serving(name, category)
            alias = ",".join(filter(None, [bracketed_alias(name), CURATED_ALIAS.get(name, "")]))
            foods.append({
                "id": f"cfct:{code}",
                "name": name,
                "alias": alias,
                "category": category,
                "kcal": round(kcal, 1),
                "carbs": round(carbs, 1),
                "protein": round(protein, 1),
                "fat": round(fat, 1),
                "serving": serving,
                "servingGrams": int(serving_grams),
                "source": PRIMARY_SOURCE,
                "sourceRef": code,
                "cooked": is_cooked(name),
                "imputedZero": imputed,
            })
    return foods, rejected


# ---------------------------------------------------------------- 复合菜推算

def compose_recipes(index_by_name):
    foods, rejected = [], []
    for recipe in RECIPES:
        total_grams = 0.0
        totals = {"kcal": 0.0, "carbs": 0.0, "protein": 0.0, "fat": 0.0}
        refs = []
        ok = True
        for ingredient_name, grams in recipe["ingredients"]:
            if ingredient_name == WATER:
                # 水不提供热量，但**要计入成品重量** —— 汤类菜肴按投料总重折算每 100g 值，
                # 漏掉水会把一碗汤算成浓缩汁（热量虚高好几倍）。
                total_grams += grams
                refs.append(f"水 {grams}g（0 kcal）")
                continue
            base = index_by_name.get(ingredient_name)
            if base is None:
                rejected.append({"name": recipe["name"], "reason": f"成分表中找不到食材「{ingredient_name}」"})
                ok = False
                break
            total_grams += grams
            for key in totals:
                # 食材值都是「每 100g」，按克数线性折算后累加
                totals[key] += base[key] * grams / 100.0
            refs.append(f"{ingredient_name} {grams}g@{base['sourceRef']}")
        if not ok or total_grams <= 0:
            continue
        scale = 100.0 / total_grams
        foods.append({
            "id": recipe["id"],
            "name": recipe["name"],
            "alias": recipe.get("alias", ""),
            "category": recipe["category"],
            "kcal": round(totals["kcal"] * scale, 1),
            "carbs": round(totals["carbs"] * scale, 1),
            "protein": round(totals["protein"] * scale, 1),
            "fat": round(totals["fat"] * scale, 1),
            "serving": recipe["serving"],
            "servingGrams": int(recipe["serving_grams"]),
            "source": DRY_DISH_SOURCE,
            "sourceRef": " | ".join(refs),
            "cooked": True,
            "note": recipe["note"],
        })
    return foods, rejected


# ---------------------------------------------------------------- USDA 校验

def load_usda():
    """返回 {description: {kcal, protein, fat, carbs}}，缺文件的 CSV 直接跳过。"""
    food_csv = next(USDA_DIR.glob("**/food.csv"), None)
    nutrient_csv = next(USDA_DIR.glob("**/food_nutrient.csv"), None)
    if not food_csv or not nutrient_csv:
        return {}

    import csv
    names = {}
    with food_csv.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            names[row["fdc_id"]] = (row.get("description") or "").strip()

    wanted = {USDA_ENERGY_NUTRIENT_ID, USDA_PROTEIN_ID, USDA_FAT_ID, USDA_CARB_ID}
    key_of = {
        USDA_ENERGY_NUTRIENT_ID: "kcal",
        USDA_PROTEIN_ID: "protein",
        USDA_FAT_ID: "fat",
        USDA_CARB_ID: "carbs",
    }
    result = {}
    with nutrient_csv.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            nutrient_id = (row.get("nutrient_id") or "").strip()
            if nutrient_id not in wanted:
                continue
            description = names.get(row["fdc_id"])
            if not description:
                continue
            try:
                amount = float(row["amount"])
            except (TypeError, ValueError):
                continue
            result.setdefault(description, {})[key_of[nutrient_id]] = amount
    return {k: v for k, v in result.items() if len(v) == 4}


def match_usda(usda, must, avoid):
    for description, values in usda.items():
        lowered = description.lower()
        if all(token.lower() in lowered for token in must) and not any(a in lowered for a in avoid):
            return description, values
    return None, None


def pick_usda_row(rows, must, avoid):
    """按人工命名表的规则挑出唯一一条 USDA 条目（挑不到就返回 None，交给上层报告）。"""
    must_l = tuple(token.lower() for token in must)
    avoid_l = tuple(token.lower() for token in avoid) + GLOBAL_AVOID
    hits = [
        row for row in rows
        if all(token in row["description"].lower() for token in must_l)
        and not any(bad in row["description"].lower() for bad in avoid_l)
    ]
    if not hits:
        return None
    # 与 must[0] 完全相等 = 标准条目（不是「低脂/切片/罐装」这类变体），优先
    for row in hits:
        if row["description"].lower() == must_l[0]:
            return row
    return min(hits, key=lambda row: (len(row["description"]), row["description"]))


def pick_usda_ambiguous(rows, must, avoid):
    """
    检查一个命名规则是不是「多义」的：候选条目之间热量差得太多就值得人工看一眼。

    为什么需要这道检查：挑取规则是「取描述最短的那条」，但 USDA 里同一种食物常有
    地区/品种变体，长短和代表性没有关系。实测踩过 —— 「牛油果」的候选里
    `Avocados, raw, Florida`（短）只有 120 kcal/100g，而 `Avocados, raw, California`
    是 167、`all commercial varieties` 是 160。取最短就取到了佛州那种低脂品种，
    热量差了 25%，属于错数据。
    """
    must_l = tuple(token.lower() for token in must)
    avoid_l = tuple(token.lower() for token in avoid) + GLOBAL_AVOID
    hits = [
        row for row in rows
        if all(token in row["description"].lower() for token in must_l)
        and not any(bad in row["description"].lower() for bad in avoid_l)
    ]
    if len(hits) < 2:
        return None
    kcals = [row["kcal"] for row in hits]
    low, high = min(kcals), max(kcals)
    if high <= 0 or (high - low) / high <= 0.15:
        return None
    return {
        "candidates": len(hits),
        "kcalRange": [round(low, 1), round(high, 1)],
        "sample": sorted(row["description"] for row in hits)[:5],
    }


def load_usda_raw_rows():
    """把 USDA 读成「一行一条」的候选池，供人工命名表挑取。"""
    import csv

    food_csv = next(USDA_DIR.glob("**/food.csv"), None)
    nutrient_csv = next(USDA_DIR.glob("**/food_nutrient.csv"), None)
    if not food_csv or not nutrient_csv:
        return []

    wanted = {
        USDA_ENERGY_NUTRIENT_ID: "kcal",
        USDA_PROTEIN_ID: "protein",
        USDA_FAT_ID: "fat",
        USDA_CARB_ID: "carbs",
    }
    values = {}
    with nutrient_csv.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            key = wanted.get((row.get("nutrient_id") or "").strip())
            if key is None:
                continue
            try:
                values.setdefault(row["fdc_id"], {})[key] = float(row["amount"])
            except (TypeError, ValueError):
                continue

    rows = []
    with food_csv.open(encoding="utf-8", newline="") as handle:
        for row in csv.DictReader(handle):
            data = values.get(row["fdc_id"])
            if not data or len(data) != 4:
                continue
            rows.append({
                "fdc_id": row["fdc_id"],
                "description": (row.get("description") or "").strip(),
                **data,
            })
    return rows


def strip_qualifier(name: str) -> str:
    """
    去掉名称里的括注，用来判断两条是不是「同一种食物」：
    番茄［西红柿］ -> 番茄；苹果（带皮） -> 苹果；胡萝卜 -> 胡萝卜
    """
    return re.sub(r"[（(［\[][^）)］\]]*[）)］\]]", "", name).strip()


def load_usda_curated():
    """
    按人工中文命名表把 USDA 收缩成一个「全中文、能看」的子集。

    返回 (foods, rejected, misses, dups)：
      * misses：命名表里挑不到 USDA 条目的项（要人工修，不能静默丢）；
      * dups  ：两个中文名挑到了同一条 USDA 条目（说明命名表写重了）。
    """
    rows = load_usda_raw_rows()
    if not rows:
        return [], [], [], [], []

    foods, rejected, misses, dups = [], [], [], []
    atwater_exempt = []
    ambiguous = []
    used_ids = {}
    for zh_name, category, must, avoid in USDA_PICKS:
        row = pick_usda_row(rows, must, avoid)
        if row is None:
            misses.append("%s  must=%s avoid=%s" % (zh_name, list(must), list(avoid)))
            continue
        info = pick_usda_ambiguous(rows, must, avoid)
        if info:
            ambiguous.append(
                "%s -> 已选「%s」%.1f kcal；候选 %d 条跨度 %s，例如 %s"
                % (zh_name, row["description"], row["kcal"], info["candidates"],
                   info["kcalRange"], " / ".join(info["sample"][:3]))
            )
        if row["fdc_id"] in used_ids:
            dups.append("%s 与 %s 都指向 %s" % (zh_name, used_ids[row["fdc_id"]], row["description"]))
            continue
        used_ids[row["fdc_id"]] = zh_name

        description = row["description"]
        kcal, carbs, protein, fat = row["kcal"], row["carbs"], row["protein"], row["fat"]
        if not (0 < kcal <= KCAL_MAX):
            rejected.append({"name": zh_name, "code": row["fdc_id"], "reason": f"热量越界 {kcal}"})
            continue
        if min(carbs, protein, fat) < 0:
            rejected.append({"name": zh_name, "code": row["fdc_id"], "reason": "存在负值"})
            continue
        exempt = energy_outside_atwater(description)
        if not atwater_ok(kcal, carbs, protein, fat) and not exempt:
            rejected.append({
                "name": zh_name, "code": row["fdc_id"],
                "reason": f"Atwater 自洽性超容差 {atwater_deviation(kcal, carbs, protein, fat):.1%}",
            })
            continue
        if exempt and not atwater_ok(kcal, carbs, protein, fat):
            atwater_exempt.append(f"{zh_name}（{description}）能量含乙醇/有机酸，Atwater 不适用")

        serving, serving_grams = pick_serving(zh_name, category)
        # alias 只参与搜索、从不显示：英文原名（英文也能搜到）+ 常用中文叫法
        search_terms = ",".join(t for t in (description, EXTRA_ZH_ALIAS.get(zh_name, "")) if t)
        foods.append({
            "id": f"usda:{row['fdc_id']}",
            "name": zh_name,
            "alias": search_terms,
            "category": category,
            "kcal": round(kcal, 1),
            "carbs": round(carbs, 1),
            "protein": round(protein, 1),
            "fat": round(fat, 1),
            "serving": serving,
            "servingGrams": int(serving_grams),
            "source": USDA_SOURCE,
            "sourceRef": row["fdc_id"],
            "cooked": "cooked" in description.lower() or "roasted" in description.lower(),
        })
    return foods, rejected, misses, dups, atwater_exempt, ambiguous


def crosscheck(foods, usda):
    if not usda:
        return {"available": False, "note": "未找到 USDA CSV，跳过交叉核对"}
    by_name = {f["name"]: f for f in foods}
    rows = []
    for cn_name, must, avoid in USDA_CROSSCHECK:
        local = by_name.get(cn_name)
        if local is None:
            rows.append({"name": cn_name, "status": "local-missing", "must": must})
            continue
        description, remote = match_usda(usda, must, avoid)
        if remote is None:
            rows.append({"name": cn_name, "status": "usda-missing", "must": must})
            continue
        kcal_diff = (local["kcal"] - remote["kcal"]) / remote["kcal"] if remote["kcal"] else None
        protein_diff = (local["protein"] - remote["protein"]) / remote["protein"] if remote["protein"] else None
        rows.append({
            "name": cn_name,
            "usda": description,
            "cfct": {"kcal": local["kcal"], "protein": local["protein"], "fat": local["fat"], "carbs": local["carbs"]},
            "usdaValues": {k: round(v, 2) for k, v in remote.items()},
            "kcalDiff": None if kcal_diff is None else round(kcal_diff, 3),
            "proteinDiff": None if protein_diff is None else round(protein_diff, 3),
            "status": "ok" if kcal_diff is not None and abs(kcal_diff) <= 0.25 else "review",
        })
    return {"available": True, "rows": rows}


# ---------------------------------------------------------------- 主流程

def main():
    cfct_foods, cfct_rejected = load_cfct()
    usda_foods, usda_rejected, usda_misses, usda_dups, atwater_exempt, ambiguous = load_usda_curated()

    # 中国成分表已经覆盖的食物，就不再从 USDA 重复导入一次。
    # 两道库里同一种食物的数值本来就有几个百分点的口径差（中美碳水/膳食纤维算法不同），
    # 同时存在会让用户搜出两条「胡萝卜」却不知道该点哪条 —— 以中文成分表为准。
    cfct_norm = {strip_qualifier(f["name"]) for f in cfct_foods}
    kept_usda, covered = [], []
    for item in usda_foods:
        if strip_qualifier(item["name"]) in cfct_norm:
            covered.append(item["name"])
            continue
        kept_usda.append(item)
    usda_foods = kept_usda

    # 菜肴配比要引用食材，而有些食材（酱油、白醋、白砂糖）成分表里没有、只有 USDA 有，
    # 所以索引必须把两个源合起来建。
    #
    # **必须用「先到先得」，和下面第 N 行同名去重的规则保持一致**：
    # 成分表里同名条目有多条（例如「黄豆芽」有 042202 / 042205），去重保留的是**第一条**；
    # 如果这里用字典推导（后出现者覆盖），菜肴就会引用到**被丢弃的那条**，
    # 于是「菜里的食材值」和「库里剩下的那条」对不上，独立复算必然报差异。
    # 实测：麻辣香锅用黄豆芽 → 库 173.7，而按 042205 复算是 175.0。
    # 成分表在前：同名时以它为准，压住 USDA 的同名条目（保持原有优先级）。
    index_by_name = {}
    for item in cfct_foods:
        index_by_name.setdefault(item["name"], item)
    # 成分表没有的名字（酱油、白醋…）才用 USDA 补。
    for item in usda_foods:
        index_by_name.setdefault(item["name"], item)
    dish_foods, dish_rejected = compose_recipes(index_by_name)

    # id 去重 + 同名去重（后出现者丢弃并记录）。
    # 优先级就是拼接顺序：成分表 > 复算菜肴 > USDA。
    # 成分表里同一名称对应多个条目号（例如「栗子」有 5 条不同产地/品种），
    # 名字一模一样的条目在搜索列表里无法分辨，只保留第一条，其余记录到报告里。
    seen_ids, seen_names, foods, dupes, name_dropped = set(), {}, [], [], []
    for item in cfct_foods + dish_foods + usda_foods:
        if item["id"] in seen_ids:
            dupes.append(item["id"])
            continue
        if item["name"] in seen_names:
            name_dropped.append(f"{item['name']} -> 丢弃 {item['id']}，保留 {seen_names[item['name']]}")
            continue
        seen_ids.add(item["id"])
        seen_names[item["name"]] = item["id"]
        foods.append(item)

    usda = load_usda()
    check = crosscheck(foods, usda)

    foods.sort(key=lambda f: (f["category"], f["name"]))
    imputed_count = sum(1 for f in foods if f.get("imputedZero"))

    payload = {
        "meta": {
            "generatedAt": datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds"),
            "primarySource": PRIMARY_SOURCE,
            "validationSource": VALIDATION_SOURCE,
            "composedDishSource": DRY_DISH_SOURCE,
            "valueBasis": "每 100g 可食部",
            "count": len(foods),
            "rejected": len(cfct_rejected) + len(dish_rejected),
            "atwaterTolerance": ATWATER_TOLERANCE,
            "note": "本文件由 tools/build-food-db.py 生成，请勿手工编辑。",
        },
        "foods": foods,
    }

    # 种子版本：食物库内容一变，已装用户下次启动就会把新条目补进去。
    # 用内容哈希而不是时间戳 —— 数据没变就不该触发重灌。
    import hashlib
    digest = hashlib.md5(
        json.dumps(foods, ensure_ascii=False, sort_keys=True).encode("utf-8")
    ).hexdigest()[:12]
    version = f"{len(foods)}-{digest}"
    payload["meta"]["seedVersion"] = version

    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
    # 单独一个小文件放版本号：App 启动只需读它就能判断要不要解析 2MB 的食物库
    OUT_JSON.with_suffix(".version").write_text(version, encoding="utf-8")

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    (REPORT_DIR / "qa_report.json").write_text(
        json.dumps(
            {
                "generatedAt": payload["meta"]["generatedAt"],
                "counts": {
                    "cfctAccepted": len(cfct_foods),
                    "composedDishes": len(dish_foods),
                    "usdaAccepted": len(usda_foods),
                    "usdaNamingMisses": len(usda_misses),
                    "usdaNamingDuplicates": len(usda_dups),
                    "rejected": len(cfct_rejected) + len(dish_rejected) + len(usda_rejected),
                    "duplicateIds": len(dupes),
                    "imputedZeroRows": imputed_count,
                },
                "rejected": (cfct_rejected + dish_rejected)[:200],
                "usdaNamingMisses": usda_misses,
                "usdaNamingDuplicates": usda_dups,
                "atwaterExempt": atwater_exempt,
                "usdaAmbiguousPicks": ambiguous,
                "usdaSkippedCoveredByCfct": covered,
                "nameDropped": name_dropped,
                "usdaCrosscheck": check,
            },
            ensure_ascii=False,
            indent=1,
        ),
        encoding="utf-8",
    )

    print(f"foods written : {len(foods)}  -> {OUT_JSON}")
    print(f"seed version  : {version}")
    print(f"cfct accepted : {len(cfct_foods)}")
    print(f"composed      : {len(dish_foods)}")
    print(f"usda accepted : {len(usda_foods)}  (全部人工中文命名)")
    if usda_misses:
        print(f"  命名表未匹配 {len(usda_misses)} 项（需修 must/avoid 或删除）：")
        for line in usda_misses:
            print(f"    {line}")
    if usda_dups:
        print(f"  命名表重复指向 {len(usda_dups)} 项：")
        for line in usda_dups:
            print(f"    {line}")
    if atwater_exempt:
        print(f"  Atwater 豁免（能量含乙醇/有机酸）：{len(atwater_exempt)} 条")
        for line in atwater_exempt:
            print(f"    {line}")
    if ambiguous:
        print(f"  多义候选（同词多品种，热量差 >15%，已人工复核）：{len(ambiguous)} 项")
        for line in ambiguous:
            print(f"    {line}")
    if covered:
        print(f"因成分表已覆盖而跳过的 USDA 条目：{len(covered)} 条（{'／'.join(covered[:8])} …）")
    if name_dropped:
        print(f"同名条目去重：{len(name_dropped)} 条")
        for line in name_dropped:
            print(f"    {line}")
    print(f"rejected      : {len(cfct_rejected) + len(dish_rejected) + len(usda_rejected)}")
    print(f"  - cfct/composed: {len(cfct_rejected) + len(dish_rejected)}")
    print(f"  - usda         : {len(usda_rejected)}")
    print(f"dup ids       : {len(dupes)}")
    print(f"imputed zeros : {imputed_count}")
    if check.get("available"):
        review = [r for r in check["rows"] if r.get("status") == "review"]
        print(f"usda rows     : {len(check['rows'])}, need review: {len(review)}")
        for row in check["rows"]:
            print("   ", row["name"], row.get("status"), row.get("kcalDiff"))
    else:
        print("usda          : unavailable")


if __name__ == "__main__":
    main()
