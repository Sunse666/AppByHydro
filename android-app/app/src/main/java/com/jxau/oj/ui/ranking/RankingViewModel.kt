package com.jxau.oj.ui.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.RankedUser
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RankingUiState(
    val users: List<RankedUser> = emptyList(),
    val totalUsers: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextPage: Int = 1,
    val hasMore: Boolean = true,
) {
    val showEmpty: Boolean get() = !loading && error == null && users.isEmpty()
}

/**
 * 排行榜。
 *
 * 契约是【实测】的（`/ranking` 匿名即可访问，见方案 3.4.4），所以这里**不需要**
 * "契约不符"的兜底话术 —— 拉不到就是真的网络或站点问题，如实报错即可。
 *
 * 名次由客户端推算：站点不下发名次，所以用 `已累计条数` 作为偏移量传给仓库，
 * 这样分页到第 2 页时名次不会从 1 重新开始。
 */
class RankingViewModel(private val repo: UserRepository) : ViewModel() {

    private val _state = MutableStateFlow(RankingUiState())
    val state: StateFlow<RankingUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _state.update {
            it.copy(loading = true, error = null, users = emptyList(), nextPage = 1, hasMore = true)
        }
        job = viewModelScope.launch {
            when (val res = repo.ranking(page = 1, rankOffset = 0)) {
                is HydroResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        users = res.data.users,
                        totalUsers = res.data.totalUsers,
                        nextPage = 2,
                        hasMore = res.data.hasMore(loadedCount = res.data.users.size),
                    )
                }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(loading = false, error = "这个站点要求登录后才能看排行") }
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
        val offset = snapshot.users.size
        viewModelScope.launch {
            when (val res = repo.ranking(page = page, rankOffset = offset)) {
                is HydroResult.Success -> _state.update {
                    // 站点在同一页重复返回同一个人时按 id 去重 —— 分页边界上这很常见，
                    // 否则列表里会出现两条一模一样的记录。
                    val merged = (it.users + res.data.users).distinctBy { user -> user.id }
                    it.copy(
                        loadingMore = false,
                        users = merged,
                        nextPage = page + 1,
                        totalUsers = if (res.data.totalUsers > 0) res.data.totalUsers else it.totalUsers,
                        hasMore = res.data.hasMore(loadedCount = merged.size),
                    )
                }
                // 翻页失败不清空已有内容，也不弹全屏错误 —— 用户已经在看的东西不该消失
                else -> _state.update { it.copy(loadingMore = false, hasMore = false) }
            }
        }
    }
}
