package com.jxau.oj.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jxau.oj.data.model.PretestResult
import com.jxau.oj.data.model.ProblemSample
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.net.SessionStore
import com.jxau.oj.data.repo.ProblemRepository
import com.jxau.oj.data.repo.SubmissionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val docId: Int = 0,
    val loading: Boolean = true,
    val title: String = "",
    val langKeys: List<String> = emptyList(),
    val selectedLang: String = "",
    val code: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val needLogin: Boolean = false,
    /** 提交成功后拿到的记录编号，UI 据此跳转结果页。 */
    val submittedRid: String? = null,
    val draftRestored: Boolean = false,
    // ---- 题面顶栏 ----
    /** 题目正文（Markdown）。下拉顶栏展开后渲染。 */
    val statement: String = "",
    /** 从题面解析出的样例（用于自测一键填充）。 */
    val samples: List<ProblemSample> = emptyList(),
    val showStatement: Boolean = false,
    // ---- 自测底栏 ----
    /** 各组测试输入。至少一组；pretest 要求 input 非空数组。 */
    val testInputs: List<String> = listOf(""),
    val pretestBusy: Boolean = false,
    val pretestError: String? = null,
    /** 最近一次自测的结果。null = 还没测过。 */
    val pretestResult: PretestResult? = null,
    // ---- 竞赛/作业降级提交 ----
    /**
     * 提交/自测撞上 ContestNotLiveError 后已降级为练习模式（不带 tid）重发成功。
     * UI 弹一次 snackbar 告知「不计入成绩」，然后消费掉。
     */
    val practiceNotice: Boolean = false,
)

