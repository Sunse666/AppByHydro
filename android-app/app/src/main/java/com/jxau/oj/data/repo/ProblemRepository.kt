package com.jxau.oj.data.repo

import com.jxau.oj.data.dto.ProblemDetailEnvelopeDto
import com.jxau.oj.data.dto.ProblemListDto
import com.jxau.oj.data.mapper.Mappers
import com.jxau.oj.data.model.ProblemDetail
import com.jxau.oj.data.model.ProblemPage
import com.jxau.oj.data.net.HydroClient
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.net.map
import com.jxau.oj.data.net.mapJson

class ProblemRepository(private val client: HydroClient) {

    /** 题库列表。`/p` 以 `Accept: application/json` 直接返回结构化数据，无需解析 HTML。 */
    suspend fun list(page: Int = 1, query: String? = null): HydroResult<ProblemPage> {
        val params = mutableMapOf<String, String?>("page" to page.toString())
        if (!query.isNullOrBlank()) params["q"] = query.trim()
        return client.get("/p", params)
            .mapJson { ProblemListDto.from(it) }
            .map { dto ->
                ProblemPage(
                    page = dto.page,
                    totalCount = dto.pcount,
                    items = dto.pdocs.map { Mappers.toSummary(it) },
                )
            }
    }

    /**
     * 题目详情。
     *
     * 注意列表项**没有 pid**（实测只有 docId），所以详情链接一律用 docId 构造：
     * `/p/{docId}`。用 pid（形如 P1000）也能访问，但列表侧拿不到它。
     *
     * [tid] 是竞赛/作业上下文，**从竞赛或作业里进来时必须传**：
     * 那些题目在 Hydro 里是 hidden 题，普通用户没有域级的「查看隐藏题目」权限，
     * 站点只认「已报名/已认领 + 带 tid」这一条授权路径。实测（2026-09-15）：
     * 不带 tid 得 403 `PermissionError: View hidden problems`，带上即 200。
     * 从题库直接进来的公开题传 null，请求形态与从前完全一致。
     */
    suspend fun detail(docId: Int, tid: String? = null): HydroResult<ProblemDetail> {
        val query = if (tid.isNullOrBlank()) emptyMap() else mapOf("tid" to tid)
        val res = client.get("/p/$docId", query).mapJson { ProblemDetailEnvelopeDto.from(it) }
        return when (res) {
            is HydroResult.Success -> {
                val pdoc = res.data.pdoc
                if (pdoc == null) {
                    HydroResult.Failure(404, "题目不存在或已被删除")
                } else {
                    HydroResult.Success(Mappers.toDetail(pdoc, docId))
                }
            }
            is HydroResult.NeedLogin -> res
            is HydroResult.Failure -> res
            is HydroResult.TransportError -> res
        }
    }
}
