package com.jxau.oj.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jxau.oj.data.model.PretestResult
import com.jxau.oj.ui.component.ErrorState
import com.jxau.oj.ui.component.LoadingState
import com.jxau.oj.ui.component.MarkdownText
import com.jxau.oj.ui.theme.LocalEditorFontId
import com.jxau.oj.ui.theme.LocalEditorFontSizeSp
import com.jxau.oj.ui.theme.VerdictColors
import com.jxau.oj.ui.theme.editorFontById

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSelectLang: (String) -> Unit,
    onSubmit: () -> Unit,
    onSubmitted: (String) -> Unit,
    onErrorShown: () -> Unit,
    onDraftRestoredShown: () -> Unit,
    onPracticeNoticeShown: () -> Unit,
    onToggleStatement: () -> Unit,
    onExpandStatement: () -> Unit,
    onCollapseStatement: () -> Unit,
    onTestInputChange: (Int, String) -> Unit,
    onAddTestCase: () -> Unit,
    onRemoveTestCase: (Int) -> Unit,
    onFillSamples: () -> Unit,
    onRunSelfTest: () -> Unit,
    onPretestErrorShown: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    var showLangSheet by remember { mutableStateOf(false) }
    var showSelfTestSheet by remember { mutableStateOf(false) }
    // 查找替换（C4-2）的 UI 态；查询串实时同步给 handle（驱动装饰层高亮）
    var showFindBar by remember { mutableStateOf(false) }
    var showReplaceRow by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    // 编辑器手柄：给符号条一个"在光标处插入"的入口
    val editorHandle = remember { CodeEditorHandle() }

    // 消费编辑器回写的查找结果：0 = 没找到；>0 = 替换次数（显示完即清，避免重组重弹）
    LaunchedEffect(editorHandle.lastFindHit) {
        val hit = editorHandle.lastFindHit ?: return@LaunchedEffect
        editorHandle.lastFindHit = null
        snackbarHost.showSnackbar(
            if (hit == 0) "未找到" else "已替换 $hit 处",
        )
    }

    // 代码字体与字号来自设置页（全局），这里只消费
    val codeFontId = LocalEditorFontId.current
    val codeFontFamily = remember(codeFontId) { editorFontById(codeFontId).family }
    val codeFontSizeSp = LocalEditorFontSizeSp.current

    // 提交拿到记录编号后，交给导航层去结果页
    LaunchedEffect(state.submittedRid) {
        state.submittedRid?.let(onSubmitted)
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHost.showSnackbar(it)
            onErrorShown()
        }
    }
    LaunchedEffect(state.draftRestored) {
        if (state.draftRestored) {
            snackbarHost.showSnackbar("已恢复上次未提交的代码")
            onDraftRestoredShown()
        }
    }
    // 降级练习提交（竞赛/作业不在进行中）成功后告知语义，避免用户误以为成绩已计入
    LaunchedEffect(state.practiceNotice) {
        if (state.practiceNotice) {
            snackbarHost.showSnackbar("竞赛/作业不在进行中，本次已按练习提交（不计入成绩）")
            onPracticeNoticeShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.title.ifBlank { "写代码" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            showFindBar = !showFindBar
                            if (!showFindBar) {
                                showReplaceRow = false
                                findQuery = ""
                                editorHandle.findQuery = ""
                            }
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "查找",
                            tint = if (showFindBar) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.langKeys.isEmpty() && state.error != null ->
                ErrorState(state.error, Modifier.padding(padding), onRetry = null)
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                StatementPanel(
                    state = state,
                    onToggle = onToggleStatement,
                    onExpand = onExpandStatement,
                    onCollapse = onCollapseStatement,
                )

                if (showFindBar) {
                    FindBar(
                        query = findQuery,
                        onQueryChange = {
                            findQuery = it
                            editorHandle.findQuery = it
                        },
                        showReplace = showReplaceRow,
                        onToggleReplace = { showReplaceRow = !showReplaceRow },
                        replaceText = replaceText,
                        onReplaceChange = { replaceText = it },
                        matchCase = editorHandle.findMatchCase,
                        onToggleMatchCase = { editorHandle.findMatchCase = !editorHandle.findMatchCase },
                        onPrevious = { editorHandle.findNext(findQuery, backward = true) },
                        onNext = { editorHandle.findNext(findQuery) },
                        onReplaceAll = {
                            editorHandle.replaceAll(findQuery, replaceText)
                        },
                        onClose = {
                            showFindBar = false
                            showReplaceRow = false
                            findQuery = ""
                            editorHandle.findQuery = ""
                        },
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    EditorEngine.Default.Editor(
                        value = state.code,
                        onValueChange = onCodeChange,
                        languageKey = state.selectedLang,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = codeFontFamily,
                            fontSize = codeFontSizeSp.sp,
                            lineHeight = (codeFontSizeSp + 7).sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        readOnly = false,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        handle = editorHandle,
                    )
                }

                // 符号条：软键盘没有 Tab / 方向键，把最高频的符号与缩进做成按钮，
                // 走同一个 handle（点 `{` 与手打 `{` 行为一致，都会补出 `{}`）
                SymbolBar(
                    fontFamily = codeFontFamily,
                    onInsert = editorHandle::insert,
                )

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 撤销/重做（C4-1）：可用性由 handle 上的 Compose state 驱动，
                    // 栈空自动禁用。悬停提示用 contentDescription 满足读屏
                    IconButton(onClick = editorHandle::undo, enabled = editorHandle.canUndo) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "撤销",
                            Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = editorHandle::redo, enabled = editorHandle.canRedo) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = "重做",
                            Modifier.size(20.dp),
                        )
                    }

                    TextButton(onClick = { showLangSheet = true }) {
                        Icon(Icons.Outlined.Code, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(state.selectedLang.ifBlank { "选择语言" })
                    }

                    // 自测入口。底栏本身常驻（与语言/字号同级），
                    // 点开后是可上拉展开的底部面板 —— 输入数据、跑自测、看结果
                    TextButton(onClick = { showSelfTestSheet = true }) {
                        Icon(Icons.Outlined.Science, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("自测")
                    }

                    Spacer(Modifier.weight(1f))

                    Button(onClick = onSubmit, enabled = !state.submitting) {
                        if (state.submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                Icons.AutoMirrored.Outlined.Send,
                                contentDescription = null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.submitting) "提交中" else "提交")
                    }
                }
            }
        }
    }

    if (showLangSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLangSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LanguagePicker(
                langKeys = state.langKeys,
                selected = state.selectedLang,
                onPick = {
                    onSelectLang(it)
                    showLangSheet = false
                },
            )
        }
    }

    if (showSelfTestSheet) {
        // skipPartiallyExpanded = false：半开态可看到输入区，继续上拉到全高看结果 ——
        // 即「上拉出现底栏」的形态。测试数据保存在 VM 里，关掉再开不丢。
        ModalBottomSheet(
            onDismissRequest = { showSelfTestSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        ) {
            SelfTestSheet(
                state = state,
                onInputChange = onTestInputChange,
                onAddCase = onAddTestCase,
                onRemoveCase = onRemoveTestCase,
                onFillSamples = onFillSamples,
                onRun = onRunSelfTest,
                onErrorShown = onPretestErrorShown,
            )
        }
    }
}

