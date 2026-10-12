# -*- coding: utf-8 -*-
"""找一张真实的食物照片用于测试视觉识别（优先 Wikimedia Commons）。"""
import json
import os
import sys
import urllib.parse
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")
OUT = r"e:\AI开发\tools\data\food-test.jpg"

CANDIDATES = [
    ("https://commons.wikimedia.org/w/api.php?action=query&generator=search"
     "&gsrsearch=tomato%20scrambled%20egg&gsrnamespace=6&gsrlimit=5&prop=imageinfo"
     "&iiprop=url&iiurlwidth=1024&format=json"),
    ("https://commons.wikimedia.org/w/api.php?action=query&generator=search"
     "&gsrsearch=steamed%20rice%20bowl&gsrnamespace=6&gsrlimit=5&prop=imageinfo"
     "&iiprop=url&iiurlwidth=1024&format=json"),
]


def fetch(url, timeout=40):
    req = urllib.request.Request(url, headers={"User-Agent": "calorie-app-test/1.0"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()


def try_commons():
    for api in CANDIDATES:
        try:
            payload = json.loads(fetch(api))
        except Exception as exc:  # noqa: BLE001
            print("搜索失败:", type(exc).__name__, exc)
            continue
        pages = (payload.get("query") or {}).get("pages") or {}
        for page in pages.values():
            infos = page.get("imageinfo") or []
            if not infos:
                continue
            url = infos[0].get("thumburl") or infos[0].get("url")
            if not url:
                continue
            try:
                data = fetch(url)
            except Exception as exc:  # noqa: BLE001
                print("下载失败:", url, exc)
                continue
            if len(data) < 5000:
                continue
            with open(OUT, "wb") as handle:
                handle.write(data)
            print("已下载:", page.get("title"), "->", OUT, len(data), "bytes")
            return True
    return False


def main():
    if not try_commons():
        print("没有拿到网络图片，改用本机截图")
    print("exists:", os.path.exists(OUT), os.path.getsize(OUT) if os.path.exists(OUT) else 0)


if __name__ == "__main__":
    main()
