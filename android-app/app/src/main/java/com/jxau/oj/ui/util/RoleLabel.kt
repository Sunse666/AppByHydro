package com.jxau.oj.ui.util

/**
 * 角色中文名。榜单、用户主页、"我的"三处共用。
 *
 * 站点下发的 `role` 实测取值为 `root` / `admin` / `default` / `guest`；
 * 未知取值原样显示而不是吞掉 —— 站点将来加了新角色，界面上能直接看出来，
 * 比显示一个"未知"然后让人去翻代码强。
 */
fun roleLabel(role: String): String = when (role) {
    "root" -> "超级管理员"
    "admin" -> "管理员"
    "default" -> "普通用户"
    "guest" -> "游客"
    else -> role
}
