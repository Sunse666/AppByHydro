@file:OptIn(ExperimentalTextApi::class)

package com.jxau.oj.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.jxau.oj.R

/**
 * 代码字体目录。
 *
 * 与主题目录（[THEME_CATALOG]）同一个套路：**把可选项集中成一份清单**，
 * 设置页直接遍历渲染，将来加字体不用动任何 UI 代码。
 *
 * 三条硬约束：
 * 1. **id 一旦发布不可改** —— 用户的偏好按 id 落盘，改名等于把选择丢掉；
 * 2. **只允许收录可合法再分发的字体**。所以这里是「系统字体族 + OFL 许可的打包字体」，
 *    绝不包含 Consolas / Courier New 这类微软专有字体；
 * 3. 打包字体必须给出 [credit]（署名 + 许可 + 随包声明文件名）——
 *    设置页「关于」段由清单拼出许可说明，**不再手写**，避免"加了字体忘了改文案"。
 */
data class EditorFont(
    val id: String,
    val label: String,
    val note: String,
    val family: FontFamily,
    /** 打包字体的授权署名；**系统字体留空串**（不随包分发，无需声明）。 */
    val credit: String = "",
)

/**
 * 可变字体（`wght` 轴）→ 按字重声明三档。
 *
 * ⚠️ `FontVariation.Settings(weight, style)` **自己就会派生 `wght` 轴**，
 * 再手写一个 `FontVariation.weight(x)` 会因"轴名重复"直接抛
 * `IllegalArgumentException: 'wght' must be unique`（静态初始化期，App 启动即崩，
 * 2026-09-14 MuMu 实测）。所以这里只传 weight，不传轴值。
 *
 * 为什么要拆三档：同一个可变 ttf 注册三次、各钉一个 weight，Compose 才能在
 * 语法高亮的 `FontWeight.Medium`（关键字）处拿到真字重；否则会被忽略或由系统伪粗，
 * 字形会糊（Cascadia 是 200–700 的连续轴，Medium 有真字形）。
 */
private fun variableFamily(resId: Int): FontFamily = FontFamily(
    Font(
        resId = resId,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontWeight.Normal, FontStyle.Normal),
    ),
    Font(
        resId = resId,
        weight = FontWeight.Medium,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontWeight.Medium, FontStyle.Normal),
    ),
    Font(
        resId = resId,
        weight = FontWeight.Bold,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontWeight.Bold, FontStyle.Normal),
    ),
)

/**
 * 打包字体：Cascadia Mono / Cascadia Code（© 2020 Microsoft Corporation，
 * 基于 SIL OFL 的 Microsoft 字体许可）。两者都是**可变字体**（wght 200–700，默认 400），
 * 且字形同源 —— 差别只在 Cascadia Code 带编程连字（`=>` `!=` `>=` 会连成一格）。
 *
 * 资源在 `res/font/cascadia_mono.ttf`、`res/font/cascadia_code.ttf`；
 * 来源与许可声明见 `res/raw/cascadia_mono_notice.txt`、`res/raw/cascadia_code_notice.txt`。
 */
private val CascadiaMono = variableFamily(R.font.cascadia_mono)
private val CascadiaCode = variableFamily(R.font.cascadia_code)

/**
 * 打包字体：JetBrains Mono / Fira Code / Source Code Pro（都是 SIL OFL 1.1）。
 *
 * 这三款是**静态字重**：官方发布的就是「每档一个 ttf」，没有 `fvar` 表 ——
 * 所以**不能**套 [variableFamily]（对静态字体设 variationSettings 毫无作用，
 * 只会让人误以为字重在生效）。按文件—字重一对一登记即可。
 *
 * 均已用 `tools/inspect_fonts.py` 核对：编程符号覆盖完整、无缺字，
 * `tools/inspect_font_tables.py` 确认无 `fvar`。
 */
private val JetBrainsMono = FontFamily(
    Font(resId = R.font.jetbrains_mono_regular, weight = FontWeight.Normal),
    Font(resId = R.font.jetbrains_mono_medium, weight = FontWeight.Medium),
    Font(resId = R.font.jetbrains_mono_bold, weight = FontWeight.Bold),
)
private val FiraCode = FontFamily(
    Font(resId = R.font.fira_code_regular, weight = FontWeight.Normal),
    Font(resId = R.font.fira_code_medium, weight = FontWeight.Medium),
    Font(resId = R.font.fira_code_bold, weight = FontWeight.Bold),
)
private val SourceCodePro = FontFamily(
    Font(resId = R.font.source_code_pro_regular, weight = FontWeight.Normal),
    Font(resId = R.font.source_code_pro_bold, weight = FontWeight.Bold),
)

