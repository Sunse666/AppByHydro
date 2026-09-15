package com.jxau.oj.data.dto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 轻量 JSON 读取工具。
 *
 * 之所以不用 `@Serializable` 自动反序列化，有两个原因：
 * 1. 本机离线环境下 kotlin-serialization 编译器插件不可得；
 * 2. 更重要的是 —— 站点的字段裁剪是常态（列表版 / 详情版 / 搜索版字段集不同），
 *    手写映射能让"每个字段缺失时回落到什么值"完全显式可控，
 *    而不是依赖序列化库的默认值策略。
 *
 * 所有读取函数都不会抛异常：类型不符或缺失时返回默认值。
 */
internal val HydroJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** 宽松取字符串：数字/布尔也会被转成字符串，避免类型漂移导致整条数据丢失。 */
internal fun JsonObject.str(key: String, default: String = ""): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: default

internal fun JsonObject.strOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.int(key: String, default: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: default

internal fun JsonObject.long(key: String, default: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: default

/** 浮点数读取（如评测耗时 `0.835` 毫秒）。缺失或类型不符返回 null。 */
internal fun JsonObject.doubleOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()

internal fun JsonObject.boolOrNull(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.bool(key: String, default: Boolean = false): Boolean =
    boolOrNull(key) ?: default

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/**
 * 字符串数组。
 *
 * 标量（字符串 / 数字 / 布尔）一律转成文本 —— 与 [str] 同理，字段类型漂移时
 * 宁可留下一个 `"1"` 也不让整条数据消失（`tag` 真下发过数字形态）；
 * 只有对象 / 数组 / null 这类**结构不符**的元素才跳过，不让一处脏数据废掉整个列表。
 */
internal fun JsonObject.strList(key: String): List<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .orEmpty()

internal fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

/** 嵌套数组的数组（如成绩表 `rows`：每行是一个单元格对象数组）。缺失返回空列表。 */
internal fun JsonObject.arrays(key: String): List<JsonArray> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonArray }.orEmpty()

/**
 * 「带正文的数组」：元素可能是字符串，也可能是 `{"message": "..."}` / `{"text": "..."}`。
 * 站点的 `compilerTexts` / `judgeTexts` 属于后者，但两种都容错 —— 这类字段的结构
 * 未经 M0 验证，不能赌。
 */
internal fun JsonObject.textList(key: String): List<String> {
    val array = this[key] as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject ->
                (element["message"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: (element["text"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            else -> null
        }
    }
}
