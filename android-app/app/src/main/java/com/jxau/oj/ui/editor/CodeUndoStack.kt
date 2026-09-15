package com.jxau.oj.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * 编辑器撤销栈（C4-1）。
 *
 * **纯类、零 UI 依赖**（只依赖 ui-text 的 TextFieldValue，与 [CodeEditActions] 同待遇），
 * 可被离线自检直接驱动。
 *
 * 与「每次变更压一帧」的朴素做法的区别在**合并策略**：
 * - 连续的**打字**（单个可打印字符插在上一次插入点的正后方）合并成一条 ——
 *   否则打一句 `int main` 会炸出 8 级撤销，用户按半天 Ctrl+Z；
 * - 连续的**退格**（光标连续左移、逐个删字符）合并成一条 —— 同理；
 * - 其余一切（换行、粘贴、符号条、补全采纳、查找替换、自动缩进…）各自成条，
 *   **换行是天然的合并断点**（与主流编辑器一致）。
 *
 * 快照存整个 [TextFieldValue]（含选区）：undo 回到的是**变更前**的状态，
 * 光标也一并还原。容量封顶防内存 —— 代码文件就几百行，200 条足够。
 */
class CodeUndoStack(initial: TextFieldValue, private val limit: Int = 200) {

    private enum class Kind { Type, Backspace, Chunk }

    private var current: TextFieldValue = initial
    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    /** 上一次变更的种类与「会话锚点」光标 —— 合并判据全在这两个变量上。 */
    private var lastKind: Kind? = null
    private var lastCaret: Int = -1

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** 外部整体重置（草稿恢复 / 切语言重载）：历史作废，从零开始。 */
    fun reset(value: TextFieldValue) {
        current = value
        undoStack.clear()
        redoStack.clear()
        lastKind = null
        lastCaret = -1
    }

    /**
     * 记录一次编辑。[old] 是变更前的值，[new] 是变更后的值。
     * [forceChunk] = 这次变更**必须**独立成条（程序化编辑：符号条/补全/替换），
     * 不参与打字合并。
     *
     * ⚠️ 任何新的用户编辑都会**清空 redo 栈** —— 这是撤销/重做的通用语义。
     */
    fun push(old: TextFieldValue, new: TextFieldValue, forceChunk: Boolean = false) {
        if (new.text == old.text) {
            // 只有光标动：不影响历史，但要打断打字会话（挪过光标再打字是新的一段）
            if (new.selection != old.selection) {
                lastKind = null
                lastCaret = -1
            }
            current = new
            return
        }

        val prefix = commonPrefix(old.text, new.text)
        val suffix = commonSuffix(old.text, new.text, prefix)
        val inserted = new.text.substring(prefix, new.text.length - suffix)
        val deleted = old.text.substring(prefix, old.text.length - suffix)

        val kind: Kind
        val merged: Boolean

        if (!forceChunk && inserted.length == 1 && deleted.isEmpty() && inserted[0] != '\n') {
            kind = Kind.Type
            // 合并条件：接着上一次的插入点继续打（上条锚点 == 本条插入位置）
            merged = lastKind == Kind.Type && prefix == lastCaret
            lastCaret = prefix + 1
        } else if (!forceChunk && inserted.isEmpty() && deleted.length == 1 &&
            old.selection.collapsed && new.selection.collapsed &&
            new.selection.end == old.selection.end - 1
        ) {
            // 退格语义的判据取自 selection（与成对删除是同一条硬约束 ——
            // diff 下标在「被删字符与左侧相同」时会归并出错位）。
            kind = Kind.Backspace
            // 连续退格的光标是**递减**的：上次删完停在 lastCaret，
            // 这次删完应是 lastCaret - 1（打字是递增续接，退格正好相反）
            merged = lastKind == Kind.Backspace && new.selection.end == lastCaret - 1
            lastCaret = new.selection.end
        } else {
            kind = Kind.Chunk
            merged = false
            lastCaret = -1
        }

        if (!merged) {
            undoStack.addLast(current)
            redoStack.clear()
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        lastKind = kind
        current = new
    }

    /** 撤销：回到上一条历史；没有可撤销的返回 null（调用方不动输入框）。 */
    fun undo(): TextFieldValue? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = prev
        // 撤销/重做本身是"跳转"，打断当前合并会话
        lastKind = null
        lastCaret = -1
        return prev
    }

    /** 重做：与 [undo] 镜像。 */
    fun redo(): TextFieldValue? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        lastKind = null
        lastCaret = -1
        return next
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
}
