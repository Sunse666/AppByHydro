package com.jxau.oj.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jxau.oj.data.net.SessionStore
import com.jxau.oj.ui.theme.AppTheme
import com.jxau.oj.ui.theme.BUNDLED_EDITOR_FONTS
import com.jxau.oj.ui.theme.EDITOR_FONTS
import com.jxau.oj.ui.theme.EDITOR_FONT_SAMPLE
import com.jxau.oj.ui.theme.EditorFont
import com.jxau.oj.ui.theme.MAX_EDITOR_FONT_SP
import com.jxau.oj.ui.theme.MIN_EDITOR_FONT_SP
import com.jxau.oj.ui.theme.THEME_CATALOG
import com.jxau.oj.ui.theme.staticScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeId: String,
    darkMode: Int,
    editorFontId: String,
    editorFontSizeSp: Int,
    onSelectTheme: (String) -> Unit,
    onSelectDarkMode: (Int) -> Unit,
    onSelectEditorFont: (String) -> Unit,
    onChangeEditorFontSize: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            SectionTitle("主题")
            Text(
                text = "内置 ${THEME_CATALOG.size} 套配色。全部由 Material 官方调色算法推导，" +
                    "浅深两套方案的正文对比度都不低于 4.5:1。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            THEME_CATALOG.forEach { theme ->
                ThemeRow(
                    theme = theme,
                    selected = theme.id == themeId,
                    onClick = { onSelectTheme(theme.id) },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("深色模式")
            DarkModeRow(
                label = "跟随系统",
                mode = SessionStore.DARK_MODE_SYSTEM,
                current = darkMode,
                onSelect = onSelectDarkMode,
            )
            DarkModeRow(
                label = "浅色",
                mode = SessionStore.DARK_MODE_LIGHT,
                current = darkMode,
                onSelect = onSelectDarkMode,
            )
            DarkModeRow(
                label = "深色",
                mode = SessionStore.DARK_MODE_DARK,
                current = darkMode,
                onSelect = onSelectDarkMode,
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("编辑器字体")
            Text(
                text = "只影响代码的显示方式，不影响提交内容。内置 ${EDITOR_FONTS.size} 款：" +
                    "默认的「系统等宽」不占安装包体积，其余 ${BUNDLED_EDITOR_FONTS.size} 款随包内置。" +
                    "带连字的字体里 `!=` `=>` 会连成一格，样张里那一处就能看出来。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            EDITOR_FONTS.forEach { font ->
                EditorFontRow(
                    font = font,
                    selected = font.id == editorFontId,
                    fontSizeSp = editorFontSizeSp,
                    onClick = { onSelectEditorFont(font.id) },
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(4.dp))
            FontSizeRow(sizeSp = editorFontSizeSp, onChange = onChangeEditorFontSize)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            SectionTitle("关于")
            Text(
                text = "JXAU OJ · 非官方移动客户端\n" +
                    "数据来自 oj.wbhtqlorz.cv（Hydro）。本应用不修改站点，仅以其公开接口交互。\n\n" +
                    bundledFontLicenseNote(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

/**
 * 「关于」段的字体许可说明 —— **由清单拼出来，不手写**。
 *
 * 手写版本每加一款字体就变成假话（本项目真踩过：加完 4 款之后，那段还在说
 * 「内置代码字体 Cascadia Mono 版权归 Microsoft…」，另外四份声明等于没提）。
 * 清单是唯一事实来源，这里只负责排版；`CheckPureHelpers` 的 V 组守着
 * 「每款打包字体都有 credit 且点名了对应声明文件」。
 */
private fun bundledFontLicenseNote(): String =
    "内置代码字体 ${BUNDLED_EDITOR_FONTS.size} 款，均为开源许可、随应用原样打包" +
        "（未子集化、未改名、未重排）：\n" +
        BUNDLED_EDITOR_FONTS.joinToString("\n") { "· ${it.credit}" } +
        "\n各款许可声明全文随安装包一并分发（res/raw/ 下，文件名见上）。"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ThemeRow(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 用两套浅色色板的色块做预览：无论当前是深色还是浅色模式，
    // 用户看到的都是同一套"色卡"，便于横向比较，而不是被当前模式干扰。
    val lightScheme = theme.staticScheme(dark = false)
    val swatches = listOfNotNull(
        lightScheme?.primary,
        lightScheme?.secondary,
        lightScheme?.tertiary,
        lightScheme?.error,
    )

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (swatches.isEmpty()) {
                    // 跟随壁纸主题没有固定色板，用一个描边圆点表示"由系统决定"
                    Box(
                        Modifier
                            .size(24.dp)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                } else {
                    swatches.forEach { color ->
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(color, CircleShape),
                        )
                    }
                }
            }

            Spacer(Modifier.size(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = theme.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = theme.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * 字体选项行。
 *
 * 样张**用该字体本身渲染**，并且跟随当前字号 —— 选之前就能看出合不合适，
 * 不用"选中了才知道"再退回来。
 */
@Composable
private fun EditorFontRow(
    font: EditorFont,
    selected: Boolean,
    fontSizeSp: Int,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = font.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "已选中",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            Text(
                text = font.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = EDITOR_FONT_SAMPLE,
                    style = TextStyle(
                        fontFamily = font.family,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp + 6).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 字号调节。数值本身不给输入框 —— 手机上点加减最不容易出错。 */
@Composable
private fun FontSizeRow(sizeSp: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("字号", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "编辑器与只读代码块共用 · 范围 " +
                    "$MIN_EDITOR_FONT_SP–$MAX_EDITOR_FONT_SP sp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = { onChange(sizeSp - 1) },
            enabled = sizeSp > MIN_EDITOR_FONT_SP,
        ) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = sizeSp.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp),
        )
        TextButton(
            onClick = { onChange(sizeSp + 1) },
            enabled = sizeSp < MAX_EDITOR_FONT_SP,
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DarkModeRow(
    label: String,
    mode: Int,
    current: Int,
    onSelect: (Int) -> Unit,
) {
    val selected = mode == current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = { onSelect(mode) })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = { onSelect(mode) })
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 给外部（如预览）用的一组占位色，避免在无主题上下文时崩溃。 */
internal val PlaceholderSwatch: Color = Color(0xFF185FA5)
