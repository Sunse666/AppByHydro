@file:Suppress("UNCHECKED_CAST")

package check

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.jxau.oj.ui.editor.CodeEditActions

/**
 * CodeEditActions 的表驱动自检。
 *
 * 为什么不用 JVM 单元测试框架：本机离线缓存里**只有 junit-bom（没有 jar）**，
 * 引入不了测试框架。但 [CodeEditActions] 是纯函数、只依赖 TextFieldValue/TextRange，
 * 于是用缓存里的 kotlin-compiler-embeddable + ui-text 直接编成可执行程序来跑。
 *
 * 覆盖重点：UI 真机测试难以构造的分支 —— 嵌套括号、不同括号族、字符串转义、
 * 多级缩进、以及"插入字符 == 右侧已有字符"这个曾导致线上 bug 的歧义场景。
 */

private var passed = 0
private val failures = mutableListOf<String>()

private fun check(name: String, actual: Any?, expected: Any?) {
    if (actual == expected) {
        passed++
        println("  PASS  $name")
    } else {
        failures += "$name\n        期望: $expected\n        实际: $actual"
        println("  FAIL  $name\n        期望: $expected\n        实际: $actual")
    }
}

// ---------------- 构造"系统给的变更后值" ----------------

/**
 * 单字符输入（选区可折叠或非折叠）。
 *
 * 有选区时等价于「用这个字符替换选区」—— 这正是符号条 `insertAtCursor` 交给
 * `apply` 的那个 naive 值（见 `ComposeCodeEditor.insertAtCursor`），所以选区包裹
 * 那组用例走的就是真机符号条的路径。
 */
private fun type(old: TextFieldValue, ch: Char): TextFieldValue {
    val s = old.selection
    val text = if (s.collapsed) {
        old.text.substring(0, s.start) + ch + old.text.substring(s.start)
    } else {
        old.text.substring(0, s.start) + ch + old.text.substring(s.end)
    }
    return TextFieldValue(text, TextRange(s.start + 1))
}

/** 退格一格（折叠选区）或删除选中段。 */
private fun backspace(old: TextFieldValue): TextFieldValue {
    val s = old.selection
    return if (s.collapsed) {
        if (s.start == 0) old
        else TextFieldValue(old.text.removeRange(s.start - 1, s.start), TextRange(s.start - 1))
    } else {
        TextFieldValue(old.text.removeRange(s.start, s.end), TextRange(s.start))
    }
}

/** 多字符粘贴（应原样放行）。 */
private fun paste(old: TextFieldValue, s: String): TextFieldValue {
    val c = old.selection.start
    return TextFieldValue(old.text.substring(0, c) + s + old.text.substring(c), TextRange(c + s.length))
}

private fun fv(text: String, cursor: Int = text.length) = TextFieldValue(text, TextRange(cursor))

/** 断言「在某处输入一个字符」的结果。 */
private fun ins(name: String, text: String, cursor: Int, ch: Char, wantText: String, wantCursor: Int) {
    val old = fv(text, cursor)
    val got = CodeEditActions.apply(old, type(old, ch))
    check(name, "${got.text}|${got.selection.end}", "$wantText|$wantCursor")
}

/** 断言「退格一次」的结果。 */
private fun del(name: String, text: String, cursor: Int, wantText: String, wantCursor: Int) {
    val old = fv(text, cursor)
    val got = CodeEditActions.apply(old, backspace(old))
    check(name, "${got.text}|${got.selection.end}", "$wantText|$wantCursor")
}

