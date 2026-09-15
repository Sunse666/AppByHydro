package com.jxau.oj.data.repo

import com.jxau.oj.data.dto.RankingPageDto
import com.jxau.oj.data.dto.UserProfileEnvelopeDto
import com.jxau.oj.data.mapper.Mappers
import com.jxau.oj.data.model.RankedUser
import com.jxau.oj.data.model.RankingPage
import com.jxau.oj.data.model.UserProfile
import com.jxau.oj.data.net.HydroClient
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.net.map
import com.jxau.oj.data.net.mapJson

/**
 * 排行榜与用户主页。
 *
 * 与 [SubmissionRepository] 不同，**这个仓库的契约全部是【实测】的** ——
 * `GET /ranking` 与 `GET /user/:uid` 都在 M0 之前就采到了真实响应（见方案 3.4.4）。
 * 因此这两个页面不需要"契约不符"的兜底话术，出问题就是真的网络或站点问题。
 *
 * 唯一的不确定点是 `upcount` / `ucount` 的精确分工（【推断】），
 * 已由 [RankingPage.hasMore] 做成不单独依赖它。
 *
 * ⚠️ 隐私红线：站点的榜单与用户对象的 JSON 里**明文包含 `mail`**。
 * [com.jxau.oj.data.dto.UserDto] 刻意不声明该字段，所以它在此层就已经不存在了 ——
 * 不会进模型、不会落盘、不会进日志。
 */
class UserRepository(private val client: HydroClient) {

    /**
     * 排行榜一页。
     *
     * [rankOffset] 是"本页第一名的名次 - 1"，通常等于 `(page - 1) * 每页条数`。
     * 名次由客户端推算（站点不下发），偏移量由调用方按已累计的条数给出，
     * 这样即使站点每页条数与我们预估的不同，名次也仍然连续。
     */
    suspend fun ranking(page: Int = 1, rankOffset: Int = 0): HydroResult<RankingPage> =
        client.get("/ranking", mapOf("page" to page.toString()))
            .mapJson { RankingPageDto.from(it) }
            .map { dto ->
                RankingPage(
                    page = dto.page,
                    totalUsers = dto.ucount,
                    totalPages = dto.upcount.coerceAtLeast(1),
                    users = dto.udocs.mapIndexed { index, user ->
                        Mappers.toRankedUser(user, rankOffset + index + 1)
                    },
                )
            }

    /** 用户主页。[isSelf] 取站点下发的 `isSelfProfile`，不靠本地 uid 对比 —— 后者在换账号后会错。 */
    suspend fun profile(uid: Int): HydroResult<UserProfile> =
        client.get("/user/$uid")
            .mapJson { UserProfileEnvelopeDto.from(it) }
            .map { Mappers.toUserProfile(it.udoc ?: error("udoc 缺失"), it.isSelfProfile) }
}
