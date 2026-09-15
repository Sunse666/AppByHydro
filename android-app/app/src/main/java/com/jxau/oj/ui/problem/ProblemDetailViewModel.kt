package com.jxau.oj.ui.problem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.ProblemDetail
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.ProblemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProblemDetailUiState(
    val loading: Boolean = true,
    val detail: ProblemDetail? = null,
    val error: String? = null,
)

class ProblemDetailViewModel(
    private val repo: ProblemRepository,
    private val docId: Int,
    /** 竞赛/作业上下文；从题库进来的公开题为空串。见 [ProblemRepository.detail]。 */
    private val tid: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(ProblemDetailUiState())
    val state: StateFlow<ProblemDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = repo.detail(docId, tid.takeIf { it.isNotBlank() })) {
                is HydroResult.Success -> _state.update {
                    it.copy(loading = false, detail = res.data)
                }
                is HydroResult.NeedLogin -> _state.update {
                    it.copy(loading = false, error = "需要登录后才能查看这道题")
                }
                is HydroResult.Failure -> _state.update {
                    it.copy(loading = false, error = res.friendly)
                }
                is HydroResult.TransportError -> _state.update {
                    it.copy(loading = false, error = res.message)
                }
            }
        }
    }
}
