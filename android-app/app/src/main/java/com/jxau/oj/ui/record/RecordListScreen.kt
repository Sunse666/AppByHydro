package com.jxau.oj.ui.record

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.model.Submission
import com.jxau.oj.ui.component.EmptyState
import com.jxau.oj.ui.component.ErrorState
import com.jxau.oj.ui.component.LoadingState
import com.jxau.oj.ui.theme.VerdictColors
import com.jxau.oj.ui.util.TimeFormat
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    title: String,
    state: RecordListUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        when {
            state.loading -> LoadingState(Modifier.padding(padding))

            state.needLogin -> NeedLoginState(Modifier.padding(padding), onLogin)

            // 契约不符必须与"没有记录"分开说，否则用户会以为自己没交过题
            state.contractMismatch -> ContractMismatchState(Modifier.padding(padding), onRefresh)

            state.error != null -> ErrorState(state.error, Modifier.padding(padding), onRefresh)

            state.showEmpty -> EmptyState(
                title = "还没有提交记录",
                description = "做完一道题提交后，记录会出现在这里",
            )

            else -> RecordBody(state, onLoadMore, onOpenRecord, Modifier.padding(padding))
        }
    }
}

@Composable
private fun NeedLoginState(modifier: Modifier, onLogin: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "提交记录只对自己可见",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "登录后就能看到自己的提交与评测结果",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogin) {
            Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("去登录")
        }
    }
}

/**
 * 请求成功了，但响应里没有我们认识的记录数组。
 *
 * 措辞要点：**不能**说成"你没有提交记录"，也不能说成"加载失败" ——
 * 事实是"App 还没适配站点的返回格式"，责任在客户端而不是用户或站点。
 */
@Composable
private fun ContractMismatchState(modifier: Modifier, onRetry: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "记录列表暂时读不出来",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "请求已成功发出，但站点返回的数据结构与 App 预期的不一致 —— " +
                "不是你没有提交过，而是这块还没适配完（该链路尚未经 M0 验证）。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun RecordBody(
    state: RecordListUiState,
    onLoadMore: () -> Unit,
    onOpenRecord: (String) -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = state.items,
            // rid 是列表 key。空白 rid 会退化成位置索引，虽然不理想但不会崩
            key = { it.rid.ifBlank { it.hashCode().toString() } },
        ) { record ->
            RecordRow(record = record, onClick = { onOpenRecord(record.rid) })
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    state.loadingMore -> {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.size(12.dp))
                        Text(
                            "加载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.showEndOfList -> Text(
                        text = if (state.totalCount > 0) {
                            "已到底部 · 共 ${state.totalCount} 条"
                        } else {
                            "已到底部"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Box(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: Submission, onClick: () -> Unit) {
    val color = VerdictColors.colorOf(record.statusKey)
    val label = VerdictColors.label(record.statusKey)

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态：颜色 + 文字同时给出。只靠颜色表达判题结果对色盲用户不可用（方案 10.2）
            Surface(
                color = color.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = 48.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = record.title.ifBlank { record.pid.ifBlank { "（未知题目）" } },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = metaLine(record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(8.dp))

            // 提交时刻。它优先来自 rid 的 ObjectId 反解，所以即使字段名猜错也显示得出来
            Text(
                text = TimeFormat.relativeFromMillis(record.submitAtMs).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )

            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 副行信息：语言 / 耗时 / 内存 / 提交者。
 *
 * 逐段拼接而不是写死模板 —— 站点缺哪个字段就不显示哪一段，
 * 而不是显示成 `0 ms / 0 KB` 让人误以为是真实数据。
 */
private fun metaLine(record: Submission): String {
    val parts = mutableListOf<String>()
    if (record.langKey.isNotBlank()) parts += record.langKey
    if (record.timeMs > 0) parts += "${record.timeMs} ms"
    if (record.memoryKb > 0) parts += "${record.memoryKb} KB"
    if (record.score > 0) parts += "${record.score} 分"
    if (!record.submitterName.isNullOrBlank()) parts += record.submitterName
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}
