package com.jxau.oj.data.net

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 会话与本地偏好的落盘。
 *
 * 用 SharedPreferences 而不是 DataStore：本机离线环境下 DataStore 依赖不可得，
 * 而这里要存的东西很少（一条会话 Cookie 集合 + 一个主题 id + 一个深色模式偏好），
 * 引入一个协程流式的存储层并不划算。
 *
 * 该文件已在 `backup_rules.xml` / `data_extraction_rules.xml` 中排除备份，
 * 避免会话凭据随云备份流转到其他设备。
 */
class SessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 主题 id，见 ui/theme/ThemeCatalog.kt。null 表示还没选过。 */
    var themeId: String?
        get() = prefs.getString(KEY_THEME_ID, null)
        set(value) {
            prefs.edit().putString(KEY_THEME_ID, value).apply()
        }

    /** 深色模式偏好：0 跟随系统 / 1 浅色 / 2 深色。 */
    var darkMode: Int
        get() = prefs.getInt(KEY_DARK_MODE, DARK_MODE_SYSTEM)
        set(value) {
            prefs.edit().putInt(KEY_DARK_MODE, value).apply()
        }

    /** 默认代码语言，登录后从服务端 `codeLang` 同步。 */
    var defaultLang: String?
        get() = prefs.getString(KEY_DEFAULT_LANG, null)
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_LANG, value).apply()
        }

    /**
     * 代码字体 id，见 `ui/theme/EditorFonts.kt`。null 表示还没选过。
     * **id 一旦发布不可改** —— 与主题 id 同理，改名会让用户的选择丢失。
     */
    var editorFontId: String?
        get() = prefs.getString(KEY_EDITOR_FONT_ID, null)
        set(value) {
            prefs.edit().putString(KEY_EDITOR_FONT_ID, value).apply()
        }

    /** 编辑器字号（sp）。0 = 还没设置过，由 EditorFonts 决定默认值。 */
    var editorFontSizeSp: Int
        get() = prefs.getInt(KEY_EDITOR_FONT_SIZE, 0)
        set(value) {
            prefs.edit().putInt(KEY_EDITOR_FONT_SIZE, value).apply()
        }

    internal fun readCookieLines(): List<String> =
        prefs.getStringSet(KEY_COOKIES, emptySet())?.toList().orEmpty()

    internal fun writeCookieLines(lines: List<String>) {
        prefs.edit().putStringSet(KEY_COOKIES, lines.toSet()).apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    /**
     * 每道题一份独立草稿。
     *
     * 手机做题最怕切出去接个电话回来代码没了，所以草稿是**本地优先**的：
     * 编辑时按防抖写入，不依赖任何网络请求。
     */
    fun draft(docId: Int): String? = prefs.getString(KEY_DRAFT_PREFIX + docId, null)

    fun saveDraft(docId: Int, code: String) {
        prefs.edit().putString(KEY_DRAFT_PREFIX + docId, code).apply()
    }

    companion object {
        private const val PREFS_NAME = "jxau_oj_session"
        private const val KEY_COOKIES = "cookies"
        private const val KEY_THEME_ID = "theme_id"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_DEFAULT_LANG = "default_lang"
        private const val KEY_EDITOR_FONT_ID = "editor_font_id"
        private const val KEY_EDITOR_FONT_SIZE = "editor_font_size_sp"
        private const val KEY_DRAFT_PREFIX = "draft_"

        const val DARK_MODE_SYSTEM = 0
        const val DARK_MODE_LIGHT = 1
        const val DARK_MODE_DARK = 2
    }
}

/**
 * 持久化 CookieJar。
 *
 * 站点用 Cookie 维持登录态，所以进程被杀后必须能恢复；
 * 但站点 Cookie 名未经 M0 实测确认，因此这里做通用实现（按 name+domain+path 去重），
 * 不硬编码任何具体 Cookie 名。
 *
 * 刻意不处理 SameSite：它只影响浏览器侧的跨站请求策略，
 * 我们这个 OkHttp 客户端不涉及浏览器语义，手工存取反而更可靠。
 */
class PersistentCookieJar(private val store: SessionStore) : CookieJar {

    private val cookies = CopyOnWriteArrayList<Cookie>()

    init {
        store.readCookieLines().mapNotNullTo(cookies, ::decode)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        this.cookies.removeAll { old ->
            cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path }
        }
        this.cookies.addAll(cookies)
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return cookies.filter {
            it.matches(url) && (it.expiresAt == Long.MIN_VALUE || it.expiresAt > now)
        }
    }

    fun clear() {
        cookies.clear()
        store.clearSession()
    }

    /**
     * 诊断用快照：只输出名字与属性，**绝不输出 Cookie 值**（会话凭据不进日志）。
     */
    fun debugSnapshot(): String =
        cookies.joinToString(", ") { c ->
            "${c.name}(domain=${c.domain}, path=${c.path}, hostOnly=${c.hostOnly}, " +
                "httpOnly=${c.httpOnly}, secure=${c.secure}, persistent=${c.persistent})"
        }

    private fun persist() {
        store.writeCookieLines(cookies.map(::encode))
    }

    private fun encode(c: Cookie): String = listOf(
        c.name,
        c.value,
        c.domain,
        c.path,
        c.expiresAt.toString(),
        if (c.secure) "1" else "0",
        if (c.hostOnly) "1" else "0",
        if (c.httpOnly) "1" else "0",
    ).joinToString(SEP)

    private fun decode(line: String): Cookie? {
        val p = line.split(SEP)
        if (p.size < 8) return null
        return try {
            Cookie.Builder().apply {
                name(p[0])
                value(p[1])
                // 与 encode 的字段顺序严格对应：5=secure 6=hostOnly 7=httpOnly。
                // （此前 hostOnly/httpOnly 两个标志位读写错位，已修正。）
                if (p[6] == "1") hostOnlyDomain(p[2]) else domain(p[2])
                path(p[3])
                expiresAt(p[4].toLongOrNull() ?: 0L)
                if (p[5] == "1") secure()
                if (p[7] == "1") httpOnly()
            }.build()
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val SEP = "\u0001"
    }
}