class EditorViewModel(
    private val problemRepository: ProblemRepository,
    private val submissionRepository: SubmissionRepository,
    private val sessionStore: SessionStore,
    private val docId: Int,
    /**
     * 竞赛/作业上下文；从题库进来的公开题为空串。
     *
     * 三个地方都依赖它：① 拉题面（竞赛题是 hidden 题，不带 tid 得 403）；
     * ② 提交、③ 自测 —— 站点据此把这条记录归到竞赛名下，缺了就"提交成功但不计分"，
     * 而提交**不会重试**，用户通常到赛后看榜单才发现，属于最坏的一类静默失效。
     *
     * 例外：竞赛/作业不在进行中时，带 tid 的提交会被服务端 ContestNotLiveError
     * 直接拒绝（见 [submit] 里的降级逻辑）—— 此时按练习提交重试一次并告知用户。
     */
    private val tid: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState(docId = docId))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var draftJob: Job? = null
    private var pretestJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = problemRepository.detail(docId, contextTid())) {
                is HydroResult.Success -> {
                    val langs = res.data.langKeys
                    // 优先用用户在网页端设过的默认语言，其次是 C++，最后退到列表首项
                    val preferred = sessionStore.defaultLang?.takeIf { it in langs }
                        ?: langs.firstOrNull { it == "cc" }
                        ?: langs.firstOrNull()
                        ?: "cc"
                    val draft = sessionStore.draft(docId)
                    val statement = res.data.statement
                    _state.update {
                        it.copy(
                            loading = false,
                            title = res.data.title,
                            statement = statement,
                            samples = parseSamples(statement),
                            langKeys = langs,
                            selectedLang = preferred,
                            code = draft.orEmpty(),
                            draftRestored = !draft.isNullOrBlank(),
                        )
                    }
                }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(loading = false, needLogin = true, error = "需要登录才能作答") }
                is HydroResult.Failure ->
                    _state.update { it.copy(loading = false, error = res.friendly) }
                is HydroResult.TransportError ->
                    _state.update { it.copy(loading = false, error = res.message) }
            }
        }
    }

    fun onCodeChange(code: String) {
        _state.update { it.copy(code = code) }
        // 防抖落草稿：手机做题最怕切出去回来代码没了，所以本地优先、不等网络
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            sessionStore.saveDraft(docId, code)
        }
    }

    fun selectLang(langKey: String) {
        _state.update { it.copy(selectedLang = langKey) }
        sessionStore.defaultLang = langKey
    }

    // 字号与字体不在这里 —— 它们由设置页统一管理（AppViewModel + LocalEditorFontId/SizeSp），
    // 编辑器只是消费方，避免两处状态各自为政。

    // ---- 题面顶栏 ----

    fun toggleStatement() {
        _state.update { it.copy(showStatement = !it.showStatement) }
    }

    fun collapseStatement() {
        _state.update { it.copy(showStatement = false) }
    }

    fun expandStatement() {
        _state.update { it.copy(showStatement = true) }
    }

    // ---- 自测 ----

    fun setTestInput(index: Int, value: String) {
        _state.update {
            if (index !in it.testInputs.indices) it
            else it.copy(
                testInputs = it.testInputs.toMutableList().also { list -> list[index] = value },
                pretestError = null,
            )
        }
    }

    fun addTestCase() {
        _state.update { if (it.testInputs.size >= MAX_TEST_INPUTS) it else it.copy(testInputs = it.testInputs + "") }
    }

    fun removeTestCase(index: Int) {
        _state.update {
            if (it.testInputs.size <= 1) it
            else it.copy(testInputs = it.testInputs.filterIndexed { i, _ -> i != index })
        }
    }

    /** 用题面样例填充输入（样例的期望输出仅展示用，判题输出以机判为准）。 */
    fun fillFromSamples() {
        val samples = _state.value.samples
        if (samples.isEmpty()) return
        _state.update { it.copy(testInputs = samples.map { s -> s.input }.take(MAX_TEST_INPUTS)) }
    }

    fun runSelfTest() {
        val snapshot = _state.value
        if (snapshot.pretestBusy) return
        if (snapshot.code.isBlank()) {
            _state.update { it.copy(pretestError = "代码为空，先写点什么再自测") }
            return
        }
        if (snapshot.testInputs.all { it.isBlank() }) {
            _state.update { it.copy(pretestError = "至少填入一组测试输入") }
            return
        }
        _state.update { it.copy(pretestBusy = true, pretestError = null, pretestResult = null) }
        pretestJob = viewModelScope.launch {
            val first = submissionRepository.pretest(
                docId, snapshot.selectedLang, snapshot.code, snapshot.testInputs, contextTid(),
            )
            // 与正式提交同一处硬规则（ContestNotLiveError / HomeworkNotLiveError）：
            // 竞赛/作业不在进行中时带 tid 的请求一律被拒。降级为练习自测重试一次。
            var downgraded = false
            val outcome = if (first is HydroResult.Failure &&
                first.name != null && first.name in NOT_LIVE_ERROR_NAMES &&
                contextTid() != null
            ) {
                downgraded = true
                submissionRepository.pretest(
                    docId, snapshot.selectedLang, snapshot.code, snapshot.testInputs, null,
                )
            } else {
                first
            }
            val rid = when (outcome) {
                is HydroResult.Success -> {
                    if (downgraded) _state.update { it.copy(practiceNotice = true) }
                    outcome.data
                }
                is HydroResult.NeedLogin -> {
                    _state.update { it.copy(pretestBusy = false, pretestError = "登录状态已失效，请重新登录") }
                    return@launch
                }
                is HydroResult.Failure -> {
                    _state.update { it.copy(pretestBusy = false, pretestError = outcome.friendly) }
                    return@launch
                }
                is HydroResult.TransportError -> {
                    // 与正式提交同理：自测请求不自动重试
                    _state.update { it.copy(pretestBusy = false, pretestError = outcome.message) }
                    return@launch
                }
            }
            if (rid.isBlank()) {
                _state.update {
                    it.copy(pretestBusy = false, pretestError = "自测请求已发出，但没有取到记录编号，请稍后重试")
                }
                return@launch
            }
            pollPretest(rid)
        }
    }

    /**
     * 轮询自测结果。退避序列与正式评测一致（[POLL_DELIVERY_MS]），
     * 封顶 90 秒 —— 避免 Cloudflare 风控。
     */
    private suspend fun pollPretest(rid: String) {
        var attempt = 0
        val deadline = System.currentTimeMillis() + POLL_DEADLINE_MS
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_DELIVERY_MS[attempt.coerceAtMost(POLL_DELIVERY_MS.lastIndex)])
            when (val res = submissionRepository.pretestResult(rid)) {
                is HydroResult.Success -> {
                    val result = res.data
                    _state.update { it.copy(pretestBusy = false, pretestResult = result) }
                    if (!result.running) return
                    attempt++
                }
                is HydroResult.Failure -> {
                    _state.update { it.copy(pretestBusy = false, pretestError = res.friendly) }
                    return
                }
                else -> attempt++
            }
        }
        _state.update { it.copy(pretestBusy = false, pretestError = "自测超时：站点迟迟没有返回结果，可稍后在记录页查看") }
    }

    fun submit() {
        val snapshot = _state.value
        if (snapshot.submitting) return
        if (snapshot.code.isBlank()) {
            _state.update { it.copy(error = "代码为空，先写点什么再提交") }
            return
        }

        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val first = submissionRepository.submit(docId, snapshot.selectedLang, snapshot.code, contextTid())
            // Hydro 提交路径的硬规则（packages/hydrooj/src/handler/problem.ts）：
            // `if (tid && !contest.isOngoing(...)) throw new ContestNotLiveError(...)` ——
            // 带 tid 的提交只在竞赛进行中受理。已结束/未开始时网页还能交，是因为
            // 从题目页发起的提交不带 tid（服务端按普通练习计，不入榜）。
            // 这里对齐网页：确实带了 tid 又撞上该错时，降级为练习提交重试一次。
            // 注意这不违反「提交不自动重试」：前一次请求被服务端在业务层拒绝，没有产生任何记录。
            var downgraded = false
            val outcome = if (first is HydroResult.Failure &&
                first.name != null && first.name in NOT_LIVE_ERROR_NAMES &&
                contextTid() != null
            ) {
                downgraded = true
                submissionRepository.submit(docId, snapshot.selectedLang, snapshot.code, null)
            } else {
                first
            }
            when (outcome) {
                is HydroResult.Success -> {
                    if (outcome.data.isBlank()) {
                        // 没拿到 rid：多半是提交接口的契约与预期不符，而不是用户操作失败。
                        // 这种情况必须说清楚，否则会被误读成"站点挂了"。
                        _state.update {
                            it.copy(
                                submitting = false,
                                error = "提交请求已发出，但没有取到评测记录编号。\n" +
                                    "这通常意味着提交接口的字段契约与预期不符（该链路尚未经 M0 验证）。",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                submitting = false,
                                submittedRid = outcome.data,
                                practiceNotice = if (downgraded) true else it.practiceNotice,
                            )
                        }
                    }
                }
                is HydroResult.NeedLogin ->
                    _state.update { it.copy(submitting = false, needLogin = true, error = "登录状态已失效，请重新登录") }
                is HydroResult.Failure ->
                    _state.update { it.copy(submitting = false, error = outcome.friendly) }
                is HydroResult.TransportError ->
                    // 刻意不自动重试：提交类请求重试会造成重复提交
                    _state.update { it.copy(submitting = false, error = outcome.message) }
            }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    fun consumeSubmittedRid() {
        _state.update { it.copy(submittedRid = null) }
    }

    fun consumeDraftRestored() {
        _state.update { it.copy(draftRestored = false) }
    }

    fun consumePretestError() {
        _state.update { it.copy(pretestError = null) }
    }

    fun consumePracticeNotice() {
        _state.update { it.copy(practiceNotice = false) }
    }

    /** 空串 = 无竞赛上下文；仓库层用 null 表示"不带这个参数"。 */
    private fun contextTid(): String? = tid.takeIf { it.isNotBlank() }

    private companion object {
        const val DRAFT_DEBOUNCE_MS = 600L
        const val MAX_TEST_INPUTS = 5

        /** 与正式评测一致的轮询退避（毫秒），封顶 90 秒。 */
        val POLL_DELIVERY_MS = longArrayOf(800, 1200, 1600, 2000, 2500, 3000, 4000, 5000)
        const val POLL_DEADLINE_MS = 90_000L

        /**
         * 触发「降级为练习提交」的服务端错误名。
         * 竞赛与作业共用同一检查（Hydro 作业是 contest 的 homework rule 变体），
         * 但保险起见两个名字都收。
         */
        val NOT_LIVE_ERROR_NAMES = setOf("ContestNotLiveError", "HomeworkNotLiveError")
    }
}

