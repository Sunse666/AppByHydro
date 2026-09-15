package com.jxau.oj.data.mapper

import com.jxau.oj.BuildConfig
import com.jxau.oj.data.dto.ContestTdocDto
import com.jxau.oj.data.dto.HomeworkTdocDto
import com.jxau.oj.data.dto.HydroJson
import com.jxau.oj.data.dto.ProblemDto
import com.jxau.oj.data.dto.UserDto
import com.jxau.oj.data.model.Contest
import com.jxau.oj.data.model.ContestDetail
import com.jxau.oj.data.model.Homework
import com.jxau.oj.data.model.HomeworkDetail
import com.jxau.oj.data.model.LabeledProblem
import com.jxau.oj.data.model.ProblemDetail
import com.jxau.oj.data.model.ProblemSummary
import com.jxau.oj.data.model.RankedUser
import com.jxau.oj.data.model.ScoreCell
import com.jxau.oj.data.model.ScoreColumn
import com.jxau.oj.data.model.Scoreboard
import com.jxau.oj.data.model.ScoreboardRow
import com.jxau.oj.data.model.User
import com.jxau.oj.data.model.UserProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest

/**
 * DTO → 领域模型。
 *
 * 所有"站点脏数据"都在这一层被消化：字段裁剪、嵌套 JSON、avatar 前缀、
 * 游客态缺字段。UI 层拿到的必须是可以直接渲染的干净模型。
 */
object Mappers {

    fun toUser(dto: UserDto?): User {
        if (dto == null) return User.GUEST
        // 登录态判据（2026-09-13 实测）：`/api/user` 无论登录与否都**没有** `authn` 字段
        // （Hydro 的 authn 是"是否启用 WebAuthn"的用户属性，与会话无关，勿再误用）。
        // 游客恒为 `_id:0, role:"guest"`；已登录 `_id>0` 且 role 非 guest。
        val loggedIn = dto.id > 0 && dto.role != "guest"
        return User(
            id = dto.id,
            uname = dto.uname.ifBlank { if (loggedIn) "用户" else "游客" },
            role = dto.role,
            isLoggedIn = loggedIn,
            avatarUrl = avatarUrl(dto.avatar),
            defaultCodeLang = dto.codeLang,
            timeZone = dto.timeZone?.takeIf { it.isNotBlank() },
        )
    }

    fun toSummary(dto: ProblemDto): ProblemSummary = ProblemSummary(
        id = dto.id,
        docId = dto.docId,
        pid = dto.pid,
        title = dto.title.ifBlank { "(无标题)" },
        tags = dto.tag,
        nSubmit = dto.nSubmit,
        nAccept = dto.nAccept,
    )

    fun toDetail(dto: ProblemDto, fallbackDocId: Int): ProblemDetail {
        val cfg = dto.config
        return ProblemDetail(
            id = dto.id,
            docId = if (dto.docId != 0) dto.docId else fallbackDocId,
            pid = dto.pid,
            title = dto.title.ifBlank { "(无标题)" },
            statement = statement(dto.content),
            tags = dto.tag,
            nSubmit = dto.nSubmit,
            nAccept = dto.nAccept,
            timeLimitMs = cfg?.timeMax ?: 0,
            memoryLimitMb = cfg?.memoryMax ?: 0,
            langKeys = cfg?.langs.orEmpty(),
            testFileNames = dto.data.map { it.name },
        )
    }

    /**
     * `content` 是**嵌套 JSON 字符串**（`{"en":"...","zh":"..."}`）。
     * 只解析一次会把转义后的 JSON 原样渲染到屏幕上 —— 这是本项目最容易踩的坑之一。
     * 二次解析失败时回落到原始文本：宁可排版丑，不可白屏。
     */
    fun statement(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val element = try {
            HydroJson.parseToJsonElement(raw)
        } catch (e: Exception) {
            return raw
        }
        if (element !is JsonObject) return raw
        (element["zh"] as? JsonPrimitive)
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return element.values
            .filterIsInstance<JsonPrimitive>()
            .firstOrNull { it.contentOrNull?.isNotBlank() == true }
            ?.contentOrNull
            ?: raw
    }

