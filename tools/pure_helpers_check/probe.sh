#!/bin/bash
# 变异测试探针：人为改坏被测实现，验证自检**确实抓得住**。
#
# 为什么需要它：自检全绿可能是「实现正确」，也可能是「用例没有判别力」——
# 后者更危险，因为它给的是虚假安全感。本项目真的踩过：某次补的用例里，
# 把实现改坏后 412 项**照样全过**。
#
# 三条血泪规矩：
#  1) 每一步硬校验 md5：起始必须等于原版、变异后必须变、还原后必须回到原版。
#     否则「变异根本没落盘」会被误读成「自检没牙」，反过来把好用例删掉。
#  2) **判据一律用 ASCII**（PASS / FAIL / error:），绝不用中文字符串做 grep 模式。
#     本脚本曾按 GBK 落盘，脚本里的「通过」二字变成 GBK 字节，永远匹配不上
#     UTF-8 的节目输出 —— 结果 5 次探针全被误报成「编译失败」。
#     同理，**变异用的锚点字符串也尽量只用 ASCII** —— 免得再被编码问题咬一口。
#  3) **空变异要自己先推演掉**。例：把 inlineRegex 里 `*斜*` 与 `**粗**` 两支对调，
#     看着像大改，其实两支在任一起始位置上互斥（第二字符一个要求非 `*`、一个要求 `*`），
#     顺序可证无关 —— 它永远不会被任何断言抓住，别写进探针充当「覆盖」。
#
# 用法：bash tools/pure_helpers_check/probe.sh [变异名...]
#      不给参数就跑全部。会临时改动工作区源码，每条用完立刻还原并校验。
set -u

# ⚠️ ROOT 用**字面量** Windows 路径，不用 `$(cd ... && pwd)` —— 后者在 Git Bash 里
# 会给出 `/d/IO/...` 这种 MSYS 路径，Windows 的 python 解析不了（FileNotFoundError）。
ROOT="D:/IO/HTML/Hydro"
SRC_MD="$ROOT/android-app/app/src/main/java/com/jxau/oj/ui/component/MarkdownParser.kt"
SRC_FONTS="$ROOT/android-app/app/src/main/java/com/jxau/oj/ui/theme/EditorFonts.kt"
TMP="$ROOT/.workbuddy/tmp/mut"
mkdir -p "$TMP"

md5() { md5sum "$1" | cut -d' ' -f1; }

ALL_MD="A-heading-level B-no-bold-alt C-keep-bold-markers D-depth-div4 E-divider-too-loose F-no-formula-alt"
ALL_FONT="G-dup-font-id H-notice-shared I-default-not-first J-system-credit"
ALL="$ALL_MD $ALL_FONT"

SEL="${*:-$ALL}"
MD_SEL=""; FONT_SEL=""; PRE=0
for m in $SEL; do
  case " $ALL_MD " in *" $m "*) MD_SEL="$MD_SEL $m"; continue;; esac
  case " $ALL_FONT " in *" $m "*) FONT_SEL="$FONT_SEL $m"; continue;; esac
  echo "!! unknown mutation: $m"; PRE=1
done

