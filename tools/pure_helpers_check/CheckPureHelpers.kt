@file:Suppress("UNCHECKED_CAST")

package checkpure

import com.jxau.oj.data.dto.HydroJson
import com.jxau.oj.data.dto.ContestTdocDto
import com.jxau.oj.data.dto.HomeworkTdocDto
import com.jxau.oj.data.dto.PretestRecordDto
import com.jxau.oj.data.dto.ProblemDto
import com.jxau.oj.data.dto.RecordDto
import com.jxau.oj.data.dto.RecordListDto
import com.jxau.oj.data.dto.ScoreCellDto
import com.jxau.oj.data.dto.ScoreColumnDto
import com.jxau.oj.data.dto.ScoreRowDto
import com.jxau.oj.data.dto.ScoreboardDto
import com.jxau.oj.data.dto.SubmitResultDto
import com.jxau.oj.data.dto.UserDto
import com.jxau.oj.data.dto.arrays
import com.jxau.oj.data.dto.doubleOrNull
import com.jxau.oj.data.dto.int
import com.jxau.oj.data.dto.objects
import com.jxau.oj.data.dto.str
import com.jxau.oj.data.dto.strList
import com.jxau.oj.data.dto.textList
import com.jxau.oj.data.mapper.Mappers
import com.jxau.oj.data.model.ContestPhase
import com.jxau.oj.data.model.Phase
import com.jxau.oj.data.model.User
import com.jxau.oj.data.net.HydroResult
import com.jxau.oj.data.net.ErrorMessages
import com.jxau.oj.data.net.errorText
import com.jxau.oj.data.net.ResponseDispatch
import com.jxau.oj.ui.component.AvatarFallback
import com.jxau.oj.ui.component.MdBlock
import com.jxau.oj.ui.component.inline as renderInline
import com.jxau.oj.ui.component.parseBlocks
import com.jxau.oj.ui.editor.CodeCompleter
import com.jxau.oj.ui.editor.CodeFindReplace
import com.jxau.oj.ui.editor.CodeUndoStack
import com.jxau.oj.ui.editor.HighlightTransformation
import com.jxau.oj.ui.editor.SyntaxColors
import com.jxau.oj.ui.editor.SyntaxHighlighter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import com.jxau.oj.ui.theme.BUNDLED_EDITOR_FONTS
import com.jxau.oj.ui.theme.DEFAULT_EDITOR_FONT_ID
import com.jxau.oj.ui.theme.EDITOR_FONTS
import com.jxau.oj.ui.theme.EDITOR_FONT_SAMPLE
import com.jxau.oj.ui.theme.editorFontById
import com.jxau.oj.ui.theme.normalizeEditorFontSp
import com.jxau.oj.ui.theme.VerdictColors
import com.jxau.oj.ui.util.TimeFormat
import com.jxau.oj.ui.util.roleLabel
import java.time.Instant
import java.util.TimeZone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * 纯函数自检（第二批）：映射层与工具层。
 *
 * 与 `editor_actions_check` 同一套路 —— 离线编成可执行程序直接跑，不用 junit
 * （缓存里 `org.junit` 只有 bom、没有 jar）。
 *
 * 为什么专挑这些函数：它们的共同点是**错了不会崩、只会静默给错结果**。
 * 26 进制标号差一位 → 整列竞赛题目标号全偏；ObjectId 反解判错 → 提交时间整列空白；
 * avatar 前缀漏一种 → 头像静默变占位图；缩进/相对时间边界判错 →
 * 用户看到的是"看着不对劲但说不清哪不对"。
 *
 * 用例里的时间一律用**固定 now**（可注入）而不是 `Instant.now()`，否则跑起来会随时钟漂。
 */
private var passed = 0
private val failures = mutableListOf<String>()

private fun check(name: String, actual: Any?, expected: Any?) {
    if (actual == expected) {
        passed++
        println("  PASS  $name")
    } else {
        failures += "$name\n        期望: $expected\n        实际: $actual"
        println("  FAIL  $name\n        期望: $expected\n        实际: $actual")
    }
}

/** 固定参照时刻：2026-09-14 12:00:00 UTC。 */
private val NOW: Instant = Instant.parse("2026-09-14T12:00:00Z")

private fun lab(name: String, index: Int, want: String) =
    check(name, Mappers.alphabeticLabel(index), want)

private fun ts(name: String, id: String?, want: Long?) =
    check(name, Mappers.objectIdTimestamp(id), want)

private fun av(name: String, raw: String?, want: String?) =
    check(name, Mappers.avatarUrl(raw), want)

/** 显式注入站点根 —— 验证 `url:` 相对路径是按传入的 base 拼的，而不是写死的。 */
private fun avb(name: String, raw: String?, base: String, want: String?) =
    check(name, Mappers.avatarUrl(raw, base), want)

private fun st(name: String, raw: String?, want: String) =
    check(name, Mappers.statement(raw), want)

private fun rel(name: String, iso: String?, want: String?) =
    check(name, TimeFormat.relative(iso, NOW), want)

private fun phase(name: String, begin: String?, end: String?, want: ContestPhase) =
    check(name, Phase.of(begin, end, NOW), want)

private fun dur(name: String, begin: String?, end: String?, want: String?) =
    check(name, Phase.durationLabel(begin, end), want)

private fun norm(name: String, sp: Int, want: Int) =
    check(name, normalizeEditorFontSp(sp), want)

/** 把候选列表压成 `名字:类型` 串，便于表格化比对。 */
private fun cands(cs: List<CodeCompleter.Candidate>): String =
    if (cs.isEmpty()) "（空）" else cs.joinToString(" ") { "${it.name}:${it.kind}" }

private fun cp(
    name: String,
    completer: CodeCompleter,
    code: String,
    cursor: Int,
    want: String,
    limit: Int = CodeCompleter.MAX_CANDIDATES,
) = check(name, cands(completer.complete(code, cursor, limit)), want)

private fun json(raw: String): JsonObject = HydroJson.parseToJsonElement(raw).jsonObject

/** 把四形态压成一行，便于表格化比对。 */
private fun hr(r: HydroResult<String>): String = when (r) {
    is HydroResult.Success -> "Success(${r.data})"
    is HydroResult.NeedLogin -> "NeedLogin(${r.redirectUrl})"
    is HydroResult.Failure -> "Failure(code=${r.code},msg=${r.message},name=${r.name},params=${r.params})"
    is HydroResult.TransportError -> "TransportError(${r.message})"
}

private fun dr(name: String, status: Int, ct: String?, body: String, want: String) =
    check(name, hr(ResponseDispatch.of(status, ct, body)), want)

/** 形态四的消息里带「未知类型」等分支字样，用前缀断言，避免把措辞变化当成失败。 */
private fun drTransport(name: String, status: Int, ct: String?, body: String, prefix: String) {
    val r = ResponseDispatch.of(status, ct, body)
    check(name, r is HydroResult.TransportError && r.message.startsWith(prefix), true)
}

// ---- T 组（语法高亮器）的辅助 ----

/** 五个颜色刻意取互不相同的值，否则「染色正确」这类断言会因为撞色而失去判别力。 */
private val SH_COLORS = SyntaxColors(
    keyword = Color(0xFF111111),
    builtin = Color(0xFF222222),
    string = Color(0xFF333333),
    number = Color(0xFF444444),
    comment = Color(0xFF555555),
)

private fun sh(key: String = "cc.cc17") = SyntaxHighlighter.forLanguage(key, SH_COLORS)

/** 取覆盖下标 `index` 的最后一个样式（`null` = 该位置没有任何样式）。 */
private fun styleAt(a: AnnotatedString, index: Int): SpanStyle? =
    a.spanStyles.lastOrNull { index >= it.start && index < it.end }?.item

/** 取下标处的颜色；没有样式时给 `Unspecified`，与「有样式但没指定颜色」区分得开。 */
private fun colorAt(code: String, index: Int, key: String = "cc.cc17"): Color =
    styleAt(sh(key).highlight(code), index)?.color ?: Color.Unspecified

private fun weightAt(code: String, index: Int, key: String = "cc.cc17"): FontWeight =
    styleAt(sh(key).highlight(code), index)?.fontWeight ?: FontWeight.Normal

/** 两串第一个不同字符的下标（完全相同则返回较短者的长度）。 */
private fun firstDiff(x: String, y: String): Int {
    val n = minOf(x.length, y.length)
    var i = 0
    while (i < n && x[i] == y[i]) i++
    return i
}

/**
 * 硬不变量①：高亮后的文本必须与原文逐字符一致。
 * 失败时打印首个差异位置的上下文 —— 只说「不等」没法定位是哪条分支多吐了字符。
 */
private fun shText(name: String, code: String, key: String = "cc.cc17") {
    val got = sh(key).highlight(code).text
    if (got == code) {
        passed++
        println("  PASS  $name")
        return
    }
    val at = firstDiff(got, code)
    val lo = maxOf(0, at - 10)
    val msg = "$name：高亮改写了文本（原文 ${code.length} 字符 / 高亮后 ${got.length}），首差位置 $at\n" +
        "        原文: ${code.substring(lo, minOf(code.length, at + 10))}\n" +
        "        高亮: ${got.substring(lo, minOf(got.length, at + 10))}"
    failures += msg
    println("  FAIL  $msg")
}

/** 硬不变量②：所有样式区间必须落在文本内且 `start <= end`（越界会在排版阶段才炸）。 */
private fun shSpans(name: String, code: String, key: String = "cc.cc17") {
    val a = sh(key).highlight(code)
    val bad = a.spanStyles.firstOrNull { it.start < 0 || it.start > it.end || it.end > a.length }
    if (bad == null) {
        passed++
        println("  PASS  $name")
    } else {
        val msg = "$name：样式区间越界 [${bad.start}, ${bad.end})，文本长度 ${a.length}"
        failures += msg
        println("  FAIL  $msg")
    }
}

/** 硬不变量③：`OffsetMapping` 在 `[0, length]` 上每一点都恒等（不是抽样）。 */
private fun identityOn(m: OffsetMapping, length: Int): Boolean =
    (0..length).all { m.originalToTransformed(it) == it && m.transformedToOriginal(it) == it }

// ---- U 组（Markdown 解析器）的辅助 ----

private val MD_CODE_BG = Color(0xFFEEEEEE)
private val MD_CODE_FG = Color(0xFF111111)
private val MD_LINK = Color(0xFF3366CC)

private fun mdi(text: String): AnnotatedString = renderInline(text, MD_CODE_BG, MD_CODE_FG, MD_LINK)

/** 行内解析后的**纯文本**（标记字符会按设计被丢掉，见 MarkdownParser 文件头）。 */
private fun mdt(text: String): String = mdi(text).text

private fun mdStyle(text: String, index: Int): SpanStyle? = styleAt(mdi(text), index)

/** 换行在单行断言里不可见，压成字面 `\n` 便于比对。 */
private fun esc(s: String): String = s.replace("\n", "\\n")

/** 把块列表压成一行规范形式，便于表格化比对。 */
private fun blk(src: String): String {
    val blocks = parseBlocks(src)
    if (blocks.isEmpty()) return "（空）"
    return blocks.joinToString(" | ") { b ->
        when (b) {
            is MdBlock.Heading -> "H${b.level}(${esc(b.text)})"
            is MdBlock.Paragraph -> "P(${esc(b.text)})"
            is MdBlock.CodeBlock -> "CODE[${b.lang}](${esc(b.code)})"
            is MdBlock.ListItem -> "LI(ord=${b.ordered},idx=${b.index},depth=${b.depth},${esc(b.text)})"
            is MdBlock.Quote -> "Q(${esc(b.text)})"
            MdBlock.Divider -> "DIV"
        }
    }
}

