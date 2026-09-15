package com.jxau.oj.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.RecordPage
import com.jxau.oj.data.model.Submission
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.SubmissionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 记录列表的范围。三个入口共用同一个页面与 ViewModel，
 * 只有请求参数不同 —— 没必要为"我的提交"和"某人的提交"写两套。
 *
 * 标题（"我的提交" / "TA 的提交记录" / "本题提交"）由导航层决定并传给页面，
 * 不放在这里 —— 视图文案不该由数据层推断。
 */
sealed interface RecordScope {
    /** 全站记录（`GET /record`）。 */
    data object All : RecordScope

    /** 某个用户的记录（`GET /record?uid=`）。自己的 uid 也走这条。 */
    data class OfUser(val uid: Int) : RecordScope

    /**
     * 某道题的记录。
     *
     * 参数形式 `fullStatus=true&pid=` 来自服务端自己下发的 `getSubmissionsUrl`【实测】，
     * 不是我们猜的 —— 这也是这条链路里唯一有实测依据的部分。
     */
    data class OfProblem(val docId: Int) : RecordScope
}

data class RecordListUiState(
    val items: List<Submission> = emptyList(),
    val totalCount: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    /** 登录态失效。UI 要给"去登录"的出路，而不是干说一句"需要登录"。 */
    val needLogin: Boolean = false,
    /**
     * 请求成功了，但响应里没有任何我们认识的记录数组键。
     * 这与"没有记录"是两件事，UI 必须分开说 —— 见 [RecordPage.contractMismatch]。
     */
    val contractMismatch: Boolean = false,
    val nextPage: Int = 1,
    val hasMore: Boolean = true,
) {
    /** 只有"确实没有记录"才算空。契约不符由 [contractMismatch] 单独表达。 */
    val showEmpty: Boolean
        get() = !loading && error == null && !needLogin && !contractMismatch && items.isEmpty()

    /** 是否是"加载完了但一条都没有"。用来决定底部提示写什么。 */
    val showEndOfList: Boolean
        get() = !hasMore && items.isNotEmpty()
}

/**
 * 提交记录列表。
 *
 * ⚠️ **这条链路的契约是【未验证】的**（`/record` 需要登录，M0 之前拿不到真实响应）。
 * 因此本类对失败的表述刻意分成三层，不允许含糊：
 * - 拉不动（网络/站点）→ 如实报错；
 * - 登录态失效 → 给"去登录"的出路；
 * - 拉到了但字段对不上 → 明说"App 还没适配站点返回格式"，**绝不冒充"你还没交过题"**。
 */
class RecordListViewModel(
    private val repo: SubmissionRepository,
    private val scope: RecordScope,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordListUiState())
    val state: StateFlow<RecordListUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _state.update {
            it.copy(
                loading = true,
                error = null,
                needLogin = false,
                contractMismatch = false,
                items = emptyList(),
                nextPage = 1,
                hasMore = true,
            )
        }
        job = viewModelScope.launch {
            when (val res = fetch(page = 1)) {
                is HydroResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        items = res.data.items,
                        totalCount = res.data.totalCount,
                        contractMismatch = res.data.contractMismatch,
                        nextPage = 2,
                        hasMore = res.data.hasMore(loadedCount = res.data.items.size),
                    )
                }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(loading = false, needLogin = true) }
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
            when (val res = fetch(page = page)) {
                is HydroResult.Success -> _state.update {
                    // 按 rid 去重：分页边界上同一条记录被返回两次很常见，
                    // 而列表的 key 用的是 rid —— 重复 key 会让 LazyColumn 直接抛异常。
                    val merged = (it.items + res.data.items).distinctBy { item -> item.rid }
                    it.copy(
                        loadingMore = false,
                        items = merged,
                        totalCount = if (res.data.totalCount > 0) res.data.totalCount else it.totalCount,
                        nextPage = page + 1,
                        hasMore = res.data.hasMore(loadedCount = merged.size),
                    )
                }
                // 翻页失败不清空已有内容，也不弹全屏错误 —— 用户正在看的东西不该消失
                else -> _state.update { it.copy(loadingMore = false, hasMore = false) }
            }
        }
    }

    private suspend fun fetch(page: Int): HydroResult<RecordPage> = when (scope) {
        is RecordScope.All -> repo.records(page = page)
        is RecordScope.OfUser -> repo.records(page = page, uid = scope.uid)
        is RecordScope.OfProblem -> repo.records(
            page = page,
            docId = scope.docId,
            fullStatus = true,
        )
    }
}
