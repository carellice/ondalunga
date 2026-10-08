#!/usr/bin/env python3
"""Turns the launcher icon's vector drawables into docs/icon.svg (used by the README).

Run from the project root:  python3 tools/make_icon.py
"""
import os
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(__file__), "..")
DRAWABLE = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"

defs = []
shapes = []


def paint(color):
    """#AARRGGBB -> (css colour, opacity)."""
    color = color.lstrip("#")
    if len(color) == 8:
        return "#" + color[2:], int(color[:2], 16) / 255
    return "#" + color, 1.0


def gradient(node):
    gid = f"g{len(defs)}"
    stops = ""
    for item in node.findall("item"):
        css, opacity = paint(item.get(A + "color"))
        stops += f'<stop offset="{item.get(A + "offset")}" stop-color="{css}" stop-opacity="{opacity:.3f}"/>'
    if node.get(A + "type") == "radial":
        head = (f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{node.get(A + "centerX")}" '
                f'cy="{node.get(A + "centerY")}" r="{node.get(A + "gradientRadius")}">')
        defs.append(head + stops + "</radialGradient>")
    else:
        head = (f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{node.get(A + "startX")}" '
                f'y1="{node.get(A + "startY")}" x2="{node.get(A + "endX")}" y2="{node.get(A + "endY")}">')
        defs.append(head + stops + "</linearGradient>")
    return f"url(#{gid})"


def convert(name):
    for path in ET.parse(os.path.join(DRAWABLE, name)).getroot().findall("path"):
        attrs = {"fill": "none"}
        for kind, android_name in (("fill", "fillColor"), ("stroke", "strokeColor")):
            if path.get(A + android_name):
                attrs[kind], attrs[kind + "-opacity"] = paint(path.get(A + android_name))
        for nested in path.findall(AAPT + "attr"):
            kind = "fill" if nested.get("name") == "android:fillColor" else "stroke"
            attrs[kind] = gradient(nested.find("gradient"))
        for svg_name, android_name in (("stroke-width", "strokeWidth"), ("stroke-linecap", "strokeLineCap"),
                                       ("stroke-linejoin", "strokeLineJoin")):
            if path.get(A + android_name):
                attrs[svg_name] = path.get(A + android_name)
        rendered = " ".join(f'{k}="{v:.3f}"' if isinstance(v, float) else f'{k}="{v}"' for k, v in attrs.items())
        shapes.append(f'<path d="{path.get(A + "pathData")}" {rendered}/>')


convert("ic_launcher_background.xml")
convert("ic_launcher_foreground.xml")

# launchers show the central 72x72 of the 108x108 canvas, masked to a rounded shape
svg = (
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 72 72" width="256" height="256">'
    f'<defs>{"".join(defs)}<clipPath id="mask"><rect width="72" height="72" rx="17"/></clipPath></defs>'
    f'<g clip-path="url(#mask)"><g transform="translate(-18 -18)">{"".join(shapes)}</g></g></svg>\n'
)
os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
with open(os.path.join(ROOT, "docs", "icon.svg"), "w") as f:
    f.write(svg)
print("wrote docs/icon.svg")
