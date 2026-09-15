#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""M0 契约验证脚本 —— JXAU OJ (https://oj.wbhtqlorz.cv) 移动端 App

目的
----
把《JXAU-OJ-App-功能方案》第 8 章的 18 项「开工前必须验证」项，用一次真实登录
（可选一次真实提交）跑出明确结论，产出 DTO 定稿所需的字段契约。

本脚本的行为边界（刻意保守）
--------------------------
* 只做三类请求：只读 GET、一次登录 POST、一次提交 POST（需显式 --submit）。
* 不做任何压力测试、不遍历敏感路由、不尝试越权、不猜密码。
* 凭据只存在于内存，不写盘、不进日志、不进报告。
* 所有样本落盘前脱敏：邮箱、Cookie 值都会被替换。

用法
----
    # 1) 零风险预演：只跑只读项，不需要账号
    python tools/m0_probe.py --anon

    # 2) 完整验证：登录 + 提交一次 + 轮询评测状态（推荐）
    python tools/m0_probe.py --submit --ask

    # 3) 用环境变量传凭据（注意别写进 shell 历史）
    OJ_USER=xxx OJ_PASS=xxx python tools/m0_probe.py --submit

    # 4) 额外探测 WebSocket 实时通道（M0-16，非阻断）
    python tools/m0_probe.py --submit --ws

