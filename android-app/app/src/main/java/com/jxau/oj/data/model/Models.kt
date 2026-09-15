package com.jxau.oj.data.model

import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

/** UI 层只接触这些模型，不再接触 DTO，也不接触 `mail` 这类字段。 */

data class User(
    val id: Int,
    val uname: String,
    val role: String,
    val isLoggedIn: Boolean,
    val avatarUrl: String?,
    val defaultCodeLang: String?,
    val timeZone: String?,
) {
    companion object {
        val GUEST = User(
            id = 0,
            uname = "游客",
            role = "guest",
            isLoggedIn = false,
            avatarUrl = null,
            defaultCodeLang = null,
            timeZone = null,
        )
    }
}

data class ProblemSummary(
    val id: String,
    val docId: Int,
    val pid: String?,
    val title: String,
    val tags: List<String>,
    val nSubmit: Int,
    val nAccept: Int,
) {
    /** 通过率。`nSubmit == 0` 时返回 null —— 显示 "—" 而不是 0%，避免把它读成"没人做对"。 */
    val acceptance: Double?
        get() = if (nSubmit > 0) nAccept.toDouble() / nSubmit else null
}

data class ProblemDetail(
    val id: String,
    val docId: Int,
    val pid: String?,
    val title: String,
    val statement: String,
    val tags: List<String>,
    val nSubmit: Int,
    val nAccept: Int,
    val timeLimitMs: Int,
    val memoryLimitMb: Int,
    val langKeys: List<String>,
    val testFileNames: List<String>,
) {
    val acceptance: Double?
        get() = if (nSubmit > 0) nAccept.toDouble() / nSubmit else null
}

data class ProblemPage(
    val page: Int,
    val totalCount: Int,
    val items: List<ProblemSummary>,
)

/**
 * 评测记录。
 *
 * [statusKey] 是给 UI 用的语义键（`AC` / `WA` / `TLE` …），配合
 * `ui.theme.VerdictColors` 取色与文案 —— 颜色与文案都在 UI 层，
 * 模型层不掺展示逻辑。
 *
 * ⚠️ **状态码 → 语义的映射来自 Hydro 的通用约定，未经 M0 实测。**
 * 若实测发现错位，只需改 [statusKeyOf] 这一个函数。
 */
data class Submission(
    val rid: String,
    val statusCode: Int,
    val statusKey: String?,
    val score: Int,
    val timeMs: Int,
    val memoryKb: Int,
    val langKey: String,
    val pid: String,
    val title: String,
    val messages: List<String>,
    /**
     * 提交时刻（epoch millis）。
     *
     * ⚠️ 这里刻意**优先从 `rid` 反解**而不是读字段：`rid` 是 MongoDB ObjectId，
     * 前 4 字节就是创建时间戳，这是**格式本身保证的**，不依赖任何字段名猜测。
     * 而提交时间字段（`judgeAt` / `time` / …）叫什么，在 M0 之前完全未知。
     * 所以即便字段名全猜错，列表里也还能正确显示提交时间。
     */
    val submitAtMs: Long? = null,
    /** 提交者昵称。仅全站记录列表会用到；自己的记录列表留空。 */
    val submitterName: String? = null,
    /**
     * 提交的源代码。
     *
     * ⚠️ `code` 字段在记录详情里**是否存在未经 M0 验证** —— 有的 OJ 不回传源码。
     * 为 null 时 UI 整个区块不渲染：宁可不显示，不给一个空盒子。
     */
    val code: String? = null,
) {
    companion object {
        /** 仍在评测中的状态码。有这些状态时 UI 才继续轮询。 */
        private val RUNNING_STATUS = setOf(0, 20, 21, 22)

        /** 把站点状态码映射成语义键。未知状态返回 null，UI 会显示"未知"而不是误报成功。 */
        fun statusKeyOf(code: Int): String? = when (code) {
            1 -> "AC"
            2 -> "WA"
            3 -> "TLE"
            4 -> "MLE"
            5 -> "RE"
            6 -> "CE"
            7 -> "SE"
            8 -> "IGN"
            0, 20, 21, 22 -> "PENDING"
            else -> null
        }

        fun isRunning(code: Int): Boolean = code in RUNNING_STATUS
    }
}

