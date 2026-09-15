package com.jxau.oj.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 用户头像。榜单、用户主页、"我的"三处共用，避免各写一份。
 *
 * 三层兜底（2026-09-15 起，替换"失败就留空洞"的旧行为）：
 * 1. 有地址且加载成功 → 图片；
 * 2. **加载中 / 加载失败 / 无地址，但知道名字 → 首字母色块**（底色从 M3 容器色
 *    按名字稳定派生，同一个人永远同色）—— gravatar 域名国内不可达，那几位用户
 *    从此不再是空洞；
 * 3. 连名字都没有 → 人形图标。
 *
 * 实现上刻意让色块永远垫在 `AsyncImage` 底下：Coil 加载中/失败时什么都不画，
 * 底下的色块自然露出来；成功时图片整个盖住它。不需要监听加载状态，也没有
 * 「成功前一帧是空白」的闪烁。颜色全部取自 colorScheme（UI 层禁硬编码色）。
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    /** 用于首字母兜底的名字（uname）；null 时回落人形图标。 */
    name: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fallbackContainer(name).first),
    ) {
        AvatarFallback.initial(name)?.let { letter ->
            Text(
                text = letter,
                color = fallbackContainer(name).second,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center),
            )
        } ?: Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = fallbackContainer(name).second,
            modifier = Modifier.align(Alignment.Center).size(size * 0.6f),
        )

        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * 兜底色对（容器底色 / 其上文字色）。档位由名字哈希决定，四档全取自 M3 角色，
 * 不引入任何硬编码色。没有名字时统一用 secondaryContainer（与旧版占位一致）。
 */
@Composable
private fun fallbackContainer(name: String?): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (AvatarFallback.containerIndex(name)) {
        0 -> cs.primaryContainer to cs.onPrimaryContainer
        1 -> cs.secondaryContainer to cs.onSecondaryContainer
        2 -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.surfaceVariant to cs.onSurfaceVariant
    }
}
