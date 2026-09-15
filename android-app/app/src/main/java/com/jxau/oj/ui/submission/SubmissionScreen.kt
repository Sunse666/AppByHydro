package com.jxau.oj.ui.submission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.model.Submission
import com.jxau.oj.ui.component.ErrorState
import com.jxau.oj.ui.component.LoadingState
import com.jxau.oj.ui.editor.CodeView
import com.jxau.oj.ui.theme.LocalEditorFontId
import com.jxau.oj.ui.theme.VerdictColors
import com.jxau.oj.ui.theme.editorFontById

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionScreen(
    state: SubmissionUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("评测结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        val submission = state.submission
        when {
            state.loading && submission == null ->
                LoadingState(Modifier.padding(padding))

            state.error != null && submission == null ->
                ErrorState(state.error, Modifier.padding(padding), onRetry = onRefresh)

            submission == null ->
                ErrorState("没有取到这条评测记录", Modifier.padding(padding), onRetry = onRefresh)

            else -> ResultBody(submission, state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ResultBody(
    submission: Submission,
    state: SubmissionUiState,
    modifier: Modifier = Modifier,
) {
    // 编译/评测输出是等宽文本，跟随设置里的代码字体，与"提交的代码"块保持一致
    val codeFontFamily = editorFontById(LocalEditorFontId.current).family
    val color = VerdictColors.colorOf(submission.statusKey)
    val label = when (submission.statusKey) {
        "PENDING" -> if (state.polling) "评测中…" else "等待评测"
        null -> "未知状态"
        else -> VerdictColors.label(submission.statusKey)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // 状态卡：颜色 + 文字同时给出。
        // 只靠颜色表达判题结果对色盲用户是不可用的，这是无障碍硬要求（方案 10.2）。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = color.copy(alpha = 0.14f),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = color,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "状态码 ${submission.statusCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.polling) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(
                text = "已查询 ${state.pollCount} 次，评测结束后会自动停止",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.timedOut) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "评测还没结束。点右上角刷新可以看最新状态 —— 评测队列可能有排队。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        InfoRow("记录编号", submission.rid.ifBlank { "—" })
        InfoRow("语言", submission.langKey.ifBlank { "—" })
        if (submission.timeMs > 0) InfoRow("耗时", "${submission.timeMs} ms")
        if (submission.memoryKb > 0) InfoRow("内存", "${submission.memoryKb} KB")
        if (submission.title.isNotBlank()) InfoRow("题目", submission.title)

        if (submission.messages.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                text = "评测信息",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(12.dp),
            ) {
                submission.messages.forEach { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = codeFontFamily,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (submission.statusKey == null) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "站点返回了不认识的评测状态。这可能是状态码映射与站点实现不一致" +
                    "（该映射尚未经 M0 验证），不代表你的提交有问题。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // code 字段的存在与否未经 M0 验证 —— 站点不回传就整个区块不渲染，不给空盒子
        if (!submission.code.isNullOrBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "提交的代码",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            CodeView(
                code = submission.code.orEmpty(),
                languageKey = submission.langKey,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