/**
 * 提交记录列表的一页。
 *
 * ⚠️ 契约【未验证】—— 与 [Submission] 同属 M0 欠账。`totalCount` 在站点不下发
 * （或键名不同）时会回落到 0，此时 [hasMore] 靠"本页是否满页"来判断，
 * 见 [FULL_PAGE_HINT]。
 */
data class RecordPage(
    val page: Int,
    val totalCount: Int,
    val items: List<Submission>,
    /**
     * 响应里没有出现任何我们认识的记录数组键。
     *
     * 与"列表为空"是**两件事**：前者说明 App 还没适配站点的返回格式（该链路尚未经 M0 验证），
     * 后者说明用户确实还没交过题。混淆这两者会让用户以为自己没交过。
     */
    val contractMismatch: Boolean = false,
) {
    /**
     * 是否还有下一页。
     *
     * 三条互不依赖的判据，任一成立即可继续翻页：
     * 1. 本页为空 → 一定到底了；
     * 2. 知道总数（`totalCount > 0`）→ 用累计条数与它比；
     * 3. 总数未知 → 用"本页是否接近满页"猜。阈值取 [FULL_PAGE_HINT] 而不是精确的
     *    每页条数，是因为站点每页条数并不确定 —— 猜错多请求一页的代价，
     *    远小于猜错少一页导致用户永远看不到更早的记录。
     */
    fun hasMore(loadedCount: Int): Boolean {
        if (items.isEmpty()) return false
        if (totalCount > 0) return loadedCount < totalCount
        return items.size >= FULL_PAGE_HINT
    }

    companion object {
        /** 站点单页条数未知，用来判断"是不是满页"。 */
        const val FULL_PAGE_HINT = 20
    }
}

/**
 * 榜单上的一位用户。
 *
 * 字段集来自 `GET /ranking` 的 `udocs[]`【实测】。**刻意不包含任何联系方式** ——
 * 站点明文下发了 `mail`，我们在 DTO 层就不建这个字段，它自然到不了这里。
 *
 * [rank] 是**客户端推出来的名次**（列表位置 + 页码偏移），站点并不下发。
 * 这样分页翻到第 2 页时名次也不会从头开始数。
 */
data class RankedUser(
    val id: Int,
    val rank: Int,
    val uname: String,
    val role: String,
    val avatarUrl: String?,
    val registeredAt: String?,
    val lastLoginAt: String?,
)

/**
 * 排行榜一页。
 *
 * [hasMore] 的判据写得比较保守，三个条件与关系：
 * 1. 拿到的列表非空（空列表绝不可能还有下一页）；
 * 2. 页码小于总页数 —— 但 `upcount` 的含义是【推断】的（见 `RankingPageDto`），
 *    所以**不能**单独依赖它；
 * 3. 兜底：已累计的人数还不到总人数。
 *
 * 任一条成立且列表非空就允许继续翻页。多请求一页的代价只是浪费一次网络往返，
 * 而少一页会让用户永远看不到后面的人 —— 两者不对称，所以宁可宽松。
 */
data class RankingPage(
    val page: Int,
    val totalUsers: Int,
    val totalPages: Int,
    val users: List<RankedUser>,
) {
    fun hasMore(loadedCount: Int): Boolean {
        if (users.isEmpty()) return false
        if (page < totalPages) return true
        return totalUsers > 0 && loadedCount < totalUsers
    }
}

/**
 * 用户主页。来自 `GET /user/:uid` 的 `udoc` + `isSelfProfile`【实测】。
 * 同样不含 `mail`。
 */
