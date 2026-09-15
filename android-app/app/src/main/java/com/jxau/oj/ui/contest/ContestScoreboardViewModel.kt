package com.jxau.oj.ui.contest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.Scoreboard
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.ContestRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ScoreboardUiState(
    val board: Scoreboard? = null,
    val loading: Boolean = true,
    /** 首次加载失败的原因。已有数据后的刷新失败不打断展示。 */
    val error: String? = null,
    val needLogin: Boolean = false,
    /** 静默刷新进行中（顶栏小指示器用）。 */
    val refreshing: Boolean = false,
    /** 最近一次成功同步的墙钟（ms），用于「最近同步」展示。 */
    val lastSyncAt: Long? = null,
)

/**
 * 竞赛成绩表。契约【实测 2026-09-14】：`GET /contest/:tid/scoreboard` 返回站点
 * 渲染好的 rows 矩阵，匿名也可访问（进行中会实时变化）。
 *
 * **实时刷新**：页面存续期间每 [REFRESH_INTERVAL_MS] 静默拉一次。选 10 秒：
 * 一次响应约 150KB，太密会撞 Cloudflare 限流，太疏又不像"实时"。
 * 刷新失败**不清空已有数据**（比赛现场网络抖动不该让成绩表消失），
 * 只在从没拿到过数据时才展示错误。
 */
class ContestScoreboardViewModel(
    private val repo: ContestRepository,
    private val tid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ScoreboardUiState())
    val state: StateFlow<ScoreboardUiState> = _state.asStateFlow()

    private var refreshLoop: Job? = null

    init {
        refresh(initial = true)
        refreshLoop = viewModelScope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                refresh(initial = false)
            }
        }
    }

    fun refresh(initial: Boolean) {
        _state.update {
            it.copy(
                refreshing = true,
                loading = initial && it.board == null,
                error = if (initial) null else it.error,
            )
        }
        viewModelScope.launch {
            when (val res = repo.scoreboard(tid)) {
                is HydroResult.Success ->
                    _state.update {
                        it.copy(
                            board = res.data,
                            loading = false,
                            refreshing = false,
                            error = null,
                            needLogin = false,
                            lastSyncAt = System.currentTimeMillis(),
                        )
                    }
                is HydroResult.NeedLogin ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            needLogin = true,
                            error = if (it.board == null) "需要登录后才能查看成绩表" else it.error,
                        )
                    }
                is HydroResult.Failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = if (it.board == null) res.friendly else it.error,
                        )
                    }
                is HydroResult.TransportError ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = if (it.board == null) res.message else it.error,
                        )
                    }
            }
        }
    }

    override fun onCleared() {
        refreshLoop?.cancel()
        super.onCleared()
    }

    private companion object {
        /** 静默刷新间隔。响应体较大（~150KB），兼顾"实时"与 Cloudflare 限流。 */
        const val REFRESH_INTERVAL_MS = 10_000L
    }
}
