package com.jxau.oj.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * `LocalIsDarkTheme` 的最小替身（仅供 `tools/pure_helpers_check` 离线编译用）。
 *
 * 真身在 `ui/theme/AppTheme.kt`，但那个文件把整套 Material 3 主题（`lightColorScheme` /
 * `darkColorScheme` / 字体 / 主题目录）都拖了进来，离线编译代价不划算。
 * 被测的 `VerdictColors` 只需要这个符号存在、且类型是 `CompositionLocal<Boolean?>`。
 */
val LocalIsDarkTheme = staticCompositionLocalOf<Boolean?> { null }
