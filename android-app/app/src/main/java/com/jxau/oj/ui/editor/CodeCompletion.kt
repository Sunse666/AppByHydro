package com.jxau.oj.ui.editor

/**
 * 编辑器自动补全引擎。**纯 Kotlin、零 UI 依赖** —— 只做「光标处该弹什么候选」这一件事。
 *
 * 候选来源（按优先级）：
 * 1. 语言保留字 / 内建类型与常用函数（[com.jxau.oj.ui.editor.Language] 的静态表）；
 * 2. 当前代码里已出现的标识符（变量名、类名、函数名）—— 用正则收集并按出现次数加权，
 *    用户刚定义的名字永远排最前。
 *
 * 刻意不做的：不做语义分析（分不清变量与类名也不影响补全体验）、
 * 不跨文件、不持久词表 —— 关掉页面词表即重置，比赛场景下这是正确取舍。
 */
class CodeCompleter(
    private val keywords: Set<String>,
    private val builtins: Set<String>,
) {

    enum class Kind { KEYWORD, BUILTIN, IDENT }

    data class Candidate(val name: String, val kind: Kind)

    /**
     * 给出光标 [cursor] 处的候选列表（≤ [limit] 条，已排序）。
     *
     * [cursor] 是字符串索引（TextFieldValue.selection.end）。
     * 光标在数字中间 / 前缀为空时不给候选 —— 那是用户在写数值，弹窗只会添乱。
     */
    fun complete(code: String, cursor: Int, limit: Int = MAX_CANDIDATES): List<Candidate> {
        if (cursor <= 0 || cursor > code.length) return emptyList()
        val start = wordStart(code, cursor)
        val prefix = code.substring(start, cursor)
        if (prefix.isEmpty() || prefix[0].isDigit()) return emptyList()

        val result = ArrayList<Candidate>(limit)

        // 1) 保留字（前缀匹配 + 忽略大小写的次级匹配，输入 Int 也能找到 int）。
        //    已经打完整的词不再作为候选 —— 全词弹窗是噪音。
        keywords.filterTo(mutableListOf()) { it.startsWith(prefix) && it != prefix }
            .sorted()
            .forEach { if (result.size < limit) result.add(Candidate(it, Kind.KEYWORD)) }
        if (result.size < limit) {
            keywords.filter { !it.startsWith(prefix) && it.equals(prefix, ignoreCase = true) && it != prefix }
                .forEach { if (result.size < limit) result.add(Candidate(it, Kind.KEYWORD)) }
        }

        // 2) 内建类型/函数
        if (result.size < limit) {
            builtins.filter { it.startsWith(prefix) && it != prefix && it !in keywords }
                .sorted()
                .forEach { if (result.size < limit) result.add(Candidate(it, Kind.BUILTIN)) }
        }

        // 3) 代码里的标识符：按出现次数加权 —— 刚写的变量名排最前
        if (result.size < limit) {
            val freq = HashMap<String, Int>()
            for (m in IDENT_REGEX.findAll(code)) {
                val word = m.value
                if (word.length <= 1 || word[0].isDigit()) continue
                freq.merge(word, 1, Int::plus)
            }
            freq.entries
                .filter { it.key.startsWith(prefix) && it.key != prefix }
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .forEach { (word, _) ->
                    if (result.size >= limit) return@forEach
                    if (result.none { it.name == word }) result.add(Candidate(word, Kind.IDENT))
                }
        }
        return result
    }

    /** 光标前最近一个标识符词的起点（补全时要替换的就是这段前缀）。 */
    fun wordStart(code: String, cursor: Int): Int {
        var i = cursor
        while (i > 0) {
            val c = code[i - 1]
            if (c.isLetterOrDigit() || c == '_') i-- else break
        }
        return i
    }

    companion object {
        /**
         * 候选条数上限。
         *
         * 5 是权衡后的值：弹层最高 5×30dp = 150dp，在小屏上不会盖掉半屏代码；
         * 而三级候选（保留字 → 内建 → 代码标识符）里真正有用的通常就在前几条。
         */
        const val MAX_CANDIDATES = 5
        private val IDENT_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
