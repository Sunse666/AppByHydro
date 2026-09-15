package com.jxau.oj.ui.editor

/**
 * 查找替换的纯逻辑（C4-2）。零 UI 依赖，可被离线自检直接驱动（Z 组）。
 *
 * 设计取舍：
 * - **查找不做文本层任何改写** —— 匹配区间由装饰层画底色（只加样式不改文本长度，
 *   这是编辑器样式层的既定硬约束），跳转用选区选中匹配；
 * - **替换只提供「全部替换」**，且作为一次 forceChunk 编辑进撤销栈 ——
 *   一次 undo 整体撤销全部替换，与主流编辑器一致；
 * - 匹配**逐个码点**推进而不是逐 char：查询串里的 emoji / 增补平面字符按码点算，
 *   `String.indexOf` 在 UTF-16 层面天然逐 char，但查询串本身也按 char 存储，
 *   逐 char 匹配对增补平面查询串同样正确 —— 这里保持 indexOf 简单实现，
 *   自检用例覆盖 emoji 查询。
 */
object CodeFindReplace {

    /** 文本里 [query] 的全部匹配区间（`start` 升序、不相交）。空查询返回空表。 */
    fun matches(text: String, query: String, ignoreCase: Boolean): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val hay = if (ignoreCase) text.lowercase() else text
        val needle = if (ignoreCase) query.lowercase() else query
        if (needle.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var from = 0
        while (from <= hay.length - needle.length) {
            val at = hay.indexOf(needle, from)
            if (at < 0) break
            out.add(at until at + needle.length)
            from = at + needle.length
        }
        return out
    }

    /**
     * 全部替换。返回 (新文本, 替换次数)。空查询原样返回、次数 0。
     * 实现直接按区间拼接：**不做先 lower 再替换** —— 那会丢原文大小写。
     */
    fun replaceAll(text: String, query: String, replacement: String, ignoreCase: Boolean): Pair<String, Int> {
        if (query.isEmpty()) return text to 0
        val list = matches(text, query, ignoreCase)
        if (list.isEmpty()) return text to 0
        val sb = StringBuilder()
        var prev = 0
        for (r in list) {
            sb.append(text, prev, r.first)
            sb.append(replacement)
            prev = r.last + 1
        }
        sb.append(text, prev, text.length)
        return sb.toString() to list.size
    }

    /**
     * 环形步进：从 [index] 前进/后退一步（[count] 个匹配）。
     * 向后越过末尾回 0，向前越过开头回最后 —— 与主流编辑器一致。
     */
    fun step(index: Int, count: Int, backward: Boolean): Int {
        if (count == 0) return -1
        return when {
            backward -> if (index - 1 < 0) count - 1 else index - 1
            else -> if (index + 1 >= count) 0 else index + 1
        }
    }

    /**
     * 从光标位置找到**下一个**匹配在 [matches] 中的下标（[backward] = 上一个）。
     * [caret] 是当前选区起点。找不到（无匹配）返回 -1。
     * 向后：第一个 `start >= caret` 的匹配，没有则环形回第一个；
     * 向前：最后一个 `start < caret` 的匹配，没有则环形回最后一个。
     */
    fun locate(matches: List<IntRange>, caret: Int, backward: Boolean): Int {
        if (matches.isEmpty()) return -1
        return if (!backward) {
            val idx = matches.indexOfFirst { it.first >= caret }
            if (idx >= 0) idx else 0
        } else {
            val idx = matches.indexOfLast { it.first < caret }
            if (idx >= 0) idx else matches.size - 1
        }
    }
}