/** 可选代码字体。**顺序即设置页显示顺序**。 */
val EDITOR_FONTS: List<EditorFont> = listOf(
    EditorFont(
        id = "system-mono",
        label = "系统等宽",
        note = "跟随系统自带等宽字体（Android 上通常是 Roboto Mono / Droid Sans Mono），不占安装包体积",
        family = FontFamily.Monospace,
    ),
    EditorFont(
        id = "cascadia-mono",
        label = "Cascadia Mono",
        note = "微软开源的编程字体，内置（可变字重 200–700），无连字，字形清瘦",
        family = CascadiaMono,
        credit = "Cascadia Mono — © 2020 Microsoft Corporation，" +
            "以基于 SIL OFL 的 Microsoft 字体许可分发（res/raw/cascadia_mono_notice.txt）",
    ),
    EditorFont(
        id = "cascadia-code",
        label = "Cascadia Code",
        note = "与 Cascadia Mono 同源，带编程连字：=> != >= 会连成一格，字面更紧凑",
        family = CascadiaCode,
        credit = "Cascadia Code — © 2020 Microsoft Corporation，" +
            "以基于 SIL OFL 的 Microsoft 字体许可分发（res/raw/cascadia_code_notice.txt）",
    ),
    EditorFont(
        id = "jetbrains-mono",
        label = "JetBrains Mono",
        note = "JetBrains 出品，字高偏大、易混字符（0O 1lI）区分明显；带连字（!= 会连成一格），三档字重",
        family = JetBrainsMono,
        credit = "JetBrains Mono — Copyright 2020 The JetBrains Mono Project Authors，" +
            "SIL Open Font License 1.1（OFL）（res/raw/jetbrains_mono_notice.txt）",
    ),
    EditorFont(
        id = "fira-code",
        label = "Fira Code",
        note = "以编程连字著称，符号与字母偏圆润；三档字重",
        family = FiraCode,
        credit = "Fira Code — Copyright 2014-2020 The Fira Code Project Authors，" +
            "SIL Open Font License 1.1（OFL）（res/raw/fira_code_notice.txt）",
    ),
    EditorFont(
        id = "source-code-pro",
        label = "Source Code Pro",
        note = "Adobe 出品的经典等宽字体，笔画朴素、字宽偏窄；常规与粗体两档",
        family = SourceCodePro,
        credit = "Source Code Pro — Copyright 2010, 2012 Adobe Systems Incorporated，" +
            "SIL Open Font License 1.1（OFL）（res/raw/source_code_pro_notice.txt）",
    ),
)

/** 打包字体（需随包携带许可声明的那几款）。设置页「关于」段用的就是它。 */
val BUNDLED_EDITOR_FONTS: List<EditorFont> = EDITOR_FONTS.filter { it.credit.isNotEmpty() }

/** 默认字体：系统等宽 —— 与历史行为一致，不改变老用户观感。 */
const val DEFAULT_EDITOR_FONT_ID = "system-mono"

/** 字号范围与默认值（编辑器与代码展示共用）。 */
const val MIN_EDITOR_FONT_SP = 10
const val MAX_EDITOR_FONT_SP = 22
const val DEFAULT_EDITOR_FONT_SP = 13

/** 字号归一：0（未设置）或越界值一律拉回合法区间。 */
fun normalizeEditorFontSp(sp: Int): Int =
    if (sp <= 0) DEFAULT_EDITOR_FONT_SP else sp.coerceIn(MIN_EDITOR_FONT_SP, MAX_EDITOR_FONT_SP)

/** 按 id 取字体，id 为空或已被移除时回落到默认字体（绝不抛异常）。 */
fun editorFontById(id: String?): EditorFont =
    EDITOR_FONTS.firstOrNull { it.id == id }
        ?: EDITOR_FONTS.first { it.id == DEFAULT_EDITOR_FONT_ID }

/**
 * 当前代码字体 id。用 CompositionLocal 下发，是为了让**只读代码块**
 * （评测详情 [com.jxau.oj.ui.editor.CodeView]、评测信息）不必层层传参
 * 也能与编辑器保持一致 —— 设置页在主壳之外，改成显式传参会污染整条导航链。
 */
val LocalEditorFontId = staticCompositionLocalOf { DEFAULT_EDITOR_FONT_ID }

/** 当前编辑器字号（sp）。与字体同源下发，保证编辑器与只读代码块一致。 */
val LocalEditorFontSizeSp = staticCompositionLocalOf { DEFAULT_EDITOR_FONT_SP }

/**
 * 设置页与编辑器共用的样张。
 *
 * 特意塞了三样能"一眼看出差别"的东西：① `!=`（连字字体里会连成一格，其余字体是两格）；
 * ② 中文（等宽字体没有汉字，看回落字形与西文是否协调）；③ 对齐的注释（看字宽是否真等宽）。
 */
const val EDITOR_FONT_SAMPLE = "if (a != b) return 0; // 中文注释"
