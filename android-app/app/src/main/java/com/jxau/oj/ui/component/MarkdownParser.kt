package com.jxau.oj.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * 题面用的极简 Markdown 解析器（与 UI 无关）。
 *
 * 从 `MarkdownText.kt` 抽出来单独成文件，原因和 `SyntaxHighlighter.kt` 一样：
 * 它本来就只吃 `String`、吐数据（`List<MdBlock>` / `AnnotatedString`），
 * 抽出来后能进 `tools/pure_helpers_check` 的离线自检（U 组）。
 *
 * ## 支持的范围（按站点题干的**实测**用法定）
 *
 * 抽样 12 道线上题目的 `content.zh`（2026-09-14，只读 GET）后统计：
 * 标题只用 `##`(60) 与 `###`(24)；`*` 出现 164 处，**全部是长度为 2 的 `**`**；
 * 行内代码用反引号；公式用 `$...$`；**没有**引用块、链接、分隔线、`_强调_`。
 * 所以这里只实现这个子集 —— 不做完整 CommonMark。
 *
 * ## 行内解析**会改变文本长度**（这条注释曾经写错，已更正）
 *
 * `**粗**` 只输出 `粗`，`` `码` `` 只输出 `码`，`[文字](url)` 只输出 `文字` ——
 * **标记字符是被有意丢弃的**，这才是 markdown 渲染的目的。
 *
 * ⚠️ 别把它和编辑器那条「高亮只加样式、绝不改文本长度」搞混（那是 `SyntaxHighlighter.kt`
 * 的硬不变量，因为 `HighlightTransformation` 用的是 `OffsetMapping.Identity`）。
 * 这里的输出只喂给只读 `Text`，不参与任何光标偏移计算。
 * 旧注释写「刻意不改文本长度 —— 这样如果将来把它接到编辑器上，光标偏移不会错位」，
 * 与实现**正好相反**（U 组用例 `**输入**` → `输入` 就是反例）。
 *
 * ## 已知限制（**有意不修**，都有实测依据）
 *
 * - `***粗斜体***` → 输出 `*粗斜体*`（**首尾各漏一个 `*`**，不是只漏一个）：
 *   从下标 1 起配到 `**粗斜体**`，下标 0 与末位那两个 `*` 成了残料。
 *   站点 164 处 `*` 全是 `**`，没出现过三个连写，故不动；U 组已按此值锁定断言。
 * - `a * b * c` 会把中间的 ` b ` 当作斜体（未实现 CommonMark 的 flanking 规则）→ `a  b  c`。
 *   实测语料里没有裸 `*`（全部成对），动它反而有回归风险，故不动；U 组同样已锁定。
 * - `> a` 换行 `> b` 是**两个**引用块，不合并；缩进代码块（4 空格）不支持。
 */
internal sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class CodeBlock(val lang: String, val code: String) : MdBlock
    data class ListItem(
        val ordered: Boolean,
        val index: Int,
        val text: String,
        val depth: Int,
    ) : MdBlock

    data class Quote(val text: String) : MdBlock
    data object Divider : MdBlock
}

private val headingRegex = Regex("""^(#{1,6})\s+(.*)$""")
private val orderedItemRegex = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val bulletItemRegex = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val quoteRegex = Regex("""^>\s?(.*)$""")
private val dividerRegex = Regex("""^\s*([-*_])\s*\1\s*\1[\s\-*_]*$""")
private val codeFenceRegex = Regex("""^\s*```\s*([A-Za-z0-9+#._-]*)\s*$""")

