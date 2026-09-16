package com.jxau.oj.data.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 竞赛与作业的 DTO。
 *
 * **契约【实测】**（见方案 3.4.5）：
 * - `GET /contest` → `{page, tpcount, tdocs[], tsdict, ...}`
 * - `GET /contest/:tid` → `{tdoc}`
 * - `GET /homework` → `{tdocs[], calendar, tpcount, page, ...}`
 * - `GET /homework/:tid` → `{tdoc, tsdoc, udict, ddocs, page}`
 *
 * ⚠️ 竞赛 tdoc 与作业 tdoc 的字段集**不同**（竞赛有 `duration` / `allowViewCode` / `lockAt`，
 * 作业有 `penaltySince` / `penaltyRules`），因此**分成两个 DTO**，不共用 ——
 * 共用会让反序列化时字段错位或语义混淆。两者都是全字段默认值，脏字段安全。
 *
 * 正文 `content` 与题目一样是**嵌套 JSON 字符串**，二次解析统一走 `Mappers.statement()`。
 */

/** 竞赛的 `tdoc`。字段集为实测（方案 3.4.5）。 */
data class ContestTdocDto(
    val id: String = "",
    val docId: Int = 0,
    val title: String = "",
    val rule: String = "",
    val beginAt: String? = null,
    val endAt: String? = null,
    /**
     * 竞赛时长。⚠️ 单位是**小时** —— Hydro 源码 `isOngoing` 用
     * `tdoc.duration * Time.hour` 折算【2026-09-16 源码核对】，
     * 此前「单位未明确」的注释就此销账。个人截止 = tsdoc.startAt + duration 小时。
     */
    val durationHours: Long = 0,
    val attend: Int = 0,
    val rated: Boolean = false,
    val content: String? = null,
    /** 题目 id 列表。元素形态实测样本未采到（列表页没有点开过详情），映射保持防御。 */
    val pids: List<String> = emptyList(),
    val maintainer: Int = 0,
) {
    companion object {
        fun from(o: JsonObject) = ContestTdocDto(
            id = o.str("_id"),
            // ⚠️ 实测（MuMu 真机 2026-09-13）：/contest 列表响应里 docId 可能缺失，
            // 全部回落 0 曾导致 LazyColumn key 重复 → 渲染即崩。列表页用 _id 兜底。
            docId = o.int("docId").takeIf { it != 0 }
                ?: o.str("_id").trim().toIntOrNull()
                ?: 0,
            title = o.str("title"),
            rule = o.str("rule"),
            beginAt = o.strOrNull("beginAt"),
            endAt = o.strOrNull("endAt"),
            durationHours = o.long("duration"),
            attend = o.int("attend"),
            rated = o.bool("rated"),
            content = o.strOrNull("content"),
            pids = o.strList("pids"),
            maintainer = o.int("maintainer"),
        )
    }
}

/** `GET /contest` 的响应。 */
data class ContestListDto(
    val page: Int = 1,
    val totalPages: Int = 0,
    val contests: List<ContestTdocDto> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject) = ContestListDto(
            page = o.int("page", 1),
            totalPages = o.int("tpcount"),
            contests = o.objects("tdocs").map { ContestTdocDto.from(it) },
        )
    }
}

/** `GET /contest/:tid` 的响应。 */
data class ContestDetailDto(
    val tdoc: ContestTdocDto? = null,
    /**
     * tsdoc 的 startAt/endAt（已报名的登录用户才下发；游客态实测只有 `{tdoc}`）。
     * 原样透传给模型层合成个人截止 —— 阶段徽标据此对齐服务端 `isOngoing`。
     */
    val tsStartAt: String? = null,
    val tsEndAt: String? = null,
) {
    companion object {
        fun from(o: JsonObject) = ContestDetailDto(
            tdoc = o.obj("tdoc")?.let { ContestTdocDto.from(it) },
            tsStartAt = o.obj("tsdoc")?.strOrNull("startAt"),
            tsEndAt = o.obj("tsdoc")?.strOrNull("endAt"),
        )
    }
}

