# -*- coding: utf-8 -*-
"""生成设备上的 AI 配置文件（SharedPreferences），把 TokenHub 的 Key 与能力绑定写进去。

App 的 AI 设置界面走的就是同一份存储，这里只是省去在模拟器里手打 50 位 Key 的步骤。
Key 只落在设备本地，不会写进源码仓库。
"""
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

key = os.environ.get("TOKENHUB_KEY", "")
if not key:
    print("缺少 TOKENHUB_KEY")
    sys.exit(1)

providers = [
    {
        "id": "tokenhub",
        "name": "腾讯 TokenHub",
        "baseUrl": "https://tokenhub.tencentmaas.com/v1",
        "apiKey": key,
        "textModel": "hy4-preview",
        "visionModel": "deepseek/deepseek-flash",
        "isBuiltIn": True,
        "consoleUrl": "https://tokenhub.tencentmaas.com",
        "hint": "文本 hy4-preview，视觉 deepseek/deepseek-flash",
    }
]
binding = {"text": "tokenhub", "vision": "tokenhub"}


def esc(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


xml = (
    "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
    "<map>\n"
    f'    <string name="ai_providers_v1">{esc(json.dumps(providers, ensure_ascii=False))}</string>\n'
    f'    <string name="ai_binding_v1">{esc(json.dumps(binding, ensure_ascii=False))}</string>\n'
    "</map>\n"
)

out = r"e:\AI开发\tools\data\ai_config.xml"
with open(out, "w", encoding="utf-8") as handle:
    handle.write(xml)
print("written:", out, len(xml), "chars")
print("binding:", binding)