/** `sub` 是否是 `of` 的子序列（保持顺序、允许丢弃）—— Markdown 只许丢标记，不许改序。 */
private fun isSubsequence(sub: String, of: String): Boolean {
    var i = 0
    for (c in of) if (i < sub.length && sub[i] == c) i++
    return i == sub.length
}

fun main() {
    // ⚠️ TimeFormat 按系统时区展示，>30 天会退化成日期 —— 不固定时区，结果随跑测试的机器变。
    // 项目约定时区 Asia/Shanghai，这里显式固定，保证可重现。
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
    println("参照时刻 NOW = $NOW ｜ 时区 = ${TimeZone.getDefault().id}")

    // ---------- A. 竞赛题目标号（双射 26 进制） ----------
    println("\n[A] Mappers.alphabeticLabel")
    lab("下标 0", 0, "A")
    lab("下标 1", 1, "B")
    lab("下标 25（Z）", 25, "Z")
    lab("下标 26 → 进位成两位", 26, "AA")
    lab("下标 27", 27, "AB")
    lab("下标 51（AZ）", 51, "AZ")
    lab("下标 52（BA）", 52, "BA")
    lab("下标 701（ZZ）", 701, "ZZ")
    lab("下标 702 → 三位", 702, "AAA")
    lab("下标 1000", 1000, "ALM")
    lab("下标 1001", 1001, "ALN")
    lab("下标 17576", 17576, "YZA")
    run {
        val thrown = try {
            Mappers.alphabeticLabel(-1)
            "没有抛异常"
        } catch (e: IllegalArgumentException) {
            "IllegalArgumentException"
        }
        check("负下标是契约违反（抛 IllegalArgumentException）", thrown, "IllegalArgumentException")
    }

    // ---------- B. ObjectId → 时间 ----------
    println("\n[B] Mappers.objectIdTimestamp")
    ts("null", null, null)
    ts("空串", "", null)
    ts("太短（7 位）", "5f00000", null)
    ts("刚好 8 位", "5f000000", 1593835520000L)
    ts("标准 24 位 ObjectId", "5f0000000000000000000000", 1593835520000L)
    ts("大写十六进制也认", "5F0000000000000000000000", 1593835520000L)
    ts("下界（2001-09-09）", "3b9aca00", 1000000000000L)
    ts("下界再减 1 → 拒绝", "3b9ac9ff", null)
    ts("全 0（早于下界）", "000000000000000000000000", null)
    ts("全 f（超出 2100 年）", "ffffffffffffffffffffffff", null)
    ts("前 8 位非十六进制", "zzzzzzzzzzzzzzzzzzzzzzzz", null)
    ts("只校验前 8 位（后 16 位不参与）", "5f000000zzzzzzzzzzzzzzzz", 1593835520000L)

    // ---------- C. avatar 前缀 → 可加载 URL ----------
    println("\n[C] Mappers.avatarUrl")
    av("null", null, null)
    av("空串", "", null)
    av("qq 前缀", "qq:3475086817", "https://q1.qlogo.cn/g?b=qq&nk=3475086817&s=100")
    av("前缀大小写不敏感", "QQ:123", "https://q1.qlogo.cn/g?b=qq&nk=123&s=100")
    val gravatar = "https://s.gravatar.com/avatar/93dccfd2059789405993939fbe82175e?d=identicon&s=100"
    av("gravatar（邮箱转小写后取 md5）", "gravatar:Hydro@hydro.local", gravatar)
    av("gravatar 已是小写 → 同一个 URL", "gravatar:hydro@hydro.local", gravatar)
    av("github 前缀", "github:foo", "https://github.com/foo.png")
    // ---- url 前缀：2026-09-14 实测形态，值是**站内相对路径**而非完整 URL ----
    // 实测来源：`GET /ranking` 的第 15 位用户、`GET /user/9` 均为 `url:/file/9/.avatar.jpg`
    av("url 前缀 + 站内相对路径（实测形态）", "url:/file/9/.avatar.jpg", "https://oj.wbhtqlorz.cv/file/9/.avatar.jpg")
    av("url 前缀不带前导斜杠也拼成绝对地址", "url:file/9/.avatar.jpg", "https://oj.wbhtqlorz.cv/file/9/.avatar.jpg")
    av("url 前缀指向绝对地址 → 原样放行", "url:https://cdn.example.com/a.png", "https://cdn.example.com/a.png")
    av("url 前缀大小写不敏感", "URL:/file/9/.avatar.jpg", "https://oj.wbhtqlorz.cv/file/9/.avatar.jpg")
    avb("站点根可注入，不写死", "url:/a.png", "https://example.com/", "https://example.com/a.png")
    avb("站点根末尾多余的斜杠不会拼出双斜杠", "url:/a.png", "https://example.com///", "https://example.com/a.png")
    av("已经是 https 完整 URL → 原样放行", "https://q1.qlogo.cn/g?b=qq&nk=1&s=100", "https://q1.qlogo.cn/g?b=qq&nk=1&s=100")
    av("http 完整 URL 也放行", "http://cdn.example.com/a.png", "http://cdn.example.com/a.png")
    av("未知前缀 → null（不猜）", "weibo:1", null)
    av("有冒号但前缀为空 → null", ":123", null)
    av("有冒号但值为空 → null", "qq:", null)
    av("没有冒号 → null", "noColon", null)

    // ---------- D. statement（嵌套 JSON 题干） ----------
    println("\n[D] Mappers.statement")
    st("null", null, "")
    st("空串", "", "")
    st("只有空白", "   ", "")
    st("取 zh", """{"zh":"你好","en":"hi"}""", "你好")
    st("无 zh → 第一个非空", """{"en":"hi"}""", "hi")
    st("zh 为空串 → 回落", """{"zh":"","en":"hi"}""", "hi")
    st("zh 为空白 → 回落", """{"zh":"  ","en":"hi"}""", "hi")
    st("非 JSON → 原样返回（宁可丑不可白屏）", "just text", "just text")
    st("JSON 数组（不是对象）→ 原样返回", "[1,2]", "[1,2]")
    st("空对象 → 原样返回", "{}", "{}")
    st("数字值也会被取成文本", """{"zh":123}""", "123")

    // ---------- E. 相对时间 ----------
    println("\n[E] TimeFormat.relative（固定 NOW）")
    rel("59 秒 → 刚刚", "2026-09-14T11:59:01Z", "刚刚")
    rel("整 60 秒 → 1 分钟前", "2026-09-14T11:59:00Z", "1 分钟前")
    rel("59 分钟", "2026-09-14T11:01:00Z", "59 分钟前")
    rel("整 1 小时", "2026-09-14T11:00:00Z", "1 小时前")
    rel("23 小时 59 分", "2026-09-13T12:00:01Z", "23 小时前")
    rel("整 1 天", "2026-09-13T12:00:00Z", "1 天前")
    rel("整 30 天（仍是「天」）", "2026-08-15T12:00:00Z", "30 天前")
    rel("31 天 → 退化成日期", "2026-08-14T12:00:00Z", "2026-08-14")
    rel("未来时间按「刚刚」（不倒计时）", "2026-09-14T12:05:00Z", "刚刚")
    rel("带偏移量且不带 Z 的形态", "2026-09-14T11:30:00+08:00", "8 小时前")
    rel("解析失败 → null（不抛）", "not-a-time", null)
    rel("null → null", null, null)
    check(
        "毫秒入口与 ISO 入口同结果",
        TimeFormat.relativeFromMillis(Instant.parse("2026-09-13T12:00:00Z").toEpochMilli(), NOW),
        "1 天前",
    )

    // ---------- F. 竞赛/作业阶段 ----------
    println("\n[F] Phase.of（固定 NOW）")
    phase("还没开始", "2026-09-14T13:00:00Z", "2026-09-14T14:00:00Z", ContestPhase.UPCOMING)
    phase("进行中", "2026-09-14T11:00:00Z", "2026-09-14T13:00:00Z", ContestPhase.RUNNING)
    phase("已结束", "2026-09-14T09:00:00Z", "2026-09-14T11:00:00Z", ContestPhase.ENDED)
    phase("恰好等于开始时刻 → 进行中", "2026-09-14T12:00:00Z", "2026-09-14T13:00:00Z", ContestPhase.RUNNING)
    phase("恰好等于结束时刻 → 仍进行中（isAfter 为假）", "2026-09-14T11:00:00Z", "2026-09-14T12:00:00Z", ContestPhase.RUNNING)
    phase("缺结束但有开始且已过 → 进行中", "2026-09-14T11:00:00Z", null, ContestPhase.RUNNING)
    phase("缺开始 → 保守判已结束（不假装进行中）", null, "2026-09-14T13:00:00Z", ContestPhase.ENDED)
    phase("起止都缺 → 已结束", null, null, ContestPhase.ENDED)
    phase("时间串不合法 → 已结束", "xx", "yy", ContestPhase.ENDED)

    // ---------- F2. 个人截止（方案 C，对齐 isOngoing） ----------
    println("\n[F2] Phase.personalEndAtIso + of(personalEndAtIso)")
    // NOW = 2026-09-14T12:00:00Z（见上方 NOW 常量定义处约定）
    // ① 合成：tsdoc.endAt 优先；无 endAt 用 startAt+duration（小时）；两者都有取更早
    check(
        "F2 个人截止：有 tsdoc.endAt 直接用",
        Phase.personalEndAtIso("2026-09-14T10:00:00Z", "2026-09-14T12:30:00Z", 5),
        "2026-09-14T12:30:00Z",
    )
    check(
        "F2 个人截止：无 endAt 用 startAt+duration(小时)",
        Phase.personalEndAtIso("2026-09-14T10:00:00Z", null, 2),
        "2026-09-14T12:00:00Z",
    )
    check(
        "F2 个人截止：两者都有限更早（endAt 早于 startAt+duration）",
        Phase.personalEndAtIso("2026-09-14T10:00:00Z", "2026-09-14T11:30:00Z", 5),
        "2026-09-14T11:30:00Z",
    )
    check(
        "F2 个人截止：duration=0 只看 endAt（作业无 duration 字段）",
        Phase.personalEndAtIso("2026-09-14T10:00:00Z", "2026-09-14T11:45:00Z", 0),
        "2026-09-14T11:45:00Z",
    )
    check("F2 个人截止：tsdoc 缺失 → null", Phase.personalEndAtIso(null, null, 3), null)
    check("F2 个人截止：startAt 解析失败且无 endAt → null", Phase.personalEndAtIso("xx", null, 3), null)
    // ② 阶段判定：个人截止先于全局窗口生效
    // 全局窗口 [11:00, 14:00)，NOW=12:00 → 全局视角进行中
    check(
        "F2 徽标：个人截止已过 → ENDED（全局还在窗口内）",
        Phase.of("2026-09-14T11:00:00Z", "2026-09-14T14:00:00Z", NOW, "2026-09-14T11:30:00Z"),
        ContestPhase.ENDED,
    )
    check(
        "F2 徽标：个人截止未到 → 仍 RUNNING",
        Phase.of("2026-09-14T11:00:00Z", "2026-09-14T14:00:00Z", NOW, "2026-09-14T12:30:00Z"),
        ContestPhase.RUNNING,
    )
    check(
        "F2 徽标：有效终点取全局与个人更早（个人早 → 按 ENDED）",
        Phase.of("2026-09-14T11:00:00Z", "2026-09-14T14:00:00Z", NOW, "2026-09-14T11:00:00Z"),
        ContestPhase.ENDED,
    )
    check(
        "F2 徽标：无个人截止 → 行为与旧版一致",
        Phase.of("2026-09-14T11:00:00Z", "2026-09-14T14:00:00Z", NOW, null),
        ContestPhase.RUNNING,
    )

    // ---------- G. 时长文案 ----------
    println("\n[G] Phase.durationLabel")
    dur("30 分钟", "2026-09-14T10:00:00Z", "2026-09-14T10:30:00Z", "30 分钟")
    dur("整 1 小时", "2026-09-14T10:00:00Z", "2026-09-14T11:00:00Z", "1 小时")
    dur("1 小时 1 分", "2026-09-14T10:00:00Z", "2026-09-14T11:01:00Z", "1 小时 1 分钟")
    dur("1 小时 40 分", "2026-09-14T10:00:00Z", "2026-09-14T11:40:00Z", "1 小时 40 分钟")
    dur("整 1 天", "2026-09-14T10:00:00Z", "2026-09-15T10:00:00Z", "1 天")
    dur("1 天 1 小时", "2026-09-14T10:00:00Z", "2026-09-15T11:00:00Z", "1 天 1 小时")
    dur("超过 1 天时不再给分钟（粒度取舍）", "2026-09-14T00:00:00Z", "2026-09-15T00:30:00Z", "1 天")
    dur("零长度 → null", "2026-09-14T10:00:00Z", "2026-09-14T10:00:00Z", null)
    dur("起点晚于终点 → null", "2026-09-14T11:00:00Z", "2026-09-14T10:00:00Z", null)
    dur("起止缺一 → null", null, "2026-09-14T10:00:00Z", null)
    dur("不合法 → null", "xx", "yy", null)

    // ---------- H. 角色名 ----------
    println("\n[H] roleLabel")
    check("root", roleLabel("root"), "超级管理员")
    check("admin", roleLabel("admin"), "管理员")
    check("default", roleLabel("default"), "普通用户")
    check("guest", roleLabel("guest"), "游客")
    check("未知角色原样显示（新角色能一眼看出来）", roleLabel("vip"), "vip")

    // ---------- I. 字号与字体回落 ----------
    println("\n[I] 字号归一与字体回落")
    norm("0（未设置）→ 默认", 0, 13)
    norm("负数 → 默认", -5, 13)
    norm("低于下限 → 10", 5, 10)
    norm("下限本身", 10, 10)
    norm("正常值原样", 15, 15)
    norm("上限本身", 22, 22)
    norm("高于上限 → 22", 99, 22)
    check("字体 id 为 null → 默认", editorFontById(null).id, DEFAULT_EDITOR_FONT_ID)
    check("字体 id 已移除 → 默认", editorFontById("removed-font").id, DEFAULT_EDITOR_FONT_ID)
    check("字体 id 命中", editorFontById("cascadia-mono").id, "cascadia-mono")

    // ---------- J. 补全引擎 ----------
    println("\n[J] CodeCompleter")
    val cc = CodeCompleter(
        keywords = setOf("alpha", "alpine", "alps", "int", "void"),
        builtins = setOf("printf", "aligned"),
    )
    check("wordStart：光标在词中", cc.wordStart("int ma", 6), 4)
    check("wordStart：光标在词首", cc.wordStart("int ma", 4), 4)
    check("wordStart：光标在行首", cc.wordStart("abc", 0), 0)
    check("wordStart：下划线算词内", cc.wordStart("foo_bar", 7), 0)
    check("wordStart：非标识符处断开", cc.wordStart("a+b", 3), 2)

    cp("光标在 0 → 不给候选", cc, "abc", 0, "（空）")
    cp("光标越界 → 不给候选", cc, "abc", 99, "（空）")
    cp("前缀以数字开头 → 不给候选", cc, "12", 2, "（空）")
    cp("光标前是空白（空前缀）→ 不给候选", cc, "int ", 4, "（空）")
    cp("已打完整的词不再作为候选", cc, "alpha", 5, "（空）")
    // 前缀取 "alp"：三个保留字都命中，而内建 "aligned" 不命中 —— 否则测的就不是"字母序"了
    cp("保留字前缀匹配，按字母序", cc, "alp", 3, "alpha:KEYWORD alpine:KEYWORD alps:KEYWORD")
    cp("忽略大小写的次级匹配（Int → int）", cc, "Int", 3, "int:KEYWORD")
    cp("内建排在保留字之后", cc, "p", 1, "printf:BUILTIN")
    cp("两者都命中：保留字先、内建后", cc, "a", 1, "alpha:KEYWORD alpine:KEYWORD alps:KEYWORD aligned:BUILTIN")
    cp("limit 截断", cc, "a", 1, "alpha:KEYWORD alpine:KEYWORD", limit = 2)
    cp("代码标识符按出现次数加权", cc, "zoo zoo zap", 1, "zoo:IDENT zap:IDENT")
    cp("同频次按名字序", cc, "zap zoo", 1, "zap:IDENT zoo:IDENT")
    // 用 "z" 前缀把保留字/内建都排除掉，否则单字符过滤这条根本轮不到被测
    cp("单字符标识符不收", cc, "z zz", 1, "zz:IDENT")
    // 光标要落在**第二个** "alpha" 上（索引 12），这样上文已出现同名标识符才会进候选去重分支
    cp("标识符与保留字同名时不重复出", cc, "alpha x alph", 12, "alpha:KEYWORD")
    run {
        // 内建里混入保留字：该名字已在第 1 步作为保留字进过，不该在第 2 步重复
        val over = CodeCompleter(
            keywords = setOf("int"),
            builtins = setOf("int", "printf"),
        )
        cp("内建与保留字重叠 → 只出一次", over, "in", 2, "int:KEYWORD")
    }

    // ---------- K. JSON 读取容错（含未验证的 compilerTexts 形态） ----------
    println("\n[K] JsonSupport 读取容错")
    check("str：缺失回落默认", json("{}").str("k", "d"), "d")
    check("str：数字被转成文本（类型漂移不丢数据）", json("""{"k":12}""").str("k"), "12")
    check("int：缺失回落默认", json("{}").int("k", 7), 7)
    check("int：非数字串回落默认", json("""{"k":"abc"}""").int("k", 7), 7)
    check("doubleOrNull：正常", json("""{"k":0.835}""").doubleOrNull("k"), 0.835)
    check("doubleOrNull：缺失为 null", json("{}").doubleOrNull("k"), null)
    check(
        "strList：标量转文本，对象/数组/null 跳过",
        json("""{"k":["a",1,true,null,{"x":1},["y"]]}""").strList("k"),
        listOf("a", "1", "true"),
    )
    check("objects：剔除非对象元素", json("""{"k":[{"a":1},"x"]}""").objects("k").size, 1)
    check("arrays：剔除非数组元素", json("""{"k":[[1],[2],"x"]}""").arrays("k").size, 2)
    check("textList：字符串数组", json("""{"k":["x","y"]}""").textList("k"), listOf("x", "y"))
    check("textList：对象取 message", json("""{"k":[{"message":"boom"}]}""").textList("k"), listOf("boom"))
    check("textList：对象回落 text", json("""{"k":[{"text":"note"}]}""").textList("k"), listOf("note"))
    check("textList：空白元素跳过", json("""{"k":["x","  ","y"]}""").textList("k"), listOf("x", "y"))
    check("textList：标量转文本、无 message/text 的对象跳过", json("""{"k":["a",1,{"other":"z"}]}""").textList("k"), listOf("a", "1"))
    check("textList：非数组 → 空", json("""{"k":"x"}""").textList("k"), emptyList<String>())
    check("textList：键缺失 → 空", json("{}").textList("k"), emptyList<String>())

    // ---------- L. 标号组装 ----------
    println("\n[L] Mappers.toLabeledProblems")
    run {
        val list = Mappers.toLabeledProblems(listOf(101, 102, 103), mapOf(101 to "两数读入", 103 to "座位排布"))
        check("按 pids 顺序打标号", list.map { "${it.label}#${it.docId}" }, listOf("A#101", "B#102", "C#103"))
        check("拉不到题目名时 title 为 null（UI 回落占位）", list.map { it.title }, listOf("两数读入", null, "座位排布"))
    }

    // ---------- M. UserDto 解析 + 登录判定 ----------
    // 登录判定历史上真错过一次（曾误用 authn 字段），且错了的表现是"登录成功却显示未登录"，
    // 所以这里把三条"只看一半就会判错"的组合都钉住。
    println("\n[M] UserDto / Mappers.toUser（登录判定）")
    check("null DTO → User.GUEST", Mappers.toUser(null) == User.GUEST, true)
    check("游客 _id:0 + role:guest → 未登录", Mappers.toUser(UserDto.from(json("""{"_id":0,"uname":"Guest","role":"guest"}"""))).isLoggedIn, false)
    run {
        val me = UserDto.from(json("""{"_id":5,"uname":"alice","role":"default","timeZone":"Asia/Shanghai","codeLang":"cc.cc17","avatar":"qq:123"}"""))
        val u = Mappers.toUser(me)
        check("已登录 _id>0 且 role!=guest", u.isLoggedIn, true)
        check("avatar 走同一套归一化", u.avatarUrl, "https://q1.qlogo.cn/g?b=qq&nk=123&s=100")
        check("timeZone 有值则保留", u.timeZone, "Asia/Shanghai")
        check("codeLang 透传", u.defaultCodeLang, "cc.cc17")
    }
    check("⚠️ _id>0 但 role=guest → 仍判未登录", Mappers.toUser(UserDto.from(json("""{"_id":5,"uname":"x","role":"guest"}"""))).isLoggedIn, false)
    check("⚠️ role 非 guest 但 _id=0 → 仍判未登录", Mappers.toUser(UserDto.from(json("""{"_id":0,"uname":"x","role":"default"}"""))).isLoggedIn, false)
    check("⚠️ role 键缺失 → 默认 guest，不得误判成已登录", Mappers.toUser(UserDto.from(json("""{"_id":9,"uname":"x"}"""))).isLoggedIn, false)
    check("uname 空 + 已登录 → 回落「用户」", Mappers.toUser(UserDto.from(json("""{"_id":9,"role":"default"}"""))).uname, "用户")
    check("uname 空 + 游客 → 回落「游客」", Mappers.toUser(UserDto.from(json("""{"_id":0,"role":"guest"}"""))).uname, "游客")
    check("timeZone 是空白串 → null（不把空串带到 UI）", Mappers.toUser(UserDto.from(json("""{"_id":9,"role":"default","timeZone":"  "}"""))).timeZone, null)

    // ---------- N. ProblemDto 解析 + docId 回退 ----------
    println("\n[N] ProblemDto → toSummary / toDetail")
    val pdoc = ProblemDto.from(
        json(
            """{"_id":"abc123","docId":42,"pid":"P1000","title":"座位排布","tag":["一 · 报到那天"],
                "nSubmit":29,"nAccept":14,"content":"{\"zh\":\"**题干**\",\"en\":\"stmt\"}",
                "config":{"timeMax":1000,"memoryMax":256,"langs":["cc.cc17","py.py3"]},
                "data":[{"_id":"f1","name":"1.in"},{"_id":"f2","name":"1.out"}]}""",
        ),
    )
    check("_id 是字符串 / docId 是数字", "${pdoc.id}/${pdoc.docId}", "abc123/42")
    check("summary：pid / title / tags 透传", "${Mappers.toSummary(pdoc).pid}|${Mappers.toSummary(pdoc).title}|${Mappers.toSummary(pdoc).tags.size}", "P1000|座位排布|1")
    check("summary：通过率由 nAccept/nSubmit 算", Mappers.toSummary(pdoc).acceptance, 14.0 / 29.0)
    run {
        val dt = Mappers.toDetail(pdoc, 999)
        check("detail：docId 非 0 → 用自己的，忽略 fallback", dt.docId, 42)
        check("detail：config → 时限/内存/语言数", "${dt.timeLimitMs}/${dt.memoryLimitMb}/${dt.langKeys.size}", "1000/256/2")
        check("detail：data → 测试文件名", dt.testFileNames, listOf("1.in", "1.out"))
        check("detail：content 被二次解析成题干", dt.statement, "**题干**")
    }
    // ⚠️ 这条是「竞赛/作业列表 key 重复 → 渲染即崩」那类事故的守门用例
    val noDocId = ProblemDto.from(json("""{"_id":"xyz","title":"无 docId"}"""))
    check("⚠️ docId 缺失(为 0) → 采用调用方给的 fallbackDocId", Mappers.toDetail(noDocId, 999).docId, 999)
    check("summary：docId 缺失时如实保持 0（由列表层兜底）", Mappers.toSummary(noDocId).docId, 0)
    check("title 空白 → 回落「(无标题)」", Mappers.toSummary(ProblemDto.from(json("""{"_id":"a"}"""))).title, "(无标题)")
    run {
        val bare = Mappers.toDetail(ProblemDto.from(json("""{"_id":"a"}""")), 7)
        check("config 缺失 → 0/0/空语言表", "${bare.timeLimitMs}/${bare.memoryLimitMb}/${bare.langKeys.size}", "0/0/0")
        check("data 缺失 → 空测试文件名", bare.testFileNames, emptyList<String>())
        check("nSubmit=0 → 通过率为 null（UI 显示「—」而非 0%）", bare.acceptance, null)
    }

    // ---------- O. 竞赛 / 作业 tdoc ----------
    println("\n[O] 竞赛与作业映射")
    val tdoc = ContestTdocDto.from(
        json("""{"_id":"9","title":"","beginAt":"2026-09-01T00:00:00Z","endAt":"2026-09-02T00:00:00Z",
                "pids":["101","abc","102",null],"attend":1}"""),
    )
    check("docId 缺失 → 回落 _id 的数字形式", tdoc.docId, 9)
    check("_id 也非数字 → docId 为 0", ContestTdocDto.from(json("""{"_id":"abc"}""")).docId, 0)
    check("pids 里的非数字元素被剔除", Mappers.toContestDetail(tdoc).problemDocIds, listOf(101, 102))
    check("竞赛标题空白 → 回落「未命名竞赛」", Mappers.toContest(tdoc).title, "未命名竞赛")
    check("时长用起止差值（不赌 duration 单位）", Mappers.toContest(tdoc).durationLabel, "1 天")
    run {
        val hw = HomeworkTdocDto.from(json("""{"_id":"8","title":"题单","pids":["101","102"]}"""))
        val hd = Mappers.toHomeworkDetail(hw, true, mapOf(101 to "两数读入", 102 to "加减", 999 to "pdict 独有"))
        check("按 pids 顺序打标号 + pdict 独有项追加尾部", hd.problems.map { "${it.label}#${it.docId}" }, listOf("A#101", "B#102", "C#999"))
        check("claimed 透传 true", hd.claimed, true)
        check("claimed=null（站点没下发 tsdoc）→ 保持 null，不猜", Mappers.toHomeworkDetail(hw, null, emptyMap()).claimed, null)
        check("作业标题空白 → 回落「未命名作业」", Mappers.toHomework(HomeworkTdocDto.from(json("""{"_id":"8"}"""))).title, "未命名作业")
    }

    // ---------- P. 成绩表补格 / 截断 ----------
    println("\n[P] Mappers.toScoreboard")
    run {
        val sb = Mappers.toScoreboard(
            ScoreboardDto(
                columns = listOf(ScoreColumnDto("A", 101), ScoreColumnDto("B", 102), ScoreColumnDto("C", 103)),
                rows = listOf(
                    ScoreRowDto("1", 7, "alice", "300", listOf(ScoreCellDto("100", 100, "r1"))),
                    ScoreRowDto("2", 8, "bob", "0", listOf(ScoreCellDto("1", 1, "x"), ScoreCellDto("2", 2, "y"), ScoreCellDto("3", 3, "z"), ScoreCellDto("4", 4, "w"))),
                ),
            ),
        )
        check("列标签与 docId 透传", sb.columns.map { "${it.label}#${it.docId}" }, listOf("A#101", "B#102", "C#103"))
        check("格子少于列数 → 补「-」且 score/rid 为 null", sb.rows[0].cells.map { "${it.scoreText}:${it.score}:${it.rid}" }, listOf("100:100:r1", "-:null:null", "-:null:null"))
        check("格子多于列数 → 截断到列数", sb.rows[1].cells.size, 3)
        check("截断保留前 N 个、rid 不错位", sb.rows[1].cells.map { it.rid }, listOf("x", "y", "z"))
    }

    // ---------- Q. 提交 / 记录 DTO（全是候选键与兜底） ----------
    println("\n[Q] 提交与记录 DTO")
    check("SubmitResultDto：rid 键", SubmitResultDto.from(json("""{"rid":"r1"}""")).rid, "r1")
    check("SubmitResultDto：_id 兜底", SubmitResultDto.from(json("""{"_id":"r2"}""")).rid, "r2")
    check("SubmitResultDto：id 兜底", SubmitResultDto.from(json("""{"id":"r3"}""")).rid, "r3")
    check("SubmitResultDto：都没有 → 空串（UI 据此提示契约不符）", SubmitResultDto.from(json("""{"foo":1}""")).rid, "")
    run {
        val flat = RecordDto.from(
            json("""{"_id":"r9","status":1,"score":100,"time":835,"memory":2048,"lang":"cc.cc17",
                    "pid":"P1000","title":"座位排布","uid":7,"uname":"alice","code":"int main(){}"}"""),
        )
        check("RecordDto：平铺形态各字段", "${flat.rid}/${flat.statusCode}/${flat.timeMs}/${flat.memoryKb}/${flat.lang}", "r9/1/835/2048/cc.cc17")
        check("RecordDto：code 有值时透传", flat.code, "int main(){}")
        check("RecordDto：code 缺失 → null（详情页据此不渲染代码块）", RecordDto.from(json("""{"_id":"r5"}""")).code, null)
        check("RecordDto：status 缺失 → -1（不是 0，0 是「等待中」）", RecordDto.from(json("""{"_id":"r7"}""")).statusCode, -1)
        check("RecordDto：title 缺失 → 回落 pdocTitle", RecordDto.from(json("""{"_id":"r6","pdocTitle":"备用标题"}""")).title, "备用标题")
        val wrapped = RecordDto.from(json("""{"rdoc":{"_id":"r8","status":2,"udoc":{"_id":9,"uname":"bob"}}}"""))
        check("RecordDto：包在 rdoc 里也能读", "${wrapped.rid}/${wrapped.statusCode}", "r8/2")
        check("RecordDto：提交者从嵌套 udoc 取", "${wrapped.uid}/${wrapped.uname}", "9/bob")
    }
    run {
        val a = RecordListDto.from(json("""{"rdocs":[{"_id":"r1"},{"_id":"r2"}]}"""))
        check("RecordListDto：rdocs 形态", "${a.records.size}/${a.sawKnownArray}", "2/true")
        val b = RecordListDto.from(json("""{"docs":[{"_id":"r1"}]}"""))
        check("RecordListDto：docs 形态", "${b.records.size}/${b.sawKnownArray}", "1/true")
        val c = RecordListDto.from(json("""{"mystery":[]}"""))
        check("RecordListDto：键名不认识 → 空列表 + sawKnownArray=false（UI 说「契约不符」而非「你没交过」）", "${c.records.size}/${c.sawKnownArray}", "0/false")
        check("RecordListDto：总数 rcount 优先", RecordListDto.from(json("""{"rcount":12}""")).totalCount, 12)
        check("RecordListDto：rcount 缺失 → 回落 count", RecordListDto.from(json("""{"count":7}""")).totalCount, 7)
        check("RecordListDto：rcount/count 都缺 → 回落 total", RecordListDto.from(json("""{"total":42}""")).totalCount, 42)
    }
    run {
        val pt = PretestRecordDto.from(
            json("""{"rdoc":{"status":1,"input":["1 2\n"],"testCases":[
                    {"id":2,"status":1,"time":1.5,"memory":1024,"message":"ok2"},
                    {"id":1,"status":1,"time":0.835,"memory":0,"message":"ok1\n"}]}}"""),
        )
        check("PretestRecordDto：testCases 按 id 排序（站点顺序不可靠）", pt.cases.map { it.id }, listOf(1, 2))
        check("PretestRecordDto：message 即该组 stdout", pt.cases.map { it.output }, listOf("ok1\n", "ok2"))
        check("PretestRecordDto：time 是浮点毫秒", pt.cases[0].timeMs, 0.835)
        check("PretestRecordDto：memory=0 → null（区分「没测到」与「0 KB」）", pt.cases[0].memoryKb, null)
        check("PretestRecordDto：input 原样回显", pt.inputs, listOf("1 2\n"))
    }

    // ---------- R. 网络层四形态分发 ----------
    // 这段此前只能靠真机随手点来验证，而它每一处误判都会被上层读成完全不同的用户可见语义
    // （"没交过题" vs "App 没适配"、"登录过期" vs "站点点错了"），所以逐条钉死。
    println("\n[R] ResponseDispatch（四形态分发）")
    dr("形态一：200 + JSON 对象 → Success", 200, "application/json", """{"page":1}""", """Success({"page":1})""")
    dr("形态一：JSON 数组也算成功（不要求对象）", 200, "application/json", "[1,2]", "Success([1,2])")
    dr("body 前有空白仍认得出 JSON，且 Success 保留原始 body", 200, null, "\n  {\"a\":1}", "Success(\n  {\"a\":1})")
    dr("形态一：3xx + JSON 且无 error 信封 → Success（只看 >=400）", 302, null, """{"a":1}""", """Success({"a":1})""")
    dr("形态二：200 + url=/login → NeedLogin（判据是 body，不是状态码）", 200, "application/json", """{"url":"/login?redirect=%2Frecord"}""", "NeedLogin(/login?redirect=%2Frecord)")
    dr("形态二：带 redirect 的实测形态（GET /record 匿名）", 200, null, """{"url":"/login?redirect=%2Frecord%3Fpage%3D1"}""", "NeedLogin(/login?redirect=%2Frecord%3Fpage%3D1)")
    dr("⚠️ 有 url 但不指向 /login → 不算软跳转", 200, null, """{"url":"/contest/1"}""", """Success({"url":"/contest/1"})""")
    dr("⚠️ url 非字符串（数字）→ 不算软跳转", 200, null, """{"url":123}""", """Success({"url":123})""")
    dr("⚠️ 顺序：软跳转判定先于错误信封", 403, null, """{"url":"/login","error":{"code":9,"message":"x"}}""", "NeedLogin(/login)")
    dr("形态三：200 + error 信封 → Failure（200 也可能是错误！）", 200, "application/json", """{"error":{"code":403,"message":"Not allowed.","name":"PermissionError","params":[]}}""", "Failure(code=403,msg=Not allowed.,name=PermissionError,params=[])")
    dr("形态三：404 + 信封 → 用信封里的 code，不用状态码", 404, null, """{"error":{"code":1,"message":"boom"}}""", "Failure(code=1,msg=boom,name=null,params=[])")
    dr("形态三：信封缺 code → 回落 HTTP 状态码", 500, null, """{"error":{"message":"boom"}}""", "Failure(code=500,msg=boom,name=null,params=[])")
    dr("形态三：信封缺 message → 「请求失败」", 400, null, """{"error":{"code":2}}""", "Failure(code=2,msg=请求失败,name=null,params=[])")
    dr("形态三：message 模板用 params 替换（不留 {0} 天书）", 404, null, """{"error":{"code":404,"message":"User {0} not found.","name":"UserNotFoundError","params":["bob"]}}""", "Failure(code=404,msg=User bob not found.,name=UserNotFoundError,params=[bob])")
    dr("形态三：多占位符按序号替换", 400, null, """{"error":{"message":"{0} vs {1}","params":["a","b"]}}""", "Failure(code=400,msg=a vs b,name=null,params=[a, b])")
    dr("形态三：params 为空 → 模板原样（不因替换而丢字）", 400, null, """{"error":{"message":"User {0} not found."}}""", "Failure(code=400,msg=User {0} not found.,name=null,params=[])")
    dr("形态三：数字参数转文本（与 strList 同策略）", 400, null, """{"error":{"message":"x{0}y","params":[7]}}""", "Failure(code=400,msg=x7y,name=null,params=[7])")
    dr("形态三：对象型参数被剔除", 400, null, """{"error":{"message":"m","params":[{"x":1}]}}""", "Failure(code=400,msg=m,name=null,params=[])")
    dr("形态三：404 但无 error 信封 → 兜底业务错误", 404, null, """{"foo":1}""", "Failure(code=404,msg=服务异常（HTTP 404）,name=null,params=[])")
    dr("形态四（反例）：502 是状态码，但 body 是 JSON → 仍按 code 走 Failure 而非 TransportError", 502, "text/html", """{"error":{"code":7,"message":"upstream"}}""", "Failure(code=7,msg=upstream,name=null,params=[])")
    drTransport("形态四：网关 HTML（Cloudflare 1035）→ TransportError", 502, "text/html", "<!DOCTYPE html><html><body>error 1035</body></html>", "响应不是 JSON（HTTP 502")
    drTransport("形态四：Content-Type 缺省 → 报「未知类型」", 503, null, "<html></html>", "响应不是 JSON（HTTP 503，Content-Type: 未知类型）")
    drTransport("形态四：Content-Type 只取分号前的主类型", 500, "text/html; charset=utf-8", "<html>", "响应不是 JSON（HTTP 500，Content-Type: text/html）")
    drTransport("形态四：空 body 不是 JSON", 200, null, "", "响应不是 JSON")
    drTransport("⚠️ 优先级：body 是 HTML 时先报「不是 JSON」，不去猜状态码", 200, "text/html", "<html>x</html>", "响应不是 JSON（HTTP 200")
    drTransport("形态四：坏 JSON → 「JSON 解析失败」（与「不是 JSON」区分）", 200, "application/json", """{"a":""", "JSON 解析失败")
    drTransport("形态四：JSON 数组里混坏内容也归入解析失败", 200, null, """[{"a":""", "JSON 解析失败")
    check("substitute：无 params → 原样", ResponseDispatch.substitute("plain", emptyList()), "plain")
    check("substitute：模板无占位符 → 原样", ResponseDispatch.substitute("plain", listOf("x")), "plain")
    check("substitute：模板里没出现的占位符不影响结果", ResponseDispatch.substitute("{0} ok", listOf("a", "b")), "a ok")

    // ---------- S. 判题状态色（颜色 + 文字双重表达） ----------
    // 方案 10.2 的硬要求：色盲用户无法只靠颜色区分 AC / WA，所以「有颜色」必须同时「有文字」。
    // 这组不抄实现里的色值，而是测**不变量**（两两不同、别名等价、颜色与文字覆盖一致）。
    println("\n[S] VerdictColors（状态 → 颜色 / 文字）")
    run {
        val known = listOf(
            "AC", "ACCEPTED", "WA", "WRONG_ANSWER", "TLE", "TIME_LIMIT_EXCEEDED",
            "MLE", "MEMORY_LIMIT_EXCEEDED", "RE", "RUNTIME_ERROR", "CE", "COMPILE_ERROR",
            "SE", "SYSTEM_ERROR", "PENDING", "WAITING", "JUDGING", "FETCHED", "COMPILING",
        )
        val canonical = listOf("AC", "WA", "TLE", "MLE", "RE", "CE", "SE", "PENDING", "UNKNOWN")
        for (dark in listOf(false, true)) {
            check("深色=$dark：九类状态色两两不同（防复制粘贴串色）", canonical.map { VerdictColors.of(it, dark) }.toSet().size, canonical.size)
        }
        check(
            "浅/深两套调色板每个位置都不同（否则深色模式下看不清）",
            canonical.all { VerdictColors.of(it, false) != VerdictColors.of(it, true) },
            true,
        )
        check("大小写不敏感（站点键名大小写不稳）", VerdictColors.of("ac", false), VerdictColors.of("AC", false))
        check("别名等价：ACCEPTED == AC", VerdictColors.of("ACCEPTED", false), VerdictColors.of("AC", false))
        check("别名等价：WRONG_ANSWER == WA（深色也成立）", VerdictColors.of("WRONG_ANSWER", true), VerdictColors.of("WA", true))
        check("null → unknown 色，不抛", VerdictColors.of(null, false), VerdictColors.of("UNKNOWN", false))
        check("空串与未知键同色", VerdictColors.of("", false), VerdictColors.of("ZZZ", false))
        check(
            "⚠️ 每个被认作已知状态的键都有非「未知」的中文文字（10.2 无障碍硬要求）",
            known.count { VerdictColors.label(it) != "未知" },
            known.size,
        )
        check("「等待评测」与「评测中」必须分成两种说法", VerdictColors.label("PENDING") != VerdictColors.label("JUDGING"), true)
        check("label(null) → 未知", VerdictColors.label(null), "未知")
        check("label 空串 → 未知", VerdictColors.label(""), "未知")
        check("未知键原样回显（便于排查站点新增状态）", VerdictColors.label("NEW_STATUS"), "NEW_STATUS")
        // 有意保留的非对称：IGN 有文字但没有独立颜色，走 unknown 灰（已忽略不算判题结果）
        check(
            "IGN 有文字、颜色走 unknown（有意为之）",
            "${VerdictColors.label("IGN")}/${VerdictColors.of("IGN", false) == VerdictColors.of("ZZZ", false)}",
            "已忽略/true",
        )
    }

    // ---------- T. 语法高亮器：硬不变量「只加样式、绝不改文本长度」 ----------
    // 为什么单列一组：HighlightTransformation 用的是 OffsetMapping.Identity，即「变换后文本与
    // 原文逐字符一一对应」。高亮器一旦多输出/少输出一个字符 —— 最典型的诱因就是"把 Tab 展开成
    // 空格"这种看着更顺眼的处理 —— 光标与选区会整体错位，而且**不抛异常**，真机上极难察觉。
    //
    // 所以这组不测"配色好不好看"，只测三条不变量：
    //   ① 文本逐字符相同；② 样式区间合法不越界；③ 样式确实分出来了（否则一个原样返回的假
    //   高亮器能轻松骗过 ① 和 ②）。
    println("\n[T] SyntaxHighlighter（文本长度不变 / 区间合法 / 分色正确）")
    run {
        // 「难缠片段」：每一条都对应一类容易被顺手改写的字符。
        val nasty = listOf(
            "空串" to "",
            "单空格" to " ",
            "只有一个换行" to "\n",
            "CRLF 行尾（绝不能归一成 LF）" to "int main() {\r\n\treturn 0;\r\n}\r\n",
            "制表符缩进（绝不能展开成空格）" to "int\tmain()\t{\n\t\treturn\t0;\n}",
            "制表符出现在注释与字符串内" to "\t/*\t*/\t\"\t\"\n",
            "块注释未闭合 → 吃到文件尾且不多吃" to "/* never closed\nint x = 1;\n",
            "字符串未闭合 → 只吃到行尾" to "char *s = \"oops;\nint y = 2;\n",
            "字符串内转义引号" to "char c = '\"'; char *s = \"a\\\"b\";",
            "字符串以反斜杠结尾（scanString 里 i+=2 的越界嫌疑）" to "char *s = \"abc\\",
            "中文与表情（代理对：长度≠字符数）" to "// 中文注释 😀🎉\nint 整数 = 1; // 🚀\n",
            "行尾注释后没有换行" to "int a = 1; // 尾巴",
            "行注释符本身就在文件末尾" to "int a = 1; //",
            "井号起头（C 走预处理分支，整行染色）" to "a # b",
            "Python 的井号行注释" to "x = 1  # 注释\n",
            "数字紧贴标识符" to "int a1 = 123; int b = 4;",
            "小数点 / 科学计数 / 下划线数字" to "double d = 1_000.5e-3;",
            "只有一个反斜杠" to "\\",
            "只有一个双引号" to "\"",
            "只有一个块注释开始符" to "/*",
            "块注释里含不完整的结束符" to "/** doc * / still open */ int a;",
            "只有 CR（老 Mac 行尾）" to "int a; // c\rint b;",
            "模板尖括号连写" to "vector<pair<int,int>> v;",
            "预处理指令整行" to "#include <bits/stdc++.h>\n#define MAX 100\nint main(){}\n",
            "三引号原始串" to "val s = \"\"\"\nraw text\n\"\"\"",
        )
        for ((name, code) in nasty) {
            shText("T1 文本逐字符不变：$name", code)
        }

        // 不变量②单列一轮：即便文本没变，样式区间也可能落在文本之外（排版阶段才炸，更难查）。
        for ((name, code) in nasty) {
            shSpans("T2 样式区间合法：$name", code)
        }

        // 不变量③的入口是 HighlightTransformation —— 真机上 BasicTextField 走的就是它，
        // 光测高亮器本身不够：万一这里被换成带补偿的 OffsetMapping，文本仍然是「不变」的。
        val bigCode = buildString {
            append("// 大文件稳健性\n")
            for (i in 1..400) append("int a$i = $i; // 第 $i 行\n")
        }
        val tr = HighlightTransformation(sh())
        val filtered = tr.filter(AnnotatedString(bigCode))
        check("T3 变换后文本与原文逐字符一致（400 行）", filtered.text.text == bigCode, true)
        check("T3 变换后文本长度一致", filtered.text.length, bigCode.length)
        check("T3 样式区间合法（400 行）", filtered.text.spanStyles.none { it.start < 0 || it.start > it.end || it.end > bigCode.length }, true)
        check("T3 OffsetMapping 在原点上恒等", filtered.offsetMapping.originalToTransformed(0), 0)
        check("T3 OffsetMapping 在末界上恒等（越界敏感点）", filtered.offsetMapping.originalToTransformed(bigCode.length), bigCode.length)
        check("T3 OffsetMapping 在 [0, length] 上逐点恒等（不是抽样）", identityOn(filtered.offsetMapping, bigCode.length), true)
        check("T3 包含制表符的样本也恒等", identityOn(HighlightTransformation(sh()).filter(AnnotatedString("int\tmain()\n")).offsetMapping, 11), true)

        // 分色正确性：这几条是「假高亮器」的照妖镜 —— 只断言长度的话，原样返回就能全绿。
        check("T4 关键字 → keyword 色：return", colorAt("return 0;", 0), SH_COLORS.keyword)
        check("T4 内建类型 → builtin 色：int", colorAt("int x;", 0), SH_COLORS.builtin)
        check("T4 数字 → number 色", colorAt("x = 42;", 4), SH_COLORS.number)
        check("T4 字符串 → string 色", colorAt("s = \"hi\";", 4), SH_COLORS.string)
        check("T4 行注释 → comment 色", colorAt("x; // hi", 4), SH_COLORS.comment)
        check("T4 块注释 → comment 色", colorAt("/* c */ x;", 3), SH_COLORS.comment)
        check("T4 预处理指令整行 → keyword 色", colorAt("#include <a>\n", 3), SH_COLORS.keyword)
        check("T4 函数调用点只加粗", weightAt("main();", 0), FontWeight.Medium)
        check("T4 函数调用点不改颜色（避免与五类撞色）", colorAt("main();", 0), Color.Unspecified)
        check("T4 函数调用：标识符与括号间可隔空白", weightAt("foo   (1);", 1), FontWeight.Medium)
        check("T4 普通标识符不加粗", weightAt("foo = 1;", 0), FontWeight.Normal)
        check("T4 普通标识符不着色", colorAt("foo = 1;", 0), Color.Unspecified)
        check("T4 大小写敏感：Int 不算内建", colorAt("Int x;", 0), Color.Unspecified)
        check("T4 紧跟标识符的数字不被当字面量（a1 里的 1）", colorAt("a1=5", 1), Color.Unspecified)
        check("T4 该是字面量的仍然着色", colorAt("a1=5", 3), SH_COLORS.number)
        check("T4 下划线开头标识符不被拆开", colorAt("_1 = 2;", 0), Color.Unspecified)
        check("T4 未闭合字符串只染到行尾，后面的代码照常染", colorAt("s = \"abc\nint x;", 10), SH_COLORS.builtin)
        check("T4 字符串内转义引号不提前结束染色", colorAt("s = \"a\\\"b\";", 8), SH_COLORS.string)
        check("T4 块注释未闭合 → 余下全文都是注释色", colorAt("/* x\nint y;", 5), SH_COLORS.comment)
        check("T4 高亮确实产生了样式（非空）", sh().highlight("int x = 1; // c").spanStyles.isNotEmpty(), true)

        // 语言族分发：同一个键前缀要落到同一套词法表，换个族就换一套（注释符/内建表都不同）。
        check("T5 同族不同标准：cc.cc98 与 cc.cc17 行为一致", sh("cc.cc98").highlight("int x; // c").spanStyles.size, sh().highlight("int x; // c").spanStyles.size)
        check("T5 语言键大小写不敏感", colorAt("int x;", 0, "CC.cc17"), SH_COLORS.builtin)
        check("T5 族名取第一个 `.` 之前（`cc17` 整串当族名 → 未知族）", colorAt("int x;", 0, "cc17"), Color.Unspecified)
        check("T5 Python：井号是注释", colorAt("x = 1 # c", 6, "py.py3"), SH_COLORS.comment)
        check("T5 Python：`//` 不是注释，不会误染", colorAt("a // b", 2, "py.py3"), Color.Unspecified)
        check("T5 Rust：没有内建表 → int 不着色", colorAt("int x;", 0, "rs.rs2021"), Color.Unspecified)
        check("T5 Rust：fn 是关键字", colorAt("fn main() {}", 0, "rs.rs2021"), SH_COLORS.keyword)
        check("T5 Pascal：块注释是 `{ }` 而不是 `/* */`", colorAt("{ c } x", 2, "pas.fpc3"), SH_COLORS.comment)
        check("T5 Pascal：`/*` 不再是块注释", colorAt("/* c */ x", 3, "pas.fpc3"), Color.Unspecified)
        check("T5 Haskell：`--` 是行注释", colorAt("x = 1 -- c", 6, "hs.ghc9"), SH_COLORS.comment)
        check("T5 未知语言族：行注释仍可用", colorAt("int x; // c", 10, "cob.cobol"), SH_COLORS.comment)
        check("T5 未知语言族：关键字与内建都不染（宁可朴素）", colorAt("int x; // c", 0, "cob.cobol"), Color.Unspecified)
        check("T5 未知语言族：字符串仍染", colorAt("s = \"hi\"", 4, "cob.cobol"), SH_COLORS.string)
    }

    // ---------- U. Markdown 解析器（题面渲染：块级分类 + 行内标记） ----------
    // 站点每道题的题干都走这里。它的失败模式同样是「不崩、只是显示得不对」：
    // 块级判错 → 整段变成列表/标题；行内判错 → 标记字符漏出来或正文被吃掉。
    // 用例的语料**取自线上题干的实测形态**（2026-09-14 抽样 12 道：标题只有 ##(60)/###(24)，
    // `*` 共 164 处**全部是长度 2 的 `**`**，另有反引号行内码与 `$...$` 公式，
    // 没有引用块/链接/分隔线/`_强调_`）。
    println("\n[U] MarkdownParser（块级分类 + 行内标记）")
    run {
        // ---- U1 块级：分类与优先级 ----
        check("U1 空串 → 无块", blk(""), "（空）")
        check("U1 只有空白 → 无块", blk("   \n\n  "), "（空）")
        check("U1 `## x` → H2（语料里最常用）", blk("## 题目描述"), "H2(题目描述)")
        check("U1 `# x` → H1", blk("# 一"), "H1(一)")
        check("U1 `###### x` → H6", blk("###### 六"), "H6(六)")
        check("U1 `####### x` 七个井号不是标题", blk("####### 七"), "P(####### 七)")
        check("U1 `#x` 井号后必须有空格才算标题", blk("#没有空格"), "P(#没有空格)")
        check("U1 `### 样例 1` → H3 且保留标题内的空格", blk("### 样例 1"), "H3(样例 1)")
        check("U1 段落紧跟标题（无空行）也要先 flush 段落", blk("正文\n## 标题"), "P(正文) | H2(标题)")

        check("U1 `---` → 分隔线", blk("---"), "DIV")
        check("U1 `- - -` → 分隔线（不是列表）", blk("- - -"), "DIV")
        check("U1 `***` → 分隔线（不是列表，也不是强调）", blk("***"), "DIV")
        check("U1 `***粗斜体***` 不是分隔线（判定用整行）", blk("***粗斜体***"), "P(***粗斜体***)")

        check("U1 `- x` → 无序列表", blk("- 项目"), "LI(ord=false,idx=0,depth=0,项目)")
        check("U1 `* x` → 无序列表", blk("* 项目"), "LI(ord=false,idx=0,depth=0,项目)")
        check("U1 `+ x` → 无序列表", blk("+ 项目"), "LI(ord=false,idx=0,depth=0,项目)")
        check("U1 `1. x` → 有序列表，序号取自源文", blk("1. 第一"), "LI(ord=true,idx=1,depth=0,第一)")
        check("U1 `3) x` → 有序列表（点或右括号都认）", blk("3) 第三"), "LI(ord=true,idx=3,depth=0,第三)")
        check("U1 两个空格缩进 → depth 1", blk("  - 缩进两格"), "LI(ord=false,idx=0,depth=1,缩进两格)")
        check("U1 四个空格缩进 → depth 2", blk("    - 缩进四格"), "LI(ord=false,idx=0,depth=2,缩进四格)")
        check("U1 `1.5 x` 不是有序项（点后必须跟空白）", blk("1.5 不是有序项"), "P(1.5 不是有序项)")
        check("U1 `**输入**` 单独一行仍是段落，不是列表", blk("**输入**"), "P(**输入**)")

        check("U1 `> x` → 引用", blk("> 引用"), "Q(引用)")
        check("U1 `>x`（无空格）也是引用", blk(">引用"), "Q(引用)")

        check("U1 代码块（无语言）", blk("```\n3 5\n```"), "CODE[](3 5)")
        check("U1 代码块带语言标记", blk("```cpp\nint a;\n```"), "CODE[cpp](int a;)")
        check("U1 ⚠️ 未闭合围栏 → 回落成段落，内容一个不丢", blk("```\n3 5"), "P(```\\n3 5)")
        check("U1 代码块内的 `#` 不被当标题", blk("```\n# not heading\n```"), "CODE[](# not heading)")
        check("U1 代码块内的空行原样保留", blk("```\na\n\nb\n```"), "CODE[](a\\n\\nb)")

        check("U1 空行分段", blk("第一段\n\n第二段"), "P(第一段) | P(第二段)")
        check("U1 相邻行合成一个段落（题干换行是软换行）", blk("第一行\n第二行"), "P(第一行\\n第二行)")
        check("U1 CRLF 先归一成 LF（否则 \\r 会留在正文里）", blk("## A\r\n\r\n正文"), "H2(A) | P(正文)")
        check("U1 段落首尾空白被 trim", blk("  正文  "), "P(正文)")
        check("U1 段落里的 `- 开头` 行会切成列表", blk("说明：\n- 第一\n- 第二"), "P(说明：) | LI(ord=false,idx=0,depth=0,第一) | LI(ord=false,idx=0,depth=0,第二)")
        check("U1 列表项的文本保留行内标记（行内留到 U2 处理）", blk("- **样例 1**：说明"), "LI(ord=false,idx=0,depth=0,**样例 1**：说明)")
        check(
            "U1 真实结构：标题/段落/块交替",
            blk("## 题目描述\n\n段落一。\n\n## 输入格式\n\n输入一行。"),
            "H2(题目描述) | P(段落一。) | H2(输入格式) | P(输入一行。)",
        )

        // ---- U2 行内：只丢标记、不改字 ----
        check("U2 纯文本原样", mdt("请输出 a+b。"), "请输出 a+b。")
        check("U2 制表符与 emoji 原样", mdt("a\tb 😀🎉"), "a\tb 😀🎉")
        check("U2 单个 `*` 不成对 → 原样", mdt("2 * 3"), "2 * 3")
        check("U2 下划线不做强调 → 原样", mdt("a_b_c"), "a_b_c")
        check("U2 未闭合 `**` → 原样", mdt("**未闭合"), "**未闭合")
        check("U2 未闭合反引号 → 原样", mdt("`未闭合"), "`未闭合")
        check("U2 单个 `\$` 不成对 → 原样", mdt("\$"), "\$")
        check("U2 空串 → 空串", mdt(""), "")
        // ⚠️ 这里 `\$` 是必须的：Kotlin 把裸 `$a` 当字符串模板插值（编译期 unresolved reference，
        // 不会静默），所以含 `$` 的**字面量**与**测试名**都得转义。注意 `\$` 本身是合法转义。
        check("U2 公式不跨行（`\$a\\nb\$` 不是公式）", mdt("\$a\nb\$"), "\$a\nb\$")

        check("U2 `**粗**` → 丢标记", mdt("**输入**"), "输入")
        check("U2 `*斜*` → 丢标记", mdt("*斜*"), "斜")
        check("U2 反引号行内码 → 丢标记", mdt("`8 -2`"), "8 -2")
        check("U2 行内码里的 `#define` 不被当标题也不丢字", mdt("`#define X 1`"), "#define X 1")
        check("U2 `\$公式\$` → 丢 `\$`", mdt("\$a+b\$"), "a+b")
        check("U2 公式里的反斜杠要保留（`\\le` 不能丢）", mdt("\$-10^9 \\le a\$"), "-10^9 \\le a")
        check("U2 链接只显示文字，URL 不显示", mdt("[点这里](https://x.y)"), "点这里")
        check("U2 一行里混三种标记", mdt("**输入**：`8 -2`"), "输入：8 -2")

        // 真实语料：直接取自线上 #2 / #12 的题干行（只读 GET 抓的），改动这里等于改契约
        check(
            "U2 真实语料（#2 的 Note 行）",
            mdt("**样例 1**：两台机器分别算出 \$3+5=8\$、\$3-5=-2\$，按顺序报回 `8 -2`。"),
            "样例 1：两台机器分别算出 3+5=8、3-5=-2，按顺序报回 8 -2。",
        )
        check(
            "U2 真实语料（#12 的条件句，含 \\neq 与绝对值）",
            mdt("钱数 \$a\$ 与定价 \$b\$（\$b \\neq 0\$，\$|a|, |b| \\le 10^9\$），请分两行输出"),
            "钱数 a 与定价 b（b \\neq 0，|a|, |b| \\le 10^9），请分两行输出",
        )
        check(
            "U2 真实语料（#2 的题面段，两个公式夹中文）",
            mdt("给定两个整数 \$a\$ 和 \$b\$，请先输出它们的和 \$a+b\$，再输出它们的差 \$a-b\$。"),
            "给定两个整数 a 和 b，请先输出它们的和 a+b，再输出它们的差 a-b。",
        )

        // 已知限制的**行为锁定**（characterization test）。
        // 这两例都是「有意不修」的，但输出**必须锁住**：只加子序列不变量时，判据太松 ——
        // 实测把 `**粗**` 那一支整个删掉，子序列不变量照样成立（`*输入*` 是 `**输入**` 的子序列），
        // 而实际输出已经从「输入」变成了「*输入*」。所以这两例必须逐字符比对。
        check("U2 `***三连***` 的既定输出（首尾各漏一个 `*`）", mdt("***粗斜体***"), "*粗斜体*")
        check("U2 `a * b * c` 的既定输出（中段被当斜体，未实现 flanking 规则）", mdt("a * b * c"), "a  b  c")
        check("U2 相邻标记不吞正文：`**a**b*c*`", mdt("**a**b*c*"), "abc")
        check("U2 紧邻标记：`*a*b`", mdt("*a*b"), "ab")

        // 不变量：Markdown 只许**丢弃标记字符**，绝不许改序或凭空造字。
        // 这条比逐条断言更耐用 —— 新增标记类型时它会自动覆盖。
        for (src in listOf(
            "**输入**", "*斜*", "`8 -2`", "\$a+b\$", "[点这里](https://x.y)",
            "***粗斜体***", "a * b * c", "[a](b) [c](d)",
            "**样例 1**：两台机器分别算出 \$3+5=8\$、\$3-5=-2\$，按顺序报回 `8 -2`。",
            "`#define X 1` 与 \$b \\neq 0\$ 混排",
        )) {
            check("U2 不变量「只丢标记、不改序」：$src", isSubsequence(mdt(src), src), true)
        }

        // 样式：行内四种标记各自挂对了样式（只断言「有该样式」与「没串行」）
        check("U2 行内码 → Monospace", mdStyle("`8 -2`", 0)?.fontFamily, FontFamily.Monospace)
        check("U2 粗体 → Bold", mdStyle("**输入**", 0)?.fontWeight, FontWeight.Bold)
        check("U2 斜体 → Italic", mdStyle("*斜*", 0)?.fontStyle, FontStyle.Italic)
        check("U2 公式 → Monospace", mdStyle("\$a+b\$", 0)?.fontFamily, FontFamily.Monospace)
        check("U2 链接 → 下划线", mdStyle("[点](https://x.y)", 0)?.textDecoration, TextDecoration.Underline)
        // ⚠️ 不能写 `FontStyle.Unspecified` —— FontStyle 是只有 Normal/Italic 两个常量的
        // value class（javap 确认过），没有 Unspecified。断「不是斜体」既够用也判得动。
        check("U2 粗体不该同时带上斜体样式", mdStyle("**输入**", 0)?.fontStyle != FontStyle.Italic, true)
        check("U2 纯文本没有任何样式", mdStyle("请输出 a+b。", 1), null)
    }

    // ---------- V. 代码字体清单一致性 ----------
    // 单列一组的理由：这份清单的风险**全是静默的** ——
    //   id 重复 → `firstOrNull{}` 会悄悄映射到前一个，设置页两行看着一样、点谁都一样；
    //   credit 漏写 → 打包了字体却没带许可声明（OFL 第 2 条要求），编译期毫无提示；
    //   notice 文件名抄错 → 「关于」段把用户指向一个根本不存在的文件。
    // 三条都不会崩、不会警告，只会"看着挺正常"。故用清单自身推出来的不变量钉住。
    println("\n[V] EDITOR_FONTS 清单一致性")
    run {
        val all = EDITOR_FONTS
        // 逐项钉死「顺序 + id」：id 发布后不可改（偏好按 id 落盘），
        // 改动这一行等于**故意**承认用户偏好会丢，必须人工确认。
        check(
            "V 顺序与 id（id 发布后不可改）",
            all.joinToString(",") { it.id },
            "system-mono,cascadia-mono,cascadia-code,jetbrains-mono,fira-code,source-code-pro",
        )
        check("V id 无重复", all.map { it.id }.distinct().size, all.size)
        check("V label 无重复（两行同名用户分不清）", all.map { it.label }.distinct().size, all.size)
        check("V label 全非空", all.all { it.label.isNotBlank() }, true)
        check("V note 全非空（空串会渲染成空行）", all.all { it.note.isNotBlank() }, true)

        check("V 默认字体在清单内", all.any { it.id == DEFAULT_EDITOR_FONT_ID }, true)
        check("V 默认字体排在首位（老用户看到的第一项不变）", all.first().id, DEFAULT_EDITOR_FONT_ID)
        check("V 系统字体不声明许可（未随包分发）", all.first { it.id == "system-mono" }.credit, "")
        check("V 除系统字体外的每一款都要有 credit",
            all.filter { it.id != "system-mono" }.all { it.credit.isNotBlank() }, true)
        check("V BUNDLED_EDITOR_FONTS 就是「除系统字体外」那几款",
            BUNDLED_EDITOR_FONTS.map { it.id },
            all.filter { it.id != "system-mono" }.map { it.id })

        // credit 必须点名一份 res/raw 声明文件，且**一款一份**（共用 = 有一款其实没声明）
        val noticeRe = Regex("([a-z0-9_]+_notice\\.txt)")
        val notices = BUNDLED_EDITOR_FONTS.map {
            noticeRe.find(it.credit)?.groupValues?.get(1) ?: ""
        }
        check("V 每个 credit 都点名了声明文件", notices.none { it.isEmpty() }, true)
        check("V 声明文件不重复", notices.distinct().size, notices.size)
        check("V 声明文件名与字体 id 对得上（id 里的 - 变 _）",
            BUNDLED_EDITOR_FONTS.map { it.id.replace('-', '_') + "_notice.txt" }, notices)
        check("V 每个 credit 都点出许可名称（OFL）",
            BUNDLED_EDITOR_FONTS.all { it.credit.contains("OFL") }, true)
        check("V 五款打包字体齐备",
            BUNDLED_EDITOR_FONTS.map { it.id },
            listOf("cascadia-mono", "cascadia-code", "jetbrains-mono", "fira-code", "source-code-pro"))

        // 样张是设置页唯一的"选之前先看"手段，三样特征都得在里面
        check("V 样张含 `!=`（连字字体与其它字体的分水岭）", EDITOR_FONT_SAMPLE.contains("!="), true)
        check("V 样张含中文（看汉字回落是否协调）", EDITOR_FONT_SAMPLE.any { it.code > 0x2000 }, true)
    }

    // ---------- X. AvatarFallback（头像兜底的纯逻辑） ----------
    // gravatar 域名国内不可达（见 E.8），加载失败时 Coil 什么都不画 —— 视觉上像缺陷。
    // 兜底是「首字母 + 名字稳定哈希选出的 M3 容器色」，这组钉住纯函数部分。
    println("\n[X] AvatarFallback（头像兜底）")
    run {
        check("X 英文名取首字母并大写", AvatarFallback.initial("alice"), "A")
        check("X 首尾空白先裁掉", AvatarFallback.initial("  bob  "), "B")
        check("X 中文名取首个汉字", AvatarFallback.initial("绪山真寻"), "绪")
        check("X 数字开头取数字（如 114514）", AvatarFallback.initial("114514"), "1")
        // 🦊 是增补平面字符（代理对），必须整个码点返回，切碎就成乱码
        check("X emoji 取整个码点（不切碎代理对）",
            AvatarFallback.initial("🦊fox"), "🦊")
        check("X null → null（回落人形图标）", AvatarFallback.initial(null), null)
        check("X 空串 → null", AvatarFallback.initial(""), null)
        check("X 纯空白 → null", AvatarFallback.initial("   "), null)

        check("X 档位落在 0..3", AvatarFallback.containerIndex("alice") in 0..3, true)
        check("X 同名恒同档（跨页面跨会话同色）",
            AvatarFallback.containerIndex("alice"), AvatarFallback.containerIndex("alice"))
        check("X null → 0 号档", AvatarFallback.containerIndex(null), 0)
        check("X 空串 → 0 号档", AvatarFallback.containerIndex(""), 0)
        // 四档都要有人用：拿一批真实/常见用户名验证没有某档永远取不到
        val names = listOf("114514", "alice", "bob", "绪山真寻", "carol", "dave",
            "erin", "frank", "grace", "heidi", "ivan", "judy", "mallory",
            "niaj", "olivia", "peggy", "rupert", "sybil", "trent", "victor",
            "walter", "wendy", "zoe", "Hydro", "lsflsf2023")
        val used = names.map { AvatarFallback.containerIndex(it) }.toSet()
        check("X 真实名字样本里四档都有人用（色块不退化成单色）", used.size, 4)
    }

    // ---------- Y. CodeUndoStack（编辑器撤销栈，C4-1） ----------
    // 核心价值在合并策略：打字/退格连击合并成一条，其余各自成条。
    // 全部用例用手推的 TextFieldValue 构造 —— 期望值必须手推是本自检的既定纪律。
    println("\n[Y] CodeUndoStack（撤销栈）")
    run {
        fun fv(text: String, caret: Int = text.length) =
            TextFieldValue(text, TextRange(caret))

        fun type(old: TextFieldValue, ch: String): TextFieldValue {
            val at = old.selection.end
            return fv(old.text.substring(0, at) + ch + old.text.substring(at), at + ch.length)
        }

        fun backspace(old: TextFieldValue): TextFieldValue {
            val at = old.selection.end
            return fv(old.text.removeRange(at - 1, at), at - 1)
        }

        val start = fv("int ")
        val s = CodeUndoStack(start)

        // 连续打字 → 合并成一条：undo 一次直接回到 "int "
        var cur = start
        listOf('m', 'a', 'i', 'n').forEach { ch ->
            val next = type(cur, ch.toString())
            s.push(cur, next)
            cur = next
        }
        check("Y canUndo 为真", s.canUndo, true)
        check("Y canRedo 为假（新编辑清空 redo）", s.canRedo, false)
        val u1 = s.undo()
        check("Y 连续打字合并：undo 一次回到打字前", u1?.text, "int ")

        // 重做回到最终值
        val r1 = s.redo()
        check("Y 重做回到最后一个值", r1?.text, "int main")

        // 换行是合并断点：main 后回车，undo 应该只撤掉"换行+缩进"这一条
        val enter = type(fv("int main"), "\n")
        s.push(fv("int main"), enter)
        check("Y undo 后又可 undo（换行成独立条）", s.canUndo, true)
        val u2 = s.undo()
        check("Y 换行独立成条：undo 只撤换行", u2?.text, "int main")

        // 连续退格合并（干净栈：退格连击 undo 一次撤掉两个）
        val sb = CodeUndoStack(fv("int mainx", 9))
        val b1 = backspace(fv("int mainx", 9))
        sb.push(fv("int mainx", 9), b1)
        val b2 = backspace(b1)
        sb.push(b1, b2)
        val ub = sb.undo()
        check("Y 连续退格合并：undo 一次撤掉两个", ub?.text, "int mainx")
        check("Y 合并后的栈只剩一条历史", sb.canUndo, false)

        // 粘贴（多字符插入）独立成条（干净栈）
        val sp = CodeUndoStack(fv("abc", 3))
        sp.push(fv("abc", 3), fv("abc hello", 9))
        val up = sp.undo()
        check("Y 多字符插入独立成条", up?.text, "abc")

        // 程序化编辑强制独立成条（forceChunk）
        val s2 = CodeUndoStack(fv("a"))
        val t1 = type(fv("a"), "b")
        s2.push(fv("a"), t1)
        val t2 = type(t1, "c")
        s2.push(t1, t2, forceChunk = true)
        val u5 = s2.undo()
        check("Y forceChunk 打断打字合并", u5?.text, "ab")

        // 空栈 undo/redo 安全
        val s3 = CodeUndoStack(fv("x"))
        check("Y 空栈 undo 返回 null", s3.undo(), null)
        check("Y 空栈 redo 返回 null", s3.redo(), null)

        // 新编辑清空 redo
        val s4 = CodeUndoStack(fv("x"))
        val e1 = type(fv("x"), "y")
        s4.push(fv("x"), e1)
        s4.undo()
        check("Y undo 后 canRedo 为真", s4.canRedo, true)
        val branch = type(fv("x"), "z") // undo 后改走新分支
        s4.push(fv("x"), branch)
        check("Y 新编辑清空 redo", s4.canRedo, false)

        // 光标纯移动不产生历史
        val s5 = CodeUndoStack(fv("abc"))
        s5.push(fv("abc"), fv("abc", 1))
        s5.push(fv("abc", 1), fv("abc", 2))
        check("Y 光标移动不产生历史", s5.canUndo, false)

        // undo 保留选区（回到变更前的光标）
        val s6 = CodeUndoStack(fv("ab", 2))
        val typed = type(fv("ab", 2), "c")
        s6.push(fv("ab", 2), typed)
        val u6 = s6.undo()
        check("Y undo 还原选区", listOf(u6?.text, u6?.selection?.end), listOf("ab", 2))

        // 容量封顶：limit=3 时第 4 条独立编辑会把最老的挤出去
        val s7 = CodeUndoStack(fv("0"), limit = 3)
        var c7 = fv("0")
        for (i in 1..6) {
            val nxt = type(c7, "\n" + i.toString()) // 换行断合并，每条独立
            s7.push(c7, nxt)
            c7 = nxt
        }
        var count = 0
        while (s7.undo() != null) count++
        check("Y 容量封顶（limit=3 只剩 3 条可撤销）", count, 3)
    }

    // ---------- Z. CodeFindReplace（查找替换纯逻辑，C4-2） ----------
    // 期望值全部手推（含下标）。大小写保留与环形步进是这组的判别性用例。
    println("\n[Z] CodeFindReplace（查找替换）")
    run {
        // "int x = x + x" → x 在 4、8、12
        val m1 = CodeFindReplace.matches("int x = x + x", "x", ignoreCase = true)
        check("Z 逐个匹配区间", m1, listOf(4..4, 8..8, 12..12))

        check("Z 忽略大小写三处", CodeFindReplace.matches("Foo foo FOO", "foo", true).size, 3)
        check("Z 区分大小写只中一处", CodeFindReplace.matches("Foo foo FOO", "foo", false).size, 1)

        check("Z 空查询 → 空表", CodeFindReplace.matches("abc", "", true), emptyList<IntRange>())

        // 替换必须保留原文大小写（不能先 lowercase 再拼）
        val (r1, n1) = CodeFindReplace.replaceAll("Foo foo FOO", "foo", "bar", true)
        check("Z 忽略大小写全部替换", listOf(r1, n1), listOf("bar bar bar", 3))
        val (r2, n2) = CodeFindReplace.replaceAll("Foo foo FOO", "foo", "bar", false)
        check("Z 区分大小写替换保留原文写法", listOf(r2, n2), listOf("Foo bar FOO", 1))

        // 替换串包含查询串：按区间拼接、不回溯（"aa" 两个 a → aaaa，不是无限循环）
        val (r3, n3) = CodeFindReplace.replaceAll("aa", "a", "aa", true)
        check("Z 替换串包含查询串不回溯", listOf(r3, n3), listOf("aaaa", 2))

        check("Z 空查询替换原样返回",
            CodeFindReplace.replaceAll("abc", "", "x", true), "abc" to 0)

        // 环形步进
        check("Z step 向后", CodeFindReplace.step(0, 3, false), 1)
        check("Z step 向后回卷", CodeFindReplace.step(2, 3, false), 0)
        check("Z step 向前", CodeFindReplace.step(2, 3, true), 1)
        check("Z step 向前回卷", CodeFindReplace.step(0, 3, true), 2)
        check("Z step 零匹配 → -1", CodeFindReplace.step(0, 0, false), -1)

        // 光标定位：m1 的匹配在 4/8/12
        check("Z locate 从头向后 = 第一个", CodeFindReplace.locate(m1, 0, false), 0)
        check("Z locate 越过第一个", CodeFindReplace.locate(m1, 5, false), 1)
        check("Z locate 向后环回", CodeFindReplace.locate(m1, 13, false), 0)
        check("Z locate 末尾向前 = 最后一个", CodeFindReplace.locate(m1, 13, true), 2)
        check("Z locate 开头向前环回", CodeFindReplace.locate(m1, 0, true), 2)
        check("Z locate 空匹配 → -1", CodeFindReplace.locate(emptyList(), 0, false), -1)

        // emoji 查询串（增补平面，UTF-16 两个 char）：按 char 匹配照样正确
        val m2 = CodeFindReplace.matches("🦊a🦊", "🦊", true)
        check("Z emoji 查询串匹配", m2, listOf(0..1, 3..4))
    }

    // ---------- W. 错误文案（技术错误 → 用户能行动的一句话） ----------
    // 这组是被真事逼出来的：竞赛题在 Hydro 里是 hidden 题，App 早期漏传 tid，
    // 用户看到的是 "You don't have the required permission (View hidden problems)" ——
    // 句子没错，但他无法据此判断该做什么：没报名？题被删了？站点坏了？
    // 判据一律走 name + ASCII 子串，站点改文案时最多退化成原样显示，不会翻错。
    println("\n[W] ErrorMessages（错误信封 → 面向用户的一句话）")
    run {
        // 实测原文（GET /p/154，账号 114514 / role=default，未带竞赛上下文）
        val hiddenRaw = "You don't have the required permission (View hidden problems) in this domain."

        check("W 隐藏题 403 → 提示里点明是「隐藏题」",
            ErrorMessages.humanize("PermissionError", hiddenRaw).contains("隐藏题"), true)
        check("W 隐藏题提示必须给出出路（竞赛或作业）",
            ErrorMessages.humanize("PermissionError", hiddenRaw).contains("竞赛或作业"), true)
        check("W 其它权限错误 → 中文前缀 + 保留原文",
            ErrorMessages.humanize("PermissionError", "Not allowed."),
            "你没有权限执行这个操作：Not allowed.")
        check("W ContestNotAttendedError → 指向「报名」",
            ErrorMessages.humanize("ContestNotAttendedError", "You haven't attended this contest yet.")
                .contains("报名"), true)
        check("W ContestNotLiveError → 指向竞赛状态",
            ErrorMessages.humanize("ContestNotLiveError", "Contest not live.").contains("竞赛"), true)
        check("W HomeworkNotLiveError → 指向「截止」",
            ErrorMessages.humanize("HomeworkNotLiveError", "Homework not live.").contains("截止"), true)
        check("W ProblemNotFoundError → 指向「不存在」",
            ErrorMessages.humanize("ProblemNotFoundError", "Problem {0} not found.").contains("不存在"), true)
        check("W UserNotFoundError → 指向「不存在」",
            ErrorMessages.humanize("UserNotFoundError", "User bob not found.").contains("不存在"), true)
        check("W NotFoundError → 带 404 提示",
            ErrorMessages.humanize("NotFoundError", "NotFoundError").contains("404"), true)

        // ⚠️ 这组的核心不变量：没认出来的，原样吐出来。
        // 宁可让用户看到看不懂的真话，也不要编一句看起来合理、实际误导的假话。
        check("W 未知 name → 原样透传（不猜）",
            ErrorMessages.humanize("SomeWeirdError", "boom happened"), "boom happened")
        check("W name 缺失 → 原样透传",
            ErrorMessages.humanize(null, "raw site message"), "raw site message")
        check("W 空 message → 不留空白给用户",
            ErrorMessages.humanize("ContestNotLiveError", ""), "请求失败")
        // 分类看 name，不看 message 里碰巧出现的字眼
        check("W ⚠️ 分类以 name 为准：别因 message 撞词就误判成隐藏题",
            ErrorMessages.humanize("ContestNotLiveError", "View hidden problems"),
            "这场竞赛当前不能作答（尚未开始，或已经结束）。")

        // 译文里不该残留站点原句或未替换的占位符
        val translated = listOf(
            ErrorMessages.humanize("PermissionError", hiddenRaw),
            ErrorMessages.humanize("ContestNotAttendedError", "You haven't attended this contest yet."),
            ErrorMessages.humanize("ProblemNotFoundError", "Problem {0} not found."),
        )
        check("W 译文里不留 {0} 占位符", translated.none { it.contains("{0}") }, true)
        check("W 译文里不留站点英文原句",
            translated.none { it.contains("You don't have the required permission") }, true)

        // Failure 上 message 与 friendly 的分工：前者是诊断用的原文，后者才是给用户的
        val f = HydroResult.Failure(403, hiddenRaw, "PermissionError")
        check("W Failure.message 保留站点原文（诊断/自检钉契约用）",
            f.message.contains("View hidden problems"), true)
        check("W Failure.friendly 才是给人看的中文",
            f.friendly.contains("隐藏题"), true)
        check("W errorText() 走 friendly，不把英文抛给 UI",
            f.errorText()?.contains("隐藏题"), true)
    }

    // ---------- 结果 ----------
    println("\n" + "=".repeat(56))
    println("通过 $passed 项，失败 ${failures.size} 项")
    if (failures.isNotEmpty()) {
        println("\n失败明细：")
        failures.forEach { println("  - $it") }
        kotlin.system.exitProcess(1)
    }
}
