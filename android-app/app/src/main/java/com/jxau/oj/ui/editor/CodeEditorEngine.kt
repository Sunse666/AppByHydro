package com.jxau.oj.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

/**
 * 编辑器内核的抽象。
 *
 * 方案里定的内核是 **WebView + CodeMirror 6**（站点 29 种语言高亮可直接映射）。
 * 但本期构建环境完全离线，拿不到 CodeMirror 的 JS 产物，因此先用纯 Compose 实现顶上。
 *
 * 把这个决策的影响面压到一个文件里：将来换内核只改 [EditorEngine.Default]，
 * 编辑器页与提交链路不受影响 —— 这正是方案 12.C 第 5 项要求的"封装成接口留切换余地"。
 */
interface CodeEditorEngine {

    @Composable
    fun Editor(
        value: String,
        onValueChange: (String) -> Unit,
        /** 站点语言键，形如 `cc.cc17` / `py.py3` / `java`。 */
        languageKey: String,
        textStyle: TextStyle,
        readOnly: Boolean,
        modifier: Modifier,
        /**
         * 让外层能在光标处插入文本（符号条要用）。null = 不需要这个能力。
         *
         * 手机软键盘没有 Tab 和方向键，符号条是唯一能把这些输入带回编辑器的通道，
         * 所以它必须由内核提供"在光标处写入"的能力，而不是外层自己改字符串。
         */
        handle: CodeEditorHandle? = null,
    )
}

/**
 * 编辑器手柄。
 *
 * 暴露的动作：
 * 1. **在光标处插入片段**（符号条用）。插入复用编辑动作的规则
 *    （补配对、跳过右括号、选区包裹），所以符号条上的 `{` 与手打的 `{` 行为一致；
 * 2. **撤销 / 重做**（C4-1）。[canUndo]/[canRedo] 是 Compose state ——
 *    外层按钮直接读它就会随栈状态自动启停；
 * 3. **查找替换**（C4-2）。外层把查询串写进 [findQuery]（实时驱动装饰层高亮），
 *    调用 [findNext]/[replaceAll] 执行跳转与替换；[lastFindHit] 是编辑器回写给外层的
 *    状态（最近一次跳转/替换的结果），用于 snackbar 提示。
 */
class CodeEditorHandle {

    internal var insertAtCursor: ((String) -> Unit)? = null
    internal var undoAction: (() -> Unit)? = null
    internal var redoAction: (() -> Unit)? = null
    internal var findNextAction: ((String, Boolean, Boolean) -> Unit)? = null
    internal var replaceAllAction: ((String, String, Boolean) -> Int)? = null

    var canUndo: Boolean by mutableStateOf(false)
        internal set
    var canRedo: Boolean by mutableStateOf(false)
        internal set

    /** 当前查找串（外层写）；null/空 = 没有查找，装饰层不高亮。 */
    var findQuery: String by mutableStateOf("")
    /** 大小写是否敏感（外层写）。默认**区分**大小写 —— 代码里的标识符大小写有意义。 */
    var findMatchCase: Boolean by mutableStateOf(true)

    /** 编辑器回写：`null` = 无事发生；`Int` = 全部替换的次数（0 = 没找到）。 */
    var lastFindHit: Int? by mutableStateOf(null)
        internal set

    fun insert(text: String) {
        insertAtCursor?.invoke(text)
    }

    fun undo() {
        undoAction?.invoke()
    }

    fun redo() {
        redoAction?.invoke()
    }

    /** 跳到下一个（[backward] = 上一个）匹配。 */
    fun findNext(query: String, backward: Boolean = false) {
        findNextAction?.invoke(query, findMatchCase, backward)
    }

    /** 全部替换，返回替换次数。 */
    fun replaceAll(query: String, replacement: String): Int =
        replaceAllAction?.invoke(query, replacement, findMatchCase) ?: 0
}

object EditorEngine {
    /**
     * 当前内核。换成 `WebView + CodeMirror 6` 时改这一行即可，
     * 但需要先解决 CodeMirror 产物的离线获取（打包到 assets 里）。
     */
    val Default: CodeEditorEngine = ComposeCodeEditor
}
