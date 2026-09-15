@file:OptIn(ExperimentalTextApi::class)

package com.jxau.oj.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * 纯 Compose 的编辑器内核。
 *
 * 用 `BasicTextField` + [VisualTransformation] 做高亮，外加一层**轻量自动补全**：
 * 光标前缀发生变化时弹出候选（保留字 + 代码内标识符），硬件键盘方向键/回车/Tab
 * 或点按均可采纳。
 *
 * **关键约束**：高亮只附加样式，**绝不改变文本长度**，因此 `OffsetMapping` 用
 * `Identity` 就是正确的 —— 一旦高亮改变了字符数（例如把 Tab 展开成空格），
 * 光标位置和选区会整体错位。这也是为什么这里不做"制表符对齐"这类好看但危险的事。
 */
object ComposeCodeEditor : CodeEditorEngine {

    @Composable
    override fun Editor(
        value: String,
        onValueChange: (String) -> Unit,
        languageKey: String,
        textStyle: TextStyle,
        readOnly: Boolean,
        modifier: Modifier,
        handle: CodeEditorHandle?,
    ) {
        val colors = MaterialTheme.colorScheme
        val syntaxColors = remember(colors) { syntaxColorsOf(colors) }
        val measurer = rememberTextMeasurer()
        val candidateTextStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        // 装饰层配色：都从主题令牌派生（不像素级硬编码色值），
        // 括号配对用低透明度底、当前行竖线用中透明度
        val bracketHighlightColor = colors.primary.copy(alpha = 0.20f)
        val caretLineColor = colors.primary.copy(alpha = 0.45f)
        val findHighlightColor = colors.tertiary.copy(alpha = 0.30f)
        val lineBarWidthPx = with(LocalDensity.current) { 2.dp.toPx() }
        val highlighter = remember(languageKey, syntaxColors) {
            SyntaxHighlighter.forLanguage(languageKey, syntaxColors)
        }
        val transformation = remember(highlighter) { HighlightTransformation(highlighter) }
        val completer = remember(languageKey) {
            val lang = Language.of(languageKey.substringBefore('.').lowercase())
            CodeCompleter(lang.keywords, lang.builtins)
        }

        // 内部持有 TextFieldValue：补全采纳时要改写光标附近文本并移动光标，
        // 纯 String 值做不到。外部值变化（草稿恢复、切换语言重载）时整体重置，
        // 自己 emit 出去的值原样回流时不重置 —— 避免光标被拽到末尾。
        var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
        var lastEmitted by remember { mutableStateOf(value) }
        // 撤销栈（C4-1）：所有文本变更的单一入口都要喂它（见各变更点的 push）
        val undoStack = remember { CodeUndoStack(TextFieldValue(value, TextRange(value.length))) }

        var candidates by remember { mutableStateOf(emptyList<CodeCompleter.Candidate>()) }
        var selectedIndex by remember { mutableStateOf(0) }
        var prefixStart by remember { mutableStateOf(0) }
        var showCandidates by remember { mutableStateOf(false) }
        var cursorRect by remember { mutableStateOf<Rect?>(null) }
        var fieldSize by remember { mutableStateOf(IntSize.Zero) }
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        fun dismiss() {
            showCandidates = false
            candidates = emptyList()
            selectedIndex = 0
        }

        /** 把撤销/重做的可用性同步给外层按钮（handle 上的 state，驱动 recomposition）。 */
        fun syncUndoState() {
            handle?.canUndo = undoStack.canUndo
            handle?.canRedo = undoStack.canRedo
        }

        if (value != lastEmitted) {
            lastEmitted = value
            fieldValue = TextFieldValue(value, TextRange(value.length))
            undoStack.reset(fieldValue)
            // 外部重置后旧候选已失效
            dismiss()
        }

        fun refreshCandidates(fv: TextFieldValue) {
            val cursor = fv.selection.end
            if (fv.selection.collapsed.not()) {
                dismiss()
                return
            }
            prefixStart = completer.wordStart(fv.text, cursor)
            val list = completer.complete(fv.text, cursor)
            candidates = list
            selectedIndex = 0
            showCandidates = list.isNotEmpty()
        }

        fun accept(candidate: CodeCompleter.Candidate) {
            val cursor = fieldValue.selection.end
            if (cursor < prefixStart || cursor > fieldValue.text.length) {
                dismiss()
                return
            }
            val newText = fieldValue.text.replaceRange(prefixStart, cursor, candidate.name)
            val newCursor = prefixStart + candidate.name.length
            val newValue = TextFieldValue(newText, TextRange(newCursor))
            // 补全采纳是程序化编辑：独立成条，一次 undo 撤掉整个候选词
            undoStack.push(fieldValue, newValue, forceChunk = true)
            syncUndoState()
            fieldValue = newValue
            lastEmitted = newText
            onValueChange(newText)
            dismiss()
        }

        /**
         * 在光标处插入片段（符号条用）。
         *
         * 造一个"naive 变更后值"再交给 [CodeEditActions.apply]，这样按钮插入与手打
         * 走的是**同一套规则**：点 `{` 会补出 `{}`，选中内容点 `(` 会把选区包起来。
         */
        fun insertAtCursor(snippet: String) {
            if (readOnly) return
            val fv = fieldValue
            val start = fv.selection.min.coerceIn(0, fv.text.length)
            val end = fv.selection.max.coerceIn(0, fv.text.length)
            val naive = TextFieldValue(
                text = fv.text.substring(0, start) + snippet + fv.text.substring(end),
                selection = TextRange(start + snippet.length),
            )
            val adjusted = CodeEditActions.apply(fv, naive)
            // 符号条插入是程序化编辑：独立成条
            undoStack.push(fv, adjusted, forceChunk = true)
            syncUndoState()
            fieldValue = adjusted
            lastEmitted = adjusted.text
            onValueChange(adjusted.text)
            dismiss()
        }

        // 撤销/重做：把栈顶值写回输入框，并同步外部草稿（与普通编辑同一条回流路径）
        fun undo() {
            if (readOnly) return
            val prev = undoStack.undo() ?: return
            fieldValue = prev
            lastEmitted = prev.text
            onValueChange(prev.text)
            dismiss()
            syncUndoState()
        }

        fun redo() {
            if (readOnly) return
            val next = undoStack.redo() ?: return
            fieldValue = next
            lastEmitted = next.text
            onValueChange(next.text)
            dismiss()
            syncUndoState()
        }

        // ---- 查找替换（C4-2）----
        // 高亮由装饰层画（只加样式不改文本长度）；跳转用选区选中匹配；
        // 全部替换是一次 forceChunk 编辑 —— 一次 undo 整体撤销。
        fun findNext(query: String, matchCase: Boolean, backward: Boolean) {
            if (query.isEmpty()) return
            val list = CodeFindReplace.matches(fieldValue.text, query, ignoreCase = !matchCase)
            if (list.isEmpty()) {
                handle?.lastFindHit = 0
                return
            }
            val idx = CodeFindReplace.locate(list, fieldValue.selection.min, backward)
            val range = list[idx]
            val moved = fieldValue.copy(selection = TextRange(range.first, range.last + 1))
            undoStack.push(fieldValue, moved) // 文本未变：只当"光标移动"，打断合并会话
            fieldValue = moved
            handle?.lastFindHit = null
        }

        fun replaceAllIn(query: String, replacement: String, matchCase: Boolean): Int {
            if (query.isEmpty()) return 0
            val (newText, count) =
                CodeFindReplace.replaceAll(fieldValue.text, query, replacement, ignoreCase = !matchCase)
            if (count == 0) {
                handle?.lastFindHit = 0
                return 0
            }
            val caret = fieldValue.selection.min.coerceIn(0, newText.length)
            val newValue = TextFieldValue(newText, TextRange(caret))
            undoStack.push(fieldValue, newValue, forceChunk = true)
            syncUndoState()
            fieldValue = newValue
            lastEmitted = newText
            onValueChange(newText)
            dismiss()
            handle?.lastFindHit = count
            return count
        }

        // 把手柄接到当前实例上；离开组合时清掉，避免外层持有已失效的闭包
        DisposableEffect(handle) {
            handle?.insertAtCursor = ::insertAtCursor
            handle?.undoAction = ::undo
            handle?.redoAction = ::redo
            handle?.findNextAction = ::findNext
            handle?.replaceAllAction = ::replaceAllIn
            syncUndoState()
            onDispose {
                handle?.insertAtCursor = null
                handle?.undoAction = null
                handle?.redoAction = null
                handle?.findNextAction = null
                handle?.replaceAllAction = null
            }
        }

        // 硬件键盘：Ctrl+Z / Ctrl+Y(Ctrl+Shift+Z) 撤销/重做 —— 必须挂在候选框处理
        // **之前**（候选框弹出时也应能撤销），且只认带 Ctrl 修饰的按下事件，
        // 否则会跟 IME 的字符输入撞车
        val undoKeyHandler = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || readOnly) return@onPreviewKeyEvent false
            if (!event.isCtrlPressed) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Z -> {
                    if (event.isShiftPressed) redo() else undo()
                    true
                }
                Key.Y -> {
                    redo()
                    true
                }
                else -> false
            }
        }

        // 硬件键盘（MuMu/实体键盘）直接驱动候选选择 —— 软键盘无法产出方向键事件
        val keyHandler = Modifier.onPreviewKeyEvent { event ->
            if (!showCandidates || candidates.isEmpty()) return@onPreviewKeyEvent false
            when (event.key) {
                Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown) {
                        selectedIndex = (selectedIndex + 1) % candidates.size
                    }
                    true
                }
                Key.DirectionUp -> {
                    if (event.type == KeyEventType.KeyDown) {
                        selectedIndex = (selectedIndex - 1 + candidates.size) % candidates.size
                    }
                    true
                }
                Key.DirectionLeft, Key.DirectionRight -> {
                    dismiss()
                    false
                }
                Key.Enter, Key.NumPadEnter, Key.Tab -> {
                    if (event.type == KeyEventType.KeyDown) accept(candidates[selectedIndex])
                    true
                }
                Key.Escape -> {
                    dismiss()
                    true
                }
                else -> false
            }
        }

        // 光标位置跟随：选中（点按）移动光标时不触发 onTextLayout，用 SideEffect 补。
        // ⚠️ getCursorRect 的偏移必须钳在**该布局**的文本长度内 —— 布局与 fieldValue
        // 可能短暂不同帧（文本变短时旧布局先到），不钳会直接越界崩溃（MuMu 实测）。
        fun cursorRectOf(layout: TextLayoutResult, offset: Int): Rect =
            layout.getCursorRect(offset.coerceIn(0, layout.layoutInput.text.length))

        SideEffect {
            layoutResult?.let { cursorRect = cursorRectOf(it, fieldValue.selection.end) }
        }

        // 查找匹配区间：查询串/文本变化时重算（500 个封顶防极端文本拖垮绘制）
        val findRanges = remember(handle, handle?.findQuery, handle?.findMatchCase, fieldValue.text) {
            val q = handle?.findQuery.orEmpty()
            if (handle == null || q.isEmpty()) emptyList()
            else CodeFindReplace.matches(fieldValue.text, q, ignoreCase = !handle.findMatchCase)
                .take(500)
        }

        Box(modifier) {
            // 装饰层：当前行竖线 + 括号配对高亮 + 查找匹配底色。
            // 画在文本**之下**，而不是塞进 VisualTransformation —— 这样完全不碰文本管线
            // （长度、偏移、输入法组合区都不受影响），这也是高亮的既定约束。
            Canvas(Modifier.fillMaxSize()) {
                val layout = layoutResult ?: return@Canvas
                val textLength = layout.layoutInput.text.length

                if (findRanges.isNotEmpty()) {
                    for (r in findRanges) {
                        for (i in r) {
                            if (i !in 0 until textLength) continue
                            val box = runCatching { layout.getBoundingBox(i) }.getOrNull()
                                ?: continue
                            drawRect(
                                color = findHighlightColor,
                                topLeft = Offset(box.left, box.top),
                                size = Size(box.width, box.height),
                            )
                        }
                    }
                }

                cursorRect?.let { r ->
                    drawRoundRect(
                        color = caretLineColor,
                        topLeft = Offset(0f, r.top),
                        size = Size(lineBarWidthPx, r.height),
                        cornerRadius = CornerRadius(lineBarWidthPx / 2f),
                    )
                }

                val pair = CodeEditActions.matchingBrackets(
                    fieldValue.text,
                    fieldValue.selection.end,
                ) ?: return@Canvas
                listOf(pair.first, pair.second).forEach { offset ->
                    if (offset !in 0 until textLength) return@forEach
                    val box = runCatching { layout.getBoundingBox(offset) }.getOrNull()
                        ?: return@forEach
                    drawRoundRect(
                        color = bracketHighlightColor,
                        topLeft = Offset(box.left, box.top),
                        size = Size(box.width, box.height),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }

            BasicTextField(
                value = fieldValue,
                onValueChange = { fv ->
                    // 编辑动作在这里介入：自动补括号、跳右括号、成对删除、自动缩进。
                    // 软键盘没有按键事件，onValueChange 是唯一的落点。
                    val adjusted = if (readOnly) fv else CodeEditActions.apply(fieldValue, fv)
                    // 键盘/IME 输入：按打字/退格会话合并（合并策略在栈内判定）
                    undoStack.push(fieldValue, adjusted)
                    syncUndoState()
                    fieldValue = adjusted
                    lastEmitted = adjusted.text
                    onValueChange(adjusted.text)
                    refreshCandidates(adjusted)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { fieldSize = it }
                    .then(undoKeyHandler)
                    .then(keyHandler),
                enabled = !readOnly,
                readOnly = readOnly,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.primary),
                visualTransformation = transformation,
                onTextLayout = {
                    layoutResult = it
                    cursorRect = cursorRectOf(it, fieldValue.selection.end)
                },
                // 手机软键盘的自动纠错/首字母大写会把代码改坏，必须关掉
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                ),
            )

            val rect = cursorRect
            if (showCandidates && candidates.isNotEmpty() && rect != null && !readOnly) {
                val density = LocalDensity.current
                val rowHeightDp = 30.dp
                val horizontalPaddingDp = 8.dp
                // 分类色条：替代原来占地方的「关键字 / 内建 / 标识符」汉字标签
                val kindBarWidthDp = 3.dp
                val popupHeightPx = with(density) { (rowHeightDp * candidates.size).roundToPx() }

                // 宽度**按最长候选实测**，只钳上下限（96–180dp）：短候选不再白占 220dp，
                // 长标识符也不会把弹层撑出屏幕。
                val popupWidthPx = remember(candidates, candidateTextStyle, measurer, density) {
                    val textPx = candidates.maxOfOrNull {
                        measurer.measure(AnnotatedString(it.name), candidateTextStyle).size.width
                    } ?: 0
                    val chromePx = with(density) {
                        (horizontalPaddingDp * 2 + kindBarWidthDp + 6.dp).toPx()
                    }
                    val minPx = with(density) { 96.dp.roundToPx() }
                    val maxPx = with(density) { 180.dp.roundToPx() }
                    (textPx + chromePx).roundToInt().coerceIn(minPx, maxPx)
                }

                // 默认悬在光标行上方；顶到上边界时翻到光标行下方；再钳回可视区内
                var y = rect.top.roundToInt() - popupHeightPx - 4
                if (y < 0) y = rect.bottom.roundToInt() + 4
                y = y.coerceIn(0, (fieldSize.height - popupHeightPx).coerceAtLeast(0))
                // 右侧：贴光标左缘，但放不下时整块左移贴住编辑区右边界。
                // （此前只钳了左边界，光标在行尾时弹层会溢出屏幕右侧。）
                val x = rect.left.roundToInt()
                    .coerceIn(0, (fieldSize.width - popupWidthPx).coerceAtLeast(0))

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(x, y),
                    properties = PopupProperties(focusable = false),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.small,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.width(with(density) { popupWidthPx.toDp() })) {
                            candidates.forEachIndexed { index, candidate ->
                                val selected = index == selectedIndex
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeightDp)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedIndex = index
                                            accept(candidate)
                                        }
                                        // 分类信息对视觉隐藏、但对读屏可见 ——
                                        // 隐藏掉的信息必须有替代通道
                                        .semantics(mergeDescendants = true) {
                                            contentDescription =
                                                "${candidate.name}，${candidateKindLabel(candidate.kind)}"
                                        }
                                        .padding(end = horizontalPaddingDp),
                                ) {
                                    Box(
                                        Modifier
                                            .width(kindBarWidthDp)
                                            .fillMaxHeight()
                                            .background(candidateColor(candidate.kind, syntaxColors)),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = candidate.name,
                                        style = candidateTextStyle,
                                        color = candidateColor(candidate.kind, syntaxColors),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 候选词与分类色条的取色：与语法高亮完全同源，看到什么颜色就是什么类别。 */
private fun candidateColor(kind: CodeCompleter.Kind, colors: SyntaxColors): Color = when (kind) {
    CodeCompleter.Kind.KEYWORD -> colors.keyword
    CodeCompleter.Kind.BUILTIN -> colors.builtin
    CodeCompleter.Kind.IDENT -> colors.comment
}

/**
 * 分类的中文名。**只用于读屏（contentDescription）**，不出现在界面上 ——
 * 补全框里跳出一排汉字既占地方又出戏。
 */
private fun candidateKindLabel(kind: CodeCompleter.Kind): String = when (kind) {
    CodeCompleter.Kind.KEYWORD -> "关键字"
    CodeCompleter.Kind.BUILTIN -> "内建"
    CodeCompleter.Kind.IDENT -> "标识符"
}

private fun lerp(a: Color, b: Color, fraction: Float): Color =
    Color(
        red = a.red + (b.red - a.red) * fraction,
        green = a.green + (b.green - a.green) * fraction,
        blue = a.blue + (b.blue - a.blue) * fraction,
        alpha = a.alpha + (b.alpha - a.alpha) * fraction,
    )

/**
 * 从当前主题派生语法配色。编辑器与只读 [CodeView] 共用，
 * 保证同一份代码在两处颜色一致（全部由 M3 令牌派生，无硬编码色值）。
 */
fun syntaxColorsOf(colors: androidx.compose.material3.ColorScheme): SyntaxColors = SyntaxColors(
    keyword = colors.primary,
    builtin = lerp(colors.primary, colors.tertiary, 0.5f),
    string = colors.tertiary,
    number = colors.secondary,
    comment = colors.onSurfaceVariant,
)
