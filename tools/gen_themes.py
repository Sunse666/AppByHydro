#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""主题生成器 —— 从一份主题清单生成 Compose 主题文件 + 可视化预览页。

为什么要有生成器而不是手写色值
------------------------------
Material 3 的每套配色有 40+ 个角色（primary / onPrimary / primaryContainer /
surfaceContainerHigh / outlineVariant ...），色值之间由 HCT 色调算法约束，
手调几乎必然出现对比度不达标或角色错配。所以这里用 Material 官方算法
（material-color-utilities）从「一个种子色」推导出全部角色。

你要做的只有一件事：改 design/theme-seeds.json，然后重跑本脚本。

    python tools/gen_themes.py

产出（全部可直接编辑 / 直接引用）
--------------------------------
    design/theme-seeds.json      ← 输入：主题清单，你改这个
    design/theme-catalog.json    ← 输出：全部色值，供其他工具消费
    design/ThemeCatalog.kt       ← 输出：Compose 可直接用的主题目录
    design/theme-gallery.html    ← 输出：单文件预览页，可离线打开

依赖
----
    pip install material-color-utilities
"""

import argparse
import json
import os
import sys
from datetime import datetime, timezone

try:
    from material_color_utilities import Variant, argb_from_hex, theme_from_argb_color
except ImportError:
    sys.exit("缺少依赖，请先执行：pip install material-color-utilities")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DESIGN = os.path.join(ROOT, "design")

# Kotlin 产物直接落进 Android 工程源码目录，避免"改完忘了同步"。
# 色值清单与预览页留在 design/：它们是给人看的，不参与编译。
APP_THEME_DIR = os.path.join(
    ROOT, "android-app", "app", "src", "main", "java", "com", "jxau", "oj", "ui", "theme"
)

SEEDS_FILE = os.path.join(DESIGN, "theme-seeds.json")
CATALOG_FILE = os.path.join(DESIGN, "theme-catalog.json")
KOTLIN_FILE = os.path.join(APP_THEME_DIR, "ThemeCatalog.kt")
GALLERY_FILE = os.path.join(DESIGN, "theme-gallery.html")

# 首次运行时写出的默认清单。
# 选色原则：主色要避开判题状态色（AC 绿 / WA 红 / TLE 橙），
# 否则界面上「主色按钮」和「判题结果」会互相干扰语义。
DEFAULT_SEEDS = {
    "_readme": [
        "改这个文件就能加/改主题，然后重跑 python tools/gen_themes.py。",
        "id      主题标识，Kotlin 与本地存储用它，定了就别改",
        "label   界面显示名",
        "note    一句话说明，会在设置页与预览页显示",
        "seed    种子色 #RRGGBB。整套配色由它推导，改这一个值即可换色",
        "variant M3 配色变体：TONALSPOT(默认,最正统) / VIBRANT(高饱和) / ",
        "        EXPRESSIVE / NEUTRAL(低饱和) / MONOCHROME(纯灰阶) / FIDELITY / ",
        "        CONTENT / RAINBOW / FRUITSALAD",
        "contrast 对比度增强，0 = 标准；0.5 = 高对比（无障碍）",
        "mode     STATIC = 用这里推导的静态色板；FOLLOW_WALLPAPER = 跟随系统壁纸（Android 12+ 动态取色，忽略 seed）",
    ],
    "themes": [
        {"id": "jxau-blue", "label": "江农蓝", "note": "站点主色，默认主题", "seed": "#185FA5", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "lake-cyan", "label": "湖蓝", "note": "比江农蓝更青，清爽", "seed": "#0E7490", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "celadon", "label": "青瓷", "note": "青绿釉色，长时间看不累", "seed": "#0F6E56", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "bamboo", "label": "竹青", "note": "偏黄的绿，与 AC 绿拉开明度", "seed": "#3B6D11", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "amber", "label": "琥珀", "note": "暖橙黄，像台灯光", "seed": "#BA7517", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "coral", "label": "深珊瑚", "note": "暖橙红，醒目", "seed": "#D85A30", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "rosewood", "label": "玫红", "note": "偏深的玫色，沉稳", "seed": "#993556", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "sakura", "label": "樱粉", "note": "浅粉，明亮通透", "seed": "#D4537E", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "indigo", "label": "靛紫", "note": "夜间模式最舒服", "seed": "#534AB7", "variant": "TONALSPOT", "mode": "STATIC"},
        {"id": "violet-vivid", "label": "紫藤", "note": "高饱和，偏张扬", "seed": "#7C4DFF", "variant": "VIBRANT", "mode": "STATIC"},
        {"id": "graphite", "label": "石墨", "note": "低饱和灰蓝，最不打扰", "seed": "#44546A", "variant": "NEUTRAL", "mode": "STATIC"},
        {"id": "monochrome", "label": "单色", "note": "纯灰阶，黑白印刷质感", "seed": "#4A4A4A", "variant": "MONOCHROME", "mode": "STATIC"},
        {"id": "contrast-blue", "label": "高对比蓝", "note": "对比度增强，视力不佳者友好", "seed": "#185FA5", "variant": "TONALSPOT", "contrast": 0.5, "mode": "STATIC"},
        {"id": "follow-wallpaper", "label": "跟随壁纸", "note": "Android 12+ 从壁纸取色，最原生的观感", "seed": None, "mode": "FOLLOW_WALLPAPER"},
    ],
}

# Compose ColorScheme 参数名 -> material-color-utilities 的角色名。
# 生成全量角色是有意的：漏掉的角色会回落到 Compose 的基线紫，
# 那会让某几个控件突然变紫，很难排查。
ROLES = [
    ("primary", "primary"), ("onPrimary", "on_primary"),
    ("primaryContainer", "primary_container"), ("onPrimaryContainer", "on_primary_container"),
    ("inversePrimary", "inverse_primary"),
    ("secondary", "secondary"), ("onSecondary", "on_secondary"),
    ("secondaryContainer", "secondary_container"), ("onSecondaryContainer", "on_secondary_container"),
    ("tertiary", "tertiary"), ("onTertiary", "on_tertiary"),
    ("tertiaryContainer", "tertiary_container"), ("onTertiaryContainer", "on_tertiary_container"),
    ("background", "background"), ("onBackground", "on_surface"),
    ("surface", "surface"), ("onSurface", "on_surface"),
    ("surfaceVariant", "surface_variant"), ("onSurfaceVariant", "on_surface_variant"),
    ("surfaceTint", "surface_tint"),
    ("inverseSurface", "inverse_surface"), ("inverseOnSurface", "inverse_on_surface"),
    ("error", "error"), ("onError", "on_error"),
    ("errorContainer", "error_container"), ("onErrorContainer", "on_error_container"),
    ("outline", "outline"), ("outlineVariant", "outline_variant"), ("scrim", "scrim"),
    ("surfaceBright", "surface_bright"), ("surfaceDim", "surface_dim"),
    ("surfaceContainer", "surface_container"),
    ("surfaceContainerHigh", "surface_container_high"),
    ("surfaceContainerHighest", "surface_container_highest"),
    ("surfaceContainerLow", "surface_container_low"),
    ("surfaceContainerLowest", "surface_container_lowest"),
    ("primaryFixed", "primary_fixed"), ("primaryFixedDim", "primary_fixed_dim"),
    ("onPrimaryFixed", "on_primary_fixed"), ("onPrimaryFixedVariant", "on_primary_fixed_variant"),
    ("secondaryFixed", "secondary_fixed"), ("secondaryFixedDim", "secondary_fixed_dim"),
    ("onSecondaryFixed", "on_secondary_fixed"), ("onSecondaryFixedVariant", "on_secondary_fixed_variant"),
    ("tertiaryFixed", "tertiary_fixed"), ("tertiaryFixedDim", "tertiary_fixed_dim"),
    ("onTertiaryFixed", "on_tertiary_fixed"), ("onTertiaryFixedVariant", "on_tertiary_fixed_variant"),
]


def kotlin_ident(theme_id):
    parts = [p for p in theme_id.replace("_", "-").split("-") if p]
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:])


def build_scheme(seed, variant_name, contrast):
    variant = getattr(Variant, variant_name.upper().replace("_", ""), None)
    if variant is None:
        raise ValueError(f"未知 variant: {variant_name}（可用：TONALSPOT / VIBRANT / EXPRESSIVE / "
                         f"NEUTRAL / MONOCHROME / FIDELITY / CONTENT / RAINBOW / FRUITSALAD）")
    theme = theme_from_argb_color(argb_from_hex(seed), contrast_level=float(contrast), variant=variant)
    out = {}
    missing = []
    for name in ("light", "dark"):
        scheme = getattr(theme.schemes, name)
        row = {}
        for kt_name, mu_name in ROLES:
            val = getattr(scheme, mu_name, None)
            if val is None:
                missing.append(mu_name)
                continue
            row[kt_name] = (val if isinstance(val, str) else str(val)).upper()
        out[name] = row
    return out, sorted(set(missing))


def gen_catalog(seeds):
    themes, all_missing = [], set()
    for spec in seeds["themes"]:
        mode = spec.get("mode", "STATIC").upper()
        entry = {
            "id": spec["id"], "label": spec["label"], "note": spec.get("note", ""),
            "mode": mode,
            "seed": (spec.get("seed") or "").upper() or None,
            "variant": spec.get("variant", "TONALSPOT").upper(),
            "contrast": spec.get("contrast", 0),
        }
        if mode == "STATIC" and entry["seed"]:
            schemes, missing = build_scheme(entry["seed"], entry["variant"], entry["contrast"])
            entry["light"], entry["dark"] = schemes["light"], schemes["dark"]
            all_missing |= set(missing)
        else:
            entry["light"] = entry["dark"] = None
        themes.append(entry)
    return themes, sorted(all_missing)


def gen_kotlin(themes):
    L = []
    A = L.append
    A("// 本文件由 tools/gen_themes.py 生成，不要手改 —— 会被下次生成覆盖。")
    A("// 要增删主题请改 design/theme-seeds.json，然后重跑： python tools/gen_themes.py")
    A("//")
    A("// 生成时间：%s" % datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M %z"))
    A("")
    A("package com.jxau.oj.ui.theme")
    A("")
    A("import androidx.compose.material3.ColorScheme")
    A("import androidx.compose.material3.darkColorScheme")
    A("import androidx.compose.material3.lightColorScheme")
    A("import androidx.compose.ui.graphics.Color")
    A("")
    A("/** 主题取色方式。 */")
    A("enum class ThemeMode {")
    A("    /** Android 12+ 跟随系统壁纸（Material You）。低于 12 时回落到 [DEFAULT_THEME]。 */")
    A("    FOLLOW_WALLPAPER,")
    A("    /** 使用本文件中推导好的静态色板。 */")
    A("    STATIC,")
    A("}")
    A("")
    A("/**")
    A(" * 一个可选主题。")
    A(" *")
    A(" * 设置页直接用 [THEME_CATALOG] 渲染列表即可，新增主题不用改 UI 代码。")
    A(" */")
    A("data class AppTheme(")
    A("    val id: String,")
    A("    val label: String,")
    A("    val note: String,")
    A("    val mode: ThemeMode,")
    A("    /** 推导该主题色板的种子色，[ThemeMode.FOLLOW_WALLPAPER] 时为 null。 */")
    A("    val seed: String?,")
    A(")")
    A("")
    A("/** 默认主题（也是 Android 12 以下回落用的那一套）。 */")
    A("val DEFAULT_THEME_ID: String = \"%s\"" % themes[0]["id"])
    A("")
    A("val THEME_CATALOG: List<AppTheme> = listOf(")
    for t in themes:
        seed = ("\"%s\"" % t["seed"]) if t["seed"] else "null"
        A("    AppTheme(\"%s\", \"%s\", \"%s\", ThemeMode.%s, %s),"
          % (t["id"], t["label"], t["note"], t["mode"], seed))
    A(")")
    A("")
    for t in themes:
        if not t["light"]:
            continue
        ident = kotlin_ident(t["id"])
        for variant, scheme in (("light", t["light"]), ("dark", t["dark"])):
            fn = "lightColorScheme" if variant == "light" else "darkColorScheme"
            A("private val %s%s = %s(" % (variant, ident[:1].upper() + ident[1:], fn))
            for kt_name, _mu in ROLES:
                # lightColorScheme() / darkColorScheme() 的命名参数里**没有** Fixed 系列
                # （primaryFixed / onPrimaryFixed / ...），传进去会直接编译失败。
                # 这些角色保留 Material 默认值即可：常规 M3 组件不使用它们，
                # 而 ColorScheme.copy() 的可用性随版本浮动，不值得为 12 个角色冒险。
                if "Fixed" in kt_name:
                    continue
                if kt_name in scheme:
                    A("    %s = Color(0xFF%s)," % (kt_name, scheme[kt_name].lstrip("#")))
            A(")")
            A("")
    A("/** 静态色板查表。[ThemeMode.FOLLOW_WALLPAPER] 不在此表中，由调用方走动态取色。 */")
    A("private val STATIC_SCHEMES: Map<String, Pair<ColorScheme, ColorScheme>> = mapOf(")
    for t in themes:
        if not t["light"]:
            continue
        ident = kotlin_ident(t["id"])
        A("    \"%s\" to (light%s to dark%s)," % (t["id"], ident[:1].upper() + ident[1:], ident[:1].upper() + ident[1:]))
    A(")")
    A("")
    A("/**")
    A(" * 取某个主题的配色。返回 null 表示该主题要用动态取色（Android 12+）。")
    A(" *")
    A(" * 调用示例（见 ui/theme/AppTheme.kt）：")
    A(" * ```")
    A(" * val theme = THEME_CATALOG.firstOrNull { it.id == selectedId } ?: THEME_CATALOG.first()")
    A(" * val scheme = theme.staticScheme(dark = isSystemInDarkTheme())")
    A(" *     ?: if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)")
    A(" * ```")
    A(" */")
    A("fun AppTheme.staticScheme(dark: Boolean): ColorScheme? {")
    A("    val pair = STATIC_SCHEMES[id] ?: return null")
    A("    return if (dark) pair.second else pair.first")
    A("}")
    A("")
    return "\n".join(L)


def gen_gallery(themes):
    payload = json.dumps(themes, ensure_ascii=False)
    cards = []
    for t in themes:
        if t["light"]:
            swatch = "".join(
                f'<span style="display:inline-block;width:14px;height:14px;border-radius:4px;'
                f'background:{t["light"][r]};border:0.5px solid rgba(0,0,0,.12)"></span>'
                for r in ("primary", "secondary", "tertiary", "primaryContainer", "surfaceContainerHigh")
            )
            seed_txt = t["seed"]
            var_txt = t["variant"].title() + (f' · 对比度 {t["contrast"]}' if t["contrast"] else "")
        else:
            swatch = ""
            seed_txt = "跟随系统壁纸"
            var_txt = "Material You 动态取色"
        cards.append(
            f'<article class="card" data-id="{t["id"]}">'
            f'<header><h3>{t["label"]}</h3><p class="note">{t["note"]}</p></header>'
            f'<p class="meta"><code>{seed_txt}</code><span>{var_txt}</span></p>'
            f'<div class="swatches">{swatch}</div>'
            f'<div class="preview"></div>'
            f'<details><summary>全部色值 / Compose 代码</summary><div class="dump"></div></details>'
            f'</article>'
        )
    return GALLERY_TEMPLATE.replace("__THEMES__", payload).replace("__CARDS__", "\n".join(cards))


GALLERY_TEMPLATE = r'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>JXAU OJ · 主题目录预览</title>
<style>
:root{--bd:rgba(0,0,0,.12);--tx:#1a1a1a;--tx2:#5f5e5a;--bg:#fff;--bg2:#f4f4f2;--mono:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
*{box-sizing:border-box}
body{margin:0;padding:28px 22px 64px;background:var(--bg2);color:var(--tx);
 font:13px/1.6 system-ui,-apple-system,"Segoe UI","Microsoft YaHei",sans-serif}
h1{font-size:17px;font-weight:500;margin:0 0 4px}
.sub{color:var(--tx2);margin:0 0 20px;font-size:13px}
.bar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:22px}
button{font:inherit;padding:5px 13px;border:1px solid var(--bd);background:var(--bg);
 border-radius:8px;cursor:pointer;color:var(--tx)}
button[aria-pressed="true"]{background:var(--tx);color:var(--bg);border-color:var(--tx)}
.grid{display:grid;gap:16px;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));max-width:1280px}
.card{background:var(--bg);border:1px solid var(--bd);border-radius:12px;padding:16px 18px}
.card header{display:flex;justify-content:space-between;align-items:baseline;gap:10px}
.card h3{font-size:14px;font-weight:500;margin:0}
.note{color:var(--tx2);font-size:12px;margin:0;text-align:right}
.meta{display:flex;gap:8px;align-items:center;margin:8px 0 10px;font-size:12px;color:var(--tx2)}
.meta code{font-family:var(--mono);font-size:12px}
.meta span{border-left:1px solid var(--bd);padding-left:8px}
.swatches{display:flex;gap:5px;margin-bottom:12px}
.preview{display:grid;gap:10px}
.screen{border:1px solid var(--bd);border-radius:10px;overflow:hidden}
.slot{display:flex;flex-direction:column;gap:8px;padding:10px 12px 12px}
.appbar{display:flex;align-items:center;justify-content:space-between;padding:9px 12px;font-weight:500}
.row{display:flex;gap:6px;flex-wrap:wrap;align-items:center}
.pill{padding:3px 10px;border-radius:999px;font-size:12px}
.btn{padding:7px 15px;border-radius:999px;font-size:12px;width:max-content}
.fld{border-radius:8px;padding:9px 12px;font-size:12px;font-family:var(--mono)}
.tag{font-size:11px;color:var(--tx2);letter-spacing:.4px}
details{margin-top:12px;border-top:1px solid var(--bd);padding-top:10px}
summary{cursor:pointer;font-size:12px;color:var(--tx2)}
.dump{margin-top:10px}
table{border-collapse:collapse;width:100%;font-size:12px}
td{font-family:var(--mono);padding:2px 6px;border-bottom:1px solid var(--bd);white-space:nowrap}
td:first-child{color:var(--tx2);font-family:inherit}
pre{font-family:var(--mono);font-size:11.5px;background:var(--bg2);border-radius:8px;
 padding:10px;overflow:auto;max-height:260px;margin:10px 0 0}
</style>
</head>
<body>
<h1>JXAU OJ · 主题目录预览</h1>
<p class="sub">由 <code>tools/gen_themes.py</code> 从 <code>design/theme-seeds.json</code> 生成。
色值全部来自 Material 官方配色算法，不是手调近似值。改清单后重跑脚本即可更新本页。</p>
<div class="bar">
  <span class="tag">显示</span>
  <button data-show="both" aria-pressed="true">浅色 + 深色</button>
  <button data-show="light" aria-pressed="false">仅浅色</button>
  <button data-show="dark" aria-pressed="false">仅深色</button>
</div>
<div class="grid" id="grid">
__CARDS__
</div>
<script>
const THEMES = __THEMES__;
const ROLES = ["primary","onPrimary","primaryContainer","onPrimaryContainer",
  "secondary","secondaryContainer","tertiary","tertiaryContainer",
  "error","errorContainer","surface","onSurface","surfaceVariant","onSurfaceVariant",
  "outline","outlineVariant","surfaceContainerLow","surfaceContainer",
  "surfaceContainerHigh","surfaceContainerHighest","inverseSurface","inverseOnSurface"];

function screen(s, dark, label) {
  const title = dark ? "深色" : "浅色";
  return `<div class="screen">
    <div class="appbar" style="background:${s.primaryContainer};color:${s.onPrimaryContainer}">
      <span>${title} · 题库</span><span>⌕</span>
    </div>
    <div class="slot" style="background:${s.surface};color:${s.onSurface}">
      <div class="row">
        <span class="pill" style="background:${s.secondaryContainer};color:${s.onSecondaryContainer}">已通过</span>
        <span class="pill" style="background:${s.tertiaryContainer};color:${s.onTertiaryContainer}">模拟</span>
        <span class="pill" style="background:${s.errorContainer};color:${s.onErrorContainer}">答案错误</span>
      </div>
      <div class="fld" style="background:${s.surfaceContainerHigh};color:${s.onSurface}">int main() { … }</div>
      <div class="btn" style="background:${s.primary};color:${s.onPrimary}">提交代码</div>
      <div style="border-top:1px solid ${s.outlineVariant};padding-top:8px;
        color:${s.onSurfaceVariant};font-size:12px">第 1 页 · 共 183 题</div>
    </div>
  </div>`;
}

function render(t, show) {
  const host = document.querySelector(`.card[data-id="${t.id}"] .preview`);
  const dump = document.querySelector(`.card[data-id="${t.id}"] .dump`);
  if (t.light) {
    const parts = [];
    if (show !== "dark") parts.push(screen(t.light, false));
    if (show !== "light") parts.push(screen(t.dark, true));
    host.innerHTML = parts.join("");
    if (!dump.dataset.done) {
      const rows = ROLES.map(r => `<tr><td>${r}</td><td>${t.light[r]}</td><td>${t.dark[r]}</td></tr>`).join("");
      const kt = `lightColorScheme(\n` + ROLES.filter(r => !r.includes("Fixed")).map(r => `    ${r} = Color(0xFF${t.light[r].slice(1)}),`).join("\n") + `\n)`;
      dump.innerHTML = `<table><tr><td></td><td>light</td><td>dark</td></tr>${rows}</table><pre>${kt}</pre>`;
      dump.dataset.done = "1";
    }
  } else {
    host.innerHTML = `<div class="screen"><div class="slot" style="background:${'#f4f4f2'};color:#5f5e5a">
      <div>此主题不产生静态色板：Android 12+ 跟随系统壁纸取色，12 以下回落到默认主题。</div></div></div>`;
    dump.innerHTML = "";
  }
}

function apply(show) {
  THEMES.forEach(t => render(t, show));
  document.querySelectorAll("[data-show]").forEach(b =>
    b.setAttribute("aria-pressed", String(b.dataset.show === show)));
}
document.querySelectorAll("[data-show]").forEach(b =>
  b.addEventListener("click", () => apply(b.dataset.show)));
apply("both");
</script>
</body>
</html>
'''


def main():
    ap = argparse.ArgumentParser(description="从主题清单生成 Compose 主题文件与预览页")
    ap.add_argument("--seeds", default=SEEDS_FILE)
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    os.makedirs(DESIGN, exist_ok=True)
    os.makedirs(APP_THEME_DIR, exist_ok=True)
    if not os.path.exists(args.seeds):
        with open(args.seeds, "w", encoding="utf-8") as f:
            json.dump(DEFAULT_SEEDS, f, ensure_ascii=False, indent=2)
        print(f"已写出默认清单：{args.seeds}")

    with open(args.seeds, encoding="utf-8") as f:
        seeds = json.load(f)

    themes, missing = gen_catalog(seeds)
    if missing and not args.quiet:
        print(f"注意：以下角色在本库中不存在，已跳过：{missing}")

    with open(CATALOG_FILE, "w", encoding="utf-8") as f:
        json.dump({"generated_at": datetime.now(timezone.utc).astimezone().isoformat(),
                   "generator": "tools/gen_themes.py",
                   "source": os.path.basename(args.seeds),
                   "roles": [r[0] for r in ROLES],
                   "themes": themes}, f, ensure_ascii=False, indent=2)
    with open(KOTLIN_FILE, "w", encoding="utf-8") as f:
        f.write(gen_kotlin(themes))
    with open(GALLERY_FILE, "w", encoding="utf-8") as f:
        f.write(gen_gallery(themes))

    static = [t for t in themes if t["light"]]
    print(f"共 {len(themes)} 个主题（{len(static)} 套静态色板 + "
          f"{len(themes) - len(static)} 个动态取色），每个 {len(ROLES)} 个角色")
    for p in (CATALOG_FILE, KOTLIN_FILE, GALLERY_FILE):
        print(f"  {os.path.relpath(p, ROOT)}  ({os.path.getsize(p):,} bytes)")


if __name__ == "__main__":
    main()
