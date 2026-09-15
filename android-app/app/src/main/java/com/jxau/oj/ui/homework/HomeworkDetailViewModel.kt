package com.jxau.oj.ui.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.HomeworkDetail
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.ContestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeworkDetailUiState(
    val detail: HomeworkDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** 认领请求进行中。 */
    val claiming: Boolean = false,
    /** 认领结果提示（认领成功/失败的一句话）。由 UI 消费后置空。 */
    val claimMessage: String? = null,
)

/**
 * 作业详情。契约【实测 2026-09-13】：题目在 `pdict`（认领后或已截止才下发），
 * 认领状态在 `tsdoc.attend`；认领动作 = `POST /homework/:tid` 空 body。
 */
class HomeworkDetailViewModel(
    private val repo: ContestRepository,
    private val tid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeworkDetailUiState())
    val state: StateFlow<HomeworkDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repo.homeworkDetail(tid)) {
                is HydroResult.Success ->
                    _state.update { it.copy(loading = false, detail = res.data) }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(loading = false, error = "需要登录后才能查看这份作业") }
                is HydroResult.Failure ->
                    _state.update { it.copy(loading = false, error = res.friendly) }
                is HydroResult.TransportError ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    /** 认领作业。成功后重拉详情 —— 认领后题目列表（pdict）才会下发。 */
    fun claim() {
        val snapshot = _state.value
        if (snapshot.claiming || snapshot.detail?.claimed == true) return
        _state.update { it.copy(claiming = true, claimMessage = null) }
        viewModelScope.launch {
            when (val res = repo.claimHomework(tid)) {
                is HydroResult.Success -> {
                    _state.update { it.copy(claiming = false, claimMessage = "认领成功") }
                    load()
                }
                is HydroResult.Failure -> _state.update {
                    it.copy(claiming = false, claimMessage = "认领失败：${res.friendly}")
                }
                is HydroResult.NeedLogin -> _state.update {
                    it.copy(claiming = false, claimMessage = "登录状态已失效，请重新登录后再认领")
                }
                is HydroResult.TransportError -> _state.update {
                    it.copy(claiming = false, claimMessage = "认领失败：${res.message}")
                }
            }
        }
    }

    fun consumeClaimMessage() {
        _state.update { it.copy(claimMessage = null) }
    }
}
