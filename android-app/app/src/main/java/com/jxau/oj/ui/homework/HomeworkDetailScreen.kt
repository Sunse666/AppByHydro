package com.jxau.oj.ui.homework

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.HowToReg
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.model.ContestPhase
import com.jxau.oj.data.model.HomeworkDetail
import com.jxau.oj.ui.component.ErrorState
import com.jxau.oj.ui.component.LoadingState
import com.jxau.oj.ui.component.MarkdownText
import com.jxau.oj.ui.component.PhaseBadge
import com.jxau.oj.ui.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeworkDetailScreen(
    state: HomeworkDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onClaim: () -> Unit,
    onClaimMessageShown: () -> Unit,
    onOpenProblem: (Int) -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.claimMessage) {
        state.claimMessage?.let {
            snackbarHost.showSnackbar(it)
            onClaimMessageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.detail?.homework?.title ?: "作业",
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
        val detail = state.detail
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            detail == null -> ErrorState(state.error ?: "没有取到作业信息", Modifier.padding(padding), onRetry)
            else -> HomeworkBody(detail, state, Modifier.padding(padding), onClaim, onOpenProblem)
        }
    }
}

@Composable
private fun HomeworkBody(
    detail: HomeworkDetail,
    state: HomeworkDetailUiState,
    modifier: Modifier,
    onClaim: () -> Unit,
    onOpenProblem: (Int) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        PhaseBadge(detail.homework.phase)

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                InfoRow("赛制", detail.homework.rule.ifBlank { "未注明" })
                val begin = TimeFormat.dateTime(detail.homework.beginAt)
                if (begin != null) InfoRow("开始", begin)
                val end = TimeFormat.dateTime(detail.homework.endAt)
                if (end != null) InfoRow("截止", end)
                // 迟交罚时起点。字段实测存在，但形态未采样，解析失败就不显示
                val penalty = TimeFormat.dateTime(detail.homework.penaltySince)
                if (penalty != null) InfoRow("迟交计罚", penalty)
                if (detail.claimed == true) InfoRow("认领状态", "已认领")
            }
        }

        // 认领按钮：未认领 && 未截止时显示。已截止认领会被站点拒（HomeworkNotLive），
        // 所以索性不展示，避免一个点了必失败的按钮。
        if (detail.claimed != true && detail.homework.phase != ContestPhase.ENDED) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClaim,
                enabled = !state.claiming,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.claiming) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Outlined.HowToReg, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.claiming) "认领中…" else "认领作业")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (detail.statement.isNotBlank()) {
            MarkdownText(markdown = detail.statement, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }

        if (detail.problems.isNotEmpty()) {
            Text(
                text = "题目（${detail.problems.size}）",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            // 与竞赛一致的字母标号（A、B、C…）；标题来自 pdict
            detail.problems.forEach { problem ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onOpenProblem(problem.docId) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = problem.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = problem.title ?: "题目 #${problem.docId}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (detail.claimed == false) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "认领作业后即可查看题目列表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
