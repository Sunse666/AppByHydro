package com.jxau.oj.data.repo

import com.jxau.oj.data.dto.PretestRecordDto
import com.jxau.oj.data.dto.RecordDto
import com.jxau.oj.data.dto.RecordListDto
import com.jxau.oj.data.dto.SubmitResultDto
import com.jxau.oj.data.mapper.Mappers
import com.jxau.oj.data.model.PretestCase
import com.jxau.oj.data.model.PretestResult
import com.jxau.oj.data.model.RecordPage
import com.jxau.oj.data.model.Submission
import com.jxau.oj.data.net.HydroClient
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.net.map
import com.jxau.oj.data.net.mapJson

/**
 * 提交与评测。
 *
 * 契约【实测 2026-09-13，MuMu 真机 + 真实账号】：`lang`/`code` form 键、`{rid}` 响应、
 * `rdoc` 包裹、状态码语义全部与 Hydro 约定吻合（方案附录 D.5 已销账）。
 * 2026-09-13 晚新增自测（pretest）链路：`pretest=true&input[]=...`，同样实测命中。
 */
class SubmissionRepository(private val client: HydroClient) {

    /**
     * 提交代码。
     *
     * 用 form-urlencoded 而不是 JSON：Hydro 的提交表单是传统表单形式。
     * 若实测证明应为 JSON，改这一处即可。
     *
     * [tid] 是竞赛/作业上下文（可选）。站点据此把这条提交归到竞赛名下 ——
     * 从竞赛里作答时必须传，否则**提交会成功但不计入竞赛**，且提交不重试、
     * 用户要等赛后看榜单才发现。从题库直接做题传 null。
     */
    suspend fun submit(
        docId: Int,
        langKey: String,
        code: String,
        tid: String? = null,
    ): HydroResult<String> =
        client.postForm(
            path = "/p/$docId/submit",
            form = buildMap {
                put("lang", langKey)
                put("code", code)
                if (!tid.isNullOrBlank()) put("tid", tid)
            },
        )
            .mapJson { SubmitResultDto.from(it) }
            .map { it.rid }

    /**
     * 自测（pretest）。
     *
     * 契约【实测 2026-09-13】：与正式提交同一路由，额外带 `pretest=true` 和
     * `input[]` 数组（每组一个键）。返回同样的 `{rid}`；自测**不计入提交数**、
     * 不出现在任何记录列表里（contest 被置为哨兵值被列表查询过滤），
     * 但题目语言必须是题目 `config.langs` 允许的，且至少要有一组输入。
     */
    suspend fun pretest(
        docId: Int,
        langKey: String,
        code: String,
        inputs: List<String>,
        tid: String? = null,
    ): HydroResult<String> {
        val pairs = buildList {
            add("lang" to langKey)
            add("code" to code)
            add("pretest" to "true")
            // 与正式提交同源：竞赛内自测也带 tid，站点才知道这是哪场比赛的题
            if (!tid.isNullOrBlank()) add("tid" to tid)
            inputs.forEach { add("input[]" to it) }
        }
        return client.postForm("/p/$docId/submit", pairs)
            .mapJson { SubmitResultDto.from(it) }
            .map { it.rid }
    }

    /** 自测结果。结构与正式记录同源，但 `testCases[].message` 带每组输入的程序输出。 */
    suspend fun pretestResult(rid: String): HydroResult<PretestResult> =
        client.get("/record/$rid")
            .mapJson { PretestRecordDto.from(it) }
            .map { dto ->
                PretestResult(
                    statusCode = dto.statusCode,
                    statusKey = Submission.statusKeyOf(dto.statusCode),
                    inputs = dto.inputs,
                    cases = dto.cases.map {
                        PretestCase(
                            id = it.id,
                            statusKey = Submission.statusKeyOf(it.statusCode),
                            timeMs = it.timeMs,
                            memoryKb = it.memoryKb,
                            output = it.output,
                        )
                    },
                    compilerMessages = dto.compilerTexts,
                )
            }

    /** 单条评测记录。轮询实时状态用的就是它。 */
    suspend fun record(rid: String): HydroResult<Submission> =
        client.get("/record/$rid")
            .mapJson { RecordDto.from(it) }
            .map { it.toSubmission() }

    /**
     * 提交记录列表（分页）。
     *
     * `uid` 为空时返回全站记录；传自己 uid 可看"我的提交"；
     * 传 `docId` 可看某道题的记录（题目详情页的入口，参数形式来自服务端下发的
     * `getSubmissionsUrl = /record?fullStatus=true&pid=1`，【实测】）。
     */
    suspend fun records(
        page: Int = 1,
        uid: Int? = null,
        docId: Int? = null,
        fullStatus: Boolean = false,
    ): HydroResult<RecordPage> {
        val params = mutableMapOf<String, String?>("page" to page.toString())
        // 站点的按用户过滤参数名是 `uidOrName`（Hydro RecordListHandler 实测；
        // 传 `uid` 会被静默忽略、退化为全站记录 —— 这正是"看别人的记录看到所有人"的原因）。
        // 值可以是 uid 数字或用户名，这里恒传 uid。
        if (uid != null) params["uidOrName"] = uid.toString()
        if (docId != null) params["pid"] = docId.toString()
        // fullStatus 是服务端自己下发的参数名，含义推测为"带完整状态"
        if (fullStatus) params["fullStatus"] = "true"

        return client.get("/record", params)
            .mapJson { RecordListDto.from(it) }
            .map { dto ->
                RecordPage(
                    page = dto.page,
                    totalCount = dto.totalCount,
                    items = dto.records.map { it.toSubmission() },
                    contractMismatch = !dto.sawKnownArray,
                )
            }
    }

    private fun RecordDto.toSubmission(): Submission = Submission(
        rid = rid,
        statusCode = statusCode,
        statusKey = Submission.statusKeyOf(statusCode),
        score = score,
        timeMs = timeMs,
        memoryKb = memoryKb,
        langKey = lang,
        pid = pid,
        title = title,
        messages = (compilerTexts + judgeTexts).distinct(),
        // 优先从 rid 反解提交时刻：id 的格式是确定的，而时间字段名不是。
        // 即便字段名全猜错，列表里的时间列也不会整列空白。
        submitAtMs = Mappers.objectIdTimestamp(rid),
        submitterName = uname.takeIf { it.isNotBlank() },
        code = code,
    )
}
