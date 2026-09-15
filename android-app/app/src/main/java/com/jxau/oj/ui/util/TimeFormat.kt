package com.jxau.oj.ui.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 时间显示。
 *
 * 站点下发的时间一律是 **ISO8601 UTC**（实测，见方案 3.4.4），
 * 用户时区偏好是 `Asia/Shanghai`。这里统一按**设备当前时区**换算并展示 ——
 * 手机上看到的应该和手机时钟一致，而不是和站点配置一致。
 *
 * `java.time` 在 minSdk 26 上可直接用，无需 desugaring。
 * 解析失败一律返回 null，由调用方决定是隐藏该行还是给占位 —— 不抛异常。
 */
object TimeFormat {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val clockFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** `14:30:05`。用于「最近同步」这类只关心当天时刻的场景。 */
    fun clock(epochMillis: Long?): String? =
        epochMillis?.let {
            Instant.ofEpochMilli(it).atZone(zone).format(clockFmt)
        }

    /** `2025-03-08 14:30`。用于"注册于"这类不关心多久之前的场景。 */
    fun dateTime(iso: String?): String? =
        parse(iso)?.atZone(zone)?.format(dateTimeFmt)

    fun date(iso: String?): String? =
        parse(iso)?.atZone(zone)?.format(dateFmt)

    /**
     * `刚刚` / `12 分钟前` / `3 小时前` / `5 天前`，超过 30 天退化为日期。
     *
     * 超过 30 天不用"x 个月前"：月份长度不一，"3 个月前"到底多少天会让人反复换算，
     * 不如直接给日期。
     */
    fun relative(iso: String?, now: Instant = Instant.now()): String? =
        parse(iso)?.let { relativeOf(it, now) }

    /**
     * epoch millis 版本。
     *
     * 提交记录的时间优先来自 `rid` 的 ObjectId 反解（见 `Mappers.objectIdTimestamp`），
     * 拿到的是毫秒，所以走这个入口而不是先转成 ISO 再解析。
     */
    fun relativeFromMillis(millis: Long?, now: Instant = Instant.now()): String? =
        millis?.let { relativeOf(Instant.ofEpochMilli(it), now) }

    private fun relativeOf(instant: Instant, now: Instant): String {
        val seconds = Duration.between(instant, now).seconds

        // 站点与设备时钟可能有偏差，未来时间按"刚刚"处理，不显示负数
        if (seconds < 60) return "刚刚"
        if (seconds < 3_600) return "${seconds / 60} 分钟前"
        if (seconds < 86_400) return "${seconds / 3_600} 小时前"
        val days = seconds / 86_400
        if (days <= 30) return "$days 天前"
        return instant.atZone(zone).format(dateFmt)
    }

    private fun parse(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null

        try {
            return Instant.parse(iso)
        } catch (ignored: Exception) {
            // 继续尝试更宽松的形态
        }
        try {
            // 带偏移量但不带 Z，例如 2025-03-08T06:30:00+08:00
            return java.time.OffsetDateTime.parse(iso).toInstant()
        } catch (ignored: Exception) {
            // 继续
        }
        return try {
            // 完全没有时区信息：按设备时区理解。这是最后一档兜底，
            // 比丢时间好 —— 但站点实测给的是带 Z 的 UTC，正常不会走到这里。
            java.time.LocalDateTime.parse(iso).atZone(zone).toInstant()
        } catch (ignored: Exception) {
            null
        }
    }
}