data class UserProfile(
    val id: Int,
    val uname: String,
    val role: String,
    val avatarUrl: String?,
    val registeredAt: String?,
    val lastLoginAt: String?,
    val isSelf: Boolean,
)

// ------------------------------------------------------------ 竞赛与作业

/** 竞赛/作业当前所处阶段。由起止时间与当前时刻推算，站点不下发。 */
enum class ContestPhase {
    /** 未开始。 */
    UPCOMING,

    /** 进行中（含作业未到截止）。 */
    RUNNING,

    /** 已结束。 */
    ENDED,
}

/** 竞赛与作业共用的阶段推算逻辑。时间解析失败时宁可保守（当作已结束），不抛异常。 */
object Phase {

    fun of(beginAtIso: String?, endAtIso: String?, now: Instant = Instant.now()): ContestPhase {
        val begin = parseIso(beginAtIso)
        val end = parseIso(endAtIso)
        if (begin != null && now.isBefore(begin)) return ContestPhase.UPCOMING
        if (end != null && now.isAfter(end)) return ContestPhase.ENDED
        // 起止缺一或解析失败：有起点且已过 → 进行中；否则按已结束处理（不假装"进行中"误导用户）
        return if (begin != null) ContestPhase.RUNNING else ContestPhase.ENDED
    }

    fun parseIso(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return try {
            Instant.parse(iso)
        } catch (ignored: DateTimeParseException) {
            try {
                java.time.OffsetDateTime.parse(iso).toInstant()
            } catch (ignored: DateTimeParseException) {
                null
            }
        }
    }

    /** 两个 ISO 时刻之间的人类可读时长，如「2 小时」「3 天 2 小时」。解析失败返回 null。 */
    fun durationLabel(beginAtIso: String?, endAtIso: String?): String? {
        val begin = parseIso(beginAtIso) ?: return null
        val end = parseIso(endAtIso) ?: return null
        val minutes = Duration.between(begin, end).toMinutes()
        if (minutes <= 0) return null
        val days = minutes / (24 * 60)
        val hours = (minutes % (24 * 60)) / 60
        val mins = minutes % 60
        return buildList {
            if (days > 0) add("$days 天")
            if (hours > 0) add("$hours 小时")
            if (mins > 0 && days == 0L) add("$mins 分钟")
            if (isEmpty()) add("$minutes 分钟")
        }.joinToString(" ")
    }
}

/** 竞赛（列表项与详情共用 —— 详情只是多了正文与题目列表）。 */
data class Contest(
    /** 站点 `_id` 原样字符串，每文档唯一。列表 key 用它；docId 缺失时这是唯一可靠标识。 */
    val id: String = "",
    val docId: Int,
    val title: String,
    val rule: String,
    val beginAt: String?,
    val endAt: String?,
    val attend: Int,
    val rated: Boolean,
) {
    val phase: ContestPhase
        get() = Phase.of(beginAt, endAt)

    /** 时长标签。`duration` 字段单位未采到样本，这里用起止差值算，比猜单位可靠。 */
    val durationLabel: String?
        get() = Phase.durationLabel(beginAt, endAt)
}

/** 竞赛详情。正文已由 Mapper 二次解析成 Markdown。 */
data class ContestDetail(
    val contest: Contest,
    val statement: String,
    /** 竞赛包含的题目。元素形态未采到样本，能解析出数字 docId 的才有导航能力。 */
    val problemDocIds: List<Int>,
)

/**
 * 竞赛/作业题目列表里的一道题。
 *
 * [label] 是竞赛题目在赛制里的字母标号（A、B、…、Z、AA…），
 * 按站点约定由 `tdoc.pids` 的顺序推算（Hydro `getAlphabeticId`），站点不下发。
 * [title] 来自 `/contest/:tid/problems` 或作业详情 `pdict`；拉取不到时为 null，
 * UI 回落显示「题目 #docId」。
 */
data class LabeledProblem(
    val label: String,
    val docId: Int,
    val title: String?,
)

