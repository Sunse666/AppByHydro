#!/bin/bash
# 离线跑「映射层 + 工具层」纯函数自检（第二批）。
#
# 与 tools/editor_actions_check/run.sh 同一套路：本机离线缓存里 org.junit 只有 bom、没有 jar，
# 所以不用测试框架，直接用缓存里的 kotlin-compiler-embeddable 编成可执行程序跑。
# 两个脚本的 classpath 组装逻辑（add_aar/add_jar）刻意各留一份 —— 两套用例依赖不同，
# 拆成公共库反而要来回跳文件。
set -e

M="C:/Users/23836/.gradle/caches/modules-2/files-2.1"
JAVA="D:/IO/jdk17/bin/java.exe"

KOTLINC="$M/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.0.21/79346ed53db48b18312a472602eb5c057070c54d/kotlin-compiler-embeddable-2.0.21.jar"
STDLIB="$M/org.jetbrains.kotlin/kotlin-stdlib/2.0.21/618b539767b4899b4660a83006e052b63f1db551/kotlin-stdlib-2.0.21.jar"
SCRIPT_RT="$M/org.jetbrains.kotlin/kotlin-script-runtime/2.0.21/c9b044380ad41f89aa89aa896c2d32a8c0b2129d/kotlin-script-runtime-2.0.21.jar"
DAEMON="$M/org.jetbrains.kotlin/kotlin-daemon-embeddable/2.0.21/c9e933b23287de9b5a17e2116b4657bb91aea72c/kotlin-daemon-embeddable-2.0.21.jar"
REFLECT="$M/org.jetbrains.kotlin/kotlin-reflect/1.8.21/662838019bc1141f8a311180d93b9e13765c7f55/kotlin-reflect-1.8.21.jar"
TROVE="$M/org.jetbrains.intellij.deps/trove4j/1.0.20200330/3afb14d5f9ceb459d724e907a21145e8ff394f02/trove4j-1.0.20200330.jar"
COROUTINES="$M/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.8.1/bb0e192bd7c2b6b8217440d36e9758e377e450/kotlinx-coroutines-core-jvm-1.8.1.jar"
ANNOTATIONS="$M/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar"
# Compose 编译器插件：被测源码里有 @Composable 函数（VerdictColors.current / colorOf），
# 没有它会在 IR lowering 阶段直接抛 "Exception while generating code"。
COMPOSE_PLUGIN="$M/org.jetbrains.kotlin/kotlin-compose-compiler-plugin-embeddable/2.0.21/e14f003d962fb25693b461de59490c91072a7979/kotlin-compose-compiler-plugin-embeddable-2.0.21.jar"
CP_COMPILER="$KOTLINC;$STDLIB;$SCRIPT_RT;$DAEMON;$REFLECT;$TROVE;$COROUTINES;$ANNOTATIONS"

ROOT="D:/IO/HTML/Hydro"
APP="$ROOT/android-app/app/src/main/java"
OUT="$ROOT/.workbuddy/tmp/checkout_pure"
LIB="$OUT/lib"
mkdir -p "$LIB"

