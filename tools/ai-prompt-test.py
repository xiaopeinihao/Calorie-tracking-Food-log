# -*- coding: utf-8 -*-
"""用 App 里的那条真实 prompt 打模型，看原始返回（含 finish_reason / usage）。

用来诊断「思考型模型把 max_tokens 烧在推理上，content 为空」这类问题。
"""
import json
import os
import sys
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

BASE = "https://tokenhub.tencentmaas.com/v1"
KEY = os.environ.get("TOKENHUB_KEY", "")
MODEL = os.environ.get("TOKENHUB_MODEL", "hy4-preview")
MAX_TOKENS = int(os.environ.get("MAX_TOKENS", "2048"))

PROMPT = """你是中文食物营养识别助手。请根据用户提供的文字描述，识别出吃了什么，并给出每 100 克的营养值。

当前时间：2026-10-08 16:05（据此判断餐次，当前大概属于：加餐）

用户描述：
午饭：番茄炒蛋配一碗米饭

输出要求（违反任何一条都视为失败）：
1. 只输出一个 JSON 数组，不要解释、不要 markdown 代码块、不要前后缀文字。
2. 数组元素字段：
   {"name":"食物名（中文，用家常叫法）","grams":份量克数(整数),
    "kcalPer100g":每100克热量千卡(数字或null),"carbsPer100g":碳水克数(数字或null),
    "proteinPer100g":蛋白质克数(数字或null),"fatPer100g":脂肪克数(数字或null),"confidence":0到1}
3. grams 必须是数字：按常见份量估算（一碗米饭约200g、一个鸡蛋约50g、一份炒菜约250g、一杯牛奶约250g、一个苹果约180g）。
4. 营养值必须是你确实知道的该食物每 100 克标准值；只要有不确定就填 null。**绝对不要为了凑数编一个看起来合理的数字。**
5. 中餐按「主食 / 菜 / 汤 / 饮料」拆成独立条目；一道菜不要拆成原料（不要把番茄炒蛋拆成番茄和鸡蛋）。
6. 油、糖、酱汁这类调料只有明显改变热量时才单独列出（例如「红烧肉」不用再单列油）。
7. 最多 8 条，按份量从大到小排序。
8. 如果内容不是食物（例如拍到人、桌面、风景），返回 []。
9. 只输出 JSON 数组本身，第一个字符必须是 [，最后一个字符必须是 ]。
"""

payload = {
    "model": MODEL,
    "messages": [{"role": "user", "content": PROMPT}],
    "stream": False,
    "temperature": 0.2,
    "max_tokens": MAX_TOKENS,
}

req = urllib.request.Request(
    BASE + "/chat/completions",
    data=json.dumps(payload).encode("utf-8"),
    headers={"Authorization": "Bearer " + KEY, "Content-Type": "application/json"},
    method="POST",
)
try:
    with urllib.request.urlopen(req, timeout=300) as resp:
        body = json.loads(resp.read().decode("utf-8", "replace"))
except urllib.error.HTTPError as exc:
    print("HTTP", exc.code, exc.read().decode("utf-8", "replace")[:500])
    sys.exit(1)

choice = body["choices"][0]
message = choice["message"]
print("model        :", body.get("model"))
print("finish_reason:", choice.get("finish_reason"))
print("usage        :", json.dumps(body.get("usage"), ensure_ascii=False))
print("content len  :", len(message.get("content") or ""))
print("reasoning len:", len(message.get("reasoning_content") or ""))
print("--- content ---")
print((message.get("content") or "")[:800])
print("--- reasoning（尾部 300 字） ---")
print((message.get("reasoning_content") or "")[-300:])
