# -*- coding: utf-8 -*-
"""生成 res/raw/ 下的字体许可声明（幂等：重复运行结果一致）。

合规依据：OFL 1.1 第 2 条要求「字体副本必须携带版权声明与本许可」。
因此每份声明 = 该字体自身的版权行（name 表 ID 0，逐字）+ OFL 1.1 正文
（逐字取自 OFL 官方文本副本 D:/tools/byteTable/src-tauri/assets/fonts/OFL.txt）。

Cascadia Code 是「基于 OFL 的 Microsoft 字体许可」，正文取自其 name 表 ID 13
（字体自带的官方声明），与 cascadia_mono 的处理方式一致。

用法：python tools/gen_font_notices.py
"""
import os
import struct
import sys

ROOT = "D:/IO/HTML/Hydro/"
FONT_DIR = ROOT + "android-app/app/src/main/res/font/"
RAW_DIR = ROOT + "android-app/app/src/main/res/raw/"
OFL_SRC = "D:/tools/byteTable/src-tauri/assets/fonts/OFL.txt"
MARKER = "SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"
RULE = "-" * 70

# ---------------------------------------------------------------- TTF name


def best_name(path, nid):
    d = open(path, 'rb').read()
    n = struct.unpack(">H", d[4:6])[0]
    off = None
    for i in range(n):
        r = 12 + 16 * i
        if d[r:r + 4] == b'name':
            off = struct.unpack(">II", d[r + 8:r + 16])
            break
    if off is None:
        raise SystemExit("no name table: " + path)
    o, _ = off
    _, count, strOff = struct.unpack(">HHH", d[o:o + 6])
    hits = []
    for i in range(count):
        r = o + 6 + 12 * i
        pid, eid, lid, cur, ln, so = struct.unpack(">HHHHHH", d[r:r + 12])
        if cur != nid:
            continue
        raw = d[o + strOff + so:o + strOff + so + ln]
        try:
            s = raw.decode('utf-16-be') if pid in (0, 3) else raw.decode('latin-1')
        except Exception:
            s = raw.decode('utf-16-be', 'replace')
        hits.append((pid, eid, lid, s))
    if not hits:
        raise SystemExit("no name id%d: %s" % (nid, path))
    for pid, eid, lid, s in hits:
        if pid == 3 and eid == 1 and lid == 0x409:
            return s
    return hits[0][3]


def ofl_body():
    s = open(OFL_SRC, encoding='utf-8').read()
    i = s.index(MARKER)
    body = s[i:]
    if not body.endswith("\n"):
        body += "\n"
    return body


# ---------------------------------------------------------------- 声明模板

HEAD = """代码字体许可声明
================

字体名称：{label}
字体文件：{files}
文件版本：{version}
版权归属：{copyright}
设计者：{design}
字重配置：{weights}
许可协议：SIL Open Font License 1.1（见下方全文）
许可网址：{license_url}
官方来源：{home}

本应用将上述字体文件原样打包，未做任何字形修改（未子集化、未改名、未重排、
未调整任何 OpenType 特性）。

以下版权行逐字取自该字体 name 表 ID 0（Copyright）；许可正文逐字取自 OFL 1.1
官方文本（未改写，仅将文本开头的版权行替换为本字体自己的版权行）。按 OFL 第 2
条要求，字体副本须携带版权声明与本许可，故此处原样保留全文。该字体 name 表
ID 13（License Description）原文为下面这段（原文只有一行，此处按 62 列折行，
仅为便于阅读）：

    {id13}

{rule}

{body}"""

CASCADIA_HEAD = """代码字体许可声明
================

字体名称：{label}
字体文件：{files}
文件版本：{version}
版权归属：{copyright}
商标：Cascadia Code is a trademark of the Microsoft group of companies.
制造商：Saja Typeworks ／ 设计者：Aaron Bell
字重配置：{weights}
许可协议：基于 SIL Open Font License 的 Microsoft 字体许可（见下方全文）
许可网址：{license_url}
官方来源：{home}

本应用将上述字体文件原样打包，未做任何字形修改（未子集化、未改名、未重排）。

以下许可正文逐字取自该字体 name 表 ID 13（License Description）——
即字体自带的官方声明，非另行摘抄。按该许可的要求，字体副本须携带本声明，
故此处原样保留全文：

{rule}

{body}"""


