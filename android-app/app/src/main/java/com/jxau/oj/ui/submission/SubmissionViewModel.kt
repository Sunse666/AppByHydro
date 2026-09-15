package com.jxau.oj.ui.submission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.Submission
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.repo.SubmissionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubmissionUiState(
    val rid: String = "",
    val loading: Boolean = true,
    val submission: Submission? = null,
    val error: String? = null,
    val polling: Boolean = false,
    val pollCount: Int = 0,
    /** 轮询超过上限仍未出结果。区别于"请求失败"——这是正常但需要用户稍后回来看的情况。 */
    val timedOut: Boolean = false,
)

/**
 * 评测结果 + 实时状态。
 *
 * 轮询策略刻意保守（方案第 11 章风险 4：站点有 Cloudflare 前置，不能打太快）：
 * - 退避间隔 0.8s → 5s，不是固定高频
 * - 总时长封顶 90 秒，到点就停下来让用户手动刷新，而不是无限打
 * - 拿到终态立即停止
 */
class SubmissionViewModel(
    private val repo: SubmissionRepository,
    private val rid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SubmissionUiState(rid = rid))
    val state: StateFlow<SubmissionUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        poll()
    }

    fun poll() {
        pollJob?.cancel()
        _state.update {
            it.copy(loading = it.submission == null, error = null, timedOut = false, pollCount = 0, polling = true)
        }

        pollJob = viewModelScope.launch {
            var index = 0
            var elapsedMs = 0L

            while (true) {
                when (val res = repo.record(rid)) {
                    is HydroResult.Success -> {
                        val submission = res.data
                        _state.update {
                            it.copy(
                                loading = false,
                                submission = submission,
                                pollCount = index + 1,
                                polling = Submission.isRunning(submission.statusCode),
                            )
                        }
                        if (!Submission.isRunning(submission.statusCode)) return@launch
                    }

                    is HydroResult.NeedLogin -> {
                        _state.update { it.copy(loading = false, polling = false, error = "登录状态已失效，请重新登录") }
                        return@launch
                    }

                    // 已经拿到过数据时，偶发的单次失败不该打断轮询 —— 继续等下一轮
                    is HydroResult.Failure -> if (_state.value.submission == null) {
                        _state.update { it.copy(loading = false, polling = false, error = res.friendly) }
                        return@launch
                    }

                    is HydroResult.TransportError -> if (_state.value.submission == null) {
                        _state.update { it.copy(loading = false, polling = false, error = res.message) }
                        return@launch
                    }
                }

                val wait = BACKOFF_MS.getOrElse(index) { BACKOFF_MS.last() }
                if (elapsedMs + wait > TOTAL_TIMEOUT_MS) {
                    _state.update { it.copy(polling = false, timedOut = true) }
                    return@launch
                }
                delay(wait)
                elapsedMs += wait
                index++
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        val BACKOFF_MS = longArrayOf(800, 1200, 1600, 2000, 2500, 3000, 4000, 5000)
        const val TOTAL_TIMEOUT_MS = 90_000L
    }
}
