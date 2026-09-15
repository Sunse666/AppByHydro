package com.jxau.oj.ui.contest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.Contest
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.ContestRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ContestListUiState(
    val items: List<Contest> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextPage: Int = 1,
    val hasMore: Boolean = false,
) {
    val showEmpty: Boolean get() = !loading && error == null && items.isEmpty()
}

/**
 * 竞赛列表。契约【实测】（`GET /contest`），失败如实报错即可。
 *
 * 站点当前只有 3 场竞赛，但分页结构照常实现 —— 站点将来加场次时不需要改代码。
 */
class ContestListViewModel(private val repo: ContestRepository) : ViewModel() {

    private val _state = MutableStateFlow(ContestListUiState())
    val state: StateFlow<ContestListUiState> = _state.asStateFlow()

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
            when (val res = repo.contests(page = 1)) {
                is HydroResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        items = res.data,
                        nextPage = 2,
                        // 保守翻页：站点场次少，下一页拉空自然停。tpcount 的语义不赌
                        hasMore = res.data.isNotEmpty(),
                    )
                }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(loading = false, error = "这个站点要求登录后才能看竞赛") }
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
            when (val res = repo.contests(page = page)) {
                is HydroResult.Success -> _state.update {
                    // 去重按稳定标识：_id 优先（每文档唯一），docId 可能整体缺失回落 0，
                    // 用它去重会把整页压成一条（实测教训）
                    val merged = (it.items + res.data).distinctBy { c ->
                        c.id.ifBlank { "doc-${c.docId}" }
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
