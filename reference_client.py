#!/usr/bin/env python3
"""智谱 GLM Coding Plan 5 小时限额查询 — 参考实现（Mac 菜单栏插件 / Android 小组件共用逻辑）"""
import json, sys, urllib.request, datetime, zoneinfo

API = "https://open.bigmodel.cn/api/monitor/usage/quota/limit"
TZ = zoneinfo.ZoneInfo("Asia/Shanghai")

def query(key: str) -> dict:
    req = urllib.request.Request(API, headers={
        "Authorization": f"Bearer {key}",
        "Accept": "application/json",
    })
    with urllib.request.urlopen(req, timeout=10) as r:
        d = json.loads(r.read())
    if not d.get("success"):
        raise RuntimeError(d.get("msg", "查询失败"))
    data = d["data"]
    out = {"level": data.get("level", "unknown"), "windows": []}
    now = datetime.datetime.now(datetime.timezone.utc).timestamp() * 1000
    for lim in data.get("limits", []):
        unit, number = lim.get("unit"), lim.get("number", 1)
        # unit=3+number=5 → 5 小时窗口; unit=6 → 周
        label = {3: f"{number}小时", 6: "每周"}.get(unit, f"unit{unit}x{number}")
        reset_h = max(0.0, (lim.get("nextResetTime", 0) - now) / 3_600_000)
        out["windows"].append({
            "label": label,
            "used_pct": lim.get("percentage", 0),
            "remaining_pct": 100 - lim.get("percentage", 0),
            "reset_hours": round(reset_h, 1),
        })
    return out

if __name__ == "__main__":
    key = sys.argv[1] if len(sys.argv) > 1 else sys.stdin.read().strip()
    w = query(key)
    five = next((x for x in w["windows"] if "小时" in x["label"]), w["windows"][0])
    print(f"{five['used_pct']}%")           # 菜单栏主显示
    for x in w["windows"]:
        print(f"{x['label']}: 已用 {x['used_pct']}% | 剩 {x['remaining_pct']}% | {x['reset_hours']}h 后重置")
    print(f"套餐档位: {w['level']}")
