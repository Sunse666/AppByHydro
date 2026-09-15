package com.jxau.oj.data.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * 提交与评测相关的 DTO。
 *
 * ⚠️ **本文件是全工程"契约未验证"最集中的地方。**
 *
 * 提交接口 `/p/{docId}/submit` 的请求体字段名、响应体字段名、评测记录
 * `GET /record/{rid}` 的字段名与状态码语义，**全部没有实测数据**（没有账号）。
 * 已按 Hydro 的通用约定实现，并把所有候选键名都做成"依次尝试"，
 * 使 M0 跑完后**只需要改这一个文件**即可定稿。
 *
 * 设计原则：宁可多试几个候选键，也不要因为键名猜错而整条链路失效。
 */

/** `POST /p/{docId}/submit` 的响应。Hydro 约定返回 `{"rid": "..."}`。 */
data class SubmitResultDto(
    val rid: String = "",
) {
    companion object {
        fun from(o: JsonObject): SubmitResultDto {
            // 候选键依次尝试：rid（Hydro 约定）/ _id / id
            val rid = o.strOrNull("rid")
                ?: o.strOrNull("_id")
                ?: o.strOrNull("id")
                ?: ""
            return SubmitResultDto(rid)
        }
    }
}

/**
 * 评测记录。`GET /record/{rid}` 的响应可能把记录包在 `rdoc` 里，也可能直接平铺，
 * 两种都兼容。
 */
data class RecordDto(
    val rid: String = "",
    val statusCode: Int = -1,
    val score: Int = 0,
    val timeMs: Int = 0,
    val memoryKb: Int = 0,
    val lang: String = "",
    val pid: String = "",
    val title: String = "",
    val compilerTexts: List<String> = emptyList(),
    val judgeTexts: List<String> = emptyList(),
    val code: String? = null,
    /** 提交者。全站记录列表要显示"谁交的"；自己的记录列表用不上。 */
    val uid: Int = 0,
    val uname: String = "",
) {
    companion object {
        fun from(o: JsonObject): RecordDto {
            // 记录可能整体包在 rdoc 里
            val body = o.obj("rdoc") ?: o
            // 提交者也可能是嵌套对象，两种形态都试
            val udoc = body.obj("udoc")
            return RecordDto(
                rid = body.strOrNull("_id") ?: body.strOrNull("rid") ?: "",
                statusCode = body.int("status", -1),
                score = body.int("score"),
                timeMs = body.int("time"),
                memoryKb = body.int("memory"),
                lang = body.str("lang"),
                pid = body.str("pid"),
                title = body.strOrNull("title") ?: body.str("pdocTitle"),
                compilerTexts = body.textList("compilerTexts"),
                judgeTexts = body.textList("judgeTexts"),
                code = body.strOrNull("code"),
                uid = body.int("uid").takeIf { it != 0 } ?: udoc?.int("_id") ?: 0,
                uname = body.strOrNull("uname") ?: udoc?.str("uname") ?: "",
            )
        }
    }
}

/**
 * 自测（pretest）记录详情。`GET /record/{rid}`，rdoc 内含 `testCases[]` 与 `input[]`。
 *
 * 契约【实测 2026-09-13】：`testCases[]` 每项
 * `{id, subtaskId, status, score, time, memory, message}`，
 * 其中 **`message` 就是该组输入下程序的 stdout**（如 `"8 -2\n"`）；
 * `time` 是浮点毫秒（如 0.835）。`input` 是提交时各组输入的原样回显。
 * 测评中时 `testCases` 可能为空数组。
 */
data class PretestRecordDto(
    val statusCode: Int = -1,
    val inputs: List<String> = emptyList(),
    val cases: List<PretestCaseDto> = emptyList(),
    val compilerTexts: List<String> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject): PretestRecordDto {
            val body = o.obj("rdoc") ?: o
            return PretestRecordDto(
                statusCode = body.int("status", -1),
                inputs = body.strList("input"),
                cases = body.objects("testCases")
                    .map { PretestCaseDto.from(it) }
                    .sortedBy { it.id },
                compilerTexts = body.textList("compilerTexts"),
            )
        }
    }
}

data class PretestCaseDto(
    val id: Int = 0,
    val statusCode: Int = -1,
    val timeMs: Double? = null,
    val memoryKb: Int? = null,
    val output: String? = null,
) {
    companion object {
        fun from(o: JsonObject) = PretestCaseDto(
            id = o.int("id"),
            statusCode = o.int("status", -1),
            timeMs = o.doubleOrNull("time"),
            memoryKb = o.int("memory").takeIf { it != 0 },
            output = o.strOrNull("message"),
        )
    }
}

/** 提交记录列表项（`GET /record`）。 */
data class RecordListDto(
    val page: Int = 1,
    val totalCount: Int = 0,
    val records: List<RecordDto> = emptyList(),
    /**
     * 响应里**是否出现过我们认识的记录数组键**（`rdocs` / `docs`）。
     *
     * 这个字段只有一个用途：把"真的没有提交记录"和"数组键名猜错了"区分开。
     * 两者在 `records.isEmpty()` 时长得一模一样，但给用户的解释完全不同 ——
     * 前者是"你还没交过"，后者是"App 还没适配站点的返回格式"。
     * 混淆这两者会让用户以为自己没交过题。
     */
    val sawKnownArray: Boolean = false,
) {
    companion object {
        fun from(o: JsonObject): RecordListDto {
            // 实测只知道这条路需要登录，字段名未知 → 数组键名按候选依次尝试
            val rdocs = o.objects("rdocs")
            val docs = o.objects("docs")
            val array = rdocs.ifEmpty { docs }
            return RecordListDto(
                page = o.int("page", 1),
                // 总数键名同样不确定：rcount 是 Hydro 的通用约定，其余为兜底。
                // ⚠️ 每一级都要 `.takeIf { it != 0 }` —— `int()` 返回的是**非空** Int，
                // 漏掉某一级会让 `?:` 变成死代码（编译器只给个警告，编译照样过），
                // 后面那个候选键**永远不会被尝试**。实测踩过：写 `?: o.int("count") ?: o.int("total")`
                // 时 `total` 是死的，样例 `{"total":42}` 会静默读成 0。
                totalCount = o.int("rcount").takeIf { it != 0 }
                    ?: o.int("count").takeIf { it != 0 }
                    ?: o.int("total"),
                records = array.map { RecordDto.from(it) },
                sawKnownArray = hasArray(o, "rdocs", "docs"),
            )
        }

        private fun hasArray(o: JsonObject, vararg keys: String): Boolean =
            keys.any { o[it] is JsonArray }
    }
}
