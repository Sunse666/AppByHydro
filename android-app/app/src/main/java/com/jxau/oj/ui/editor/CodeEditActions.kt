package com.jxau.oj.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 编辑动作：自动补全括号 / 自动缩进 / 成对删除 / 选区包裹。
 *
 * **纯函数、零 UI 依赖**，输入「变更前」「变更后」两个 TextFieldValue，
 * 输出应当回写进输入框的值。
 *
 * 为什么用**差异分析**而不是自己拦截按键：软键盘（IME）根本不产生按键事件 ——
 * 只有 `onValueChange` 是软键盘和实体键盘都会经过的地方。手机上做 IDE 功能，
 * 这条是硬约束（见 `docs/JXAU-OJ-App-功能方案.md` 的编辑器分层说明）。
 *
 * 刻意不做的事：
 * - 不做完整语法分析（字符串/注释只按**当前行**简单扫描，够用且不会误判整篇）；
 * - 不动 Tab 字符展开（高亮与光标偏移的既定约束，见 `ComposeCodeEditor` 文件头）；
 * - 粘贴、多字符替换、非折叠选区的拖动一律原样放行 —— 那些是用户明确意图。
 */
object CodeEditActions {

    /**
     * 缩进单位：**4 个空格**。
     *
     * 刻意不用 Tab 字符：Tab 的显示宽度取决于阅读环境，代码贴到别处会歪。
     */
    const val INDENT = "    "

    private val PAIRS = mapOf(
        '(' to ')',
        '[' to ']',
        '{' to '}',
        '"' to '"',
        '\'' to '\'',
    )
    private val CLOSERS: Set<Char> = PAIRS.values.toSet()

    /**
     * 应用编辑动作。返回**应当写回输入框**的值（可能就是 [new] 本身）。
     *
     * [old] 是本次变更前输入框持有的值，[new] 是系统给出的变更后值。
     */
    fun apply(old: TextFieldValue, new: TextFieldValue, indent: String = INDENT): TextFieldValue {
        if (new.text == old.text) return new

        val prefix = commonPrefix(old.text, new.text)
        val suffix = commonSuffix(old.text, new.text, prefix)
        val inserted = new.text.substring(prefix, new.text.length - suffix)
        val deleted = old.text.substring(prefix, old.text.length - suffix)

        // ---- 1) 删除单个字符 → 成对删除（光标在 () 中间按退格，一次删掉两个）----
        if (inserted.isEmpty() && deleted.length == 1) {
            // 优先按"退格"语义定位：光标左移一位 ⇒ 删掉的就是光标左边那个字符。
            // 不能只靠 diff 的下标 —— 当被删字符与它左侧字符相同时（典型是 `""`），
            // 公共前缀会把删除归并到右边那个，`new[prefix]` 直接越界，成对删除整个失效。
            // 这与 2b「跳过闭符」是**同一类歧义**：位置结论必须取自 selection，不能取自 diff。
            if (old.selection.collapsed && new.selection.collapsed &&
                new.selection.end == old.selection.end - 1
            ) {
                val at = old.selection.end
                val closer = if (at >= 1) PAIRS[old.text[at - 1]] else null
                if (closer != null && at < old.text.length && old.text[at] == closer) {
                    return TextFieldValue(old.text.removeRange(at - 1, at + 1), TextRange(at - 1))
                }
            }

            val removed = deleted[0]
            val closer = PAIRS[removed]
            if (closer != null && prefix < new.text.length && new.text[prefix] == closer) {
                val text = new.text.removeRange(prefix, prefix + 1)
                return TextFieldValue(text, TextRange(prefix))
            }
            return new
        }

        // ---- 2) 插入单个字符 ----
        if (inserted.length == 1 && deleted.isEmpty()) {
            val ch = inserted[0]
            val cursor = prefix + 1

            // 2a) 换行 → 继承缩进；上一行以 { 结尾则多缩一级
            if (ch == '\n') return autoIndent(new, prefix, indent)

            // 2b) 右括号/右引号：光标右边本来就是同一个字符（多为自动补上的）→
            //     **不算插入**，只把光标移过去。
            //     判据取自"编辑前光标右侧的字符"，而不是 diff 出来的插入位置 ——
            //     当插入的字符与它右侧的字符相同时，diff 会把两者归并到同一位置，
            //     用 diff 索引判断必然漏掉（实测打出 `int main())`）。
            if (ch in CLOSERS && old.selection.collapsed) {
                val oldCursor = old.selection.end
                if (oldCursor < old.text.length && old.text[oldCursor] == ch) {
                    return skipExistingCloser(old, oldCursor, ch, indent)
                }
            }

            // 2c) 左括号/引号：补上配对
            val closer = PAIRS[ch]
            if (closer != null && shouldAutoClose(new.text, prefix, ch)) {
                val text = new.text.substring(0, cursor) + closer + new.text.substring(cursor)
                return TextFieldValue(text, TextRange(cursor))
            }

            // 2d) 手打右花括号：这一行前面只有空白时先退回一级缩进，
            //     否则 `}` 会一直缩在块里（"以 } 开头自动回退"就是这一步）
            if (ch == '}') return dedentBefore(new, prefix, indent)

            return new
        }

        // ---- 3) 选中一段内容后输入左括号/引号 → 用括号把选区**包起来**（而不是替换掉）----
        if (inserted.length == 1 && deleted.isNotEmpty()) {
            val closer = PAIRS[inserted[0]]
            if (closer != null) {
                val text = old.text.substring(0, prefix) +
                    inserted + deleted + closer +
                    old.text.substring(prefix + deleted.length)
                // 光标落在闭括号之前，可以直接继续写
                return TextFieldValue(text, TextRange(prefix + 1 + deleted.length))
            }
        }

        return new
    }

