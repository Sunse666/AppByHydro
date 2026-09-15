package com.jxau.oj.ui.component

/**
 * 头像兜底的纯逻辑。与 UI 分离以便离线自检（pure_helpers_check 的 X 组钉住）。
 *
 * 背景：站点的 `gravatar:` 头像域名在国内不可达，加载失败时 Coil 什么都不画 ——
 * 视觉上像缺陷。兜底方案是「首字母 + 从名字稳定派生的 M3 容器色」色块，
 * 三个使用场景共用：无 avatar 字段、加载中、加载失败。
 */
object AvatarFallback {

    /**
     * 取名字的首字符作字母兜底，取**第一个码点**（不是第一个 char）——
     * emoji 或增补平面字符占两个 char，按 char 切会得到孤立代理项，画出来是乱码。
     * 大小写归一走 Locale.ROOT（避免土耳其语区 `i` → `İ` 之类的区域意外）。
     * 空白名字返回 null（调用方回落到人形图标）。
     */
    fun initial(name: String?): String? {
        val trimmed = name?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        val cp = trimmed.codePointAt(0)
        return String(Character.toChars(cp)).uppercase(java.util.Locale.ROOT)
    }

    /**
     * 从名字稳定派生 0..3 的容器色档位：同一个人永远同色（跨页面、跨会话），
     * 不同人尽量散开。用 [String.hashCode] —— 其算法被 Java 规范钉死，跨设备一致。
     * 先加 `+4` 再取模，避免 `Int.MIN_VALUE % 4` 之外仍可能出现负数余数的边界。
     */
    fun containerIndex(name: String?): Int {
        if (name.isNullOrEmpty()) return 0
        return ((name.hashCode() % 4) + 4) % 4
    }
}
