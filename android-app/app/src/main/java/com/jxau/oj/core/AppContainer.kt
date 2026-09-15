package com.jxau.oj.core

import android.app.Application
import com.jxau.oj.BuildConfig
import com.jxau.oj.data.net.HydroClient
import com.jxau.oj.data.net.PersistentCookieJar
import com.jxau.oj.data.net.SessionStore
import com.jxau.oj.data.repo.AuthRepository
import com.jxau.oj.data.repo.ContestRepository
import com.jxau.oj.data.repo.ProblemRepository
import com.jxau.oj.data.repo.SubmissionRepository
import com.jxau.oj.data.repo.UserRepository

/**
 * 手写依赖容器。
 *
 * 不引 Hilt / Koin：依赖图只有一层，注解处理器带来的构建复杂度
 * （kapt/KSP、本地依赖缓存）在这个规模下不划算。
 */
class AppContainer(application: Application) {

    val sessionStore = SessionStore(application)

    private val cookieJar = PersistentCookieJar(sessionStore)

    /** 诊断用：把当前会话 Cookie 的非敏感属性打到日志（不含值）。 */
    fun debugCookies(): String = cookieJar.debugSnapshot()

    val client = HydroClient(
        baseUrl = BuildConfig.SITE_BASE_URL,
        cookieJar = cookieJar,
        userAgent = BuildConfig.APP_UA,
    )

    val authRepository = AuthRepository(client)
    val problemRepository = ProblemRepository(client)

    /** 排行榜与用户主页。`/ranking`、`/user/:uid` 的契约均已实测。 */
    val userRepository = UserRepository(client)

    /** 竞赛与作业。`/contest`、`/homework` 的列表与详情契约均已实测。 */
    val contestRepository = ContestRepository(client)

    /**
     * ⚠️ 提交与评测链路的契约**尚未经 M0 验证**（没有测试账号）。
     * 仓库本身可编译可运行，但字段名是照 Hydro 通用约定写的。
     */
    val submissionRepository = SubmissionRepository(client)

    fun clearSession() = cookieJar.clear()
}
