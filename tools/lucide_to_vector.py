#!/usr/bin/env python3
"""Конвертирует SVG-иконки Lucide (ISC) в Android VectorDrawable.

Использование:
    python3 tools/lucide_to_vector.py <папка lucide-static/icons> <res/drawable> имя1 имя2 ...

Иконки получают имя ic_<имя с подчёркиваниями>.xml. Цвет обводки белый —
в Compose он перекрашивается через tint.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"


def f(v):
    return float(v)


def fmt(x):
    s = ("%.3f" % x).rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def circle(cx, cy, r):
    return (f"M{fmt(cx - r)},{fmt(cy)}a{fmt(r)},{fmt(r)} 0 1,0 {fmt(2 * r)},0"
            f"a{fmt(r)},{fmt(r)} 0 1,0 {fmt(-2 * r)},0z")


def ellipse(cx, cy, rx, ry):
    return (f"M{fmt(cx - rx)},{fmt(cy)}a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(2 * rx)},0"
            f"a{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(-2 * rx)},0z")


def rect(x, y, w, h, rx, ry):
    if rx == 0 and ry == 0:
        return f"M{fmt(x)},{fmt(y)}h{fmt(w)}v{fmt(h)}h{fmt(-w)}z"
    rx = min(rx, w / 2)
    ry = min(ry, h / 2)
    return (f"M{fmt(x + rx)},{fmt(y)}h{fmt(w - 2 * rx)}"
            f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(rx)},{fmt(ry)}v{fmt(h - 2 * ry)}"
            f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(-rx)},{fmt(ry)}h{fmt(-(w - 2 * rx))}"
            f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(-rx)},{fmt(-ry)}v{fmt(-(h - 2 * ry))}"
            f"a{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(rx)},{fmt(-ry)}z")


def points(p, close):
    nums = [float(n) for n in re.findall(r"-?\d*\.?\d+(?:e-?\d+)?", p)]
    pts = list(zip(nums[0::2], nums[1::2]))
    d = "M" + " L".join(f"{fmt(a)},{fmt(b)}" for a, b in pts)
    return d + ("z" if close else "")


NUM = re.compile(r"-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARGS = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}


def normalize(d):
    """Переписывает path с явными разделителями (в т.ч. флаги дуг: «0 01» → «0 0 1»)."""
    out = []
    i = 0
    cmd = None
    while i < len(d):
        ch = d[i]
        if ch.isalpha():
            cmd = ch
            out.append(ch)
            i += 1
            if cmd in "zZ":
                continue
            argi = 0
            continue
        if ch in " ,\t\n":
            i += 1
            continue
        n = ARGS[cmd.lower()]
        if cmd.lower() == "a" and argi % 7 in (3, 4):
            out.append(ch)
            i += 1
        else:
            m = NUM.match(d, i)
            out.append(m.group(0))
            i = m.end()
        argi += 1
        out.append(" ")
    return "".join(out).replace(" \n", "").strip()


def convert(svg_path):
    root = ET.parse(svg_path).getroot()
    paths = []
    for el in root.iter():
        tag = el.tag.replace(NS, "")
        a = el.attrib
        if tag == "path":
            paths.append(normalize(a["d"]))
        elif tag == "circle":
            paths.append(circle(f(a["cx"]), f(a["cy"]), f(a["r"])))
        elif tag == "ellipse":
            paths.append(ellipse(f(a["cx"]), f(a["cy"]), f(a["rx"]), f(a["ry"])))
        elif tag == "rect":
            rx = f(a.get("rx", a.get("ry", "0")))
            ry = f(a.get("ry", a.get("rx", "0")))
            paths.append(rect(f(a.get("x", "0")), f(a.get("y", "0")), f(a["width"]), f(a["height"]), rx, ry))
        elif tag == "line":
            paths.append(f"M{a['x1']},{a['y1']}L{a['x2']},{a['y2']}")
        elif tag == "polyline":
            paths.append(points(a["points"], False))
        elif tag == "polygon":
            paths.append(points(a["points"], True))
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Lucide Icons (ISC), сконвертировано tools/lucide_to_vector.py -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="24dp"', '    android:height="24dp"',
           '    android:viewportWidth="24"', '    android:viewportHeight="24">']
    for d in paths:
        out.append('    <path')
        out.append(f'        android:pathData="{d}"')
        out.append('        android:strokeColor="#FFFFFFFF"')
        out.append('        android:strokeWidth="2"')
        out.append('        android:strokeLineCap="round"')
        out.append('        android:strokeLineJoin="round" />')
    out.append('</vector>')
    return "\n".join(out) + "\n"


def main():
    src, dst, names = sys.argv[1], sys.argv[2], sys.argv[3:]
    for n in names:
        xml = convert(os.path.join(src, n + ".svg"))
        with open(os.path.join(dst, "ic_" + n.replace("-", "_") + ".xml"), "w", encoding="utf-8") as fh:
            fh.write(xml)
    print(f"Готово: {len(names)} иконок")


if __name__ == "__main__":
    main()