    /**
     * 换行：新行带上当前行的前导空白。
     *
     * 若光标前的内容以 `{` 结尾，再多缩一级 —— 这是"打完左括号回车就该缩进"的直觉来源。
     */
    private fun autoIndent(new: TextFieldValue, newlineAt: Int, indent: String): TextFieldValue {
        val text = new.text
        val lineStart = text.lastIndexOf('\n', newlineAt - 1).let { if (it < 0) 0 else it + 1 }
        val linePrefix = text.substring(lineStart, newlineAt)
        val base = linePrefix.takeWhile { it == ' ' || it == '\t' }
        val next = if (linePrefix.trimEnd().endsWith("{")) base + indent else base
        val replacement = "\n" + next
        val result = text.substring(0, newlineAt) + replacement + text.substring(newlineAt + 1)
        return TextFieldValue(result, TextRange(newlineAt + replacement.length))
    }

    /**
     * 跳过已存在的右括号：把这次输入当没发生（回到 [old] 的文本），光标前移一位。
     *
     * 若右侧那个是 `}` 且它所在行前面只有空白，顺手退回一级缩进 —— 收块时
     * 自动补出的 `}` 才不会一直缩在里面。
     */
    private fun skipExistingCloser(
        old: TextFieldValue,
        closerAt: Int,
        ch: Char,
        indent: String,
    ): TextFieldValue {
        val text = old.text
        val caret = closerAt + 1
        if (ch != '}') return TextFieldValue(text, TextRange(caret))
        val removed = dedentWidthAt(text, closerAt, indent)
        if (removed == 0) return TextFieldValue(text, TextRange(caret))
        return TextFieldValue(
            text.removeRange(closerAt - removed, closerAt),
            TextRange(closerAt - removed + 1),
        )
    }

    /** 手打 `}`：若 [at] 之前的本行内容只有空白，就退回一级缩进。 */
    private fun dedentBefore(new: TextFieldValue, at: Int, indent: String): TextFieldValue {
        val removed = dedentWidthAt(new.text, at, indent)
        if (removed == 0) return new
        return TextFieldValue(
            new.text.removeRange(at - removed, at),
            TextRange(at - removed + 1),
        )
    }

