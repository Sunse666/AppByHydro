package com.jxau.oj

/**
 * `BuildConfig` 的最小替身（仅供 `tools/pure_helpers_check` 离线编译用）。
 *
 * 真身由 AGP 生成，离线用 `K2JVMCompiler` 直编时不存在。这里只需补上被测代码
 * 真正引用到的字段 —— `Mappers.avatarUrl` 的默认 `siteBase` 参数。
 * 值与 `android-app/app/build.gradle.kts` 的 `buildConfigField("String", "SITE_BASE_URL", …)`
 * 保持一致；改站点地址时两处都要改。
 */
object BuildConfig {
    const val SITE_BASE_URL: String = "https://oj.wbhtqlorz.cv"
}