# 变异 1：MarkdownParser.kt（题面渲染的块级/行内解析）
mutate_md() {
  python - "$1" "$SRC_MD" <<'PY'
import io, sys
name, path = sys.argv[1], sys.argv[2]
s = io.open(path, encoding="utf-8").read()

CASES = {
    # heading level off by one (block classification)
    "A-heading-level": [
        "MdBlock.Heading(heading.groupValues[1].length,",
        "MdBlock.Heading(heading.groupValues[1].length + 1,",
    ],
    # drop the bold alternative (REAL change: `**x**` degrades to italic + leaked stars)
    "B-no-bold-alt": [
        '        """(\\*\\*[^*]+\\*\\*)""" + "|" +\n',
        "",
    ],
    # bold keeps its markers instead of dropping them
    "C-keep-bold-markers": [
        """                SpanStyle(fontWeight = FontWeight.Bold),
            ) { append(token.trim('*')) }""",
        """                SpanStyle(fontWeight = FontWeight.Bold),
            ) { append(token) }""",
    ],
    # list depth divisor 2 -> 4
    "D-depth-div4": [
        "depth = bulletItem.groupValues[1].length / 2,",
        "depth = bulletItem.groupValues[1].length / 4,",
    ],
    # divider regex loosened (swallows lists and paragraphs)
    "E-divider-too-loose": [
        'private val dividerRegex = Regex("""^\\s*([-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$""")',
        'private val dividerRegex = Regex("""^\\s*[-*_].*$""")',
    ],
    # formula branch no longer excludes newlines (would eat across lines)
    "F-no-formula-alt": [
        '        """(\\$[^$\\n]+\\$)""",',
        '        """(\\$[^$]+\\$)""",',
    ],
}

if name not in CASES:
    sys.stderr.write("UNKNOWN-MUTATION: " + name + "\n")
    raise SystemExit(3)
old, new = CASES[name]
if old not in s:
    sys.stderr.write("MUTATION-PATTERN-NOT-FOUND: " + name + "\n")
    raise SystemExit(2)
io.open(path, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

# 变异 2：EditorFonts.kt（字体清单 —— V 组与 run.sh 的字体资源核对共同守它）
# 四个锚点**全 ASCII**，改的都是"清单自身的自洽性"，不碰字体文件本身。
mutate_fonts() {
  python - "$1" "$SRC_FONTS" <<'PY'
import io, sys
name, path = sys.argv[1], sys.argv[2]
s = io.open(path, encoding="utf-8").read()

CASES = {
    # 两款字体共用一个 id：firstOrNull{} 会把 fira-code 的偏好悄悄映射到 jetbrains-mono
    "G-dup-font-id": [
        'id = "fira-code",',
        'id = "jetbrains-mono",',
    ],
    # fira-code 的 credit 指向别人的声明文件：等于这款字体随包没有自己的许可
    "H-notice-shared": [
        "res/raw/fira_code_notice.txt",
        "res/raw/jetbrains_mono_notice.txt",
    ],
    # 默认字体改成第二项：老用户一升级，编辑器字体就自己变了
    "I-default-not-first": [
        'const val DEFAULT_EDITOR_FONT_ID = "system-mono"',
        'const val DEFAULT_EDITOR_FONT_ID = "cascadia-mono"',
    ],
    # 给系统字体也挂个 credit：系统字体不随包分发，本不该有署名
    "J-system-credit": [
        '        id = "system-mono",\n',
        '        id = "system-mono",\n        credit = "x",\n',
    ],
}

if name not in CASES:
    sys.stderr.write("UNKNOWN-MUTATION: " + name + "\n")
    raise SystemExit(3)
old, new = CASES[name]
if old not in s:
    sys.stderr.write("MUTATION-PATTERN-NOT-FOUND: " + name + "\n")
    raise SystemExit(2)
io.open(path, "w", encoding="utf-8").write(s.replace(old, new, 1))
PY
}

run_group() {  # $1 = 源码路径  $2 = mutate 函数名  $3.. = 变异名
  local path="$1" fn="$2"; shift 2
  [ $# -eq 0 ] && return 0
  local bak="$TMP/$(basename "$path").orig"
  cp "$path" "$bak"
  local orig
  orig=$(md5 "$bak")
  echo "-- $(basename "$path")  orig md5 = $orig"
  for M in "$@"; do
    if ! "$fn" "$M"; then
      echo "== $M: PROBE-BUG (pattern not found) -- not a checker problem"
      STATUS=1; continue
    fi
    if [ "$(md5 "$path")" = "$orig" ]; then
      echo "== $M: PROBE-BUG (mutation not applied -- null mutation?)"
      STATUS=1; continue
    fi

    OUT=$(bash "$ROOT/tools/pure_helpers_check/run.sh" 2>&1)
    ERRS=$(printf '%s' "$OUT" | grep -ac "error:")
    NF=$(printf '%s' "$OUT" | grep -ac "^  FAIL")
    NP=$(printf '%s' "$OUT" | grep -ac "^  PASS")
    if [ "$ERRS" -gt 0 ]; then
      echo "== $M: BUILD-FAILED ($ERRS errors) -- probe itself is broken"
      printf '%s' "$OUT" | grep -a "error:" | head -2 | sed 's/^/     /'
      STATUS=1
    elif [ "$NF" -gt 0 ]; then
      echo "== $M: CAUGHT (PASS=$NP FAIL=$NF)"
      printf '%s' "$OUT" | grep -a "^  FAIL" | head -3 | sed 's/^/     /'
    else
      echo "== $M: *** NOT CAUGHT *** (PASS=$NP FAIL=0) -- checker has no teeth here"
      STATUS=1
    fi

    cp "$bak" "$path"
    if [ "$(md5 "$path")" != "$orig" ]; then
      echo "ABORT: restore failed, workspace left mutated"; exit 9
    fi
  done
}

STATUS=$PRE
run_group "$SRC_MD" mutate_md $MD_SEL
run_group "$SRC_FONTS" mutate_fonts $FONT_SEL

echo
echo "== restored, re-run =="
bash "$ROOT/tools/pure_helpers_check/run.sh" 2>&1 | tail -4
for p in "$SRC_MD" "$SRC_FONTS"; do
  if ! cmp -s "$p" "$TMP/$(basename "$p").orig"; then
    echo "ABORT: workspace mismatch after restore: $p"; exit 9
  fi
done
echo "final md5 ok (both sources unchanged)"
exit $STATUS
