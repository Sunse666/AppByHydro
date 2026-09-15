// 顶层构建脚本：只声明插件，不 apply。
//
// 注意：**没有 kotlin-serialization 插件** —— 离线环境拿不到它的编译器插件产物，
// JSON 解析改用 kotlinx.serialization 的 JsonElement 运行时 API 手动映射，
// 见 app/src/main/java/com/jxau/oj/data/dto/JsonSupport.kt。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
