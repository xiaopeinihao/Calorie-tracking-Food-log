# -*- coding: utf-8 -*-
"""对比 TokenHub 上几个候选模型的可用性与延迟，为 App 选默认模型提供依据。"""
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

BASE = "https://tokenhub.tencentmaas.com/v1"
KEY = os.environ.get("TOKENHUB_KEY", "")
IMAGE = r"e:\AI开发\tools\data\food-test.jpg"

TEXT_MODELS = ["hy4-preview", "deepseek/deepseek-flash", "glm-5.3-flash"]
VISION_MODELS = ["glm-5.3-flash", "deepseek/deepseek-flash"]


def call(payload, timeout=180):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE + "/chat/completions",
        data=data,
        headers={"Authorization": "Bearer " + KEY, "Content-Type": "application/json"},
        method="POST",
    )
    started = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = json.loads(resp.read().decode("utf-8", "replace"))
        ms = int((time.monotonic() - started) * 1000)
        content = body["choices"][0]["message"].get("content", "")
        return ms, content.strip().replace("\n", " ")[:60]
    except urllib.error.HTTPError as exc:
        return int((time.monotonic() - started) * 1000), f"HTTP {exc.code}: {exc.read()[:120]}"
    except Exception as exc:  # noqa: BLE001
        return int((time.monotonic() - started) * 1000), f"{type(exc).__name__}: {exc}"


with open(IMAGE, "rb") as handle:
    encoded = base64.b64encode(handle.read()).decode("ascii")
image_part = {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64," + encoded}}

print("=== 文本（同样一句话） ===")
for model in TEXT_MODELS:
    ms, answer = call(
        {
            "model": model,
            "messages": [{"role": "user", "content": "午饭吃了一碗米饭和一个鸡蛋，请只回一句：收到"}],
            "stream": False,
        }
    )
    print(f"{model:28} {ms:>6} ms  -> {answer}")

print("\n=== 视觉（同一张番茄炒蛋照片） ===")
for model in VISION_MODELS:
    ms, answer = call(
        {
            "model": model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": "图里是什么食物？只回答菜名，没有食物回答「无」。"},
                        image_part,
                    ],
                }
            ],
            "stream": False,
        }
    )
    print(f"{model:28} {ms:>6} ms  -> {answer}")
