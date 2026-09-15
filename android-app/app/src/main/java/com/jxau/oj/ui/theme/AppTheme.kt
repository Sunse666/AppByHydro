package com.jxau.oj.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 当前是否处于深色。
 *
 * 存在的理由：应用支持**强制深色 / 强制浅色 / 跟随系统**三档偏好，
 * 而 `isSystemInDarkTheme()` 只反映系统设置。用户强制深色而系统是浅色时，
 * 两者会不一致 —— 依赖系统值去挑判题调色板，会让颜色落在错误的底色上（对比度不达标）。
 *
 * 默认值为 null 表示"没有人提供"，此时调用方回落到 `isSystemInDarkTheme()`：
 * 这样在预览或单元环境里也不会拿到错误的默认值。
 */
val LocalIsDarkTheme = staticCompositionLocalOf<Boolean?> { null }

/**
 * 把选中的主题应用到整棵树。
 *
 * 主题来源是 [THEME_CATALOG]（由 `tools/gen_themes.py` 从 `design/theme-seeds.json` 生成）。
 * 设置页直接遍历该目录渲染，**新增主题不需要改任何 UI 代码**。
 */
@Composable
fun JxauOjTheme(
    themeId: String,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val selected = THEME_CATALOG.firstOrNull { it.id == themeId } ?: THEME_CATALOG.first()
    val scheme = selected.staticScheme(darkTheme) ?: dynamicOrFallback(context, darkTheme)

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = JxauTypography,
            shapes = JxauShapes,
            content = content,
        )
    }
}

/**
 * `FOLLOW_WALLPAPER` 主题走动态取色；Android 12 以下没有该能力，回落到默认静态主题。
 */
private fun dynamicOrFallback(context: Context, dark: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // DEFAULT_THEME_ID 一定是静态主题，这里不会为 null
        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
        THEME_CATALOG.first { it.id == DEFAULT_THEME_ID }.staticScheme(dark)
            ?: error("默认主题必须是静态色板")
    }

/**
 * 先用 Material 3 默认排版。
 * 中文字重与行高需要真机校对，放到打磨阶段统一调 —— 现在不预设。
 */
val JxauTypography = Typography()

val JxauShapes = Shapes()
