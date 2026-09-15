package com.jxau.oj.ui.contest

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
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.mapper.Mappers
import com.jxau.oj.data.model.ContestDetail
import com.jxau.oj.ui.component.ErrorState
import com.jxau.oj.ui.component.LoadingState
import com.jxau.oj.ui.component.MarkdownText
import com.jxau.oj.ui.component.PhaseBadge
import com.jxau.oj.ui.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContestDetailScreen(
    state: ContestDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenProblem: (Int) -> Unit,
    onOpenScoreboard: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.detail?.contest?.title ?: "竞赛",
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
                    // 成绩表入口：进行中的竞赛随时可看实时排名。
                    // 未开始的竞赛点了会拿到错误信封，由成绩表页兜底提示，不在此预判。
                    IconButton(onClick = onOpenScoreboard) {
                        Icon(Icons.Outlined.Leaderboard, contentDescription = "成绩表")
                    }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            detail == null -> ErrorState(state.error ?: "没有取到竞赛信息", Modifier.padding(padding), onRetry)
            else -> ContestBody(detail, state, Modifier.padding(padding), onOpenProblem)
        }
    }
}

@Composable
private fun ContestBody(
    detail: ContestDetail,
    state: ContestDetailUiState,
    modifier: Modifier,
    onOpenProblem: (Int) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PhaseBadge(detail.contest.phase)
            Spacer(Modifier.width(8.dp))
            if (detail.contest.rated) {
                Text(
                    text = "Rated",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                InfoRow("赛制", detail.contest.rule.ifBlank { "未注明" })
                val duration = detail.contest.durationLabel
                if (duration != null) InfoRow("时长", duration)
                val begin = TimeFormat.dateTime(detail.contest.beginAt)
                if (begin != null) InfoRow("开始", begin)
                val end = TimeFormat.dateTime(detail.contest.endAt)
                if (end != null) InfoRow("结束", end)
                if (detail.contest.attend > 0) InfoRow("参加", "${detail.contest.attend} 人")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (detail.statement.isNotBlank()) {
            MarkdownText(markdown = detail.statement, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }

        if (detail.problemDocIds.isNotEmpty()) {
            Text(
                text = "题目（${detail.problemDocIds.size}）",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            // 标号按 tdoc.pids 顺序取字母序（A、B、… 与站点 web 端一致）；
            // 题目名拉取失败时回落「题目 #docId」，仍可点进题库详情
            detail.problemDocIds.forEachIndexed { index, docId ->
                val label = Mappers.alphabeticLabel(index)
                val title = state.problemTitles[docId]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    onClick = { onOpenProblem(docId) },
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = title ?: "题目 #$docId",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (state.problemTitles.isEmpty() && state.problemsNote != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.problemsNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
