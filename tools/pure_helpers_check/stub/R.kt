package com.jxau.oj

/**
 * **仅供离线自检使用的 `R` 替身。**
 *
 * `ui/theme/EditorFonts.kt` 里引用了 `R.font.*`（打包字体的资源 id）。
 * 离线自检不经过 aapt，没有生成的 `R` 类，所以在这里补一个最小替身 ——
 * 只用得到**字段存在**这件事，值本身（资源 id）在自检里毫无意义。
 *
 * ⚠️ 若将来 `EditorFonts` 又引用了别的 `R` 字段，这个文件会编不过 ——
 * 那是**响亮的编译错误**，照抄字段进来即可；不要图省事改成反射或占位值。
 *
 * ⚠️ 反过来也要留意：这里**多出**的字段不会报错，只是没人用。
 * 真正的「清单 ↔ 字体文件」一致性由 `run.sh` 末尾的资源核对步骤负责。
 */
object R {
    object font {
        const val cascadia_mono = 0
        const val cascadia_code = 0
        const val jetbrains_mono_regular = 0
        const val jetbrains_mono_medium = 0
        const val jetbrains_mono_bold = 0
        const val fira_code_regular = 0
        const val fira_code_medium = 0
        const val fira_code_bold = 0
        const val source_code_pro_regular = 0
        const val source_code_pro_bold = 0
    }
}
