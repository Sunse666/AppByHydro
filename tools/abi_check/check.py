#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""字节码 ABI 自检：接口声明的抽象方法，实现类里必须有一模一样的方法描述符。

为什么需要这个：Kotlin/Gradle 的**增量编译**在改动 @Composable 接口签名时，可能只重编实现类、
不重编接口附近的 `ComposeDefaultImpls`（或反过来），产出「接口 `(…,Composer,int)` / 实现
`(…,Composer,int,int)`」这种错配的 class。这种错配：
  - 编译期**零错误零警告**，`BUILD SUCCESSFUL`；
  - `clean` 也修不好 —— 坏产物已被 Gradle build cache 缓存，`clean` 后按 `FROM-CACHE` 原样还原；
  - 直到运行时才在**具体那个界面**抛 `AbstractMethodError` 崩掉，其它页面一切正常。

实测代价：JXAU OJ 的「去写代码」按钮一点就闪退，连续多轮重建 APK 都没发现，
因为每次验证只看了构建输出和题库渲染。

所以：**构建完跑一次本脚本**。它只比对我们自己包（默认 com.jxau.oj）里出现的
「接口 → 实现类」配对，命中就非零退出并打印缺失的方法与两侧描述符。

用法：
    python check.py <classes 根目录> [包名前缀]
    # 例：python check.py android-app/app/build/tmp/kotlin-classes/debug
"""

import os
import re
import subprocess
import sys
from collections import defaultdict

JAVAP = os.environ.get("JAVAP", "javap")

CLASS_DECL = re.compile(
    r"^(?!\s)(?:public|final|abstract|static|\s)*"
    r"(class|interface|enum)\s+([\w.$]+)"
    r"(?:\s+extends\s+([\w.$]+))?"
    r"(?:\s+implements\s+([\w.$,\s]+))?",
)
METHOD = re.compile(r"^\s{2}.*?([\w$<>]+)\((.*)\);\s*$")
DESC = re.compile(r"^\s+descriptor:\s*(\S+)\s*$")


def binary_name(root, path):
    rel = os.path.relpath(path, root)
    return rel[:-len(".class")].replace(os.sep, ".")


def javap_dump(root, names):
    """一次 javap 批量反汇编；返回 name -> 原始文本。"""
    out = {}
    BATCH = 200
    for i in range(0, len(names), BATCH):
        chunk = names[i:i + BATCH]
        proc = subprocess.run(
            [JAVAP, "-p", "-s", "-classpath", root] + chunk,
            capture_output=True, text=True, encoding="utf-8", errors="replace",
        )
        text = proc.stdout or ""
        cur = None
        for line in text.splitlines():
            m = re.match(r"^(?:public|final|abstract|static|\s)*(?:class|interface|enum)\s+([\w.$]+)", line)
            if m and line.startswith(("public", "final", "abstract", "class", "interface", "enum")):
                cur = m.group(1)
                out.setdefault(cur, [])
                out[cur].append(line)
            elif cur is not None:
                out[cur].append(line)
    return {k: "\n".join(v) for k, v in out.items()}


def parse(dump):
    """返回 (kind, super_class, interfaces, methods)。

    ⚠️ `extends` 与 `implements` 必须**分开**存 —— 早先版本把两者并成一个 supers 列表，
    结果 `methods_with_inherited()` 顺着链走进了接口，把「接口自己声明的方法」也算成
    「实现类已声明的方法」，于是永远不报错（自检形同虚设）。这是本脚本自己的判别力用例
    （人为造一对错配字节码）抓出来的。
    """
    lines = dump.splitlines()
    kind, super_class, interfaces = None, None, []
    for line in lines:
        m = CLASS_DECL.match(line)
        if m and ("class " in line or "interface " in line or "enum " in line):
            kind = m.group(1)
            if m.group(3):
                super_class = m.group(3)
            if m.group(4):
                interfaces = [s.strip() for s in m.group(4).split(",") if s.strip()]
            break
    methods = set()
    pending = None
    for line in lines:
        dm = DESC.match(line)
        if dm and pending:
            methods.add((pending[0], dm.group(1)))
            pending = None
            continue
        mm = METHOD.match(line)
        if mm and not line.strip().startswith(("descriptor:", "Compiled from")):
            pending = (mm.group(1), mm.group(2))
    return kind, super_class, interfaces, methods


def main():
    if len(sys.argv) < 2:
        print("用法: python check.py <classes 根目录> [包名前缀]")
        return 2
    root = sys.argv[1]
    prefix = sys.argv[2] if len(sys.argv) > 2 else "com.jxau.oj"
    if not os.path.isdir(root):
        print("!! 目录不存在:", root)
        return 2

    all_classes = []
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith(".class"):
                all_classes.append(os.path.join(dirpath, f))
    names = sorted(binary_name(root, p) for p in all_classes)
    ours = [n for n in names if n.startswith(prefix)]
    if not ours:
        print("!! 在 %s 下没找到 %s 的 class" % (root, prefix))
        return 2

    # 检查实现类时要顺着父类链找方法，所以把父类也一起反汇编
    dump = javap_dump(root, names)
    parsed = {n: parse(d) for n, d in dump.items()}
    kind_of = {n: parsed[n][0] for n in parsed}
    super_of = {n: parsed[n][1] for n in parsed}
    ifaces_of = {n: parsed[n][2] for n in parsed}
    methods_of = {n: parsed[n][3] for n in parsed}

    interfaces = {n for n in parsed if kind_of.get(n) == "interface"}

    def methods_with_inherited(name, seen=None):
        """类 + 其**父类链**声明的方法集合。只沿 extends 走，绝不进接口 —— 见 parse() 的注释。"""
        seen = seen or set()
        if name in seen or name not in methods_of:
            return set()
        seen.add(name)
        got = set(methods_of[name])
        sup = super_of.get(name)
        if sup and kind_of.get(sup) == "class":
            got |= methods_with_inherited(sup, seen)
        return got

    problems = []
    for cls in ours:
        if kind_of.get(cls) != "class":
            continue
        for sup in ifaces_of.get(cls, []):
            if sup not in interfaces:
                continue
            declared = methods_with_inherited(cls)
            for (mname, mdesc) in sorted(methods_of[sup]):
                if mname.endswith("$default") or mname in ("<init>", "<clinit>"):
                    continue
                if (mname, mdesc) not in declared:
                    problems.append((cls, sup, mname, mdesc, declared))

    print("== ABI 自检 ==  目录 %s  包前缀 %s" % (root, prefix))
    print("   扫描 %d 个 class（其中本包 %d 个，接口 %d 个）"
          % (len(names), len(ours), len({n for n in interfaces if n.startswith(prefix)})))
    if not problems:
        print("   OK：%s 下所有「接口 → 实现类」的方法描述符一致" % prefix)
        return 0
    print("   !! 发现 %d 处 ABI 错配（编译期不会报，运行时才 AbstractMethodError）:" % len(problems))
    for cls, sup, mname, mdesc, declared in problems:
        print("     - %s 未实现 %s.%s" % (cls, sup, mname))
        print("       接口描述符: %s" % mdesc)
        same = sorted(d for (n, d) in declared if n == mname)
        print("       实现类同名方法: %s" % (same or "（没有同名方法）"))
    return 1


if __name__ == "__main__":
    sys.exit(main())