fun main() {
    val I = CodeEditActions.INDENT
    println("缩进单位 = ${I.length} 空格")

    // ---------- 2a 换行缩进 ----------
    println("\n[2a] 换行自动缩进")
    ins("行首无缩进换行", "foo", 3, '\n', "foo\n", 4)
    ins("以 { 结尾则多缩一级", "int main(){", 11, '\n', "int main(){\n$I", 12 + I.length)
    ins("普通行继承缩进", "${I}return 0;", 4 + 9, '\n', "${I}return 0;\n$I", 4 + 9 + 1 + I.length)
    ins("已在块内再以 { 结尾", "${I}if(x){", 4 + 6, '\n', "${I}if(x){\n$I$I", 4 + 6 + 1 + 2 * I.length)
    ins("以 { 结尾但带尾随空格", "void f() { ", 11, '\n', "void f() { \n$I", 12 + I.length)

    // ---------- 2b 跳过已存在的闭符 ----------
    println("\n[2b] 跳过已存在的闭符")
    ins("跳过 )", "()", 1, ')', "()", 2)
    ins("跳过 ]", "[]", 1, ']', "[]", 2)
    ins("跳过后面的 ) 而不是重复插入", "int main()", 9, ')', "int main()", 10)
    ins("跳过 }", "{}", 1, '}', "{}", 2)
    ins("跳过成对引号", "\"\"", 1, '"', "\"\"", 2)
    ins("右侧是 } 且在缩进行 → 顺手退回一级",
        "{\n$I}", 2 + I.length, '}', "{\n}", 3)
    ins("右侧是 } 但行前有代码 → 只跳过不退缩进",
        "x}", 1, '}', "x}", 2)
    ins("右侧是 } 但缩进不足一级 → 只跳过不退缩进",
        "{\n  }", 4, '}', "{\n  }", 5)

    // ---------- 2c 补配对 ----------
    println("\n[2c] 自动补配对")
    ins("光标在行尾补 )", "int main", 8, '(', "int main()", 9)
    ins("补 {", "x", 1, '{', "x{}", 2)
    ins("补 [", "", 0, '[', "[]", 1)
    ins("补双引号", "", 0, '"', "\"\"", 1)
    ins("补单引号", "char c=", 7, '\'', "char c=''", 8)
    ins("右侧是 ; → 补", "foo;", 3, '(', "foo();", 4)
    ins("右侧是 , → 补", "f(a,", 3, '(', "f(a(),", 4)
    ins("右侧是标识符字符 → 不补", "ab", 1, '(', "a(b", 2)
    ins("预处理行整行不管", "#include <stdio.h>", 18, '(', "#include <stdio.h>(", 19)
    ins("字符串内不补", "\"\"", 1, '(', "\"(\"", 2)
    ins("行注释内不补", "// note", 7, '(', "// note(", 8)
    ins("字符串转义不误判：\\\" 后仍在串内", "\"a\\\"b", 5, '(', "\"a\\\"b(", 6)
    ins("预处理行前导空白也算预处理", "   #define X", 12, '(', "   #define X(", 13)

    // ---------- 2d 手打 } 退缩进 ----------
    println("\n[2d] 手打右花括号退缩进")
    ins("行前只有一级缩进 → 退回", "{\n$I", 2 + I.length, '}', "{\n}", 3)
    ins("行前无缩进 → 不动", "{", 1, '}', "{}", 2)
    ins("行前是代码 → 不动", "{\n$I x", 2 + I.length + 2, '}', "{\n$I x}", 2 + I.length + 3)

    // ---------- 1 成对删除 ----------
    println("\n[1] 成对删除")
    del("删 ( 连带删 )", "()", 1, "", 0)
    del("删 [ 连带删 ]", "[]", 1, "", 0)
    del("夹在中间：a()b", "a()b", 2, "ab", 1)
    del("删普通字符不连带", "(x)", 2, "()", 1)
    del("右括号侧退格不触发成对", "()", 2, "(", 1)
    del("引号也成对（两侧字符相同 → diff 歧义回归）", "\"\"", 1, "", 0)

    // ---------- 3 选区包裹 ----------
    println("\n[3] 选区包裹")
    run {
        val old = TextFieldValue("hello world", TextRange(6, 11))
        val got = CodeEditActions.apply(old, type(old, '['))
        check("选中 world 后输入 [ → 包裹", "${got.text}|${got.selection.end}", "hello [world]|12")
    }
    run {
        val old = TextFieldValue("abc", TextRange(0, 3))
        val got = CodeEditActions.apply(old, type(old, '"'))
        check("全选后输入 \" → 包裹", "${got.text}|${got.selection.end}", "\"abc\"|4")
    }
    run {
        // 非左括号字符替换选区应原样放行
        val old = TextFieldValue("abc", TextRange(0, 3))
        val got = CodeEditActions.apply(old, type(old, 'z'))
        check("选区替换为非括号字符 → 放行", "${got.text}|${got.selection.end}", "z|1")
    }

    // ---------- 4 原样放行 ----------
    println("\n[4] 应原样放行")
    run {
        val old = fv("ab", 1)
        val got = CodeEditActions.apply(old, paste(old, "XYZ"))
        check("多字符粘贴放行", "${got.text}|${got.selection.end}", "aXYZb|4")
    }
    run {
        val old = TextFieldValue("abcd", TextRange(1, 4))
        val got = CodeEditActions.apply(old, backspace(old))
        check("非折叠选区删除放行", "${got.text}|${got.selection.end}", "a|1")
    }
    run {
        val old = fv("abc", 3)
        val got = CodeEditActions.apply(old, old)
        check("无变化直接返回", "${got.text}|${got.selection.end}", "abc|3")
    }

    // ---------- matchingBrackets ----------
    println("\n[5] 括号配对定位")
    fun mb(name: String, text: String, offset: Int, want: Pair<Int, Int>?) =
        check(name, CodeEditActions.matchingBrackets(text, offset), want)

    mb("光标在左括号上", "()", 0, 0 to 1)
    mb("光标在两括号之间", "()", 1, 0 to 1)
    mb("光标在右括号之后（取 offset-1）", "()", 2, 0 to 1)
    mb("嵌套取最内层", "(())", 1, 1 to 2)
    mb("嵌套取最外层", "(())", 0, 0 to 3)
    mb("混族 [] 包 {}", "[{}]", 0, 0 to 3)
    mb("混族内层 {}", "[{}]", 1, 1 to 2)
    mb("非括号处返回 null", "abc", 1, null)
    mb("未闭合返回 null", "(()", 0, null)
    mb("空文本返回 null", "", 0, null)
    mb("越界 offset 返回 null 不崩", "()", 99, null)

    // ---------- 结果 ----------
    println("\n" + "=".repeat(56))
    println("通过 $passed 项，失败 ${failures.size} 项")
    if (failures.isNotEmpty()) {
        println("\n失败明细：")
        failures.forEach { println("  - $it") }
        kotlin.system.exitProcess(1)
    }
}