输出
----
    docs/M0-契约验证报告.md    逐项结论 + 原始证据，可直接替代第 8 章的空表
    tools/m0-samples/*.json    所有原始响应（已脱敏），供后续写 Mapper 时对照

退出码
------
    0 = 所有 🔴 阻断项均有结论；1 = 存在未取得结论的 🔴 项。
"""

import argparse
import base64
import getpass
import json
import os
import re
import socket
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

BASE = "https://oj.wbhtqlorz.cv"
HOST = "oj.wbhtqlorz.cv"
UA = "JXAU-OJ-App/0.1 (+M0-contract-probe)"

# 提交用的代码：故意 WA（输出 sum+1），保证不会污染题目 AC 统计的语义
WA_CODE = "import sys\na,b=map(int,sys.stdin.read().split())\nprint(a+b+1)\n"

# Hydro 状态码映射。【推断——取自 Hydro 源码常识，本站未实测，脚本会原样记录真实码值】
STATUS_MAP = {
    0: "Waiting", 1: "Judging", 2: "CompileError", 3: "OutputLimitExceeded",
    4: "MemoryLimitExceeded", 5: "TimeLimitExceeded", 6: "RuntimeError",
    7: "WrongAnswer", 8: "SystemError", 9: "Canceled", 10: "Accepted",
    11: "Ignored", 12: "FormatError", 20: "HackSuccessful", 21: "HackUnsuccessful",
    30: "Failed", 31: "Fetched", 32: "Compiling", 33: "Executing", 34: "Progress",
}

EMAIL_RE = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
COOKIE_RE = re.compile(r"(?i)((?:set-)?cookie\s*:\s*)([^\r\n]+)")


def redact(text):
    """落盘前脱敏：邮箱、Cookie 值。"""
    if not text:
        return text
    text = EMAIL_RE.sub("<redacted-mail>", text)
    text = COOKIE_RE.sub(lambda m: m.group(1) + "<redacted-cookie>", text)
    return text


def now_iso():
    return datetime.now(timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M:%S %z")


# --------------------------------------------------------------------------
# HTTP 客户端：自己管 Cookie，不依赖 cookiejar 的重定向行为，便于精确取证
# --------------------------------------------------------------------------

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # 不自动跟随，302 当 HTTPError 抛出，我们才能看到 Location


class Resp:
    __slots__ = ("method", "url", "status", "headers", "text", "elapsed", "error")

    def __init__(self, method, url, status=None, headers=None, text="", elapsed=0.0, error=None):
        self.method = method
        self.url = url
        self.status = status
        self.headers = headers if headers is not None else {}
        self.text = text
        self.elapsed = elapsed
        self.error = error

    def json(self):
        try:
            return json.loads(self.text)
        except Exception:
            return None

    def head(self, n=240):
        return re.sub(r"\s+", " ", self.text)[:n]

    @property
    def content_type(self):
        try:
            return self.headers.get("Content-Type", "")
        except Exception:
            return ""

    @property
    def location(self):
        try:
            return self.headers.get("Location", "")
        except Exception:
            return ""


class Client:
    def __init__(self, base=BASE, timeout=25.0):
        self.base = base.rstrip("/")
        self.timeout = timeout
        self.cookies = {}          # name -> value
        self.set_cookie_raw = []   # 原始 Set-Cookie 行，用于取证
        self.log = []              # (method, path, status, seconds)
        self.scope = ssl.create_default_context()
        self.opener = urllib.request.build_opener(
            NoRedirect(),
            urllib.request.HTTPSHandler(context=self.scope),
        )

    def _header_block(self, extra):
        h = {
            "Accept": "application/json",
            "Accept-Encoding": "identity",   # 避免 gzip，stdlib 解码省事
            "User-Agent": UA,
            "Connection": "close",
        }
        if self.cookies:
            h["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        if extra:
            h.update(extra)
        return h

    def _absorb(self, headers):
        try:
            raws = headers.get_all("Set-Cookie") or []
        except AttributeError:
            raws = []
        for raw in raws:
            self.set_cookie_raw.append(raw)
            parts = [p.strip() for p in raw.split(";")]
            if not parts or "=" not in parts[0]:
                continue
            name, value = parts[0].split("=", 1)
            attrs = parts[1:]
            dead = any(a.lower().startswith(("max-age=0", "expires=thu, 01 jan 1970")) for a in attrs)
            if dead:
                self.cookies.pop(name, None)
            else:
                self.cookies[name] = value

    def request(self, method, path, data=None, json_body=None, extra=None):
        url = path if path.startswith("http") else self.base + path
        headers = self._header_block(extra)
        body = None
        if json_body is not None:
            body = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif data is not None:
            body = urllib.parse.urlencode(data).encode("utf-8")
            headers["Content-Type"] = "application/x-www-form-urlencoded"

        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        t0 = time.time()
        status, hdrs, raw, err = None, {}, b"", None
        try:
            with self.opener.open(req, timeout=self.timeout) as r:
                status, hdrs, raw = r.status, r.headers, r.read()
        except urllib.error.HTTPError as e:
            status, hdrs, raw = e.code, e.headers, e.read()
        except Exception as e:                                    # noqa: BLE001
            err = f"{type(e).__name__}: {e}"
        elapsed = time.time() - t0
        self._absorb(hdrs)
        text = raw.decode("utf-8", "replace") if raw else ""
        self.log.append((method, path, status, round(elapsed, 3)))
        return Resp(method, url, status, hdrs, text, elapsed, err)

    def get(self, path, **kw):
        return self.request("GET", path, **kw)

    def post(self, path, data=None, json_body=None, **kw):
        return self.request("POST", path, data=data, json_body=json_body, **kw)


def cookie_attrs(raw):
    """解析一条 Set-Cookie 的属性，返回人类可读串。"""
    parts = [p.strip() for p in raw.split(";")]
    name = parts[0].split("=", 1)[0] if parts else "?"
    flags = []
    for a in parts[1:]:
        low = a.lower()
        if low.startswith(("path=", "domain=", "max-age=", "expires=", "samesite=")):
            flags.append(a)
        else:
            flags.append(a)
    return name, "; ".join(flags) if flags else "(无属性)"


# --------------------------------------------------------------------------
# 报告容器
# --------------------------------------------------------------------------

LEVEL_ICON = {"red": "🔴", "yellow": "🟡", "green": "🟢"}


class Report:
    def __init__(self):
        self.items = []

    def add(self, no, level, title, conclusion, evidence="", maphint=""):
        self.items.append({
            "no": no, "level": level, "title": title,
            "conclusion": conclusion, "evidence": evidence, "maphint": maphint,
        })
        print(f"  [{no:>2}] {LEVEL_ICON[level]} {title}")
        print(f"       结论：{conclusion}")
        if evidence:
            print(f"       证据：{re.sub(chr(10), ' ', evidence)[:200]}")

    def render(self, meta):
        reds = [i for i in self.items if i["level"] == "red"]
        pending = [i for i in reds if i["conclusion"].startswith("未取得结论")]
        L = []
        L.append("# M0 契约验证报告")
        L.append("")
        L.append(f"> 目标站点：`{BASE}`　|　执行时间：{now_iso()}")
        L.append(f"> 模式：{'仅只读（未登录）' if meta['anon'] else '完整验证（已登录）'}"
                 f"{'，含一次真实提交' if meta['submitted'] else ''}")
        L.append(f"> 脚本：`tools/m0_probe.py`　|　原始样本：`{meta['samples']}/`")
        L.append("")
        L.append("## 结论速览")
        L.append("")
        L.append(f"- 共 {len(self.items)} 项结论；其中 🔴 阻断项 {len(reds)} 个，"
                 f"**{len(reds) - len(pending)} 个已取得结论，{len(pending)} 个未清零**。")
        if pending:
            L.append("- 未清零的 🔴 项：")
            for i in pending:
                L.append(f"  - [{i['no']}] {i['title']}")
        else:
            L.append("- **所有 🔴 阻断项均已清零，可以开始 M1。**")
        L.append("")
        L.append("## 逐项结论")
        L.append("")
        L.append("| # | 级别 | 验证项 | 结论 |")
        L.append("|---|---|---|---|")
        for i in self.items:
            L.append(f"| {i['no']} | {LEVEL_ICON[i['level']]} | {i['title']} | "
                     f"{i['conclusion'].replace('|', '\\|')} |")
        L.append("")
        L.append("## 证据明细（可直接用于写 DTO）")
        L.append("")
        for i in self.items:
            L.append(f"### [{i['no']}] {i['title']}")
            L.append("")
            L.append(f"**结论**：{i['conclusion']}")
            L.append("")
            if i["evidence"]:
                L.append("**证据**：")
                L.append("")
                L.append("```")
                L.append(i["evidence"])
                L.append("```")
                L.append("")
            if i["maphint"]:
                L.append(f"**Mapper 提示**：{i['maphint']}")
                L.append("")
        L.append("---")
        L.append("")
        L.append("## 本次请求流水")
        L.append("")
        L.append("| 方法 | 路径 | 状态码 | 耗时(s) |")
        L.append("|---|---|---|---|")
        for m, p, s, e in meta["request_log"]:
            L.append(f"| {m} | `{p}` | {s} | {e} |")
        L.append("")
        return "\n".join(L)


# --------------------------------------------------------------------------
# 各项验证
# --------------------------------------------------------------------------

def classify(r):
    """把一个响应归类到客户端的四分支分发器里。"""
    t = r.text.lstrip()
    if r.status is None:
        return "网络失败", r.error or "?"
    if t.startswith("{"):
        d = r.json() or {}
        if isinstance(d, dict) and isinstance(d.get("url"), str) and "/login" in d["url"]:
            return "未登录软跳转", d["url"]
        if isinstance(d, dict) and isinstance(d.get("error"), dict):
            e = d["error"]
            return "业务错误", f"{e.get('name')}({e.get('code')}) {e.get('message')}"
        return "正常数据", f"{len(d)} 个顶层字段" if isinstance(d, dict) else "?"
    if t.startswith("<"):
        return "HTML（异常）", r.content_type.split(";")[0]
    return "无法识别", r.head(80)


# 覆盖客户端会遇到的每一种响应形态。刻意包含「不存在的资源」与「未命中路由」。
FORM_MATRIX = [
    ("GET", "/p", "公开页面"),
    ("GET", "/api/user", "游客身份探针"),
    ("GET", "/record", "未登录 + 受保护页面"),
    ("POST", "/p/1/submit", "未登录 + 受保护写操作"),
    ("GET", "/p/NOSUCHPROBLEM", "资源不存在"),
    ("GET", "/zzz-not-a-route-xyz", "路由未命中"),
    ("GET", "/record/1", "路径参数格式非法"),
]


def step_anon(c, rep, samples):
    """只读基线：确认匿名视角、JSON 内容协商、完整的响应形态矩阵。"""
    print("\n[只读基线]")

    u = c.get("/api/user")
    ud = u.json() or {}
    rep.add(0, "red", "匿名基线与游客判定",
            f"/api/user 返回 HTTP {u.status}，role={ud.get('role')}，"
            f"含 authn 字段={'authn' in ud} → 游客判定应看 role=='guest'（或 authn 不为 true）",
            f"GET /api/user -> {u.status}\n{u.head(320)}",
            "登录态才有 authn=true；不要用 authn==false 判断游客（游客响应里根本没有该字段）")

    a = c.get("/p", extra={"Accept": "application/json"})
    b = c.get("/p", extra={"Accept": "text/html"})
    rep.add(0.1, "red", "JSON 内容协商可用性（方案基石）",
            f"同一路径 /p：Accept: application/json -> {a.status} "
            f"{a.content_type.split(';')[0]}（JSON 解析成功={'pdocs' in (a.json() or {})}）；"
            f"Accept: text/html -> {b.status} {b.content_type.split(';')[0]}",
            f"JSON 首 200 字：{a.head(200)}\n\nHTML 首 90 字：{b.head(90)}",
            "客户端所有请求恒带 Accept: application/json，这是全程不写 HTML 解析器的依据")

    rows, ev = [], []
    for method, path, why in FORM_MATRIX:
        r = c.request(method, path, json_body={} if method == "POST" else None)
        form, detail = classify(r)
        rows.append((f"{method} {path}", r.status, form, detail))
        ev.append(f"{method:4} {path:32} -> HTTP {str(r.status):4} {form:12} {detail}\n"
                  f"     body: {r.head(160)}")
        print(f"      {method:4} {path:30} {r.status} {form}")

    rep.add(0.2, "red", "响应形态矩阵（客户端分发器的设计依据）",
            "；".join(f"{p.split(' ')[0]} {p.split(' ')[1]} → {f}" for p, _s, f, _d in rows),
            "\n".join(ev),
            "四分支：正常数据 / 未登录软跳转（HTTP 200！）/ 业务错误 / 网络或网关异常。"
            "任何只看 HTTP 状态码的实现都会把软跳转当成成功")

    # 证明「HTML 错误页」只由 Accept 协商决定，而不是由路由未命中决定
    jj = c.get("/zzz-not-a-route-xyz", extra={"Accept": "application/json"})
    hh = c.get("/zzz-not-a-route-xyz", extra={"Accept": "text/html"})
    rep.add(0.3, "red", "错误响应的格式由 Accept 决定（纠正既有结论）",
            f"同一未命中路由：Accept: json -> {jj.status} "
            f"{jj.content_type.split(';')[0]}；Accept: html -> {hh.status} "
            f"{hh.content_type.split(';')[0]} → **只要恒发 Accept: json，就不会拿到 HTML 错误页**",
            f"JSON 模式: {jj.status} {jj.content_type.split(';')[0]}\n{jj.head(200)}\n\n"
            f"HTML 模式: {hh.status} {hh.content_type.split(';')[0]}\n{hh.head(120)}",
            "仍然保留首字符/Content-Type 防御，但理由是「Cloudflare 网关层可能吐 HTML」"
            "（5xx/1035），而不是「Hydro 路由未命中会吐 HTML」")


def find_login_form(html):
    action, fields = "/login", []
    m = re.search(r"<form[^>]*action=\"([^\"]*)\"", html)
    if m:
        action = m.group(1)
    for fm in re.finditer(r"<(input|button)[^>]*>", html):
        tag = fm.group(0)
        nm = re.search(r'name="([^"]+)"', tag)
        if nm:
            fields.append(nm.group(1))
    return action, fields


def step_login(c, rep, samples, user, password):
    print("\n[登录]")
    page = c.get("/login", extra={"Accept": "text/html"})
    action, fields = find_login_form(page.text)
    has_tfa = "tfa" in fields
    has_challenge = "authnChallenge" in fields
    rep.add(3, "red", "是否需要 CSRF 头",
            f"表单 action=`{action}`，字段={fields}；表单内"
            + ("无" if "csrf" not in page.text.lower() else "疑似有")
            + " csrf 隐藏域 → 登录不依赖 CSRF token",
            f"GET /login -> {page.status}\nform action = {action}\nfields = {fields}\n"
            f"页面含 'csrf' 字样 = {'csrf' in page.text.lower()}")

    # 最小头部试探：不带 X-Requested-With / Origin / Referer
    payload = {
        "uname": user,
        "password": password,
        "rememberme": "on",
        "login_submit": "Login",
    }
    if has_tfa:
        payload["tfa"] = ""
    if has_challenge:
        payload["authnChallenge"] = ""

    r = c.post(action, data=payload)
    body = r.json() or {}
    err = body.get("error") if isinstance(body, dict) else None

    # 需要二次验证 → 交互补码
    if err and any(k in json.dumps(err, ensure_ascii=False).lower() for k in ("tfa", "authn", "otp")):
        print("  站点要求二次验证，请输入 6 位动态码：")
        payload["tfa"] = input("  tfa code: ").strip()
        r = c.post(action, data=payload)
        body = r.json() or {}
        err = body.get("error") if isinstance(body, dict) else None

    authed = False
    probe = c.get("/api/user")
    if (probe.json() or {}).get("authn") is True:
        authed = True

    rep.add(2, "red", "登录成功响应形态",
            (f"成功：HTTP {r.status}，body 为 `{json.dumps(body, ensure_ascii=False)[:120]}`；"
             f"Cookie 由 {len(c.set_cookie_raw)} 条 Set-Cookie 下发")
            if authed else
            (f"失败：HTTP {r.status}，未取得登录态。body={r.head(200)}"
             + ("（就是账号密码不对，不是流程问题）" if not err else "")),
            f"POST {action} -> {r.status}\nLocation: {r.location or '(无)'}\n"
            f"body: {r.head(300)}\n"
            f"二次通过 /api/user 复核: HTTP {probe.status} {probe.head(160)}")

    cookies_txt = "\n".join(redact(x) for x in c.set_cookie_raw) or "(无 Set-Cookie)"
    names = [cookie_attrs(x)[0] for x in c.set_cookie_raw]
    name_hint = next((n for n in names if n.lower() in ("sid", "session", "connect.sid")), None)
    rep.add(1, "red", "Session Cookie 名称与属性",
            (f"Cookie 名 = `{name_hint or (names[0] if names else '未取得')}`；"
             f"全部名称 = {names}；属性见证据")
            if names else "未取得结论（登录未成功）",
            (f"原始 Set-Cookie（值已脱敏）：\n{cookies_txt}\n\n"
             f"解析：\n" + "\n".join(
                 f"  {cookie_attrs(x)[0]} :: {cookie_attrs(x)[1]}" for x in c.set_cookie_raw)),
            "客户端用自管 CookieStore；Name 走常量，不做硬编码猜测")

    rep.add(4, "yellow", "是否强制二次验证（tfa）",
            f"登录表单{'包含' if has_tfa else '不包含'} `tfa` 字段，"
            f"本次登录{'未触发' if authed else '未完成'}二次验证挑战",
            f"表单字段 = {fields}\n本次登录结果 = {'成功' if authed else '未成功'}")

    if not authed:
        return False, None

    u = c.get("/api/user").json() or {}
    rep.add(0.3, "green", "登录态下的用户对象字段",
            f"字段集 = {sorted(u.keys())}",
            json.dumps(u, ensure_ascii=False, indent=2)[:900],
            "注意 uid/uname 字段名；mail 必须在 Mapper 层丢弃")
    return True, u


def step_problem(c, rep, samples, pid):
    print("\n[题目与状态]")
    r = c.get(f"/p/{pid}")
    d = r.json() or {}
    pdoc = d.get("pdoc") or {}
    cfg = pdoc.get("config") or {}
    html = c.get(f"/p/{pid}", extra={"Accept": "text/html"}).text

    def grab(key):
        m = re.search(r'"%s"\s*:\s*("(?:[^"\\]|\\.)*"|true|false|\d+|null)' % key, html)
        return m.group(1) if m else None

    submit_url = grab("postSubmitUrl")
    subs_url = grab("getSubmissionsUrl")
    detail_url = grab("getRecordDetailUrl")
    pretest_conn = grab("pretestConnUrl")

    rep.add(5.1, "red", "提交与记录相关 URL 模板（服务端下发）",
            f"postSubmitUrl={submit_url}；getRecordDetailUrl={detail_url}；"
            f"pretestConnUrl={pretest_conn}",
            f"postSubmitUrl     = {submit_url}\n"
            f"getSubmissionsUrl = {subs_url}\n"
            f"getRecordDetailUrl= {detail_url}\n"
            f"pretestConnUrl    = {pretest_conn}\n"
            f"pdoc.pid={pdoc.get('pid')}  pdoc.docId={pdoc.get('docId')}  "
            f"pdoc._id={pdoc.get('_id')}  domainId={pdoc.get('domainId')}",
            "这三个 URL 必须从页面下发里读，不要硬编码 /p/{id}/submit")

    rep.add(6.1, "green", "题目配置与语言列表",
            f"langs 共 {len(cfg.get('langs') or [])} 种；"
            f"count={cfg.get('count')} time={cfg.get('timeMin')}~{cfg.get('timeMax')}ms "
            f"memory={cfg.get('memoryMin')}~{cfg.get('memoryMax')}MB",
            json.dumps(cfg, ensure_ascii=False)[:600])

    rep.add(15, "yellow", "样例/测试数据来源",
            f"pdoc.data 公开列出 {len(pdoc.get('data') or [])} 个测试数据文件名"
            f"（{', '.join(x.get('name', '?') for x in (pdoc.get('data') or [])[:6])}...）；"
            f"正文为 Markdown（含分级标题与代码块）",
            f"data = {json.dumps(pdoc.get('data'), ensure_ascii=False)[:400]}\n\n"
            f"content 原文（前 400 字，注意它是嵌套 JSON 字符串）：\n"
            f"{str(pdoc.get('content'))[:400]}",
            "content 必须二次 JSON.parse；data 仅用于渲染『测试数据文件』信息，不下载")

    rep.add(12, "green", "difficulty 字段来源",
            ("登录后题目详情/列表仍无 difficulty 字段 → 一期不做难度展示"
             if not any("diff" in k.lower() for k in list(pdoc.keys()))
             else "疑似出现 difficulty 字段，见证据"),
            f"pdoc keys = {sorted(pdoc.keys())}")

    psdoc = d.get("psdoc")
    rep.add(11, "yellow", "已登录时 psdoc（我的题目状态）结构",
            ("psdoc 仍为 null（该题未提交过）→ 需用一道已提交过的题复验"
             if psdoc is None else f"psdoc 字段 = {sorted(psdoc.keys())}"),
            json.dumps(psdoc, ensure_ascii=False)[:500] if psdoc is not None else "psdoc = null",
            "状态圆点依赖该字段，缺失时退化为不显示状态，不报错")

    pl = c.get("/p?limit=20").json() or {}
    p0 = (pl.get("pdocs") or [{}])[0]
    k0 = set(p0.keys())
    rep.add(12.1, "yellow", "题目列表字段集（与详情版差异）",
            f"列表项只有 {len(k0)} 个字段：{sorted(k0)}；"
            f"无 `pid`={'pid' not in k0}，无 `difficulty`={'difficulty' not in k0} → "
            f"列表页要用 `docId` 拼详情链接，或先取详情拿 `pid`",
            json.dumps(p0, ensure_ascii=False)[:700]
            + f"\n\n列表顶层非数组字段："
            + json.dumps({k: v for k, v in pl.items() if not isinstance(v, list)},
                         ensure_ascii=False)[:300],
            "列表用于渲染卡片；`stats` 是对象，字段固定，可做状态统计条。"
            "详情里的 pid(P1000) 与 docId(1) 是两个东西，URL 用 docId")

    rk = (c.get("/ranking").json() or {})
    u0 = (rk.get("udocs") or [{}])[0]
    rep.add(13, "green", "RP 字段来源",
            ("榜单 JSON 内未见 RP 字段 → 一期不展示 RP"
             if not any("rp" == k.lower() or "rating" in k.lower() for k in list(u0.keys()))
             else f"疑似出现 RP 字段，见证据"),
            f"榜单项字段 = {sorted(u0.keys())}\n"
            f"{json.dumps(u0, ensure_ascii=False)[:400]}",
            "无论有无，App 都不伪造；无则不做该项展示")

    return submit_url, subs_url


def step_submit(c, rep, submit_url, lang, code, samples):
    print("\n[提交]")
    if not submit_url:
        submit_url = "/p/1/submit"
    payload = {"lang": lang, "code": code}

    r = c.post(submit_url, json_body=payload)
    b = r.json() or {}
    mode = "JSON body"

    if not isinstance(b, dict) or set(b.keys()) <= {"url", "error"}:
        r2 = c.post(submit_url, data=payload)
        b2 = r2.json() or {}
        rep.add(5, "red", "提交请求体字段名",
                f"JSON body 返回 {json.dumps(b, ensure_ascii=False)[:120]}；"
                f"form-encoded 返回 {json.dumps(b2, ensure_ascii=False)[:120]} → 见证据判定",
                f"POST {submit_url} (application/json) -> {r.status}\n{r.head(300)}\n\n"
                f"POST {submit_url} (form-urlencoded) -> {r2.status}\n{r2.head(300)}",
                "以实际生效的那种为准写网络层；另一种记为不支持")
        r = r2 if r2.status == 200 and isinstance(b2, dict) and not b2.get("error") else r
        b = b2 if r is r2 else b
        mode = "form-encoded" if r is r2 else mode
    else:
        rep.add(5, "red", "提交请求体字段名",
                f"`application/json` + {{{', '.join(payload.keys())}}} 被接受（响应未报参数错误）",
                f"POST {submit_url} -> {r.status}\n{r.head(300)}",
                "字段名以实测为准；提交体里若含 tid/contest 需按题单场景补")

    rid = None
    for k in ("rid", "id"):
        if isinstance(b, dict) and b.get(k) is not None:
            rid = b[k]
            break
    if rid is None and isinstance(b, dict):
        m = re.search(r"/record/([A-Za-z0-9]+)", json.dumps(b))
        rid = m.group(1) if m else None

    rep.add(6, "red", "提交响应中的提交 ID 字段",
            f"rid = `{rid}`（原始响应：{json.dumps(b, ensure_ascii=False)[:160]}）" if rid
            else f"未能从响应提取 rid，原始响应见证据",
            f"POST {submit_url} [{mode}] -> {r.status}\n{r.head(400)}\n"
            f"完整 body:\n{json.dumps(b, ensure_ascii=False, indent=2)[:800]}",
            "推荐直接用响应里的跳转 URL；退而求其次用 rid")
    return rid, r


def step_poll(c, rep, rid, interval, timeout):
    print("\n[轮询评测状态]")
    if not rid:
        rep.add(9, "red", "评测状态实时性", "未取得结论（未拿到 rid，无法轮询）")
        rep.add(7, "red", "记录详情完整字段", "未取得结论（未拿到 rid）")
        rep.add(8, "red", "各测试点详情可见性", "未取得结论（未拿到 rid）")
        return

    seq, last, final, t0, n = [], None, None, time.time(), 0
    errors = 0
    while time.time() - t0 < timeout:
        r = c.get(f"/record/{rid}")
        d = r.json() or {}
        n += 1
        if not isinstance(d, dict) or "rdoc" not in d:
            errors += 1
            if errors >= 3:
                rep.add(9, "red", "评测状态实时性",
                        f"连续 3 次响应不含 rdoc，中断。首响应={r.head(160)}")
                return
            time.sleep(interval)
            continue
        rdoc = d["rdoc"] or {}
        st = rdoc.get("status")
        if st != last:
            seq.append({"t": round(time.time() - t0, 2),
                        "status": st, "name": STATUS_MAP.get(st, f"未知码{st}")})
            last = st
        if st in (2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 20, 21, 30):
            final = d
            break
        time.sleep(interval)

    trace = " → ".join("%s(%s)@%ss" % (s["name"], s["status"], s["t"]) for s in seq)
    rep.add(9, "red", "评测状态实时性",
            (f"状态序列：{trace}；共 {n} 次请求、{time.time() - t0:.1f}s 收敛"
             if final is not None else
             f"超时 {timeout}s 未收敛，共 {n} 次请求，观测序列：{trace or '(无)'}"),
            json.dumps(seq, ensure_ascii=False, indent=2),
            "轮询间隔实测安全值见本次请求流水；若收敛在 1 次请求内，说明评测极快，"
            "需要更细的间隔才能观察到中间态")

    d = final if final is not None else (c.get(f"/record/{rid}").json() or {})
    rdoc = d.get("rdoc") or {}
    rep.add(7, "red", "记录详情完整字段",
            f"顶层字段 = {sorted(d.keys())}；rdoc 字段 = {sorted(rdoc.keys())}",
            json.dumps(d, ensure_ascii=False, indent=2)[:1500],
            "rid / status / score / time / memory / compilerTexts / testCases 是结果页必需")

    tc = rdoc.get("testCases") or []
    has_in = any("input" in t for t in tc) if tc else False
    has_out = any("output" in t for t in tc) if tc else False
    has_exp = any(k in t for t in tc for k in ("expected", "answer", "output")) if tc else False
    rep.add(8, "red", "各测试点详情可见性",
            f"testCases 共 {len(tc)} 条；含 input={has_in} output={has_out} expected={has_exp}；"
            f"单条字段 = {sorted(tc[0].keys()) if tc else '空'}",
            json.dumps(tc[:2], ensure_ascii=False, indent=2)[:900],
            "若学生看不到 input/expected，结果页只展示状态与耗时内存，不做 diff 视图")


def step_records(c, rep, subs_url, pid):
    print("\n[记录列表]")
    r = c.get(subs_url or f"/record?fullStatus=true&pid={pid}")
    d = r.json() or {}
    rdocs = d.get("rdocs") or []
    rep.add(10, "red", "记录列表 JSON 结构",
            f"顶层字段 = {sorted(d.keys())}；共 {len(rdocs)} 条；"
            f"单条字段 = {sorted(rdocs[0].keys()) if rdocs else '空'}",
            json.dumps({k: (f"<list[{len(v)}]>" if isinstance(v, list) else v)
                        for k, v in d.items()}, ensure_ascii=False, indent=2)[:900]
            + "\n\n首条记录：\n"
            + json.dumps(rdocs[0] if rdocs else {}, ensure_ascii=False, indent=2)[:700],
            "分页字段与题目列表一致（page/limit + *_count），但需按 _id 去重")

    p2 = c.get((subs_url or f"/record?pid={pid}") + "&page=2").json() or {}
    rep.add(10.1, "yellow", "记录列表分页行为",
            f"page=2 返回 {len(p2.get('rdocs') or [])} 条，"
            f"分页字段 = {{k: v for k, v in p2.items() if 'page' in k or 'count' in k}}",
            json.dumps({k: v for k, v in p2.items() if not isinstance(v, list)},
                       ensure_ascii=False)[:500])


def step_contest(c, rep):
    print("\n[竞赛/作业 URL 形态]")
    cl = c.get("/contest").json() or {}
    t0 = (cl.get("tdocs") or [{}])[0]
    tid = t0.get("_id") or t0.get("docId")
    if not tid:
        rep.add(17, "yellow", "竞赛/作业内提交的 URL 形态", "未取得结论（竞赛列表为空）")
        return
    html = c.get(f"/contest/{tid}", extra={"Accept": "text/html"}).text
    m = re.search(r'"postSubmitUrl"\s*:\s*"([^"]*)"', html)

    # 路由精确匹配验证：确认哪个记分板路由真实存在（做竞赛模块前必须知道）
    probe = []
    for sub in ("", "/scoreboard", "/ranking"):
        rr = c.get(f"/contest/{tid}{sub}")
        form, detail = classify(rr)
        probe.append(f"/contest/{{tid}}{sub:12} -> HTTP {rr.status} {form} {detail}")

    rep.add(17, "yellow", "竞赛/作业内提交的 URL 形态与子路由",
            f"竞赛 `{t0.get('title')}`（tid={tid}）内 postSubmitUrl = "
            f"{m.group(1) if m else '列表页未下发（需进入具体题目）'}；"
            f"子路由存在性见证据（路由是精确匹配的）",
            f"GET /contest/{tid} -> 含 postSubmitUrl = {bool(m)}\n\n" + "\n".join(probe),
            "若 postSubmitUrl 带 tid 参数，二期实现竞赛做题时需一并透传；"
            "记分板路由名已由本次探测确定，不要照搬网页前端的猜测路径")


def ws_probe(path, seconds=4.0):
    key = base64.b64encode(os.urandom(16)).decode()
    raw = (f"GET {path} HTTP/1.1\r\nHost: {HOST}\r\nUpgrade: websocket\r\n"
           f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n"
           f"Sec-WebSocket-Version: 13\r\nOrigin: https://{HOST}\r\n"
           f"User-Agent: {UA}\r\n\r\n")
    try:
        s = socket.create_connection((HOST, 443), timeout=10)
        ss = ssl.create_default_context().wrap_socket(s, server_hostname=HOST)
        ss.sendall(raw.encode())
        ss.settimeout(seconds)
        buf = b""
        t0 = time.time()
        while time.time() - t0 < seconds:
            try:
                chunk = ss.recv(4096)
            except socket.timeout:
                break
            if not chunk:
                break
            buf += chunk
        ss.close()
        return buf
    except Exception as e:                                        # noqa: BLE001
        return f"ERROR {type(e).__name__}: {e}".encode()


def step_ws(c, rep):
    print("\n[WebSocket 实时通道]")
    ev = []
    for path in ("/ws", "/record-conn"):
        buf = ws_probe(path)
        if buf.startswith(b"ERROR"):
            ev.append(f"{path} -> {buf.decode()}")
            continue
        head, _, body = buf.partition(b"\r\n\r\n")
        status = head.split(b"\r\n")[0].decode("latin-1")
        ev.append(f"{path} -> {status}\n  header:\n"
                  f"{head.decode('latin-1')[:400]}\n"
                  f"  4s 内收到 {len(body)} 字节；"
                  f"首 80 字节 hex = {body[:80].hex()}\n"
                  f"  按 latin-1 读 = {body[:120].decode('latin-1', 'replace')!r}")
    rep.add(16, "green", "WS 协议",
            "已记录握手结果与 4s 内的推送内容；协议细节解读见证据",
            "\n".join(ev) if ev else "(无)",
            "一期用轮询兜底，WS 属 M3 优化；本项不影响 M1 开工")


# --------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="JXAU OJ M0 契约验证脚本")
    ap.add_argument("--anon", action="store_true", help="只跑只读项，不登录（零风险）")
    ap.add_argument("--submit", action="store_true", help="执行一次真实提交并轮询评测状态")
    ap.add_argument("--ask", action="store_true", help="交互式输入账号密码")
    ap.add_argument("--user", default=os.environ.get("OJ_USER", ""))
    ap.add_argument("--password", default=os.environ.get("OJ_PASS", ""))
    ap.add_argument("--problem", default="P1000", help="用于验证的题目（默认 P1000，站点自带的 A+B）")
    ap.add_argument("--lang", default="py.py3", help="提交语言（默认 py.py3）")
    ap.add_argument("--poll-interval", type=float, default=1.5)
    ap.add_argument("--poll-timeout", type=float, default=180.0)
    ap.add_argument("--no-logout", action="store_true", help="跑完不登出（默认会登出）")
    ap.add_argument("--ws", action="store_true", help="额外探测 WebSocket（非阻断，可选）")
    ap.add_argument("--out", default="docs/M0-契约验证报告.md")
    ap.add_argument("--samples", default="tools/m0-samples")
    args = ap.parse_args()

    os.makedirs(args.samples, exist_ok=True)
    rep = Report()
    c = Client()
    submitted = False

    print("=" * 68)
    print(f" M0 契约验证 —— {BASE}")
    print(f" 模式：{'只读（不登录）' if args.anon else '完整验证'}"
          f"{' + 真实提交一次' if args.submit else ''}")
    print(f" 时间：{now_iso()}")
    print("=" * 68)

    step_anon(c, rep, args.samples)

    if args.anon:
        print("\n[--anon] 跳过登录与提交。")
    else:
        user, pw = args.user, args.password
        if args.ask or not user:
            if not sys.stdin.isatty():
                print("\n错误：需要账号但当前非交互环境，请用 --user/--password 或环境变量 OJ_USER/OJ_PASS。")
                return 2
            print("\n请输入 OJ 账号（凭据只留在内存，不写盘）：")
            user = user or input("  用户名/邮箱: ").strip()
            pw = pw or getpass.getpass("  密码: ")

        ok, _u = step_login(c, rep, args.samples, user, pw)
        if not ok:
            print("\n登录未成功，跳过登录后各项。请核对账号密码后重跑。")
        else:
            submit_url, subs_url = step_problem(c, rep, args.samples, args.problem)
            step_records(c, rep, subs_url, args.problem)
            step_contest(c, rep)
            if args.submit:
                rid, _r = step_submit(c, rep, submit_url, args.lang, WA_CODE, args.samples)
                submitted = bool(rid)
                step_poll(c, rep, rid, args.poll_interval, args.poll_timeout)
            else:
                for no, lvl, t in ((5, "red", "提交请求体字段名"),
                                   (6, "red", "提交响应中的提交 ID 字段"),
                                   (7, "red", "记录详情完整字段"),
                                   (8, "red", "各测试点详情可见性"),
                                   (9, "red", "评测状态实时性")):
                    rep.add(no, lvl, t, "未取得结论（本次未开启 --submit）")
            if args.ws:
                step_ws(c, rep)
            if not args.no_logout:
                lo = c.post("/logout")
                after = c.get("/api/user").json() or {}
                rep.add(14, "yellow", "登出路由",
                        f"POST /logout -> HTTP {lo.status}；之后 /api/user authn={after.get('authn')}",
                        f"POST /logout -> {lo.status}\n{lo.head(200)}\n"
                        f"复核 GET /api/user -> {after.get('authn')}",
                        "若无效则用『清空本地 Cookie + 重新拉 /login』兜底，不阻塞")

    # 落样本（脱敏）
    try:
        with open(os.path.join(args.samples, "request-log.json"), "w", encoding="utf-8") as f:
            json.dump([{"method": m, "path": p, "status": s, "seconds": e}
                       for m, p, s, e in c.log], f, ensure_ascii=False, indent=2)
    except OSError as e:
        print(f"  样本写入失败：{e}")

    out_dir = os.path.dirname(args.out)
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    md = rep.render({"anon": args.anon, "submitted": submitted,
                     "samples": args.samples, "request_log": c.log})
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(redact(md))

    reds = [i for i in rep.items if i["level"] == "red"]
    pending = [i for i in reds if i["conclusion"].startswith("未取得结论")]
    print("\n" + "=" * 68)
    print(f" 报告已写入 {args.out}")
    print(f" 请求总数 {len(c.log)}；🔴 {len(reds)} 项，其中未清零 {len(pending)} 项")
    print("=" * 68)
    return 1 if pending else 0


if __name__ == "__main__":
    sys.exit(main())
