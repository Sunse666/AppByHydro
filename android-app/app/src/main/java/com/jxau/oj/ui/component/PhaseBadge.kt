package com.jxau.oj.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jxau.oj.data.model.ContestPhase

/**
 * 竞赛/作业的阶段徽标（未开始 / 进行中 / 已结束）。
 *
 * 颜色用 Material 令牌的容器色，**不进判题语义色** —— 这是状态展示，不是判题结果。
 * 同时始终带文字：与判题状态同一条无障碍纪律，不靠颜色独占信息。
 */
@Composable
fun PhaseBadge(phase: ContestPhase, modifier: Modifier = Modifier) {
    val (label, container, content) = when (phase) {
        ContestPhase.UPCOMING -> Triple(
            "未开始",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ContestPhase.RUNNING -> Triple(
            "进行中",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ContestPhase.ENDED -> Triple(
            "已结束",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}
