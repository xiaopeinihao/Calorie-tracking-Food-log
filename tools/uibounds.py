# -*- coding: utf-8 -*-
"""从 uiautomator dump 里取节点坐标。

用法:
  python tools/uibounds.py <dump.xml> 记录 我的      # 按文本/描述查坐标
  python tools/uibounds.py <dump.xml> --all          # 列出所有有文案的节点
"""
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

xml = open(sys.argv[1], encoding="utf-8").read()
nodes = re.findall(r"<node[^>]*>", xml)
pattern = re.compile(r'(\w[\w-]*)="([^"]*)"')


def center(bounds):
    x1, y1, x2, y2 = (int(v) for v in bounds)
    return (x1 + x2) // 2, (y1 + y2) // 2


if sys.argv[2:3] == ["--all"]:
    for node in nodes:
        attrs = dict(pattern.findall(node))
        label = (attrs.get("text") or "").strip() or (attrs.get("content-desc") or "").strip()
        bounds = attrs.get("bounds")
        if not label or not bounds:
            continue
        x, y = center(re.findall(r"\d+", bounds))
        print("%-30s center=(%4d,%4d)" % (label[:30], x, y))
    sys.exit(0)

for target in sys.argv[2:]:
    for node in nodes:
        attrs = dict(pattern.findall(node))
        label = (attrs.get("text") or "").strip() or (attrs.get("content-desc") or "").strip()
        if label != target:
            continue
        bounds = attrs.get("bounds")
        if not bounds:
            continue
        x, y = center(re.findall(r"\d+", bounds))
        print("%-10s center=(%d,%d)  %s" % (target, x, y, attrs.get("bounds")))
