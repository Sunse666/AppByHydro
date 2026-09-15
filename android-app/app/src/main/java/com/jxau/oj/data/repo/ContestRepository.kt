package com.jxau.oj.data.repo

import com.jxau.oj.data.dto.ContestDetailDto
import com.jxau.oj.data.dto.ContestListDto
import com.jxau.oj.data.dto.ContestProblemsDto
import com.jxau.oj.data.dto.HomeworkDetailDto
import com.jxau.oj.data.dto.HomeworkListDto
import com.jxau.oj.data.dto.ScoreboardDto
import com.jxau.oj.data.mapper.Mappers
import com.jxau.oj.data.model.Contest
import com.jxau.oj.data.model.ContestDetail
import com.jxau.oj.data.model.Homework
import com.jxau.oj.data.model.HomeworkDetail
import com.jxau.oj.data.net.HydroClient
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.net.map
import com.jxau.oj.data.net.mapJson

/**
 * 竞赛与作业。
 *
 * **契约【实测】**（方案 3.4.5）：列表与详情四条路由的响应结构都采到了真实样本，
 * 与 [SubmissionRepository] 不同，这里不需要「契约不符」的兜底话术。
 *
 * 路由的 `tid` 用文档 `_id` 原样字符串。⚠️ 实测修正（2026-09-13，MuMu 真机）：
 * `/contest` 列表响应里 **docId 缺失**（曾致列表页 LazyColumn key 重复崩溃），
 * tid 不能指望 docId —— Hydro 的 `:tid` 即文档 `_id`，字符串形态直接可用。
 * 注意：`/contest/{tid}/scoreboard` 存在而 `/contest/{tid}/ranking` 不存在 ——
 * 路由是精确匹配的。榜单页本批不接（数据量大且需要登录，排在登录态完善之后）。
 */
class ContestRepository(private val client: HydroClient) {

    /** 竞赛列表（分页）。站点当前只有 3 场，tpcount 通常为 1。 */
    suspend fun contests(page: Int = 1): HydroResult<List<Contest>> =
        client.get("/contest", mapOf("page" to page.toString()))
            .mapJson { ContestListDto.from(it) }
            .map { dto -> dto.contests.map(Mappers::toContest) }

    /** 竞赛详情。响应里只有 `{tdoc}`，正文与题目列表都在 tdoc 上。 */
    suspend fun contest(tid: String): HydroResult<ContestDetail> =
        client.get("/contest/$tid")
            .mapJson { ContestDetailDto.from(it) }
            .map { dto ->
                Mappers.toContestDetail(dto.tdoc ?: error("tdoc 缺失"))
            }

    /**
     * 竞赛题目名（docId → title）。来自 `GET /contest/:tid/problems` 的 `pdict`【实测】。
     * 题目顺序以详情里的 `tdoc.pids` 为准，这里只回字典。
     *
     * 失败是**预期内**的常态：未开始的竞赛（ContestNotLive）与进行中未报名
     * （ContestNotAttended）都会拿到错误信封 —— 调用方必须兜底而不是当崩溃处理。
     */
    suspend fun contestProblemTitles(tid: String): HydroResult<Map<Int, String>> =
        client.get("/contest/$tid/problems")
            .mapJson { ContestProblemsDto.from(it) }
            .map { it.titles }

    /**
     * 竞赛成绩表。`GET /contest/:tid/scoreboard`【实测 2026-09-14】。
     *
     * 响应是站点已渲染好的 rows 矩阵（约 150KB / 19 人 × 26 题），DTO 只提取
     * 表头与数据行；`udict`（含明文 mail）刻意不解析。未开始的竞赛会拿到
     * 错误信封（ContestNotLiveError 等），调用方兜底。
     */
    suspend fun scoreboard(tid: String): HydroResult<com.jxau.oj.data.model.Scoreboard> =
        client.get("/contest/$tid/scoreboard")
            .mapJson { ScoreboardDto.from(it) }
            .map(Mappers::toScoreboard)

    /** 作业列表（分页）。站点当前 4 份。 */
    suspend fun homework(page: Int = 1): HydroResult<List<Homework>> =
        client.get("/homework", mapOf("page" to page.toString()))
            .mapJson { HomeworkListDto.from(it) }
            .map { dto -> dto.homework.map(Mappers::toHomework) }

    /**
     * 作业详情。契约【实测 2026-09-13】：题目在 `pdict`（认领后或已截止才下发），
     * `ddocs` 是讨论区不是题目；认领状态在 `tsdoc.attend`。
     */
    suspend fun homeworkDetail(tid: String): HydroResult<HomeworkDetail> =
        client.get("/homework/$tid")
            .mapJson { HomeworkDetailDto.from(it) }
            .map { dto ->
                Mappers.toHomeworkDetail(
                    dto.tdoc ?: error("tdoc 缺失"),
                    dto.claimed,
                    dto.problems,
                )
            }

    /**
     * 认领作业。Hydro 的 POST 操作分发机制：请求体里带 `operation=attend`
     * 字段即调用 `HomeworkDetailHandler.postAttend`（实测：不带该字段会 405
     * MethodNotAllowedError）。成功回 `{"url":…}`；已截止的作业会得到
     * `HomeworkNotLiveError`。
     */
    suspend fun claimHomework(tid: String): HydroResult<Unit> =
        client.postForm("/homework/$tid", mapOf("operation" to "attend")).map { }
}