/** 作业（题单）。 */
data class Homework(
    /** 站点 `_id` 原样字符串，每文档唯一（与 Contest.id 同因加入）。 */
    val id: String = "",
    val docId: Int,
    val title: String,
    val rule: String,
    val beginAt: String?,
    val endAt: String?,
    val penaltySince: String?,
    val attend: Int,
) {
    val phase: ContestPhase
        get() = Phase.of(beginAt, endAt)
}

/** 作业详情。 */
data class HomeworkDetail(
    val homework: Homework,
    val statement: String,
    /** 已认领（attend）状态。null = 站点没下发 tsdoc（多为游客态），无法判断。 */
    val claimed: Boolean?,
    val problems: List<LabeledProblem>,
)

// ------------------------------------------------------------ 自测（pretest）

/**
 * 一次自测的完整结果。
 *
 * 契约【实测 2026-09-13】：`POST /p/:pid/submit` 带 `pretest=true&input[]=...`，
 * 返回 `{rid}`；`GET /record/:rid` 的 `rdoc` 里 `testCases[]` 每项的 **`message`
 * 就是该组输入下程序的 stdout**。自测记录不进提交数、不出现在记录列表
 * （`contest` 被置为哨兵 ObjectId，列表查询天然过滤）。
 */
data class PretestResult(
    val statusCode: Int,
    val statusKey: String?,
    /** 提交给判题机的各组输入（站点会原样回显）。 */
    val inputs: List<String>,
    val cases: List<PretestCase>,
    val compilerMessages: List<String>,
) {
    val running: Boolean get() = Submission.isRunning(statusCode)
}

/** 自测的单组结果。 */
data class PretestCase(
    val id: Int,
    val statusKey: String?,
    val timeMs: Double?,
    val memoryKb: Int?,
    /** 程序在该组输入下的输出（站点字段名 `message`，实测）。 */
    val output: String?,
)

/** 题面里的一道样例（自测一键填充用）。解析失败时不会出现在列表里。 */
data class ProblemSample(
    val input: String,
    val output: String?,
)

// ------------------------------------------------------------ 竞赛成绩表（scoreboard）

/**
 * 竞赛成绩表。
 *
 * 契约【实测 2026-09-14】：`GET /contest/:tid/scoreboard` →
 * `{tdoc, tsdoc, rows, udict, pdict, page_name, groups, availableViews}`。
 * 站点已把榜单渲染成 **rows 矩阵**（网页端直接渲染的结构），App 端直接吃矩阵：
 * 首行是表头（rank / user / total_score + 每题一列，`raw` 为题目 docId），
 * 之后每行是一名选手。⚠️ `udict` 里明文含 `mail`，映射层**不解析 udict**，天然不落盘。
 */
data class Scoreboard(
    /** 题目列（表头里 `type == "problem"` 的格子，按场内顺序 = 字母标号顺序）。 */
    val columns: List<ScoreColumn>,
    /** 选手行（已按名次排序，服务端排好）。 */
    val rows: List<ScoreboardRow>,
)

/** 成绩表的一道题目列。 */
data class ScoreColumn(
    /** 字母标号（A、B、…），站点直接下发，无需客户端推算。 */
    val label: String,
    val docId: Int,
)

/** 成绩表的一名选手行。 */
data class ScoreboardRow(
    val rank: String,
    val uid: Int,
    val uname: String,
    /** 总分（站点以字符串下发，原样展示，不猜数值格式）。 */
    val totalScore: String,
    /** 与 [Scoreboard.columns] 一一对应的各题成绩格。 */
    val cells: List<ScoreCell>,
)

/** 成绩表的单格。 */
data class ScoreCell(
    /** 展示文本（"100" / "0" / "-"）。 */
    val scoreText: String,
    /** 数值分数。缺失（未提交）时为 null。 */
    val score: Int?,
    /** 该题当前计分提交的记录 id；null = 未提交。 */
    val rid: String?,
)