/**
 * 题面顶栏。常态只占一行（题目名 + 展开箭头），点击或**向下拖动**展开题面，
 * 展开后**向上拖动**（或点箭头）收起 —— 满足「写代码的地方直接下拉能拉出显示题目内容的顶栏」。
 * 拖动手势只挂在顶栏条上，不碰代码编辑区 —— 编辑区的手势属于文本选择与滚动，不能抢。
 */
/**
 * 符号条。
 *
 * 存在的理由很具体：手机软键盘**不产生 Tab 与符号按键事件**，
 * `{` `}` `(` `)` 这些要切到手写符号页翻两屏才能打到。
 *
 * 所有按钮都走编辑器的 handle，因此 `{` 会补出 `{}`、`(` 会补出 `()` ——
 * 与手打完全同一条规则，不会出现"按钮插入的和手打的不一样"。
 */
@Composable
private fun SymbolBar(
    fontFamily: FontFamily,
    onInsert: (String) -> Unit,
) {
    val symbols = listOf("Tab", "{", "}", "(", ")", "[", "]", ";", "\"")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbols.forEach { symbol ->
            TextButton(
                onClick = { onInsert(if (symbol == "Tab") CodeEditActions.INDENT else symbol) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(34.dp),
            ) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = fontFamily),
                )
            }
        }
    }
}

