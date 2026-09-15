package com.jxau.oj.ui.problem

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.model.ProblemDetail
import com.jxau.oj.ui.component.ErrorState
import com.jxau.oj.ui.component.LoadingState
import com.jxau.oj.ui.component.MarkdownText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemDetailScreen(
    state: ProblemDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.detail?.title ?: "题目",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        val content = state.detail
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(
                message = state.error,
                modifier = Modifier.padding(padding),
                onRetry = onRetry,
            )
            content != null -> DetailBody(
                detail = content,
                modifier = Modifier.padding(padding),
                onOpenEditor = onOpenEditor,
                onOpenRecords = onOpenRecords,
            )
        }
    }
}

@Composable
private fun DetailBody(
    detail: ProblemDetail,
    modifier: Modifier = Modifier,
    onOpenEditor: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = detail.pid ?: "#${detail.docId}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (detail.timeLimitMs > 0) {
                InfoChip("时间 ${detail.timeLimitMs} ms")
            }
            if (detail.memoryLimitMb > 0) {
                InfoChip("内存 ${detail.memoryLimitMb} MB")
            }
            InfoChip("${detail.nAccept}/${detail.nSubmit} 通过")
        }

        if (detail.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.tags.forEach { InfoChip(it) }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        if (detail.statement.isBlank()) {
            Text(
                text = "这道题还没有题面。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // 题面是 Markdown，且颜色全部从 Material 3 令牌派生 —— 见 MarkdownText 的说明。
            // 公式排版（KaTeX）与编辑器一起在后续接入。
            MarkdownText(
                markdown = detail.statement,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onOpenEditor,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Code, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("去写代码")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenRecords,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.History, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("本题提交记录")
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoChip(text: String) {
    AssistChip(onClick = { }, label = { Text(text, style = MaterialTheme.typography.labelSmall) })
}