    /**
     * 判断 [at] 之前的本行内容是否"只有空白且以一级缩进结尾"。
     * 是则返回该缩进的长度（应删掉的字符数），否则 0。
     */
    private fun dedentWidthAt(text: String, at: Int, indent: String): Int {
        val lineStart = text.lastIndexOf('\n', at - 1).let { if (it < 0) 0 else it + 1 }
        val linePrefix = text.substring(lineStart, at)
        if (linePrefix.isEmpty() || linePrefix.any { it != ' ' && it != '\t' }) return 0
        return if (linePrefix.endsWith(indent)) indent.length else 0
    }

    /**
     * 是否该补配对。
     *
     * 只在"插入点后面是空白/行尾/分号/逗号"时补。这样能避开三类坑：
     * - 光标在已有右括号前，用户其实是在**包**它（补了会多出一对）；
     * - 光标紧贴标识符（`foo|bar` 里插括号没有语义）；
     * - 预处理行（`#include <...>`）与字符串/注释里的括号不该动。
     */
    private fun shouldAutoClose(text: String, insertedAt: Int, ch: Char): Boolean {
        val lineStart = text.lastIndexOf('\n', insertedAt - 1).let { if (it < 0) 0 else it + 1 }
        val lineBefore = text.substring(lineStart, insertedAt)
        // 预处理指令行整行不管（#include / #define 里括号引号含义不同）
        if (lineBefore.trimStart().startsWith("#")) return false
        // 已经在字符串或行注释里 —— 再补引号只会帮倒忙
        if (inStringOrLineComment(text, lineStart, insertedAt)) return false

        val next = text.getOrNull(insertedAt + 1)
        return next == null || next.isWhitespace() || next == ';' || next == ','
    }

    /**
     * 从行首扫到 [at]，判断这些位置是否落在字符串或 `//` 注释内。
     * 只看当前行：跨行的块注释不判（宁可少补，也不要乱补）。
     */
    private fun inStringOrLineComment(text: String, lineStart: Int, at: Int): Boolean {
        var i = lineStart
        var quote: Char? = null
        while (i < at) {
            val c = text[i]
            if (quote != null) {
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c == quote) quote = null
            } else {
                if (c == '"' || c == '\'') quote = c
                else if (c == '/' && text.getOrNull(i + 1) == '/') return true
            }
            i++
        }
        return quote != null
    }

    private fun commonPrefix(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    private fun commonSuffix(a: String, b: String, prefix: Int): Int {
        val max = minOf(a.length, b.length) - prefix
        var i = 0
        while (i < max && a[a.length - 1 - i] == b[b.length - 1 - i]) i++
        return i
    }

    // ---------------- 只读展示用的位置计算（供高亮层使用）----------------

    /**
     * 光标处那个括号的配对位置（栈式匹配，**不跳过字符串/注释**）。
     *
     * 返回 (左括号下标, 右括号下标)；找不到配对返回 null。
     * 优先级：光标右侧的字符 → 光标左侧的字符（与主流编辑器一致：
     * 打完左括号时高亮的是刚打的那个，而不是前面那个）。
     */
    fun matchingBrackets(text: String, offset: Int): Pair<Int, Int>? {
        // 光标右侧优先（刚打完左括号时高亮的应是它），其次看左侧。
        // 注意要**跳过非括号字符**，否则光标停在 ')' 后面时右侧是换行，就漏了。
        val at = listOf(offset, offset - 1)
            .firstOrNull { it in text.indices && (text[it] in PAIRS || text[it] in CLOSERS) }
            ?: return null
        val ch = text[at]
        val open = PAIRS[ch]
        if (open != null) {
            var depth = 0
            var i = at
            while (i < text.length) {
                val c = text[i]
                if (c == ch) depth++
                else if (c == open) {
                    depth--
                    if (depth == 0) return at to i
                }
                i++
            }
            return null
        }
        val opener = PAIRS.entries.first { it.value == ch }.key
        var depth = 0
        var i = at
        while (i >= 0) {
            val c = text[i]
            if (c == ch) depth++
            else if (c == opener) {
                depth--
                if (depth == 0) return i to at
            }
            i--
        }
        return null
    }
}
