package com.jxau.oj.data.net

import com.jxau.oj.data.dto.HydroJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 响应分发：把 `(状态码, Content-Type, body)` 判成 [HydroResult] 的四种形态之一。
 *
 * **为什么单独抽出来**：这是全 App 最该被钉死的一段逻辑 —— 它的每一处误判都会
 * 被上层读成完全不同的用户可见语义（"没交过题" vs "App 没适配"、
 * "登录过期" vs "站点点错了"），而它此前只能靠真机随手点来验证。
 * 抽成不依赖 OkHttp / Android 的纯函数后，就能进离线自检（见 `tools/pure_helpers_check`）。
 *
 * **判据顺序不能改**（每一级的先后都有实测依据）：
 * 1. 先看 body 像不像 JSON —— 不像就一定是网关/代理层吐的 HTML，别再去猜状态码；
 * 2. 再看是不是未登录软跳转（**判据是 body 里 `url` 指向 `/login`，不是状态码**，
 *    实测 `GET /record`、匿名 `POST /p/1/submit` 都是 200）；
 * 3. 再看有没有 `error` 信封（**200 也可能是错误**，校验失败就是 200）；
 * 4. 最后才敢当成功。`status >= 400` 且 body 里没有 `error` 信封的，兜底成业务错误。
 */
internal object ResponseDispatch {

    fun of(status: Int, contentType: String?, body: String): HydroResult<String> {
        val trimmed = body.trimStart()
        val looksJson = trimmed.startsWith("{") || trimmed.startsWith("[")
        if (!looksJson) {
            val type = contentType?.substringBefore(';') ?: "未知类型"
            return HydroResult.TransportError("响应不是 JSON（HTTP $status，Content-Type: $type）")
        }

        val element = try {
            HydroJson.parseToJsonElement(body)
        } catch (e: Exception) {
            return HydroResult.TransportError("JSON 解析失败：${e.message}", e)
        }

        if (element is JsonObject) {
            // 形态二：未登录软跳转。判据是 body 里的 url 指向登录页，而不是状态码。
            val urlValue = (element["url"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            if (urlValue != null && urlValue.startsWith("/login")) {
                return HydroResult.NeedLogin(urlValue)
            }

            // 形态三：业务错误信封
            val error = element["error"] as? JsonObject
            if (error != null) {
                val code = (error["code"] as? JsonPrimitive)?.intOrNull ?: status
                val rawMessage = (error["message"] as? JsonPrimitive)?.contentOrNull ?: "请求失败"
                val name = (error["name"] as? JsonPrimitive)?.contentOrNull
                val params = error["params"]?.let { p ->
                    (p as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                        .orEmpty()
                }.orEmpty()
                return HydroResult.Failure(code, substitute(rawMessage, params), name, params)
            }
        }

        if (status >= 400) return HydroResult.Failure(status, "服务异常（HTTP $status）")
        return HydroResult.Success(body)
    }

    /**
     * Hydro 的错误 message 模板形如 `"User {0} not found."`，实际值在 `params` 数组里。
     * 不替换的话用户会看到一句带 `{0}` 的天书。
     */
    fun substitute(template: String, params: List<String>): String {
        if (params.isEmpty() || !template.contains('{')) return template
        var out = template
        params.forEachIndexed { i, v -> out = out.replace("{$i}", v) }
        return out
    }
}
