package com.jxau.oj.ui.contest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.model.ScoreCell
import com.jxau.oj.data.model.Scoreboard
import com.jxau.oj.data.model.ScoreboardRow
import com.jxau.oj.ui.component.ErrorState
import com.jxau.oj.ui.component.LoadingState
import com.jxau.oj.ui.theme.VerdictColors
import com.jxau.oj.ui.util.TimeFormat

/**
 * 竞赛成绩表。
 *
 * 站点一场竞赛 26 道题、几十名选手 —— 是一张宽表格。手机上一屏放不下，
 * 做法：**表头与数据行共享同一个横向滚动**（左右滑时列永远对齐），纵向用
 * LazyColumn 复用行。每列固定宽度，分数格按 VerdictColors 语义着色
 * （满分绿 / 部分分橙 / 0 分红 / 未提交灰），当前用户的整行用 secondaryContainer 标出。
 */
private val RANK_WIDTH = 52.dp
private val USER_WIDTH = 116.dp
private val TOTAL_WIDTH = 64.dp
private val CELL_WIDTH = 54.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContestScoreboardScreen(
    state: ScoreboardUiState,
    selfUid: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    /** 点带 rid 的成绩格 → 查看这笔计分提交的详情。 */
    onOpenRecord: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("成绩表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                },
            )
        },
    ) { padding ->
        val board = state.board
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            board == null -> ErrorState(
                state.error ?: "暂无成绩数据",
                Modifier.padding(padding),
                onRetry,
            )
            else -> ScoreboardBody(board, selfUid, state.lastSyncAt, Modifier.padding(padding), onOpenRecord)
        }
    }
}

@Composable
private fun ScoreboardBody(
    board: Scoreboard,
    selfUid: Int,
    lastSyncAt: Long?,
    modifier: Modifier,
    onOpenRecord: (String) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        // 同步状态行：让「实时刷新」对用户可见可预期
        val syncLabel = TimeFormat.clock(lastSyncAt)
            ?.let { "每 10 秒自动刷新 · 最近同步 $it" }
            ?: "每 10 秒自动刷新"
        Text(
            text = syncLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        val tableWidth = RANK_WIDTH + USER_WIDTH + TOTAL_WIDTH + CELL_WIDTH * board.columns.size
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(tableWidth)) {
                HeaderRow(board)
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    items(board.rows, key = { it.uid }) { row ->
                        ScoreRowView(row, isSelf = selfUid > 0 && row.uid == selfUid, onOpenRecord)
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(board: Scoreboard) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("#", RANK_WIDTH)
        HeaderCell("选手", USER_WIDTH, TextAlign.Start, PaddingValues(start = 8.dp))
        HeaderCell("总分", TOTAL_WIDTH)
        board.columns.forEach { col ->
            HeaderCell(col.label, CELL_WIDTH)
        }
    }
}

@Composable
private fun ScoreRowView(
    row: ScoreboardRow,
    isSelf: Boolean,
    onOpenRecord: (String) -> Unit,
) {
    val bg = if (isSelf) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.rank,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(RANK_WIDTH),
        )
        Text(
            text = row.uname,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(USER_WIDTH),
        )
        Text(
            text = row.totalScore,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(TOTAL_WIDTH),
        )
        row.cells.forEach { cell ->
            ScoreCellView(cell, onOpenRecord)
        }
    }
}

@Composable
private fun ScoreCellView(cell: ScoreCell, onOpenRecord: (String) -> Unit) {
    val palette = VerdictColors.current()
    // 判定色语义（与全 App 一致，颜色是信息不是装饰）：
    // 满分 = 通过绿；有提交未满分 = 部分分橙；0 分 = 红；未提交 = 灰。
    val (color, bold) = when {
        cell.score == 100 -> palette.accepted to FontWeight.Bold
        cell.score != null && cell.score > 0 -> palette.timeLimit to FontWeight.Normal
        cell.score == 0 && cell.rid != null -> palette.wrongAnswer to FontWeight.Normal
        else -> MaterialTheme.colorScheme.onSurfaceVariant to FontWeight.Normal
    }
    val cellModifier = cell.rid
        ?.let { rid -> Modifier.clickable { onOpenRecord(rid) } }
        ?: Modifier
    Box(
        modifier = Modifier
            .width(CELL_WIDTH)
            .then(cellModifier)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.scoreText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = bold,
            textAlign = TextAlign.Center,
            color = color,
        )
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign = TextAlign.Center, padding: PaddingValues = PaddingValues()) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1,
        modifier = Modifier
            .width(width)
            .padding(padding),
    )
}
