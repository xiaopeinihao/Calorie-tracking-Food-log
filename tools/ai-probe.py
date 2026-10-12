# -*- coding: utf-8 -*-
"""直连验证 TokenHub 的 OpenAI 兼容接口：文本、模型列表、视觉（内联 base64 图片）。"""
import base64
import json
import mimetypes
import os
import sys
import urllib.error
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")

BASE = os.environ.get("TOKENHUB_BASE", "https://tokenhub.tencentmaas.com/v1")
KEY = os.environ.get("TOKENHUB_KEY", "")
MODEL = os.environ.get("TOKENHUB_MODEL", "glm-5.3-flash")

if not KEY:
    print("缺少 TOKENHUB_KEY")
    sys.exit(1)


def post(path, payload, timeout=90):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        headers={
            "Authorization": "Bearer " + KEY,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")
    except Exception as exc:  # noqa: BLE001
        return None, f"{type(exc).__name__}: {exc}"


def get(path, timeout=30):
    req = urllib.request.Request(BASE + path, headers={"Authorization": "Bearer " + KEY})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")
    except Exception as exc:  # noqa: BLE001
        return None, f"{type(exc).__name__}: {exc}"


def show(title, status, body, limit=900):
    print(f"\n=== {title} ===")
    print("status:", status)
    print(body[:limit])


def timed_post(path, payload, timeout=180):
    import time

    started = time.monotonic()
    status, body = post(path, payload, timeout=timeout)
    return status, body, int((time.monotonic() - started) * 1000)


# 1) 文本
status, body = post(
    "/chat/completions",
    {
        "model": MODEL,
        "messages": [{"role": "user", "content": "只回答四个字：接口正常"}],
        "stream": False,
    },
)
show("1) 文本 /chat/completions", status, body)

# 2) 模型列表
status, body = get("/models")
show("2) 模型列表 /models", status, body, limit=1500)

# 3) 视觉：把本地图片内联成 data URL
image_arg = sys.argv[1] if len(sys.argv) > 1 else None
if image_arg and os.path.exists(image_arg):
    mime = mimetypes.guess_type(image_arg)[0] or "image/jpeg"
    with open(image_arg, "rb") as handle:
        encoded = base64.b64encode(handle.read()).decode("ascii")
    status, body = post(
        "/chat/completions",
        {
            "model": MODEL,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": "这张图里有什么食物？只回答食物名称，没有食物就回答「无」。"},
                        {
                            "type": "image_url",
                            "image_url": {"url": f"data:{mime};base64,{encoded}"},
                        },
                    ],
                }
            ],
            "stream": False,
        },
        timeout=180,
    )
    show(f"3) 视觉 /chat/completions（{os.path.basename(image_arg)}）", status, body, limit=1200)
else:
    print("\n=== 3) 视觉 ===\n未提供图片，跳过")