add_aar() {  # $1 模块路径片段  $2 aar 文件名；产出 $LIB/<aar 基名>.jar
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
add_jar() {  # $1 模块路径片段  $2 jar 通配；已按版本取第一个
  local j
  j=$(find "$M/$1" -name "$2" | head -1)
  if [ -z "$j" ]; then echo "!! missing in cache: $1/$2" >&2; return 1; fi
  CP_RUN="$CP_RUN;$j"
}

CP_RUN="$STDLIB"
add_jar "org.jetbrains.kotlinx/kotlinx-serialization-json-jvm/1.6.3" "kotlinx-serialization-json-jvm-1.6.3.jar"
add_jar "org.jetbrains.kotlinx/kotlinx-serialization-core-jvm/1.6.3" "kotlinx-serialization-core-jvm-1.6.3.jar"
add_aar "androidx.compose.ui/ui-text-android" "ui-text-release.aar"
TEXT_JAR="$LIB/ui-text-release.jar"
add_aar "androidx.compose.runtime/runtime-android" "runtime-release.aar"
add_aar "androidx.compose.runtime/runtime-saveable-android" "runtime-saveable-release.aar"
add_aar "androidx.compose.ui/ui-unit-android" "ui-unit-release.aar"
add_aar "androidx.compose.ui/ui-geometry-android" "ui-geometry-release.aar"
add_aar "androidx.compose.ui/ui-util-android" "ui-util-release.aar"
# VerdictColors 用到了 androidx.compose.ui.graphics.Color 与 foundation 的 isSystemInDarkTheme
add_aar "androidx.compose.ui/ui-graphics-android" "ui-graphics-release.aar"
add_aar "androidx.compose.foundation/foundation-android" "foundation-release.aar"
add_jar "androidx.collection/collection-jvm" "collection-jvm-*.jar"
add_jar "androidx.annotation/annotation-jvm" "annotation-jvm-*.jar"

# 被测源码：只挑**不依赖 Android 运行时**的（映射层/模型/纯工具）。
# EditorFonts 引用了 R.font.*、Mappers 引用了 BuildConfig，用 stub/ 下的最小替身顶替。
SRCS=(
  "$APP/com/jxau/oj/data/dto/JsonSupport.kt"
  "$APP/com/jxau/oj/data/dto/Dtos.kt"
  "$APP/com/jxau/oj/data/dto/ContestDtos.kt"
  "$APP/com/jxau/oj/data/dto/SubmissionDtos.kt"
  "$APP/com/jxau/oj/data/model/Models.kt"
  "$APP/com/jxau/oj/data/mapper/Mappers.kt"
  "$APP/com/jxau/oj/data/net/HydroResult.kt"
  "$APP/com/jxau/oj/data/net/ResponseDispatch.kt"
  "$APP/com/jxau/oj/data/net/ErrorMessages.kt"
  "$APP/com/jxau/oj/ui/component/AvatarFallback.kt"
  "$APP/com/jxau/oj/ui/editor/CodeUndoStack.kt"
  "$APP/com/jxau/oj/ui/util/TimeFormat.kt"
  "$APP/com/jxau/oj/ui/util/RoleLabel.kt"
  "$APP/com/jxau/oj/ui/component/MarkdownParser.kt"
  "$APP/com/jxau/oj/ui/editor/CodeCompletion.kt"
  "$APP/com/jxau/oj/ui/editor/CodeFindReplace.kt"
  "$APP/com/jxau/oj/ui/editor/SyntaxHighlighter.kt"
  "$APP/com/jxau/oj/ui/theme/EditorFonts.kt"
  "$APP/com/jxau/oj/ui/theme/VerdictColors.kt"
  "$ROOT/tools/pure_helpers_check/stub/R.kt"
  "$ROOT/tools/pure_helpers_check/stub/BuildConfig.kt"
  "$ROOT/tools/pure_helpers_check/stub/LocalIsDarkTheme.kt"
  "$ROOT/tools/pure_helpers_check/CheckPureHelpers.kt"
)

echo "== compile =="
rm -rf "$OUT/classes" && mkdir -p "$OUT/classes"
"$JAVA" -cp "$CP_COMPILER" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -nowarn \
  -Xplugin="$COMPOSE_PLUGIN" \
  -cp "$CP_RUN;$TEXT_JAR" -d "$OUT/classes" "${SRCS[@]}"

echo "== run =="
"$JAVA" -cp "$OUT/classes;$CP_RUN" checkpure.CheckPureHelpersKt

# == font assets ==
# 清单（EditorFonts.kt）里每款打包字体都点名了一份随包许可声明；这里核对**文件真的在**。
# OFL 第 2 条要求字体副本携带版权声明与本许可 —— 少一个文件在编译期毫无提示，
# 只有上架审核或被人较真时才发现。反向也查：没人引用的声明 = 用户看不到它。
#
# ⚠️ 判据与提示串**一律 ASCII**：脚本文件可能按 GBK 落盘，
# 写中文进去后 grep 永远匹配不上，输出还会变成乱码（把「全部正常」误报成「失败」）。
echo "== font assets =="
APP_RES="$ROOT/android-app/app/src/main/res"
FONTS_KT="$APP/com/jxau/oj/ui/theme/EditorFonts.kt"
NOTICES=$(grep -oE '[a-z0-9_]+_notice\.txt' "$FONTS_KT" | sort -u)
if [ -z "$NOTICES" ]; then echo "!! EditorFonts.kt lists no *_notice.txt"; exit 1; fi
for n in $NOTICES; do
  f="$APP_RES/raw/$n"
  [ -f "$f" ] || { echo "!! listed in manifest but missing: res/raw/$n"; exit 1; }
  grep -q 'SIL Open Font License' "$f" || { echo "!! no 'SIL Open Font License' in $n"; exit 1; }
  echo "  OK  $n"
done
# 反向：res/raw 下的每份声明都必须被清单点名（否则用户根本看不到它）。
# ⚠️ 不能用 `case " $NOTICES "` 做包含判断 —— NOTICES 是**多行**的，
# 行首那个名字后面跟的是换行不是空格，永远匹配不上（实测把 5 份里的 4 份误判为"未被引用"）。
for p in "$APP_RES"/raw/*_notice.txt; do
  b=$(basename "$p")
  printf '%s\n' "$NOTICES" | grep -qx "$b" \
    || { echo "!! unreferenced notice (users never see it): res/raw/$b"; exit 1; }
done
for p in "$APP_RES"/font/*.ttf; do
  b=$(basename "$p" .ttf)
  grep -q "R\.font\.$b" "$FONTS_KT" \
    || echo "  NOTE  res/font/$b.ttf is not referenced by the manifest"
done
