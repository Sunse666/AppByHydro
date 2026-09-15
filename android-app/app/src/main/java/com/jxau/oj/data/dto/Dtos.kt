package com.jxau.oj.data.dto

import kotlinx.serialization.json.JsonObject

/**
 * 全部 DTO 遵循两条规则：
 * 1. **每个字段都有默认值** —— 站点列表版 / 详情版 / 搜索版字段集不同，缺字段是常态。
 * 2. **不声明不该拿的字段** —— 尤其是 `mail`。接口明文下发了它，但我们连字段都不建，
 *    这样它在类型层面就进不了模型、进不了日志、进不了本地存储。
 *
 * 解析入口统一是 `from(JsonObject)`，读取失败一律回落默认值，不抛异常。
 */

data class UserDto(
    val id: Int = 0,
    val uname: String = "",
    val role: String = "guest",
    val priv: Long = 0,
    val avatar: String? = null,
    val timeZone: String? = null,
    val codeLang: String? = null,
    val theme: String? = null,
    val formatCode: Boolean? = null,
    val regat: String? = null,
    val loginat: String? = null,
) {
    companion object {
        fun from(o: JsonObject) = UserDto(
            id = o.int("_id"),
            uname = o.str("uname"),
            role = o.str("role", "guest"),
            priv = o.long("priv"),
            avatar = o.strOrNull("avatar"),
            timeZone = o.strOrNull("timeZone"),
            codeLang = o.strOrNull("codeLang"),
            theme = o.strOrNull("theme"),
            formatCode = o.boolOrNull("formatCode"),
            regat = o.strOrNull("regat"),
            loginat = o.strOrNull("loginat"),
        )
    }
}

data class ProblemStatsDto(
    val ac: Int = 0,
    val wa: Int = 0,
    val tle: Int = 0,
    val mle: Int = 0,
    val re: Int = 0,
    val ce: Int = 0,
    val se: Int = 0,
    val ign: Int = 0,
) {
    companion object {
        fun from(o: JsonObject) = ProblemStatsDto(
            ac = o.int("AC"),
            wa = o.int("WA"),
            tle = o.int("TLE"),
            mle = o.int("MLE"),
            re = o.int("RE"),
            ce = o.int("CE"),
            se = o.int("SE"),
            ign = o.int("IGN"),
        )
    }
}

data class ProblemConfigDto(
    val count: Int = 0,
    val memoryMin: Int = 0,
    val memoryMax: Int = 0,
    val timeMin: Int = 0,
    val timeMax: Int = 0,
    val type: String = "default",
    /** 实测为字符串数组，如 ["bash","c","cc","cc.cc17","py.py3", ...]。 */
    val langs: List<String> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject) = ProblemConfigDto(
            count = o.int("count"),
            memoryMin = o.int("memoryMin"),
            memoryMax = o.int("memoryMax"),
            timeMin = o.int("timeMin"),
            timeMax = o.int("timeMax"),
            type = o.str("type", "default"),
            langs = o.strList("langs"),
        )
    }
}

data class ProblemFileDto(
    val id: String = "",
    val name: String = "",
    val size: Long = 0,
) {
    companion object {
        fun from(o: JsonObject) = ProblemFileDto(
            id = o.str("_id"),
            name = o.str("name"),
            size = o.long("size"),
        )
    }
}

/**
 * 题目对象在列表与详情中共用。
 * 列表版只有 11 个字段（无 `pid` / `content` / `config`），详情版是超集 —— 取并集 + 全默认值。
 */
data class ProblemDto(
    val id: String = "",
    val docId: Int = 0,
    val docType: Int = 0,
    val domainId: String = "system",
    val pid: String? = null,
    val title: String = "",
    val tag: List<String> = emptyList(),
    val hidden: Boolean = false,
    val nSubmit: Int = 0,
    val nAccept: Int = 0,
    val stats: ProblemStatsDto? = null,
    /** 嵌套 JSON 字符串，形如 `{"en":"...","zh":"..."}`，需要二次解析。 */
    val content: String? = null,
    val config: ProblemConfigDto? = null,
    val data: List<ProblemFileDto> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject) = ProblemDto(
            id = o.str("_id"),
            docId = o.int("docId"),
            docType = o.int("docType"),
            domainId = o.str("domainId", "system"),
            pid = o.strOrNull("pid"),
            title = o.str("title"),
            tag = o.strList("tag"),
            hidden = o.bool("hidden"),
            nSubmit = o.int("nSubmit"),
            nAccept = o.int("nAccept"),
            stats = o.obj("stats")?.let { ProblemStatsDto.from(it) },
            content = o.strOrNull("content"),
            config = o.obj("config")?.let { ProblemConfigDto.from(it) },
            data = o.objects("data").map { ProblemFileDto.from(it) },
        )
    }
}

