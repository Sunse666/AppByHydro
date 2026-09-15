package com.jxau.oj.data.net

/**
 * 把站点下发的**技术性错误**翻成用户能据此行动的一句话。
 *
 * 为什么值得单独抽一层：Hydro 的错误信封里 `message` 是给开发者看的英文模板
 * （`ResponseDispatch` 已负责把 `{0}` 换成实际值），但换完仍然是
 * 「You don't have the required permission (View hidden problems) in this domain.」
 * 这类句子 —— 用户读它无法判断**自己该做什么**：是没报名？是题被删了？是站点故障？
 * 而这三者的处置完全不同。
 *
 * 判据一律用 `name`（Hydro 的异常类名，如 `ContestNotAttendedError`）与
 * `message` 里的 **ASCII 子串**，不匹配中文、不依赖具体措辞顺序：
 * 站点改文案时这里最多退化成"原样显示"，不会翻错。
 *
 * 未识别的错误**原样返回** —— 宁可让用户看到看不懂的真话，
 * 也不要编一句看起来合理但可能误导的假话。
 *
 * 纯函数（只依赖 kotlin stdlib），因此可进 `tools/pure_helpers_check` 离线自检。
 */
internal object ErrorMessages {

    /** 站点「查看隐藏题目」域权限的原文片段（实测于 `GET /p/154`，403）。 */
    private const val VIEW_HIDDEN = "View hidden problems"

    fun humanize(name: String?, message: String): String {
        if (message.isBlank()) return "请求失败"
        return when (name) {
            "PermissionError" ->
                if (message.contains(VIEW_HIDDEN)) {
                    "这道题在题库里是隐藏题，不能直接打开。\n" +
                        "请从它所属的竞赛或作业进入（竞赛需先报名，作业需先认领）。"
                } else {
                    "你没有权限执行这个操作：$message"
                }

            "ContestNotAttendedError" -> "你还没有报名这场竞赛，报名后才能查看题目。"
            "ContestNotLiveError" -> "这场竞赛当前不能作答（尚未开始，或已经结束）。"
            "HomeworkNotLiveError" -> "这份作业已截止，无法再认领或作答。"
            "ProblemNotFoundError" -> "题目不存在或已被删除。"
            "UserNotFoundError" -> "用户不存在。"
            "NotFoundError" -> "站点上没有这个资源（404）。"

            // 未识别：原样返回，不猜
            else -> message
        }
    }
}