    /**
     * avatar 是带前缀的标识（`qq:3475086817`、`gravatar:Hydro@hydro.local`）。
     * 原样塞给图片库会被当成相对路径而加载失败，必须在这里转成真实 URL。
     * 注意 gravatar 分支需要邮箱的 md5，但邮箱本身不会离开本函数。
     *
     * ⚠️ **还必须放行"已经是完整 URL"的形态** —— 方案 3.4「解析陷阱」第 7 条【实测】写着
     * 「榜单里已经是完整 URL，而用户对象里是 `qq:` 前缀的协议式写法，**两种都要处理**」。
     * 早先只匹配前缀，`https://…` 会落到 `else -> null`，于是凡是绝对 URL 的头像
     * 都**静默退成占位图标**（不报错、不崩，只是看不到头像，最难发现的那类问题）。
     */
    /**
     * avatar 字段 → 可直接交给图片库的绝对地址。
     *
     * ⚠️ **2026-09-14 实测更正**：站点下发的是**前缀式**而非"完整 URL"，
     * 共三种前缀 —— `qq:<QQ号>`、`gravatar:<邮箱>`、`url:<路径>`。
     * 其中 `url:` 后面是**站内相对路径**（实测 `/ranking` 与 `/user/9` 均为
     * `url:/file/9/.avatar.jpg`，拼上站点根后 302 到 `/fs/storage?...`），
     * **不是** `https://` 开头的绝对地址。
     *
     * 早先的实现只认 `qq` / `gravatar` 两种，`url:` 会走到 `else -> null` 被**静默丢掉**
     * （表现为该用户头像整块空白，不报错、不回落）。绝对地址分支保留 —— 站点换成对象存储
     * 直链时不至于又漏一次。
     *
     * [siteBase] 只为自检可注入；线上走 `BuildConfig.SITE_BASE_URL`。
     */
    fun avatarUrl(raw: String?, siteBase: String = BuildConfig.SITE_BASE_URL): String? {
        if (raw.isNullOrBlank()) return null
        if (raw.startsWith("https://") || raw.startsWith("http://")) return raw
        val idx = raw.indexOf(':')
        if (idx <= 0 || idx == raw.length - 1) return null
        val scheme = raw.substring(0, idx).lowercase()
        val value = raw.substring(idx + 1)
        return when (scheme) {
            "qq" -> "https://q1.qlogo.cn/g?b=qq&nk=$value&s=100"
            "github" -> "https://github.com/$value.png"
            "gravatar" -> "https://s.gravatar.com/avatar/${md5(value)}?d=identicon&s=100"
            "url" ->
                if (value.startsWith("http://") || value.startsWith("https://")) value
                else siteBase.trimEnd('/') + "/" + value.trimStart('/')
            else -> null
        }
    }