/**
 * `GET /contest/:tid/problems` 的响应（【实测 2026-09-13】）。
 *
 * `{pdict, psdict, rdict, rdocs, tdoc, tsdoc, udict, tcdocs, showScore, canViewRecord}`。
 * `pdict` 的键是题目 docId（字符串形态），值是题目文档的竞赛投影
 * （`_id/docId/title/config/...`，**没有正文**）。题目顺序以 `tdoc.pids` 为准，
 * DTO 层不排序 —— 交给 Mapper 按调用方手里的 pids 排。
 *
 * 未开始的竞赛、进行中未报名的竞赛请求它会得到错误信封
 * （`ContestNotLiveError` / `ContestNotAttendedError`），调用方需兜底。
 */
data class ContestProblemsDto(
    /** docId → 题目名。 */
    val titles: Map<Int, String> = emptyMap(),
) {
    companion object {
        fun from(o: JsonObject): ContestProblemsDto {
            val pdict = o.obj("pdict") ?: return ContestProblemsDto(emptyMap())
            val titles = buildMap {
                pdict.forEach { (key, value) ->
                    val docId = key.toIntOrNull() ?: (value as? JsonObject)?.int("docId")?.takeIf { it != 0 } ?: return@forEach
                    val title = (value as? JsonObject)?.str("title") ?: return@forEach
                    if (title.isNotBlank()) put(docId, title)
                }
            }
            return ContestProblemsDto(titles)
        }
    }
}

/** 作业的 `tdoc`。字段集为实测（方案 3.4.5），与竞赛 tdoc 刻意不同。 */
data class HomeworkTdocDto(
    val id: String = "",
    val docId: Int = 0,
    val title: String = "",
    val rule: String = "",
    val beginAt: String? = null,
    val endAt: String? = null,
    val attend: Int = 0,
    val rated: Boolean = false,
    /** 迟交从该时刻开始计罚。实测字段名 `penaltySince`，单位/形态未采到样本，原样透传。 */
    val penaltySince: String? = null,
    val content: String? = null,
    val pids: List<String> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject) = HomeworkTdocDto(
            id = o.str("_id"),
            // 与 ContestTdocDto 同因：列表响应 docId 缺失时用 _id 兜底
            docId = o.int("docId").takeIf { it != 0 }
                ?: o.str("_id").trim().toIntOrNull()
                ?: 0,
            title = o.str("title"),
            rule = o.str("rule"),
            beginAt = o.strOrNull("beginAt"),
            endAt = o.strOrNull("endAt"),
            attend = o.int("attend"),
            rated = o.bool("rated"),
            penaltySince = o.strOrNull("penaltySince"),
            content = o.strOrNull("content"),
            pids = o.strList("pids"),
        )
    }
}

/** `GET /homework` 的响应。 */
data class HomeworkListDto(
    val page: Int = 1,
    val totalPages: Int = 0,
    val homework: List<HomeworkTdocDto> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject) = HomeworkListDto(
            page = o.int("page", 1),
            totalPages = o.int("tpcount"),
            homework = o.objects("tdocs").map { HomeworkTdocDto.from(it) },
        )
    }
}

/**
 * `GET /homework/:tid` 的响应。
 *
 * 契约【实测 2026-09-13】：键为
 * `{tdoc, tsdoc, udict, ddocs, page, dpcount, dcount}`，且在
 * （已认领 或 作业已截止）时额外下发 **`pdict`** —— 题目字典，键为 docId。
 * ⚠️ `ddocs` 是**讨论区**文档（Hydro 源码 `discussion.getMulti(parentType=tdoc.docType)`），
 * 不是题目 —— 此前把 ddocs 当题目解析是错的，题目只能来自 `pdict`。
 * 进行中且未认领的作业不下发 `pdict` —— 这正是「认领后才能看到题目」的站点行为。
 */
