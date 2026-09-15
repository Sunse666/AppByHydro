package com.jxau.oj.data.net

import com.jxau.oj.data.dto.HydroJson
import kotlinx.serialization.json.JsonObject

/**
 * 网络层的唯一出口类型。
 *
 * 之所以不直接用 HTTP 状态码，是因为本站在 `Accept: application/json` 下的
 * 错误形态和状态码并不同步（详见方案 3.2）：登录跳转是 200，校验错误也可能是 200。
 * 所以判断依据是 body 结构，不是状态码。
 */
sealed interface HydroResult<out T> {

    /** 形态一：正常数据。 */
    data class Success<T>(val data: T) : HydroResult<T>

    /**
     * 形态二：未登录软跳转 —— HTTP 200，body 为 `{"url":"/login?redirect=..."}`。
     * 实测自 `GET /record`、`POST /p/1/submit`（匿名）。
     */
    data class NeedLogin(val redirectUrl: String) : HydroResult<Nothing>

    /** 形态三：业务错误信封 —— body 为 `{"error":{code,message,name}}`。 */
    data class Failure(
        val code: Int,
        val message: String,
        val name: String? = null,
        /** 信封里的 `params` 数组原值（message 模板已被替换过，这里供上层做语义判断）。 */
        val params: List<String> = emptyList(),
    ) : HydroResult<Nothing> {

        /**
         * 给用户看的一句话。与 [message] 的区别：[message] 是站点原文（保留用于诊断，
         * 也是纯函数自检钉住的契约），这里经过 [ErrorMessages.humanize] 翻译，
         * 让用户知道**自己该做什么**。UI 一律用这个。
         */
        val friendly: String get() = ErrorMessages.humanize(name, message)
    }

    /** 形态四：传输层失败 —— 网络不可达、响应不是 JSON、解析异常。 */
    data class TransportError(
        val message: String,
        val cause: Throwable? = null,
    ) : HydroResult<Nothing>
}

/**
 * 把成功响应的原始字符串解析为 `JsonObject`，再交给 [transform] 映射成 DTO。
 * 其余三种形态原样透传。
 *
 * 这替代了 `kotlinx.serialization` 的自动反序列化 —— 见
 * `data/dto/JsonSupport.kt` 里对原因的解释。
 */
fun <T> HydroResult<String>.mapJson(transform: (JsonObject) -> T): HydroResult<T> =
    when (this) {
        is HydroResult.Success -> {
            val obj = try {
                HydroJson.parseToJsonElement(data) as? JsonObject
            } catch (e: Exception) {
                null
            }
            if (obj == null) {
                HydroResult.TransportError("响应不是 JSON 对象")
            } else {
                try {
                    HydroResult.Success(transform(obj))
                } catch (e: Exception) {
                    HydroResult.TransportError("数据解析失败：${e.message}", e)
                }
            }
        }
        is HydroResult.NeedLogin -> this
        is HydroResult.Failure -> this
        is HydroResult.TransportError -> this
    }

/** 变换成功值，其余形态原样透传。 */
inline fun <T, R> HydroResult<T>.map(transform: (T) -> R): HydroResult<R> =
    when (this) {
        is HydroResult.Success -> HydroResult.Success(transform(data))
        is HydroResult.NeedLogin -> this
        is HydroResult.Failure -> this
        is HydroResult.TransportError -> this
    }

val HydroResult<*>.isSuccess: Boolean
    get() = this is HydroResult.Success

/**
 * 给 UI 用的一句话错误描述。
 * `NeedLogin` 不在此列 —— 它应由导航层拦截并跳登录页，而不是当错误弹出来。
 */
fun HydroResult<*>.errorText(): String? = when (this) {
    is HydroResult.Success -> null
    is HydroResult.NeedLogin -> null
    is HydroResult.Failure -> friendly
    is HydroResult.TransportError -> message
}
