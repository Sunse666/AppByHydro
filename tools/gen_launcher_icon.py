#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 android-app/icon.ico 生成 Android 自适应图标（前景层 / 单色层 / 底色）。

设计稿只有 48x48 一张图（青色图形 #26C1D8 压在 #F0F0F0 白底上），
直接当启动图标会被拉伸糊掉。这里做三件事：

1. **把"图形"和"底色"拆开** —— 自适应图标要求前景层是带 alpha 的图形，
   底色由 background 层提供。原图是不透明白底，直接当前景会让单色层
   变成一整块实心方块（单色层只看 alpha）。
   拆分方法：把每个像素当作 white 与 cyan 的线性混合，用最小二乘解出
   cyan 的覆盖度 t，令 alpha = t、RGB = cyan。这样前景层叠在 #F0F0F0 上
   能逐像素还原原图（脚本末尾会断言这一点）。
2. **放大用 NEAREST** —— 48 -> 288 是整数 6 倍，像素画必须最近邻，
   否则边缘会糊。
3. **只产 xxxhdpi 主图再逐级缩小** —— 4 倍缩到 108、2 倍缩到 216，
   用 BOX（面积平均）降采样，避免各级之间出现不一致的毛边。

输出（108dp 画布，图形占中间 72dp 的可见区）：
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_monochrome.png

用法： python tools/gen_launcher_icon.py
"""

import io
import os
import sys

from PIL import Image

ROOT = "D:/IO/HTML/Hydro"
SRC = os.path.join(ROOT, "android-app/icon.ico")
RES = os.path.join(ROOT, "android-app/app/src/main/res")

# 自适应图标：画布 108dp。图形按 60dp 放，四周留 24dp。
# 留这么多是因为设计稿本身几乎没有留白（图形占满 48x48 画布的 40x40），
# 照 72dp 满铺会让顶栏两端正好落在圆形遮罩的切角上；60dp 时图形对角线
# 刚好贴着 66dp 安全圆，圆形 / 方圆 / 方形三种遮罩下都不缺角。
# ⚠️ 取 60 还有个硬原因：60dp × 4(xxxhdpi) = 240px = 48 × 5，是整数倍，
# 放大能用 NEAREST；换 72dp 也得凑成整数倍，60 是能满足的最小好看值。
CANVAS_DP = 108
VISIBLE_DP = 60
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}


def estimate_layers(img):
    """把 (白底 + 青图形) 的图拆成 (前景 RGBA, 底色 RGB)。

    逐像素假设 p = (1-t)*BG + t*FG，最小二乘解 t。
    BG 取图像四角的中位数（即真正的大面积底色），FG 取最饱和的那种颜色。
    """
    px = list(img.getdata())
    w, h = img.size

    # 底色：取四个角的 3x3 均值
    corners = []
    for cx, cy in ((0, 0), (w - 3, 0), (0, h - 3), (w - 3, h - 3)):
        for dy in range(3):
            for dx in range(3):
                corners.append(img.getpixel((cx + dx, cy + dy))[:3])
    bg = tuple(sorted(c[i] for c in corners)[len(corners) // 2] for i in range(3))

    # 前景色：离底色最远的那个像素（饱和色），并取同色像素的均值稳住它
    far = max(px, key=lambda p: sum((a - b) ** 2 for a, b in zip(p[:3], bg)))
    fg = far[:3]
    same = [p[:3] for p in px if sum((a - b) ** 2 for a, b in zip(p[:3], fg)) <= 3]
    fg = tuple(round(sum(c[i] for c in same) / len(same)) for i in range(3))

    d = [f - b for f, b in zip(fg, bg)]
    dd = sum(v * v for v in d)

    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            p = img.getpixel((x, y))[:3]
            t = sum((p[i] - bg[i]) * d[i] for i in range(3)) / dd
            t = 0.0 if t < 0 else (1.0 if t > 1 else t)
            out.putpixel((x, y), (fg[0], fg[1], fg[2], round(t * 255)))
    return out, bg, fg


def build_master(fg_rgba, dp_scale):
    """把源图放到 108dp 画布的中间 72dp 区域，返回该密度下的前景位图。"""
    side = round(CANVAS_DP * dp_scale)
    visible = round(VISIBLE_DP * dp_scale)
    src_side = fg_rgba.size[0]
    if visible % src_side:
        raise SystemExit(
            "可见区边长 %d 不是源图 %d 的整数倍 —— 像素画会糊，"
            "请改 VISIBLE_DP 或换更大的源图" % (visible, src_side))
    up = fg_rgba.resize((visible, visible), Image.NEAREST)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    off = (side - visible) // 2
    canvas.paste(up, (off, off))
    return canvas


def write_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    with open(path, "wb") as f:
        f.write(buf.getvalue())
    return len(buf.getvalue())


def main():
    src = Image.open(SRC).convert("RGBA")
    if src.size[0] != src.size[1]:
        raise SystemExit("源图必须是正方形，实际 %s" % (src.size,))
    fg_rgba, bg, fg = estimate_layers(src)
    print("源图 %dx%d  底色 rgb%s  图形 rgb%s" % (src.size[0], src.size[1], bg, fg))

    # ---- 自检：把前景层叠回底色，必须与源图逐像素一致（容差 2） ----
    worst = 0
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = fg_rgba.getpixel((x, y))
            t = a / 255.0
            comp = tuple(round(t * c + (1 - t) * bgc) for c, bgc in zip((r, g, b), bg))
            orig = src.getpixel((x, y))[:3]
            worst = max(worst, max(abs(c - o) for c, o in zip(comp, orig)))
    print("还原自检：最大通道偏差 %d（应 <= 2）" % worst)
    if worst > 2:
        raise SystemExit("前景/底色拆分不准，别拿它当图标")

    # ---- 主图（xxxhdpi = 4x），其余密度由它降采样 ----
    master_fg = build_master(fg_rgba, 4.0)
    master_mono = Image.new("RGBA", master_fg.size, (0, 0, 0, 0))
    master_mono.putalpha(master_fg.getchannel("A"))
    master_mono.paste((0, 0, 0), (0, 0), master_fg.getchannel("A"))

    total = 0
    for name, dp_scale in DENSITIES.items():
        side = round(CANVAS_DP * dp_scale)
        fg_out = master_fg if side == master_fg.size[0] else master_fg.resize(
            (side, side), Image.BOX)
        mono_out = master_mono if side == master_mono.size[0] else master_mono.resize(
            (side, side), Image.BOX)
        n1 = write_png(fg_out, os.path.join(RES, "mipmap-%s" % name,
                                            "ic_launcher_foreground.png"))
        n2 = write_png(mono_out, os.path.join(RES, "mipmap-%s" % name,
                                              "ic_launcher_monochrome.png"))
        total += n1 + n2
        print("  mipmap-%-7s %3dx%-3d  foreground %5dB  monochrome %5dB"
              % (name, side, side, n1, n2))
    print("合计 %d 字节" % total)
    print("底色请写到 values/colors.xml: <color name=\"ic_launcher_background\">#%02X%02X%02X</color>"
          % bg)
    return 0


if __name__ == "__main__":
    sys.exit(main())
