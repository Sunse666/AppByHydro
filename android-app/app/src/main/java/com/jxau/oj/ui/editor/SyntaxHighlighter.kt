package com.jxau.oj.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

/**
 * 极简词法高亮（词法器，与 UI 无关）。
 *
 * 从 `ComposeCodeEditor.kt` 抽出来单独成文件有两个原因：
 * 1. 它本来就不碰任何控件 —— 输入 `String`、输出 `AnnotatedString`；
 * 2. 抽出来后能进离线自检（见 `tools/pure_helpers_check`），把下面这条硬不变量钉住。
 *
 * ## ⚠️ 硬不变量：高亮**只附加样式，绝不改变文本长度**
 *
 * `HighlightTransformation` 用的是 `OffsetMapping.Identity`，即"变换后文本与原文
 * 逐字符一一对应"。一旦这里多输出或少输出一个字符（哪怕只是把 Tab 展开成空格这种
 * "看着更漂亮"的处理），光标与选区就会整体错位，而且**不会报错**。
 *
 * 所以：本文件里所有分支都只允许 `append(code[原样区间])` 或 `append(c)`，
 * 不允许 `replace` / `trim` / `padStart` / 展开 Tab 之类的改写。
 * `tools/pure_helpers_check` 的 T 组用一组"难缠片段"逐个断言 `highlight(code).text == code`。
 */

@Immutable
data class SyntaxColors(
    val keyword: Color,
    /** 语言内建类型与常用函数（int/vector/print…）。取 primary 与 tertiary 的中点色，仍由主题令牌派生。 */
    val builtin: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
)

/**
 * 把高亮接到 `BasicTextField` 的文本管线上。
 *
 * **必须是 `OffsetMapping.Identity`** —— 见本文件顶部那条硬不变量：高亮不改文本长度，
 * 所以偏移量一一对应。哪天有人为了"对齐好看"在这里换成带补偿的 OffsetMapping，
 * 说明高亮已经开始改写文本了，那是错的（自检 T 组会把这条钉住）。
 */
internal class HighlightTransformation(
    private val highlighter: SyntaxHighlighter,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlighter.highlight(text.text), OffsetMapping.Identity)
}

/**
 * 极简词法高亮。
 *
 * 不做完整的语法分析 —— 只识别注释、字符串、数字、关键字、**内建类型/函数**五类，
 * 以及「标识符后紧跟 `(` → 函数调用加粗」这一个结构性提示。
 * 这些类别都覆盖"读代码时找结构"的主要需求，而且不会因为语言细节写错而误染色。
 */
class SyntaxHighlighter private constructor(
    private val language: Language,
    private val colors: SyntaxColors,
) {

    fun highlight(code: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        val n = code.length

        while (i < n) {
            val c = code[i]

            val line = language.lineComment
            if (line != null && code.startsWith(line, i)) {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                withStyle(SpanStyle(color = colors.comment)) { append(code.substring(i, end)) }
                i = end
                continue
            }

            val block = language.blockComment
            if (block != null && code.startsWith(block.first, i)) {
                val close = code.indexOf(block.second, i + block.first.length)
                val end = if (close < 0) n else close + block.second.length
                withStyle(SpanStyle(color = colors.comment)) { append(code.substring(i, end)) }
                i = end
                continue
            }

            if (c == '"' || c == '\'') {
                val end = scanString(code, i)
                withStyle(SpanStyle(color = colors.string)) { append(code.substring(i, end)) }
                i = end
                continue
            }

            // 预处理指令整行按关键字着色（#include / #define 等）
            if (c == '#' && language.keywords.contains("#include")) {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                withStyle(SpanStyle(color = colors.keyword)) { append(code.substring(i, end)) }
                i = end
                continue
            }

            val prev = if (i > 0) code[i - 1] else ' '
            if (c.isDigit() && !prev.isLetterOrDigit() && prev != '_') {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                withStyle(SpanStyle(color = colors.number)) { append(code.substring(i, j)) }
                i = j
                continue
            }

            if (c.isLetter() || c == '_') {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                when {
                    word in language.keywords ->
                        withStyle(SpanStyle(color = colors.keyword, fontWeight = FontWeight.Medium)) {
                            append(word)
                        }
                    word in language.builtins ->
                        withStyle(SpanStyle(color = colors.builtin, fontWeight = FontWeight.Medium)) {
                            append(word)
                        }
                    // 函数调用：标识符后（跳过空白）紧跟 `(` → 加粗。只动字重不动颜色，
                    // 避免与既有五类撞色
                    isCallSite(code, j, n) ->
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(word) }
                    else -> append(word)
                }
                i = j
                continue
            }

            append(c)
            i++
        }
    }

    private fun isCallSite(code: String, from: Int, n: Int): Boolean {
        var k = from
        while (k < n && code[k].isWhitespace()) k++
        return k < n && code[k] == '('
    }

    /** 字符串扫描：处理 `\` 转义，未闭合时一直吃到行尾（避免整篇后面都被染色）。 */
    private fun scanString(code: String, start: Int): Int {
        val quote = code[start]
        var i = start + 1
        while (i < code.length) {
            val c = code[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == quote) return i + 1
            if (c == '\n') return i
            i++
        }
        return code.length
    }

    companion object {
        fun forLanguage(languageKey: String, colors: SyntaxColors): SyntaxHighlighter {
            // 站点语言键形如 cc.cc17 / py.py3 / kt.jvm，族名在第一个 '.' 之前
            val family = languageKey.substringBefore('.').lowercase()
            return SyntaxHighlighter(Language.of(family), colors)
        }
    }
}

