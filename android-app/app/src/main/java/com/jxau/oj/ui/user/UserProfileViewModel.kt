package com.jxau.oj.ui.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.UserProfile
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val uid: Int = 0,
    val profile: UserProfile? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * 用户主页。
 *
 * 契约【实测】（`GET /user/:uid` → `{isSelfProfile, udoc}`，见方案 3.4.4），
 * 所以不需要"契约不符"的兜底话术。
 *
 * `isSelf` 直接取站点下发的 `isSelfProfile`，**不拿本地 uid 去比** ——
 * 本地 uid 在换账号、或者游客态下可能不可靠，站点自己最清楚。
 */
class UserProfileViewModel(
    private val repo: UserRepository,
    private val uid: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(UserProfileUiState(uid = uid))
    val state: StateFlow<UserProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repo.profile(uid)) {
                is HydroResult.Success ->
                    _state.update { it.copy(loading = false, profile = res.data) }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(loading = false, error = "需要登录后才能查看用户主页") }
                is HydroResult.Failure ->
                    _state.update { it.copy(loading = false, error = res.friendly) }
                is HydroResult.TransportError ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }
}