data class ProblemListDto(
    val page: Int = 1,
    val pcount: Int = 0,
    val ppcount: Int = 0,
    val pdocs: List<ProblemDto> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject) = ProblemListDto(
            page = o.int("page", 1),
            pcount = o.int("pcount"),
            ppcount = o.int("ppcount"),
            pdocs = o.objects("pdocs").map { ProblemDto.from(it) },
        )
    }
}

data class ProblemDetailEnvelopeDto(
    val pdoc: ProblemDto? = null,
    val udoc: UserDto? = null,
    val title: String = "",
    val mode: String = "normal",
) {
    companion object {
        fun from(o: JsonObject) = ProblemDetailEnvelopeDto(
            pdoc = o.obj("pdoc")?.let { ProblemDto.from(it) },
            udoc = o.obj("udoc")?.let { UserDto.from(it) },
            title = o.str("title"),
            mode = o.str("mode", "normal"),
        )
    }
}

/** `GET /login` 的 JSON：只用来探测站点启用了哪些登录方式。 */
data class LoginOptionsDto(
    val redirect: String = "",
    val builtInLogin: Boolean = true,
    val loginMethods: List<String> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject) = LoginOptionsDto(
            redirect = o.str("redirect"),
            builtInLogin = o.bool("builtInLogin", true),
            loginMethods = o.strList("loginMethods"),
        )
    }
}

/**
 * 排行榜。[实测] 响应形如：
 * ```
 * { "udocs": [ { "_id":3, "uname":"...", "mail":"...", "perm":"BigInt::...",
 *                "role":"default", "priv":16842756, "regat":"...",
 *                "loginat":"...", "avatar":"qq:..." } ],
 *   "upcount": 1, "ucount": 31, "page": 2 }
 * ```
 *
 * ⚠️ `upcount` / `ucount` 的精确分工是**【推断】**：实测样本里 `ucount = 31`（与站点
 * 注册用户数一致，故判为**总人数**），`upcount = 1`（故判为**总页数**）。
 * 这个判断与题目列表的 `pcount` / `ppcount` 命名同构，但没有第二个样本可供交叉验证 ——
 * 所以 [com.jxau.oj.data.model.RankingPage.hasMore] 不单独依赖它，见该处说明。
 *
 * 复用的是 [UserDto]，它**不声明 `mail`** —— 榜单是站点明文下发邮箱的地方（隐私红线），
 * 我们连字段都不建，`mail` 自然进不来。
 */
data class RankingPageDto(
    val page: Int = 1,
    val ucount: Int = 0,
    val upcount: Int = 1,
    val udocs: List<UserDto> = emptyList(),
) {
    companion object {
        fun from(o: JsonObject) = RankingPageDto(
            page = o.int("page", 1),
            ucount = o.int("ucount"),
            upcount = o.int("upcount", 1),
            udocs = o.objects("udocs").map { UserDto.from(it) },
        )
    }
}

/**
 * `GET /user/:uid`。[实测] 响应形如：
 * ```
 * { "isSelfProfile": false, "udoc": { "_id":1, "uname":"Hydro", "mail":"...", ... } }
 * ```
 */
data class UserProfileEnvelopeDto(
    val isSelfProfile: Boolean = false,
    val udoc: UserDto? = null,
) {
    companion object {
        fun from(o: JsonObject) = UserProfileEnvelopeDto(
            isSelfProfile = o.bool("isSelfProfile"),
            udoc = o.obj("udoc")?.let { UserDto.from(it) },
        )
    }
}