def wrap(text, width=62, indent="    "):
    """把 id13 那行折成便于阅读的多行（贪心装填，只影响可读性，不改语义）。"""
    words = text.split()
    out, buf = [], ""
    for w in words:
        if not buf:
            buf = w
        elif len(indent) + len(buf) + 1 + len(w) <= width:
            buf += " " + w
        else:
            out.append(indent + buf)
            buf = w
    if buf:
        out.append(indent + buf)
    return "\n".join(out)


FONTS = [
    dict(
        out="jetbrains_mono_notice.txt",
        label="JetBrains Mono",
        files=["jetbrains_mono_regular.ttf", "jetbrains_mono_medium.ttf",
               "jetbrains_mono_bold.ttf"],
        weights="Regular 400 / Medium 500 / Bold 700（三个独立静态文件，非可变字体）",
        home="https://github.com/JetBrains/JetBrainsMono",
        design="Philipp Nurullin ／ 为「减少代码阅读疲劳」而设计，字高较大、"
               "标点与易混字符（0O 1l Ii）区分明确",
        style="ofl",
    ),
    dict(
        out="fira_code_notice.txt",
        label="Fira Code",
        files=["fira_code_regular.ttf", "fira_code_medium.ttf",
               "fira_code_bold.ttf"],
        weights="Regular 400 / Medium 500 / Bold 700（三个独立静态文件，非可变字体）",
        home="https://github.com/tonsky/FiraCode",
        design="Nikita Prokopov（基于 Fira Mono，Erik Spiekermann 原作）"
               "／ 以编程连字（ligature）闻名",
        style="ofl",
    ),
    dict(
        out="source_code_pro_notice.txt",
        label="Source Code Pro",
        files=["source_code_pro_regular.ttf", "source_code_pro_bold.ttf"],
        weights="Regular 400 / Bold 700（两个独立静态文件，非可变字体）",
        home="https://github.com/adobe-fonts/source-code-pro",
        design="Paul D. Hunt ／ Adobe 出品的等宽屏显字体",
        style="ofl",
    ),
    dict(
        out="cascadia_code_notice.txt",
        label="Cascadia Code",
        files=["cascadia_code.ttf"],
        weights="Regular 400（静态文件；本应用另单独内置其非连字版本 Cascadia Mono）",
        home="https://github.com/microsoft/cascadia-code",
        design="Saja Typeworks ／ 设计者：Aaron Bell",
        style="cascadia",
    ),
]


def main():
    ofl = ofl_body()
    for f in FONTS:
        primary = FONT_DIR + f["files"][0]
        copyright_line = best_name(primary, 0)
        version = best_name(primary, 5)
        id13 = best_name(primary, 13)
        lic_url = best_name(primary, 14) or "https://scripts.sil.org/OFL"

        files_desc = "\n          ".join(
            "app/src/main/res/font/" + x for x in f["files"])
        if f["style"] == "cascadia":
            text = CASCADIA_HEAD.format(
                label=f["label"], files=files_desc, version=version,
                copyright=copyright_line, weights=f["weights"],
                license_url=lic_url, home=f["home"],
                rule=RULE, body=id13.strip() + "\n")
        else:
            text = HEAD.format(
                label=f["label"], files=files_desc, version=version,
                copyright=copyright_line, design=f["design"],
                weights=f["weights"], license_url=lic_url, home=f["home"],
                id13=wrap(id13), rule=RULE, body=ofl)
        path = RAW_DIR + f["out"]
        data = text.encode('utf-8')
        with open(path, 'wb') as fh:
            fh.write(data)
        print("wrote %-40s %6d bytes  (font %s)" % (f["out"], len(data), version))
    return 0


if __name__ == '__main__':
    sys.exit(main())
