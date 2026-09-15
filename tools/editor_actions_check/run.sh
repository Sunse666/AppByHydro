#!/bin/bash
# 离线跑 CodeEditActions 自检：用缓存里的 kotlin-compiler-embeddable + Compose ui-text。
#
# 为什么不用 JVM 单元测试框架：本机离线缓存里只有 junit-bom（**没有 jar**），
# 引不进测试框架。但 CodeEditActions 是纯函数、只依赖 TextFieldValue/TextRange，
# 于是直接用编译器 embeddable 编成可执行程序来跑。
#
# 注意：脚本里自己的 echo 一律用 ASCII —— Java 进程按 Windows 默认 GBK 输出，
# 混排中文会让两端编码对不上（Java 那半截才是真正的测试结果）。
set -e

M="C:/Users/23836/.gradle/caches/modules-2/files-2.1"
JAVA="D:/IO/jdk17/bin/java.exe"

# ---- 编译器自身运行期依赖（少一个就 NoClassDefFoundError）----
#   stdlib/script-runtime/daemon/reflect  → 常规
#   coroutines                            → CoreApplicationEnvironment 初始化
#   annotations                           → 代码生成阶段写 @NotNull 空值注解
KOTLINC="$M/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.21/79346ed53db48b18312a472602eb5c057070c54d/kotlin-compiler-embeddable-2.0.21.jar"
STDLIB="$M/org.jetbrains.kotlin/kotlin-stdlib/2.0.21/618b539767b4899b4660a83006e052b63f1db551/kotlin-stdlib-2.0.21.jar"
SCRIPT_RT="$M/org.jetbrains.kotlin/kotlin-script-runtime/2.0.21/c9b044380ad41f89aa89aa896c2d32a8c0b2129d/kotlin-script-runtime-2.0.21.jar"
DAEMON="$M/org.jetbrains.kotlin/kotlin-daemon-embeddable/2.0.21/c9e933b23287de9b5a17e2116b4657bb91aea72c/kotlin-daemon-embeddable-2.0.21.jar"
REFLECT="$M/org.jetbrains.kotlin/kotlin-reflect/1.8.21/662838019bc1141f8a311180d93b9e13765c7f55/kotlin-reflect-1.8.21.jar"
TROVE="$M/org.jetbrains.intellij.deps/trove4j/1.0.20200330/3afb14d5f9ceb459d724e907a21145e8ff394f02/trove4j-1.0.20200330.jar"
COROUTINES="$M/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.8.1/bb0e192bd7c2b6b8217440d36e9758e377e450/kotlinx-coroutines-core-jvm-1.8.1.jar"
ANNOTATIONS="$M/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar"
CP_COMPILER="$KOTLINC;$STDLIB;$SCRIPT_RT;$DAEMON;$REFLECT;$TROVE;$COROUTINES;$ANNOTATIONS"

ROOT="D:/IO/HTML/Hydro"
SRC="$ROOT/android-app/app/src/main/java/com/jxau/oj/ui/editor/CodeEditActions.kt"
CHK="$ROOT/tools/editor_actions_check/CheckEditorActions.kt"
OUT="$ROOT/.workbuddy/tmp/checkout"
LIB="$OUT/lib"
mkdir -p "$LIB"

# Compose 以 AAR 分发，需解出里面的 classes.jar。缺哪个补哪个，
# 报错特征就是 NoClassDefFoundError 指向那个包。
add_aar() {  # $1 模块路径片段  $2 aar 文件名
  local aar
  aar=$(find "$M/$1" -name "$2" | head -1)
  if [ -z "$aar" ]; then echo "!! missing in cache: $1/$2" >&2; return 1; fi
  local name
  name="$(basename "$aar" .aar).jar"
  if [ ! -f "$LIB/$name" ]; then
    ( cd "$LIB" && unzip -o -q "$aar" classes.jar && mv -f classes.jar "$name" )
  fi
  CP_RUN="$CP_RUN;$LIB/$name"
}
add_jar() {  # $1 模块路径片段  $2 jar 通配
  local j
  j=$(find "$M/$1" -name "$2" | head -1)
  if [ -z "$j" ]; then echo "!! missing in cache: $1/$2" >&2; return 1; fi
  CP_RUN="$CP_RUN;$j"
}

CP_RUN="$STDLIB"
add_aar "androidx.compose.ui/ui-text-android" "ui-text-release.aar"
# add_aar 产出的文件名 = aar 的 basename，编译期 classpath 也要用它 ——
# 早先这里硬编码成 ui-text.jar，靠着上一轮遗留的同名文件"恰好能跑"，
# 清掉缓存后就编不过（表现为 TextFieldValue 相关的一堆 unresolved reference）。
TEXT_JAR="$LIB/ui-text-release.jar"
add_aar "androidx.compose.runtime/runtime-android" "runtime-release.aar"
add_aar "androidx.compose.runtime/runtime-saveable-android" "runtime-saveable-release.aar"
add_aar "androidx.compose.ui/ui-unit-android" "ui-unit-release.aar"
add_aar "androidx.compose.ui/ui-geometry-android" "ui-geometry-release.aar"
add_aar "androidx.compose.ui/ui-util-android" "ui-util-release.aar"
add_jar "androidx.collection/collection-jvm" "collection-jvm-*.jar"
add_jar "androidx.annotation/annotation-jvm" "annotation-jvm-*.jar"

echo "== compile =="
rm -rf "$OUT/classes" && mkdir -p "$OUT/classes"
"$JAVA" -cp "$CP_COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -nowarn \
  -cp "$STDLIB;$TEXT_JAR" -d "$OUT/classes" "$SRC" "$CHK"

echo "== run =="
"$JAVA" -cp "$OUT/classes;$CP_RUN" check.CheckEditorActionsKt
