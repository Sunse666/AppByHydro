package com.jxau.oj.ui.homework

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.Homework
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.ContestRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeworkListUiState(
    val items: List<Homework> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextPage: Int = 1,
    val hasMore: Boolean = false,
) {
    val showEmpty: Boolean get() = !loading && error == null && items.isEmpty()
}

/** 作业（题单）列表。契约【实测】（`GET /homework`），翻页策略与竞赛列表一致：拉到空页为止。 */
class HomeworkListViewModel(private val repo: ContestRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeworkListUiState())
    val state: StateFlow<HomeworkListUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _state.update {
            it.copy(loading = true, error = null, items = emptyList(), nextPage = 1, hasMore = false)
        }
        job = viewModelScope.launch {
            when (val res = repo.homework(page = 1)) {
                is HydroResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        items = res.data,
                        nextPage = 2,
                        hasMore = res.data.isNotEmpty(),
                    )
                }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(loading = false, error = "这个站点要求登录后才能看作业") }
                is HydroResult.Failure ->
                    _state.update { it.copy(loading = false, error = res.friendly) }
                is HydroResult.TransportError ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.loadingMore || !snapshot.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        val page = snapshot.nextPage

        viewModelScope.launch {
            when (val res = repo.homework(page = page)) {
                is HydroResult.Success -> _state.update {
                    // 去重按稳定标识：_id 优先（docId 可能整体缺失回落 0，实测教训）
                    val merged = (it.items + res.data).distinctBy { hw ->
                        hw.id.ifBlank { "doc-${hw.docId}" }
                    }
                    it.copy(
                        loadingMore = false,
                        items = merged,
                        nextPage = page + 1,
                        hasMore = res.data.isNotEmpty(),
                    )
                }
                else -> _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
