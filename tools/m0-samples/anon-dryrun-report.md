# M0 契约验证报告

> 目标站点：`https://oj.wbhtqlorz.cv`　|　执行时间：2026-09-13 17:52:54 +0800
> 模式：仅只读（未登录）
> 脚本：`tools/m0_probe.py`　|　原始样本：`tools/m0-samples/`

## 结论速览

- 共 4 项结论；其中 🔴 阻断项 4 个，**4 个已取得结论，0 个未清零**。
- **所有 🔴 阻断项均已清零，可以开始 M1。**

## 逐项结论

| # | 级别 | 验证项 | 结论 |
|---|---|---|---|
| 0 | 🔴 | 匿名基线与游客判定 | /api/user 返回 HTTP 200，role=guest，含 authn 字段=False → 游客判定应看 role=='guest'（或 authn 不为 true） |
| 0.1 | 🔴 | JSON 内容协商可用性（方案基石） | 同一路径 /p：Accept: application/json -> 200 application/json（JSON 解析成功=True）；Accept: text/html -> 200 text/html |
| 0.2 | 🔴 | 响应形态矩阵（客户端分发器的设计依据） | GET /p → 正常数据；GET /api/user → 正常数据；GET /record → 未登录软跳转；POST /p/1/submit → 未登录软跳转；GET /p/NOSUCHPROBLEM → 业务错误；GET /zzz-not-a-route-xyz → 业务错误；GET /record/1 → 业务错误 |
| 0.3 | 🔴 | 错误响应的格式由 Accept 决定（纠正既有结论） | 同一未命中路由：Accept: json -> 404 application/json；Accept: html -> 404 text/html → **只要恒发 Accept: json，就不会拿到 HTML 错误页** |

## 证据明细（可直接用于写 DTO）

### [0] 匿名基线与游客判定

**结论**：/api/user 返回 HTTP 200，role=guest，含 authn 字段=False → 游客判定应看 role=='guest'（或 authn 不为 true）

**证据**：

```
GET /api/user -> 200
{"_id":0,"uname":"Guest","mail":"<redacted-mail>","perm":"BigInt::37483536664552833153","role":"guest","priv":8,"regat":"2026-08-24T06:44:34.653Z","loginat":"2026-08-24T06:44:34.653Z","avatar":"gravatar:<redacted-mail>"}
```

**Mapper 提示**：登录态才有 authn=true；不要用 authn==false 判断游客（游客响应里根本没有该字段）

### [0.1] JSON 内容协商可用性（方案基石）

**结论**：同一路径 /p：Accept: application/json -> 200 application/json（JSON 解析成功=True）；Accept: text/html -> 200 text/html

**证据**：

```
JSON 首 200 字：{"page":1,"pcount":183,"ppcount":2,"pcountRelation":"eq","pdocs":[{"_id":"6a8c447b58de462e1709be25","owner":8,"domainId":"system","docType":10,"docId":2,"title":"两台机器","tag":["一 · 报到那天","两数读入","加减"],"

HTML 首 90 字：<!DOCTYPE html> <html data-page="problem_main" data-layout="basic" class="layout--basic pa
```

**Mapper 提示**：客户端所有请求恒带 Accept: application/json，这是全程不写 HTML 解析器的依据

### [0.2] 响应形态矩阵（客户端分发器的设计依据）

**结论**：GET /p → 正常数据；GET /api/user → 正常数据；GET /record → 未登录软跳转；POST /p/1/submit → 未登录软跳转；GET /p/NOSUCHPROBLEM → 业务错误；GET /zzz-not-a-route-xyz → 业务错误；GET /record/1 → 业务错误

**证据**：

```
GET  /p                               -> HTTP 200  正常数据         8 个顶层字段
     body: {"page":1,"pcount":183,"ppcount":2,"pcountRelation":"eq","pdocs":[{"_id":"6a8c447b58de462e1709be25","owner":8,"domainId":"system","docType":10,"docId":2,"title"
GET  /api/user                        -> HTTP 200  正常数据         9 个顶层字段
     body: {"_id":0,"uname":"Guest","mail":"<redacted-mail>","perm":"BigInt::37483536664552833153","role":"guest","priv":8,"regat":"2026-08-24T06:44:34.653Z","loginat":"
GET  /record                          -> HTTP 200  未登录软跳转       /login?redirect=%2Frecord
     body: {"url":"/login?redirect=%2Frecord"}
POST /p/1/submit                      -> HTTP 200  未登录软跳转       /login?redirect=%2Fp%2F1%2Fsubmit
     body: {"url":"/login?redirect=%2Fp%2F1%2Fsubmit"}
GET  /p/NOSUCHPROBLEM                 -> HTTP 404  业务错误         ProblemNotFoundError(404) Problem {1} not found.
     body: {"error":{"message":"Problem {1} not found.","stack":"","params":["system","NOSUCHPROBLEM"],"name":"ProblemNotFoundError","code":404}}
GET  /zzz-not-a-route-xyz             -> HTTP 404  业务错误         NotFoundError(404) NotFoundError
     body: {"error":{"message":"NotFoundError","stack":"","params":["/zzz-not-a-route-xyz"],"name":"NotFoundError","code":404}}
GET  /record/1                        -> HTTP 403  业务错误         ValidationError(403) Field {0} validation failed.
     body: {"error":{"message":"Field {0} validation failed.","stack":"","params":["rid"],"name":"ValidationError","code":403}}
```

**Mapper 提示**：四分支：正常数据 / 未登录软跳转（HTTP 200！）/ 业务错误 / 网络或网关异常。任何只看 HTTP 状态码的实现都会把软跳转当成成功

### [0.3] 错误响应的格式由 Accept 决定（纠正既有结论）

**结论**：同一未命中路由：Accept: json -> 404 application/json；Accept: html -> 404 text/html → **只要恒发 Accept: json，就不会拿到 HTML 错误页**

**证据**：

```
JSON 模式: 404 application/json
{"error":{"message":"NotFoundError","stack":"","params":["/zzz-not-a-route-xyz"],"name":"NotFoundError","code":404}}

HTML 模式: 404 text/html
<!DOCTYPE html> <html data-page="error" data-layout="basic" class="layout--basic page--error theme--light nojs" data-man
```

**Mapper 提示**：仍然保留首字符/Content-Type 防御，但理由是「Cloudflare 网关层可能吐 HTML」（5xx/1035），而不是「Hydro 路由未命中会吐 HTML」

---

## 本次请求流水

| 方法 | 路径 | 状态码 | 耗时(s) |
|---|---|---|---|
| GET | `/api/user` | 200 | 1.186 |
| GET | `/p` | 200 | 0.995 |
| GET | `/p` | 200 | 1.694 |
| GET | `/p` | 200 | 1.481 |
| GET | `/api/user` | 200 | 1.184 |
| GET | `/record` | 200 | 1.111 |
| POST | `/p/1/submit` | 200 | 1.008 |
| GET | `/p/NOSUCHPROBLEM` | 404 | 1.162 |
| GET | `/zzz-not-a-route-xyz` | 404 | 1.076 |
| GET | `/record/1` | 403 | 0.999 |
| GET | `/zzz-not-a-route-xyz` | 404 | 1.121 |
| GET | `/zzz-not-a-route-xyz` | 404 | 1.009 |
