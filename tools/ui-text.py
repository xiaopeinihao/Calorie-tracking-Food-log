# -*- coding: utf-8 -*-
"""从 uiautomator dump 的 xml 里提取所有可见文案，用于判定当前页面。"""
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")
path = sys.argv[1]
text = open(path, encoding="utf-8", errors="replace").read()
labels = [t for t in re.findall(r'text="([^"]*)"', text) if t.strip()]
descriptions = [t for t in re.findall(r'content-desc="([^"]*)"', text) if t.strip()]

print("visible text:", labels)
print("content-desc :", descriptions)