@Composable
private fun StatementPanel(
    state: EditorUiState,
    onToggle: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
) {
    var dragAccum by remember { mutableStateOf(0f) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccum = 0f },
                        onDragEnd = { dragAccum = 0f },
                        onDragCancel = { dragAccum = 0f },
                    ) { _, dragAmount ->
                        dragAccum += dragAmount
                        if (dragAccum > 48f && !state.showStatement) {
                            onExpand()
                            dragAccum = 0f
                        } else if (dragAccum < -48f && state.showStatement) {
                            onCollapse()
                            dragAccum = 0f
                        }
                    }
                }
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.showStatement) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.title.ifBlank { "题目" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (state.showStatement) "收起" else "下拉查看题面",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = state.showStatement) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                ) {
                    if (state.statement.isNotBlank()) {
                        MarkdownText(markdown = state.statement)
                    } else {
                        Text(
                            text = "没有取到题面内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 自测面板。
 *
 * 上半部分是若干组输入（每组对应一次独立运行），下半部分是最近一次自测的结果：
 * 总体判定色块 + 每组的状态/耗时/内存/程序输出（站点 `testCases[].message`，实测）。
 */
@Composable
private fun SelfTestSheet(
    state: EditorUiState,
    onInputChange: (Int, String) -> Unit,
    onAddCase: () -> Unit,
    onRemoveCase: (Int) -> Unit,
    onFillSamples: () -> Unit,
    onRun: () -> Unit,
    onErrorShown: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.pretestError) {
        state.pretestError?.let {
            snackbarHost.showSnackbar(it)
            onErrorShown()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Science, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("自测", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.samples.isNotEmpty()) {
                TextButton(onClick = onFillSamples) { Text("从样例填充") }
            }
        }
        Text(
            text = "每组输入会分别运行一次，不占用提交次数。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            state.testInputs.forEachIndexed { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { onInputChange(index, it) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    label = { Text("输入 #${index + 1}") },
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    trailingIcon = {
                        if (state.testInputs.size > 1) {
                            IconButton(onClick = { onRemoveCase(index) }) {
                                Text("✕", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    },
                )
            }
            if (state.testInputs.size < 5) {
                OutlinedButton(onClick = onAddCase, modifier = Modifier.padding(top = 4.dp)) {
                    Text("添加一组数据")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRun,
            enabled = !state.pretestBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.pretestBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.pretestBusy) "自测运行中…" else "运行自测")
        }

        val result = state.pretestResult
        if (state.pretestBusy) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else if (result != null) {
            Spacer(Modifier.height(16.dp))
            PretestResultView(result)
        }
    }
}

@Composable
private fun PretestResultView(result: PretestResult) {
    // 总体判定：颜色 + 文字双重表达（色盲可用性硬约束）
    val overallColor = VerdictColors.colorOf(result.statusKey)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = overallColor.copy(alpha = 0.14f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = VerdictColors.label(result.statusKey),
                style = MaterialTheme.typography.labelLarge,
                color = overallColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "共 ${result.cases.size} 组",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.height(8.dp))
    result.compilerMessages.forEach { message ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        result.cases.forEach { case ->
            val color = VerdictColors.colorOf(case.statusKey)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${case.id}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = VerdictColors.label(case.statusKey),
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                        )
                        Spacer(Modifier.weight(1f))
                        case.timeMs?.let {
                            Text(
                                text = "${"%.1f".format(it)} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        case.memoryKb?.let {
                            Text(
                                text = "$it KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!case.output.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = case.output.trimEnd('\n'),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 语言选择。
 *
 * 站点下发 29 种语言，其中 C++ 一个人就有 13 个标准变体。平铺会淹没列表，
 * 所以按语言族分组（方案附录 B 的建议）。
 */
@Composable
private fun LanguagePicker(
    langKeys: List<String>,
    selected: String,
    onPick: (String) -> Unit,
) {
    val grouped = remember(langKeys) {
        langKeys.groupBy { it.substringBefore('.') }
    }

    Column(Modifier.heightIn(max = 480.dp)) {
        Text(
            text = "选择语言",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            grouped.forEach { (family, keys) ->
                item(key = "header-$family") {
                    Text(
                        text = familyLabel(family),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(keys, key = { it }) { key ->
                    ListItem(
                        headlineContent = { Text(key) },
                        supportingContent = if (key == family) null else {
                            { Text(key.substringAfter('.')) }
                        },
                        trailingContent = {
                            if (key == selected) {
                                Text(
                                    "当前",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.clickableRow { onPick(key) },
                    )
                }
            }
        }
    }
}

private fun familyLabel(family: String): String = when (family) {
    "cc" -> "C++"
    "c" -> "C"
    "py" -> "Python"
    "java" -> "Java"
    "kt" -> "Kotlin"
    "go" -> "Go"
    "rs" -> "Rust"
    "js" -> "JavaScript"
    "rb" -> "Ruby"
    "php" -> "PHP"
    "pas" -> "Pascal"
    "hs" -> "Haskell"
    "bash" -> "Bash"
    else -> family
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)

/**
 * 查找替换条（C4-2）。
 *
 * 第一行：查询框（实时驱动编辑器内全部匹配高亮）＋ 大小写开关 ＋ 上一个/下一个 ＋ 关闭；
 * 第二行（点 ⇄ 展开）：替换框 ＋ 全部替换。全部替换是一次 forceChunk 编辑，
 * 一次 undo 整体撤销。大小写默认**区分**（代码标识符大小写有意义），Aa 按钮切换。
 */
@Composable
private fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    showReplace: Boolean,
    onToggleReplace: () -> Unit,
    replaceText: String,
    onReplaceChange: (String) -> Unit,
    matchCase: Boolean,
    onToggleMatchCase: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("查找", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                )
                TextButton(onClick = onToggleMatchCase) {
                    Text(
                        "Aa",
                        fontFamily = FontFamily.Monospace,
                        color = if (matchCase) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPrevious, enabled = query.isNotEmpty()) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "上一个")
                }
                IconButton(onClick = onNext, enabled = query.isNotEmpty()) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "下一个")
                }
                IconButton(onClick = onToggleReplace) {
                    Icon(
                        Icons.Outlined.FindReplace,
                        contentDescription = if (showReplace) "收起替换" else "展开替换",
                        tint = if (showReplace) MaterialTheme.colorScheme.primary
                        else LocalContentColor.current,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭查找")
                }
            }

            if (showReplace) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replaceText,
                        onValueChange = onReplaceChange,
                        placeholder = { Text("替换为", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    )
                    TextButton(
                        onClick = onReplaceAll,
                        enabled = query.isNotEmpty(),
                    ) {
                        Text("全部替换")
                    }
                }
            }
        }
    }
}
