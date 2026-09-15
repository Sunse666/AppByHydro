package com.jxau.oj.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 判题状态色。
 *
 * **刻意独立于 Material 3 的 ColorScheme。**
 *
 * AC 绿 / WA 红 / TLE 橙是"信息"，不是"装饰"。一旦让它们跟随主题，
 * 用户切到「竹青」主题后"答案错误"会显示成绿色 —— 语义直接反过来。
 *
 * 因此：禁止用 `colorScheme.primary` 表达任何判题结果，也禁止把本文件的颜色
 * 接进动态取色。
 */
@Immutable
data class VerdictPalette(
    val accepted: Color,
    val wrongAnswer: Color,
    val timeLimit: Color,
    val memoryLimit: Color,
    val runtimeError: Color,
    val compileError: Color,
    val systemError: Color,
    val pending: Color,
    val unknown: Color,
)

private val LightVerdicts = VerdictPalette(
    accepted = Color(0xFF2E7D32),
    wrongAnswer = Color(0xFFC62828),
    timeLimit = Color(0xFFE65100),
    memoryLimit = Color(0xFF6A1B9A),
    runtimeError = Color(0xFFAD1457),
    compileError = Color(0xFF00838F),
    systemError = Color(0xFF546E7A),
    pending = Color(0xFF616161),
    unknown = Color(0xFF757575),
)

private val DarkVerdicts = VerdictPalette(
    accepted = Color(0xFF81C995),
    wrongAnswer = Color(0xFFF28B82),
    timeLimit = Color(0xFFFFB74D),
    memoryLimit = Color(0xFFCE93D8),
    runtimeError = Color(0xFFF48FB1),
    compileError = Color(0xFF4DD0E1),
    systemError = Color(0xFFB0BEC5),
    pending = Color(0xFFBDBDBD),
    unknown = Color(0xFF9E9E9E),
)

object VerdictColors {

    /**
     * 当前该用哪套调色板。
     *
     * 取值优先级：应用内的深色偏好（[LocalIsDarkTheme]）→ 系统设置。
     * 前者优先是必须的：用户强制深色时，系统可能仍是浅色。
     */
    @Composable
    fun current(): VerdictPalette =
        if (isDarkNow()) DarkVerdicts else LightVerdicts

    /** 按判题状态键取色，跟随当前主题深浅。绝大多数调用点都应该用这个。 */
    @Composable
    fun colorOf(key: String?): Color = of(key, isDarkNow())

    @Composable
    private fun isDarkNow(): Boolean =
        LocalIsDarkTheme.current ?: isSystemInDarkTheme()

    /** 按判题状态键取色。键名来自站点（AC / WA / TLE / MLE / RE / CE / SE …）。 */
    fun of(key: String?, dark: Boolean): Color {
        val palette = if (dark) DarkVerdicts else LightVerdicts
        return when (key?.uppercase()) {
            "AC", "ACCEPTED" -> palette.accepted
            "WA", "WRONG_ANSWER" -> palette.wrongAnswer
            "TLE", "TIME_LIMIT_EXCEEDED" -> palette.timeLimit
            "MLE", "MEMORY_LIMIT_EXCEEDED" -> palette.memoryLimit
            "RE", "RUNTIME_ERROR" -> palette.runtimeError
            "CE", "COMPILE_ERROR" -> palette.compileError
            "SE", "SYSTEM_ERROR" -> palette.systemError
            "PENDING", "WAITING", "JUDGING", "FETCHED", "COMPILING" -> palette.pending
            else -> palette.unknown
        }
    }

    /**
     * 判题状态的中文名。
     *
     * 界面上必须**同时**给出文字与颜色：色盲用户无法仅凭颜色区分
     * AC 与 WA，这是无障碍硬要求（见方案 10.2）。
     */
    fun label(key: String?): String = when (key?.uppercase()) {
        "AC", "ACCEPTED" -> "通过"
        "WA", "WRONG_ANSWER" -> "答案错误"
        "TLE", "TIME_LIMIT_EXCEEDED" -> "超时"
        "MLE", "MEMORY_LIMIT_EXCEEDED" -> "内存超限"
        "RE", "RUNTIME_ERROR" -> "运行错误"
        "CE", "COMPILE_ERROR" -> "编译错误"
        "SE", "SYSTEM_ERROR" -> "系统错误"
        "PENDING", "WAITING" -> "等待评测"
        "JUDGING", "COMPILING", "FETCHED" -> "评测中"
        "IGN", "IGNORED" -> "已忽略"
        else -> key?.ifBlank { "未知" } ?: "未知"
    }
}
