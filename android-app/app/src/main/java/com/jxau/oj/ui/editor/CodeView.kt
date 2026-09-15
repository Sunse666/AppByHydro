package com.jxau.oj.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jxau.oj.ui.theme.LocalEditorFontId
import com.jxau.oj.ui.theme.LocalEditorFontSizeSp
import com.jxau.oj.ui.theme.editorFontById

/**
 * 只读代码展示块（评测详情页"提交的代码"用）。
 *
 * 复用编辑器的 [SyntaxHighlighter] 做高亮 —— 同一套颜色与词法规则，
 * 不会出现"编辑器里是绿的、详情页里是紫的"这种割裂。
 *
 * 可选中复制（做题场景高频：把自己手机上交的代码拷回电脑）。
 * 横向可滚动：代码行等宽展示，**绝不折行** —— 折行会破坏缩进结构的可读性。
 */
@Composable
fun CodeView(
    code: String,
    languageKey: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val highlighter = remember(languageKey, colors) {
        SyntaxHighlighter.forLanguage(languageKey, syntaxColorsOf(colors))
    }
    val highlighted = remember(code, highlighter) { highlighter.highlight(code) }

    // 字体/字号与编辑器同源（设置页「编辑器字体」），避免同一份代码在两处长得不一样
    val fontId = LocalEditorFontId.current
    val fontFamily = remember(fontId) { editorFontById(fontId).family }
    val sizeSp = LocalEditorFontSizeSp.current

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
    ) {
        SelectionContainer {
            Text(
                text = highlighted,
                style = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = sizeSp.sp,
                    lineHeight = (sizeSp + 5).sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = false,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}