data class HomeworkDetailDto(
    val tdoc: HomeworkTdocDto? = null,
    /** `tsdoc.attend`。tsdoc 缺失（从未操作过）时为 null。 */
    val claimed: Boolean? = null,
    /** tsdoc 的 startAt/endAt（原样透传，模型层合成个人截止；作业 tdoc 无 duration）。 */
    val tsStartAt: String? = null,
    val tsEndAt: String? = null,
    val problems: Map<Int, String> = emptyMap(),
) {
    companion object {
        fun from(o: JsonObject): HomeworkDetailDto {
            val titles = o.obj("pdict")?.let { pdict ->
                buildMap {
                    pdict.forEach { (key, value) ->
                        val docId = key.toIntOrNull()
                            ?: ((value as? JsonObject)?.int("docId")?.takeIf { it != 0 })
                            ?: return@forEach
                        val title = (value as? JsonObject)?.str("title") ?: return@forEach
                        if (title.isNotBlank()) put(docId, title)
                    }
                }
            }.orEmpty()
            return HomeworkDetailDto(
                tdoc = o.obj("tdoc")?.let { HomeworkTdocDto.from(it) },
                // ⚠️ 实测：attend 是数字 1/0，不是布尔 —— boolOrNull 会返回 null，
                // 必须按整数判。tsdoc 整个缺失（从未操作过）才返回 null。
                claimed = o.obj("tsdoc")?.let { ts ->
                    ts.boolOrNull("attend") ?: (ts.int("attend") == 1)
                },
                tsStartAt = o.obj("tsdoc")?.strOrNull("startAt"),
                tsEndAt = o.obj("tsdoc")?.strOrNull("endAt"),
                problems = titles,
            )
        }
    }
}

/**
 * `GET /contest/:tid/scoreboard` 的响应（【实测 2026-09-14】）。
 *
 * `{tdoc, tsdoc, rows, udict, pdict, page_name, groups, availableViews}`，其中 `rows`
 * 是站点已渲染好的表格矩阵：
 * - `rows[0]` 表头：`[{type:"rank"},{type:"user"},{type:"total_score"},{type:"problem",value:"A",raw:154},…]`
 * - `rows[1..]` 数据行：`[{type:"rank",value:"1"},{type:"user",value:"uname",raw:uid},`
 *   `{type:"total_score",value:"1300"},{type:"record",value:"100",raw:"<rid>",score:100},…]`
 *   未提交的格子是 `{type:"record", value:"-", raw:null}`（无 `score` 键）。
 *
 * ⚠️ `udict` 明文含 `mail` —— 本 DTO **刻意不解析 udict**（用户名与 uid 在数据行里都有），
 * 隐私字段根本不进入客户端内存。`style`（内联 HTML 背景色）同样不解析，判定色由
 * App 端按分数用 VerdictColors 表达 —— 与全 App 的判题色保持一致。
 */
data class ScoreboardDto(
    val columns: List<ScoreColumnDto> = emptyList(),
    val rows: List<ScoreRowDto> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject): ScoreboardDto {
            val rows = o.arrays("rows")
            if (rows.isEmpty()) return ScoreboardDto()

            // 首行表头：收集题目列（label + docId）
            val columns = rows.first().mapNotNull { cell ->
                if ((cell as? JsonObject)?.str("type") != "problem") return@mapNotNull null
                ScoreColumnDto(label = cell.str("value"), docId = cell.int("raw"))
            }

            val dataRows = rows.drop(1).mapNotNull { row ->
                var rank = ""
                var uid = 0
                var uname = ""
                var total = ""
                val cells = ArrayList<ScoreCellDto>(columns.size)
                for (cell in row) {
                    val c = cell as? JsonObject ?: continue
                    when (c.str("type")) {
                        "rank" -> rank = c.str("value")
                        "user" -> {
                            uname = c.str("value")
                            uid = c.int("raw")
                        }
                        "total_score" -> total = c.str("value")
                        "record" -> cells.add(
                            ScoreCellDto(
                                scoreText = c.str("value", "-"),
                                score = c.intOrNull("score"),
                                rid = c.strOrNull("raw"),
                            ),
                        )
                    }
                }
                // 没有任何用户信息的行视为脏数据，整行丢弃
                if (uname.isBlank()) null else ScoreRowDto(rank, uid, uname, total, cells)
            }
            return ScoreboardDto(columns, dataRows)
        }

        private fun JsonObject.intOrNull(key: String): Int? {
            val o = this[key] as? JsonPrimitive ?: return null
            return o.contentOrNull?.toIntOrNull()
        }
    }
}

data class ScoreColumnDto(val label: String, val docId: Int)

data class ScoreRowDto(
    val rank: String,
    val uid: Int,
    val uname: String,
    val totalScore: String,
    val cells: List<ScoreCellDto>,
)

data class ScoreCellDto(
    val scoreText: String,
    val score: Int?,
    val rid: String?,
)
