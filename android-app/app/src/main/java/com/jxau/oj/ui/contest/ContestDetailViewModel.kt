package com.jxau.oj.ui.contest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.ContestDetail
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.ContestRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContestDetailUiState(
    val detail: ContestDetail? = null,
    /** docId → 题目名。拉取失败（未开始的竞赛、进行中未报名）时为空 map。 */
    val problemTitles: Map<Int, String> = emptyMap(),
    /** 题目名拉取失败的原因，仅当 [problemTitles] 为空且 detail 已加载时展示。 */
    val problemsNote: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * 竞赛详情。主契约【实测】（`GET /contest/:tid` → `{tdoc}`）。
 * 题目名来自 `GET /contest/:tid/problems` 的 `pdict`【实测 2026-09-13】，
 * 未开始 / 进行中未报名时该请求会失败 —— 属预期内，单独降级提示，不影响详情本体。
 */
class ContestDetailViewModel(
    private val repo: ContestRepository,
    private val tid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ContestDetailUiState())
    val state: StateFlow<ContestDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null, problemsNote = null) }
        viewModelScope.launch {
            when (val res = repo.contest(tid)) {
                is HydroResult.Success -> {
                    _state.update { it.copy(loading = false, detail = res.data) }
                    loadProblemTitles()
                }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(loading = false, error = "需要登录后才能查看这场竞赛") }
                is HydroResult.Failure ->
                    _state.update { it.copy(loading = false, error = res.friendly) }
                is HydroResult.TransportError ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    private suspend fun loadProblemTitles() {
        when (val res = repo.contestProblemTitles(tid)) {
            is HydroResult.Success ->
                _state.update { it.copy(problemTitles = res.data, problemsNote = null) }
            is HydroResult.Failure ->
                _state.update { it.copy(problemsNote = "题目名称需报名竞赛（或等竞赛结束后）可见：${res.friendly}") }
            else ->
                _state.update { it.copy(problemsNote = "暂时取不到题目名称") }
        }
    }
}
