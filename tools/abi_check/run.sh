#!/bin/bash
# 字节码 ABI 自检（配套 tools/abi_check/check.py）。
#
# 用在「构建成功之后、装机之前」：Kotlin 增量编译 + Gradle build cache 可能产出
# 接口/实现描述符错配的 class，编译期零提示，运行时才 AbstractMethodError 闪退。
# 实测过一次（编辑器一打开就崩），详见 check.py 顶部注释与 docs/JXAU-OJ-App-功能方案.md。
set -e

ROOT="D:/IO/HTML/Hydro"
JAVAP="D:/IO/jdk17/bin/javap.exe"
PY="C:/Users/23836/.workbuddy/binaries/python/versions/3.13.12/python.exe"

CLASSES="${1:-$ROOT/android-app/app/build/tmp/kotlin-classes/debug}"
PKG="${2:-com.jxau.oj}"

if [ ! -d "$CLASSES" ]; then
  echo "!! 没找到编译产物目录：$CLASSES"
  echo "   先跑一次 :app:compileDebugKotlin（或 assembleDebug）再来。"
  exit 2
fi

JAVAP="$JAVAP" "$PY" "$ROOT/tools/abi_check/check.py" "$CLASSES" "$PKG"
