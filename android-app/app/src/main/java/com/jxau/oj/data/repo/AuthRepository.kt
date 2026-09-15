package com.jxau.oj.data.repo

import com.jxau.oj.data.dto.LoginOptionsDto
import com.jxau.oj.data.dto.UserDto
import com.jxau.oj.data.mapper.Mappers
import com.jxau.oj.data.model.User
import com.jxau.oj.data.net.HydroClient
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.net.map
import com.jxau.oj.data.net.mapJson

class AuthRepository(private val client: HydroClient) {

    /**
     * 判断登录态的标准方式。
     * 实测（2026-09-13）：游客 `_id:0, role:"guest"`；已登录 `_id>0` 且 role 非 guest。
     * **无论登录与否都没有 `authn` 字段** —— Hydro 的 authn 是 WebAuthn 属性，与会话无关。
     */
    suspend fun currentUser(): HydroResult<User> =
        client.get("/api/user")
            .mapJson { UserDto.from(it) }
            .map { Mappers.toUser(it) }

    /** 探测站点启用了哪些登录方式（`builtInLogin` 等）。 */
    suspend fun loginOptions(): HydroResult<LoginOptionsDto> =
        client.get("/login").mapJson { LoginOptionsDto.from(it) }

    /**
     * 内置表单登录。
     *
     * 契约（2026-09-13 实测 + Hydro 源码 `UserLoginHandler.post` 交叉确认）：
     * - 请求：`application/x-www-form-urlencoded`，字段 `uname` / `password`（必填，
     *   `rememberme` / `redirect` 等其余字段均可选）。
     * - 用户不存在 → `UserNotFoundError`（404 信封）；密码错误 → `VerifyPasswordError`。
     * - 成功 → HTTP 200 + `{"url":"<首页或回跳地址>"}`，同时 `Set-Cookie` 新会话
     *   （服务端会重建 session，App 侧由 CookieJar 自然接住）。
     * - `rememberme=true` 让服务端下发持久化会话 Cookie，App 场景应当带上。
     *
     * 注意：调用方不应把本函数的失败当作"登录一定没成功" —— 若响应体结构与
     * 预期不符，这里会返回 TransportError，但会话 Cookie 可能已经写入。
     * 因此 [com.jxau.oj.ui.AppViewModel] 在登录后统一用 `currentUser()` 回查。
     */
    suspend fun login(uname: String, password: String): HydroResult<User> =
        client.postForm(
            "/login",
            mapOf(
                "uname" to uname,
                "password" to password,
                "rememberme" to "true",
                "redirect" to "",
            ),
        )
            .mapJson { UserDto.from(it) }
            .map { Mappers.toUser(it) }

    suspend fun logout(): HydroResult<Unit> =
        client.postForm("/logout", emptyMap()).map { }
}
