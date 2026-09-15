package com.jxau.oj.ui.contest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.model.Contest
import com.jxau.oj.ui.component.EmptyState
import com.jxau.oj.ui.component.ErrorState
import com.jxau.oj.ui.component.LoadingState
import com.jxau.oj.ui.component.PhaseBadge
import com.jxau.oj.ui.util.TimeFormat
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContestListScreen(
    state: ContestListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenContest: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("竞赛") },
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
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error, onRetry = onRefresh)
                state.showEmpty -> EmptyState(
                    title = "还没有竞赛",
                    description = "站点发布竞赛后会出现在这里",
                )
                else -> ContestBody(state, onLoadMore, onOpenContest)
            }
        }
    }
}

@Composable
private fun ContestBody(
    state: ContestListUiState,
    onLoadMore: () -> Unit,
    onOpenContest: (String) -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.items.size, state.hasMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (state.hasMore && !state.loadingMore && lastVisible >= state.items.size - 3) {
                    onLoadMore()
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // key 用每文档唯一的 _id；_id 意外为空时退回下标 —— 绝不用 docId 当 key，
        // 它在列表响应里可能整体缺失（曾全部回落 0 → key 重复 → 渲染即崩，实测）。
        itemsIndexed(items = state.items, key = { index, item ->
            item.id.ifBlank { "idx-$index" }
        }) { _, contest ->
            ContestRow(
                contest = contest,
                // tid 用 _id 原样字符串（列表响应里 docId 缺失，实测教训）；
                // 两者都拿不到时才不可点。
                onClick = {
                    val tid = contest.id.ifBlank { contest.docId.takeIf { id -> id > 0 }?.toString().orEmpty() }
                    if (tid.isNotBlank()) onOpenContest(tid)
                },
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.loadingMore) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Box(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ContestRow(contest: Contest, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .padding(6.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = contest.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = timeRange(contest.beginAt, contest.endAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (contest.attend > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${contest.attend} 人参加",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))
            PhaseBadge(contest.phase)
        }
    }
}

/** 起止时间。只显示有数据的部分，解析失败就少显示一段而不是显示 1970。 */
internal fun timeRange(beginAt: String?, endAt: String?): String {
    val begin = TimeFormat.dateTime(beginAt) ?: return "时间未定"
    val end = TimeFormat.dateTime(endAt) ?: return "$begin 起"
    return "$begin ~ $end"
}
