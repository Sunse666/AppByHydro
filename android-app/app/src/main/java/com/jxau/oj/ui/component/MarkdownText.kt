package com.jxau.oj.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 题干渲染。
 *
 * **块级/行内解析已抽到同包的 `MarkdownParser.kt`**（`internal fun parseBlocks` / `inline`），
 * 本文件只管把它们画出来 —— 抽出的目的是让解析器能进离线自检（`tools/pure_helpers_check`
 * 的 U 组）。渲染侧的硬约束仍然是「颜色只能从 Material 3 令牌派生」。
 *
 * 为什么自己写而不用现成库：
 * - 离线环境装不了新依赖；
 * - 站点题干实际用到的 Markdown 子集很窄（标题 / 列表 / 代码块 / 行内代码 / 粗体 / 链接）；
 * - 关键是**颜色必须从 Material 3 令牌派生**，第三方库的默认配色是网页味的，
 *   会直接破坏原生观感（方案 5.0 的硬约束）。
 *
 * 已知限制：LaTeX 公式（`$...$`）目前只做等宽高亮占位，真正的排版需要 KaTeX，
 * 那要走 WebView —— 与编辑器一起在后续接入。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val colors = MaterialTheme.colorScheme
    val codeBg = colors.surfaceContainerHighest
    val codeFg = colors.onSurface

    Column(modifier) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> {
                    Text(
                        text = inline(block.text, codeBg, codeFg, colors.primary),
                        style = headingStyle(block.level),
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = inline(block.text, codeBg, codeFg, colors.primary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                is MdBlock.CodeBlock -> {
                    CodeBlock(block)
                    Spacer(Modifier.height(10.dp))
                }

                is MdBlock.ListItem -> {
                    Row(Modifier.padding(start = (block.depth * 16).dp)) {
                        Text(
                            text = if (block.ordered) "${block.index}." else "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.width(if (block.ordered) 26.dp else 16.dp),
                        )
                        Text(
                            text = inline(block.text, codeBg, codeFg, colors.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                is MdBlock.Quote -> {
                    // 竖线高度用 IntrinsicSize.Min 贴合内容，引用多长就多长
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(colors.outlineVariant),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = inline(block.text, codeBg, codeFg, colors.primary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                MdBlock.Divider -> {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

/** 代码块的等宽行高，与正文拉开层次但不至于太松。 */
private const val CODE_LINE_HEIGHT_SP = 20

@Composable
private fun CodeBlock(block: MdBlock.CodeBlock) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column {
            if (block.lang.isNotBlank()) {
                Text(
                    text = block.lang,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
            // 题面里的代码块宽度不可控，横向滚动比自动换行更容易读
            Text(
                text = block.code,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = CODE_LINE_HEIGHT_SP.sp,
                ),
                color = colors.onSurface,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.headlineSmall
    2 -> MaterialTheme.typography.titleLarge
    3 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}.copy(fontWeight = FontWeight.SemiBold)