/**
 * 某语言族的词法表。关键字与内建类型/常用函数**分两张表**：
 * 前者是语法保留字（const/for/return），后者是语言自带的类型与标准函数
 * （int/vector/print）—— 高亮色与补全优先级都不同。
 */
internal data class Language(
    val keywords: Set<String>,
    val builtins: Set<String>,
    val lineComment: String?,
    val blockComment: Pair<String, String>?,
) {
    companion object {
        private val C_KEYWORDS = setOf(
            "auto", "break", "case", "catch", "class", "const", "constexpr",
            "continue", "default", "delete", "do", "else", "enum", "explicit",
            "extern", "false", "final", "for", "friend", "goto", "if", "inline",
            "interface", "namespace", "new", "nullptr", "operator", "override",
            "private", "protected", "public", "register", "return",
            "sizeof", "static", "struct", "switch", "template", "this", "throw",
            "true", "try", "typedef", "typename", "union", "using", "virtual",
            "volatile", "while",
        )

        private val C_BUILTINS = setOf(
            "bool", "char", "double", "float", "int", "long", "short", "signed",
            "unsigned", "void", "string", "vector", "map", "set", "pair", "size_t",
            "cin", "cout", "endl", "swap", "sort", "min", "max", "abs", "sqrt",
            "printf", "scanf", "memset", "malloc", "free", "strlen", "getline",
        )

        private val CPP_PREPROCESSOR = setOf(
            "#include", "#define", "#ifndef", "#ifdef", "#endif", "#pragma", "#error",
        )

        private val PY_KEYWORDS = setOf(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
            "del", "elif", "else", "except", "False", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "None", "nonlocal", "not", "or", "pass",
            "raise", "return", "self", "True", "try", "while", "with", "yield",
        )

        private val PY_BUILTINS = setOf(
            "print", "range", "len", "int", "str", "float", "bool", "list", "dict",
            "set", "tuple", "input", "sum", "min", "max", "abs", "sorted", "reversed",
            "enumerate", "zip", "map", "filter", "open", "ord", "chr",
        )

        private val KT_EXTRA_KEYWORDS = setOf(
            "fun", "val", "var", "when", "object", "package", "import", "is", "in",
            "sealed", "data", "suspend", "lateinit", "companion", "init", "by", "where",
        )

        private val JAVA_EXTRA_KEYWORDS = setOf(
            "abstract", "boolean", "byte", "extends", "implements", "native", "synchronized",
        )

        private val GO_KEYWORDS = setOf(
            "break", "case", "chan", "const", "continue", "default", "defer", "else",
            "fallthrough", "for", "func", "go", "goto", "if", "import", "interface",
            "map", "package", "range", "return", "select", "struct", "switch", "type",
            "var", "nil", "true", "false",
        )

        private val GO_BUILTINS = setOf("make", "len", "cap", "append", "new", "copy", "delete", "panic", "recover")

        private val RS_KEYWORDS = setOf(
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
            "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop",
            "match", "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
            "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while",
        )

        private val JS_EXTRA_KEYWORDS = setOf(
            "function", "var", "let", "const", "typeof", "instanceof", "undefined", "null",
            "NaN", "async", "await", "export", "default", "import", "from",
        )

        private val RB_KEYWORDS = setOf(
            "alias", "and", "begin", "break", "case", "class", "def", "do", "else", "elsif",
            "end", "ensure", "false", "for", "if", "in", "module", "next", "nil", "not",
            "or", "redo", "rescue", "retry", "return", "self", "super", "then", "true",
            "undef", "unless", "until", "when", "while", "yield",
        )

        private val RB_BUILTINS = setOf("puts", "require", "gets", "chomp", "to_i", "to_s", "each", "times")

        private val BASH_KEYWORDS = setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case",
            "esac", "function", "return", "exit", "in", "select", "until",
        )

        private val BASH_BUILTINS = setOf(
            "echo", "read", "local", "export", "declare", "source", "shift", "test",
            "cd", "printf", "eval", "exec",
        )

        private val PAS_KEYWORDS = setOf(
            "and", "array", "begin", "case", "const", "do", "downto", "else", "end",
            "file", "for", "function", "goto", "if", "in", "label", "mod", "nil", "not",
            "of", "or", "packed", "procedure", "program", "record", "repeat", "set",
            "then", "to", "type", "until", "var", "while", "with",
        )

        private val PAS_BUILTINS = setOf(
            "integer", "longint", "int64", "real", "boolean", "char", "string",
            "writeln", "write", "readln", "read", "inc", "dec", "abs", "sqr", "sqrt",
        )

        private val HS_KEYWORDS = setOf(
            "case", "class", "data", "default", "deriving", "do", "else", "foreign", "if",
            "import", "in", "infix", "instance", "let", "module", "newtype", "of", "then",
            "type", "where", "forall",
        )

        private val HS_BUILTINS = setOf("map", "foldr", "foldl", "filter", "putStrLn", "getLine", "show", "read", "sum", "product")

        private val PHP_EXTRA_KEYWORDS = setOf(
            "function", "foreach", "as", "require", "include", "require_once", "include_once",
            "global", "static", "isset", "unset", "empty", "die", "exit",
        )

        private val PHP_BUILTINS = setOf("echo", "print", "array", "count", "strlen", "str_replace", "explode", "implode")

        fun of(family: String): Language = when (family) {
            "cc", "c" -> Language(C_KEYWORDS + CPP_PREPROCESSOR, C_BUILTINS, "//", "/*" to "*/")
            "py" -> Language(PY_KEYWORDS, PY_BUILTINS, "#", null)
            "java" -> Language(C_KEYWORDS + JAVA_EXTRA_KEYWORDS, C_BUILTINS, "//", "/*" to "*/")
            "kt" -> Language(C_KEYWORDS + KT_EXTRA_KEYWORDS, C_BUILTINS, "//", "/*" to "*/")
            "go" -> Language(GO_KEYWORDS, GO_BUILTINS, "//", "/*" to "*/")
            "rs" -> Language(RS_KEYWORDS, emptySet(), "//", "/*" to "*/")
            "js" -> Language(C_KEYWORDS + JS_EXTRA_KEYWORDS, C_BUILTINS, "//", "/*" to "*/")
            "rb" -> Language(RB_KEYWORDS, RB_BUILTINS, "#", null)
            "bash" -> Language(BASH_KEYWORDS, BASH_BUILTINS, "#", null)
            "pas" -> Language(PAS_KEYWORDS, PAS_BUILTINS, "//", "{" to "}")
            "hs" -> Language(HS_KEYWORDS, HS_BUILTINS, "--", "{-" to "-}")
            "php" -> Language(C_KEYWORDS + PHP_EXTRA_KEYWORDS, C_BUILTINS + PHP_BUILTINS, "//", "/*" to "*/")
            // 未知语言：不高亮关键字，仅注释与字符串。宁可朴素也不要染色出错
            else -> Language(emptySet(), emptySet(), "//", "/*" to "*/")
        }
    }
}
