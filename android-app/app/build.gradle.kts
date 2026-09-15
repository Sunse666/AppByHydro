// 注意：这里必须显式 import —— 脚本作用域里的 `java` 是 Gradle 的 java 扩展，
// 写成全限定名 java.util.Properties 会被解析成 `java` 扩展的成员，报 Unresolved reference。
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // 注意：不使用 kotlin-serialization 编译器插件。
    // 它需要 kotlin-serialization-compiler-plugin-embeddable，而该产物在当前
    // 离线环境中不可得。改用 kotlinx.serialization 的 JsonElement 运行时 API 手动映射 —
    // 见 data/dto/JsonSupport.kt，这反而让"缺失字段回落默认值"完全可控。
}

// release 签名。凭据放在工程根的 keystore.properties（与 keystore/*.jks 一同保管）。
// ⚠️ 这两个文件一旦丢失，已经装出去的 App 就再也无法覆盖升级。
// 文件不存在时不报错，只是不配签名 —— 这样别人拿到源码仍能编 debug 包。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseKey = keystorePropsFile.exists() &&
    keystoreProps.getProperty("storeFile").orEmpty().isNotBlank()

android {
    namespace = "com.jxau.oj"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jxau.oj"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-m1"

        // 契约验证脚本与调试包共用同一 UA，便于在站点侧识别客户端流量
        buildConfigField("String", "SITE_BASE_URL", "\"https://oj.wbhtqlorz.cv\"")
        buildConfigField("String", "APP_UA", "\"JXAUOJApp/0.1.0 (Android)\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // 与 release 同一把签名密钥：调试包/正式包可互相覆盖安装，
            // 不必再为换包卸载重装丢数据（release 与默认 debug 密钥不同，
            // install -r 会报 INSTALL_FAILED_UPDATE_INCOMPATIBLE）。
            // 代价：debug 包也带正式签名 —— 本工程无对外分发渠道，可接受。
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            // ⚠️ 故意关掉 R8：这个工程有过"构建成功却装着坏字节码"的事故（见 docs E.9.2），
            // 混淆/裁剪造成的破坏同样是**运行期才暴露**的。要开的话先补 keep 规则
            // （尤其 res/raw/cascadia_mono_notice.txt 只被文案提及、不被代码引用，
            // shrinkResources 会把它删掉），然后做一遍完整真机回归。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // 离线环境拿不到 com.android.tools.lint:lint-gradle（缓存里没有），
        // assembleRelease 会卡在 lintVitalAnalyzeRelease 上直接失败 —— 与代码无关。
        // 关掉的只是"打包时顺带跑的 vital lint"；`:app:lint` 单独执行仍然可用（需联网拉产物）。
        checkReleaseBuilds = false
        abortOnError = false
    }
}

configurations.configureEach {
    // listenablefuture-1.0 是一个只含注解的空 jar（Android 生态里长期被视为冗余），
    // 由 androidx.concurrent:concurrent-futures 间接引入。排除它可以避免
    // 在离线构建时为一个空 jar 卡住 —— Guava 的 ListenableFuture 实现本身并不需要它。
    //
    // ⚠️ 实测教训（2026-09-13，MuMu 模拟器 Android 12）：排除 listenablefuture 后，
    // profileinstaller 的 ProfileVerifier 在启动后台线程引用该类 → java.lang.VerifyError
    // → **启动即崩**。不能靠排除 profileinstaller 修复（排除会让 concurrent-futures
    // 在离线模式下解析失败），改在 AndroidManifest.xml 用 tools:node="remove"
    // 摘掉 ProfileInstallerInitializer —— 初始化器不跑，VerifyError 就不会发生。
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.ui.tooling)
}
