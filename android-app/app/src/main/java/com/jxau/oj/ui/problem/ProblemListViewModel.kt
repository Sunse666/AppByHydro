package com.jxau.oj.ui.problem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.ProblemSummary
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.ProblemRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProblemListUiState(
    val query: String = "",
    val items: List<ProblemSummary> = emptyList(),
    val page: Int = 1,
    val totalCount: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
) {
    val hasMore: Boolean get() = items.size < totalCount
    val showEmpty: Boolean get() = !loading && error == null && items.isEmpty()
}

class ProblemListViewModel(private val repo: ProblemRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProblemListUiState())
    val state: StateFlow<ProblemListUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun clearQuery() {
        _state.update { it.copy(query = "") }
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null, items = emptyList(), page = 1) }
        val query = _state.value.query
        loadJob = viewModelScope.launch {
            when (val res = repo.list(page = 1, query = query)) {
                is HydroResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        items = res.data.items,
                        totalCount = res.data.totalCount,
                        page = 1,
                    )
                }
                is HydroResult.NeedLogin -> _state.update { it.copy(loading = false, error = "需要登录后才能查看") }
                is HydroResult.Failure -> _state.update { it.copy(loading = false, error = res.friendly) }
                is HydroResult.TransportError -> _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.loadingMore || !snapshot.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        val nextPage = snapshot.page + 1
        viewModelScope.launch {
            when (val res = repo.list(page = nextPage, query = snapshot.query)) {
                is HydroResult.Success -> _state.update {
                    it.copy(
                        loadingMore = false,
                        page = nextPage,
                        items = it.items + res.data.items,
                    )
                }
                else -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
