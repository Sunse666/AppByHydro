package com.jxau.oj.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.BuildConfig
import com.jxau.oj.core.AppContainer
import com.jxau.oj.data.model.User
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.net.SessionStore
import com.jxau.oj.ui.theme.DEFAULT_EDITOR_FONT_ID
import com.jxau.oj.ui.theme.DEFAULT_EDITOR_FONT_SP
import com.jxau.oj.ui.theme.DEFAULT_THEME_ID
import com.jxau.oj.ui.theme.EDITOR_FONTS
import com.jxau.oj.ui.theme.normalizeEditorFontSp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val themeId: String = DEFAULT_THEME_ID,
    val darkMode: Int = SessionStore.DARK_MODE_SYSTEM,
    /** 代码字体 id，见 [EDITOR_FONTS]。 */
    val editorFontId: String = DEFAULT_EDITOR_FONT_ID,
    /** 编辑器字号（sp）。 */
    val editorFontSizeSp: Int = DEFAULT_EDITOR_FONT_SP,
    val user: User = User.GUEST,
    val userLoaded: Boolean = false,
    val loginBusy: Boolean = false,
    val loginError: String? = null,
    val message: String? = null,
)

/**
 * 应用级状态：主题、深色模式偏好、登录态。
 *
 * 登录态判据是 `GET /api/user` 的 `_id > 0 && role != "guest"`（2026-09-13 实测：
 * 该接口登录与否都**没有** `authn` 字段；游客恒为 `_id:0, role:"guest"`）。
 * 因此登录成功后一律回查一次，不依赖登录接口自身的响应体结构。
 */
class AppViewModel(val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        AppUiState(
            themeId = container.sessionStore.themeId ?: DEFAULT_THEME_ID,
            darkMode = container.sessionStore.darkMode,
            editorFontId = container.sessionStore.editorFontId ?: DEFAULT_EDITOR_FONT_ID,
            editorFontSizeSp = normalizeEditorFontSp(container.sessionStore.editorFontSizeSp),
        ),
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        refreshUser()
    }

    fun refreshUser() {
        viewModelScope.launch {
            val user = reloadUser()
            _state.update { it.copy(user = user, userLoaded = true) }
        }
    }

    fun setTheme(themeId: String) {
        container.sessionStore.themeId = themeId
        _state.update { it.copy(themeId = themeId) }
    }

    fun setDarkMode(mode: Int) {
        container.sessionStore.darkMode = mode
        _state.update { it.copy(darkMode = mode) }
    }

    /** 代码字体选择。落盘后全局生效（编辑器与只读代码块共用）。 */
    fun setEditorFont(id: String) {
        container.sessionStore.editorFontId = id
        _state.update { it.copy(editorFontId = id) }
    }

    /** 编辑器字号。越界值一律归一，避免落盘坏值。 */
    fun setEditorFontSize(sp: Int) {
        val size = normalizeEditorFontSp(sp)
        container.sessionStore.editorFontSizeSp = size
        _state.update { it.copy(editorFontSizeSp = size) }
    }

    fun login(uname: String, password: String) {
        if (_state.value.loginBusy) return
        _state.update { it.copy(loginBusy = true, loginError = null) }
        viewModelScope.launch {
            val res = container.authRepository.login(uname.trim(), password)
            // Success 与 TransportError 都不能断定登录失败（后者可能只是响应体结构
            // 与预期不符），所以统一用 /api/user 复核登录态。
            val explicitError = when (res) {
                is HydroResult.NeedLogin -> "站点要求重新验证，请重试一次"
                is HydroResult.Failure -> friendlyLoginError(res)
                else -> null
            }
            val user = reloadUser()
            if (BuildConfig.DEBUG) {
                Log.d("JXAU_DEBUG", "login done: res=${res::class.simpleName} " +
                    "loggedIn=${user.isLoggedIn} cookies=${container.debugCookies()}")
            }
            _state.update {
                it.copy(
                    loginBusy = false,
                    user = user,
                    userLoaded = true,
                    loginError = when {
                        user.isLoggedIn -> null
                        explicitError != null -> explicitError
                        // 关键区分：请求成功送达但会话没生效 —— 这**不是**密码错误，
                        // 按老文案报"密码错误"会把用户引向死胡同。
                        res is HydroResult.Success ->
                            "登录请求已送达，但会话未能生效。这不是密码问题，请重试一次；" +
                                "若反复出现请在电脑浏览器登录站点验证账号状态"
                        else -> "登录失败，请检查网络后重试"
                    },
                )
            }
        }
    }

    /**
     * 把站点的错误信封翻译成对用户有操作价值的中文。
     * name 来自 Hydro 源码（error.ts），message 的 `{0}` 占位符已在分发层替换。
     */
    private fun friendlyLoginError(res: HydroResult.Failure): String = when (res.name) {
        "UserNotFoundError" -> "用户不存在：请检查用户名或邮箱拼写"
        "VerifyPasswordError" -> "密码不正确"
        "OpcountExceededError" -> "尝试过于频繁，请等待约一分钟后再试"
        "BuiltinLoginError" -> "站点已停用内置账号登录"
        "ValidationError" ->
            if (res.params.contains("2FA") || res.params.contains("Authn"))
                "该账号启用了两步验证/安全密钥，App 暂不支持，请在浏览器中登录"
            else res.message
        else -> res.message
    }

    fun logout() {
        viewModelScope.launch {
            container.authRepository.logout()
            container.clearSession()
            val user = reloadUser()
            _state.update { it.copy(user = user, message = "已退出登录") }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private suspend fun reloadUser(): User =
        when (val res = container.authRepository.currentUser()) {
            is HydroResult.Success -> {
                res.data.defaultCodeLang?.let { container.sessionStore.defaultLang = it }
                res.data
            }
            // 站点不可达时不应把用户"降级"成游客，否则会误显示为已登出
            else -> _state.value.user.takeIf { it.isLoggedIn } ?: User.GUEST
        }
}