    private fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.trim().lowercase().toByteArray())
            .joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------ 榜单与用户

    fun toRankedUser(dto: UserDto, rank: Int): RankedUser = RankedUser(
        id = dto.id,
        rank = rank,
        uname = dto.uname.ifBlank { "(匿名)" },
        role = dto.role,
        avatarUrl = avatarUrl(dto.avatar),
        registeredAt = dto.regat?.takeIf { it.isNotBlank() },
        lastLoginAt = dto.loginat?.takeIf { it.isNotBlank() },
    )

    fun toUserProfile(dto: UserDto, isSelf: Boolean): UserProfile = UserProfile(
        id = dto.id,
        uname = dto.uname.ifBlank { "(匿名)" },
        role = dto.role,
        avatarUrl = avatarUrl(dto.avatar),
        registeredAt = dto.regat?.takeIf { it.isNotBlank() },
        lastLoginAt = dto.loginat?.takeIf { it.isNotBlank() },
        isSelf = isSelf,
    )

    // ------------------------------------------------------------ 竞赛与作业

    fun toContest(dto: ContestTdocDto): Contest = Contest(
        id = dto.id,
        docId = dto.docId,
        title = dto.title.ifBlank { "未命名竞赛" },
        rule = dto.rule,
        beginAt = dto.beginAt,
        endAt = dto.endAt,
        attend = dto.attend,
        rated = dto.rated,
    )

    fun toContestDetail(dto: ContestTdocDto): ContestDetail {
        val contest = toContest(dto)
        val docIds = dto.pids.mapNotNull { it.toIntOrNull() }
        return ContestDetail(
            contest = contest,
            // 正文与题干一样是嵌套 JSON 字符串，复用同一套二次解析
            statement = statement(dto.content),
            problemDocIds = docIds,
        )
    }

    /**
     * 竞赛题目列表项的字母标号（A、B、…、Z、AA、AB…），与站点
     * `getAlphabeticId(pids.indexOf(docId))` 的约定一致【实测 + 源码】。
     */
    fun alphabeticLabel(index: Int): String {
        require(index >= 0) { "index must be non-negative" }
        var n = index
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    /**
     * 把「pids 顺序 + docId→题目名」组装成带标号的题目列表。
     * 拉不到题目名（未报名的进行中竞赛等）时 title 为 null，UI 回落显示「题目 #docId」。
     */
    fun toLabeledProblems(docIds: List<Int>, titles: Map<Int, String>): List<LabeledProblem> =
        docIds.mapIndexed { index, docId ->
            LabeledProblem(
                label = alphabeticLabel(index),
                docId = docId,
                title = titles[docId],
            )
        }

    fun toHomework(dto: HomeworkTdocDto): Homework = Homework(
        id = dto.id,
        docId = dto.docId,
        title = dto.title.ifBlank { "未命名作业" },
        rule = dto.rule,
        beginAt = dto.beginAt,
        endAt = dto.endAt,
        penaltySince = dto.penaltySince,
        attend = dto.attend,
    )

    /**
     * 作业题目列表。pdict 的键序不可靠，按 tdoc.pids 的顺序重排并打上字母标号；
     * pdict 缺失（进行中未认领）时返回空列表，UI 据此显示「认领后可见」。
     */
    fun toHomeworkDetail(dto: HomeworkTdocDto, claimed: Boolean?, titles: Map<Int, String>): HomeworkDetail {
        val docIds = dto.pids.mapNotNull { it.toIntOrNull() }
        // pids 里没有但 pdict 里有的（理论不该出现）追加在尾部，宁可多显示
        val extra = titles.keys.filterNot { it in docIds }
        return HomeworkDetail(
            homework = toHomework(dto),
            statement = statement(dto.content),
            claimed = claimed,
            problems = toLabeledProblems(docIds + extra, titles),
        )
    }

    /**
     * 成绩表 DTO → 领域模型。格子数与题目列数可能不一致（站点裁剪/脏数据），
     * 不足的格子补「未提交」，多余的丢弃 —— 保证行内格子与表头一一对齐。
     */
    fun toScoreboard(dto: com.jxau.oj.data.dto.ScoreboardDto): Scoreboard = Scoreboard(
        columns = dto.columns.map { ScoreColumn(label = it.label, docId = it.docId) },
        rows = dto.rows.map { row ->
            ScoreboardRow(
                rank = row.rank,
                uid = row.uid,
                uname = row.uname,
                totalScore = row.totalScore,
                cells = List(dto.columns.size) { i ->
                    row.cells.getOrNull(i)?.let { ScoreCell(it.scoreText, it.score, it.rid) }
                        ?: ScoreCell("-", null, null)
                },
            )
        },
    )

    /**
     * 从 MongoDB ObjectId 反解创建时刻。
     *
     * ObjectId 是 12 字节：前 4 字节为大端 Unix 秒，通常序列化成 24 位十六进制串。
     * 但**也可能是别的形态**（站点自生成的短 id、带前缀的 id 等），
     * 因此对不合规的输入一律返回 null —— 绝不猜。
     *
     * 值得绕这一圈的理由：提交时间字段叫什么在 M0 之前是未知的，而 id 的格式是**确定的**。
     * 多一条不依赖字段名的取数路径，就少一处"猜错就整列空白"。
     */
    fun objectIdTimestamp(id: String?): Long? {
        if (id == null || id.length < 8) return null
        val head = id.substring(0, 8)
        if (!head.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val seconds = head.toLongOrNull(16) ?: return null
        // 合理区间 2001-09-09 ~ 2100 年，挡住"16 进制合法但明显不是时间戳"的 id
        if (seconds < 1_000_000_000L || seconds > 4_102_444_800L) return null
        return seconds * 1000L
    }
}