internal fun parseBlocks(source: String): List<MdBlock> {
    val lines = source.replace("\r\n", "\n").split('\n')
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks += MdBlock.Paragraph(paragraph.toString().trim())
            paragraph.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        // 代码块：成对的反引号围栏。未闭合时按普通段落处理，避免整段消失
        val fence = codeFenceRegex.matchEntire(line)
        if (fence != null) {
            val lang = fence.groupValues[1]
            val body = mutableListOf<String>()
            var j = i + 1
            var closed = false
            while (j < lines.size) {
                if (lines[j].trimStart().startsWith("```")) {
                    closed = true
                    break
                }
                body += lines[j]
                j++
            }
            if (closed) {
                flushParagraph()
                blocks += MdBlock.CodeBlock(lang, body.joinToString("\n"))
                i = j + 1
                continue
            }
        }

        if (line.isBlank()) {
            flushParagraph()
            i++
            continue
        }

        // 下面几个分支刻意写成显式 if 而不是 `?.let { ... continue }`：
        // 在 inline lambda 里 break/continue 属于实验特性，写成 if 既合法又更直白。
        val heading = headingRegex.find(line)
        if (heading != null) {
            flushParagraph()
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        if (dividerRegex.matches(line)) {
            flushParagraph()
            blocks += MdBlock.Divider
            i++
            continue
        }

        val orderedItem = orderedItemRegex.find(line)
        if (orderedItem != null) {
            flushParagraph()
            blocks += MdBlock.ListItem(
                ordered = true,
                index = orderedItem.groupValues[2].toIntOrNull() ?: 1,
                text = orderedItem.groupValues[3].trim(),
                depth = orderedItem.groupValues[1].length / 2,
            )
            i++
            continue
        }

        val bulletItem = bulletItemRegex.find(line)
        if (bulletItem != null) {
            flushParagraph()
            blocks += MdBlock.ListItem(
                ordered = false,
                index = 0,
                text = bulletItem.groupValues[2].trim(),
                depth = bulletItem.groupValues[1].length / 2,
            )
            i++
            continue
        }

        val quote = quoteRegex.find(line)
        if (quote != null) {
            flushParagraph()
            blocks += MdBlock.Quote(quote.groupValues[1].trim())
            i++
            continue
        }

        paragraph.append(line).append('\n')
        i++
    }

    flushParagraph()
    return blocks
}

// ---------------------------------------------------------------- 行内解析

/**
 * 行内标记。⚠️ **会改变文本长度** —— 标记字符被有意丢弃，见文件头说明。
 *
 * 关于分支顺序（**旧注释写错了，这里更正**）：旧注释称「五个分支顺序敏感，
 * `**` 必须排在 `*` 之前，否则粗体永远匹配不到」。这是**错的**，顺序其实**可证无关**：
 * 五个分支的首字符互斥（`` ` `` / `*` / `[` / `$`），而唯一同以 `*` 开头的两支
 * 在**第二字符**上又互斥 —— `\*[^*]+\*` 要求 `text[p+1] != '*'`，
 * `\*\*[^*]+\*\*` 要求 `text[p+1] == '*'`。同一位置上不可能两支都成立，
 * 所以谁先谁后结果恒等。实测印证：把这两支对调（当作变异跑），U 组 412 项**全过**
 * —— 那是一次**空变异**，不是自检没牙。`***三连***` 也不受顺序影响：
 * 位置 0 两支都配不上，到位置 1 才由 `\*\*` 支命中。
 */
private val inlineRegex = Regex(
    """(`[^`]+`)""" + "|" +
        """(\*\*[^*]+\*\*)""" + "|" +
        """(\*[^*]+\*)""" + "|" +
        """(\[[^\]]*]\([^)]*\))""" + "|" +
        """(\$[^$\n]+\$)""",
)

internal fun inline(
    text: String,
    codeBackground: Color,
    codeForeground: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in inlineRegex.findAll(text)) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        val token = match.value
        when {
            token.startsWith("`") -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                    color = codeForeground,
                ),
            ) { append(token.trim('`')) }

            token.startsWith("**") -> withStyle(
                SpanStyle(fontWeight = FontWeight.Bold),
            ) { append(token.trim('*')) }

            token.startsWith("*") -> withStyle(
                SpanStyle(fontStyle = FontStyle.Italic),
            ) { append(token.trim('*')) }

            token.startsWith("[") -> {
                val label = token.substringAfter('[').substringBefore(']')
                withStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ) { append(label) }
            }

            // 公式占位：等宽 + 底色。真正排版需要 KaTeX，见 MarkdownText.kt 文件头说明
            token.startsWith("$") -> withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground,
                    color = codeForeground,
                ),
            ) { append(token.trim('$')) }

            // 到不了：每个分支产出的 token 都以 ` * [ $ 之一开头。留着仅为穷尽 when
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}
