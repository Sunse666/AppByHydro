package com.jxau.oj.data.net

import android.util.Log
import com.jxau.oj.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 本站唯一的网络出入口。
 *
 * 三条纪律（都来自方案里的实测发现）：
 * 1. 恒发 `Accept: application/json` —— 响应格式由它协商，带了它连 404 都是 JSON，
 *    客户端因此永远不需要处理 HTML 错误页。
 * 2. 先看 body 结构，再看状态码 —— 见 [dispatch]。
 * 3. 不自动重试 —— 提交类请求重试会造成重复提交。
 */
class HydroClient(
    baseUrl: String,
    cookieJar: CookieJar,
    private val userAgent: String,
) {

    private val base = baseUrl.trimEnd('/')

    private val mediaForm = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): HydroResult<String> {
        val url = buildUrl(path, query) ?: return HydroResult.TransportError("非法路径：$path")
        return exec(newRequest(url).get().build())
    }

    suspend fun postForm(path: String, form: Map<String, String>): HydroResult<String> =
        postForm(path, form.map { it.key to it.value })

    /**
     * 允许同名键重复出现的表单（`input[]=a&input[]=b` 这种数组形态）。
     * 键名由调用方原样给出，本方法只负责 URL 编码。
     */
    suspend fun postForm(path: String, pairs: List<Pair<String, String>>): HydroResult<String> {
        val url = buildUrl(path) ?: return HydroResult.TransportError("非法路径：$path")
        val body = pairs.joinToString("&") {
            "${encode(it.first)}=${encode(it.second)}"
        }.toRequestBody(mediaForm)
        return exec(newRequest(url).post(body).build())
    }

    suspend fun postJson(path: String, jsonBody: String): HydroResult<String> {
        val url = buildUrl(path) ?: return HydroResult.TransportError("非法路径：$path")
        return exec(newRequest(url).post(jsonBody.toRequestBody(mediaJson)).build())
    }

    private fun buildUrl(path: String, query: Map<String, String?> = emptyMap()): HttpUrl? {
        val full = if (path.startsWith("http")) path else base + path
        val parsed = full.toHttpUrlOrNull() ?: return null
        if (query.isEmpty()) return parsed
        val builder = parsed.newBuilder()
        query.forEach { (k, v) -> if (v != null) builder.addQueryParameter(k, v) }
        return builder.build()
    }

    private fun newRequest(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", userAgent)

    private suspend fun exec(request: Request): HydroResult<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (BuildConfig.DEBUG) {
                    // 诊断日志：只记录响应，绝不记录请求体（请求体里可能带密码）。
                    // 响应体可能明文带 mail —— 按项目红线打码后再进日志。
                    Log.d(
                        "JXAU_NET",
                        "${request.method} ${request.url.encodedPath} -> ${response.code} " +
                            "ct=${response.header("Content-Type")} body=" +
                            body.take(300).replace(Regex("(\"mail\"\\s*:\\s*\")[^\"]*"), "$1<masked>"),
                    )
                }
                dispatch(response.code, response.header("Content-Type"), body)
            }
        } catch (e: IOException) {
            HydroResult.TransportError("网络不可达：${e.message ?: e.javaClass.simpleName}", e)
        } catch (e: Exception) {
            HydroResult.TransportError("请求失败：${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    /**
     * 四形态分发，实现在 [ResponseDispatch]（抽出去是为了能进离线自检 ——
     * 这是全 App 最该被钉死的一段判定，见该文件的顺序说明）。
     */
    private fun dispatch(status: Int, contentType: String?, body: String): HydroResult<String> =
        ResponseDispatch.of(status, contentType, body)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