/**
 * 从题面 Markdown 里解析样例。
 *
 * 站点题面的样例段形如：
 * ```
 * ### 样例 1
 * **输入**
 * ```
 * 3 5
 * ```
 * **输出**
 * ```
 * 8 -2
 * ```
 * ```
 * 规则：紧跟在「输入/输出」标注（粗体或标题）后面的第一个代码围栏就是样例内容。
 * 解析失败宁可返回空列表 —— 自测输入框永远是可手动填写的。
 */
fun parseSamples(markdown: String): List<ProblemSample> {
    val labelRe = Regex("(输入|输出)")
    val inputs = mutableListOf<String>()
    val outputs = mutableListOf<String>()
    val lines = markdown.lines()
    var i = 0
    while (i < lines.size) {
        val match = labelRe.find(lines[i])
        if (match != null && lines[i].length < 40) {
            // 找标注之后的第一个代码围栏
            var j = i + 1
            while (j < lines.size && !lines[j].trimStart().startsWith("```")) j++
            if (j + 1 < lines.size) {
                val body = StringBuilder()
                var k = j + 1
                while (k < lines.size && !lines[k].trimStart().startsWith("```")) {
                    body.append(lines[k]).append('\n')
                    k++
                }
                val content = body.toString().trimEnd('\n')
                if (content.isNotBlank()) {
                    if (match.groupValues[1] == "输入") inputs.add(content) else outputs.add(content)
                }
                i = k
            }
        }
        i++
    }
    return inputs.mapIndexed { index, text -> ProblemSample(input = text, output = outputs.getOrNull(index)) }
}
