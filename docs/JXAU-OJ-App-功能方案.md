# JXAU OJ 移动端 App 功能方案

> 目标站点：`https://oj.wbhtqlorz.cv`（Jxau OJ）
> 站点形态：Hydro `4.58.4` 默认部署，**未做任何改造**，App 全程基于其现有接口交互
> 文档版本：v1.18 ｜ 2026-09-15 ｜ 状态：实施中
> 一期范围：核心做题闭环（登录 → 题库 → 题目 → 编辑 → 提交 → 评测结果）
> **风格约束：原生 Android（Material 3），不复刻网页 UI —— 见 5.0**
> v1.1 变更：新增 5.0 视觉风格总纲、5.5 Material 3 组件规范、10.2 风格验收；同步调整 6.1／6.2／7.1／9／11
> v1.2 变更：新增 12.C「开工前需要拍板的决策」——汇总非取证类、只能由产品方决定的开放项
> v1.3 变更：**纠正两处错误结论**（路由未命中并非返回 HTML、游客响应无 `authn` 字段）；3.2 由"三形态"扩为"四形态"；新增 8.1 验证脚本说明与 8.2 前置已验证结论；交付 `tools/m0_probe.py`；12.C 关闭 3 项决策
> v1.4 变更：新增 **5.4.1 主题目录**——14 项内置主题 + 判题状态色与主题解耦的硬约束 + 清单驱动的生成流程；交付 `tools/gen_themes.py` 与 `design/` 下三份生成物；12.C 关闭主色决策
> v1.5 变更：**开工**。12.C 剩余 8 项决策全部关闭；工程落在 `android-app/`（Kotlin 2.0.21 + Compose BOM 2024.12.01 + AGP 8.7.3，**可完全离线构建**），已实现设计系统、四形态响应分发器、DTO/Mapper 与题库/题目/登录/设置四条页面链路；新增附录 D
> v1.6 变更：M1 闭环补齐。新增题干 Markdown 渲染器、纯 Compose 代码编辑器（语法高亮 + 草稿）、提交数据层与评测结果退避轮询；`BUILD SUCCESSFUL` 产出可安装 APK；**新增 D.5「契约待验证点清单」**——把 M0 欠账精确登记到文件级
> v1.7 变更：M2 第一批落地。排行榜与用户主页（契约【实测】）、提交记录列表（三种范围，契约【未验证】并区分「没有记录」与「契约不符」）、题目详情提交入口与「我的」页真实入口；判题色跟随应用内深色偏好；修复 `AppContainer.kt` 编码损坏（Write 工具按 GBK 落盘的坑，见 D.3）
> v1.8 变更：M2 第二批落地。竞赛列表/详情与作业列表/详情（契约均【实测】，竞赛 tdoc 与作业 tdoc 分开建模）；阶段徽标（未开始/进行中/已结束）由起止时间客户端推算；「我的」页接入竞赛与作业入口。讨论区后置（站点 0 帖 + 详情契约未验证，见 D.4）
> v1.10 变更：**MuMu 模拟器（Android 12）实测修崩**。修复两类崩溃：① 排除 listenablefuture 后
> profileinstaller 的 ProfileVerifier 启动即 VerifyError（AndroidManifest 摘除其初始化器）；
> ② 竞赛/作业列表 docId 缺失 → LazyColumn key 重复崩 + tid 改用 `_id` 字符串（3.4.5 有更正）。
> 回归实测：题库/题目/排行榜/竞赛/作业/设置/编辑器/游客提交全通过，1000 次 monkey 零 FATAL。
> v1.9 变更：M2 收尾打磨。评测详情页可查看提交的代码（复用编辑器高亮 + 可复制，`code` 字段契约未验证故为 null 时整块不渲染）；登录成功返回后登录墙后的页面（提交记录/评测详情）自动重载；确认自适应启动图标（站点主色 + 尖括号占位）已就位
> v1.11 变更：**登录链路真机排障（MuMu 实测）**。定位根因：`/api/user` 无论登录与否都**没有 `authn` 字段**（Hydro 的 authn 是 WebAuthn 属性，3.3 有 v1.11 更正）——App 用 `authn==true` 判登录态，导致"登录成功却显示未登录、报密码错误"；判定改为 `_id > 0 && role != "guest"`。顺带：`POST /login` 契约实测入档（成功返回 `{"url":"/"}`，3.3）；错误信封 `params` 占位符替换；按错误名映射人话提示；登录表单补 `rememberme=true`；修复 CookieJar hostOnly/httpOnly 标志位编解码错位；debug 构建增加请求诊断日志（响应体脱敏 mail，绝不记请求体）。**登录态识别、我的提交列表、评测详情页实测通过**。随后**在 App 内完成一次真实提交（#2 两台机器 / C / AC）**：`POST /p/:docId/submit` 的 `lang`+`code` form 键、响应 `rid` 键、轮询 `rdoc` 包裹全部命中假设，**M0 契约验证全部闭环**（D.5 终态）
> v1.12 变更：**用户报告四项体验问题全数修复并真机验证（MuMu 实测）**。① 排行榜→用户主页的提交记录过滤参数实为 **`uidOrName`**（传 `uid` 被服务端静默忽略、退化为全站记录——正是"看到所有人记录"的根因）；② 作业认领走 **`POST /homework/:tid` + `operation=attend`**（Hydro 框架按 `operation` 字段分发 POST，空 body 会 405），`tsdoc.attend` 是**数字 1/0 不是布尔**，题目列表认领后从 **`pdict`** 取（此前误把讨论区 `ddocs` 当题目）；③ 竞赛题目列表改为 **A~Z 标号 + 题目名**（`GET /contest/:tid/problems` 解析 `pdict`，标号仿 Hydro `getAlphabeticId` 26 进制，契约【实测】）；④ 编辑器新增**题面顶栏**（点击/下拉展开题面、上拉收起，Markdown 渲染）与**自测底栏**：pretest 契约【实测】——`POST /p/:pid/submit` + `pretest=true` + `input[]`（重复键数组），轮询 `rdoc.testCases[]` 每项 `{status,time(浮点ms),memory,message}`，**`message` 即程序 stdout**；自测不计提交数、不进记录列表；退避轮询与正式提交一致。App 内自测（#2 / C / 两组样例）实测：**通过 / 共 2 组 / 8 -2、6 -14**，判定卡颜色+文字双重表达
> v1.13 变更：**两项新功能落地并真机验证（MuMu 实测 2026-09-14）**。① **竞赛成绩表**：契约【实测】`GET /contest/:tid/scoreboard` → `{tdoc, tsdoc, rows, udict, pdict, ...}`，`rows` 是站点渲染好的矩阵（首行表头 `type:rank/user/total_score/problem`，数据行 record 格 `value`=分数串 / `raw`=rid / `score` 可选 / `raw:null` 显示 `-`）；**`udict` 含明文 mail → DTO 刻意不解析**；站点下发的 `style`（HTML 内联背景色）不解析，判定色由 App 端按分数用 VerdictColors 表达（满分绿/部分分橙/0分红/未提交灰）。页面：表头与数据行共享横向滚动、纵向 LazyColumn、本人行 secondaryContainer 高亮、**每 10 秒静默刷新**（刷新失败不清空已有数据）、顶栏显示最近同步时刻；点带 rid 的格子跳评测详情。② **编辑器自动补全 + 高亮增强**：候选 = 语言保留字（含忽略大小写次级匹配）+ 内建类型/常用函数 + 当前代码标识符（按出现次数加权）；弹层锚定光标行（上方优先、越界翻下方并钳回），硬件键盘 ↑↓ 选择、Enter/Tab 采纳、Esc 关闭，点按亦可；`Language` 拆出 `builtins` 表，高亮新增第五类（primary⊗tertiary 中点色）与「标识符后紧跟 `(` → 函数名加粗」；语法配色统一走 `syntaxColorsOf(colorScheme)`（编辑器与 CodeView 共用）。**踩坑实录**：`onTextLayout` 拿到的布局可能落后于 fieldValue（文本变短的帧），`getCursorRect(offset)` 不按布局长度钳制直接越界崩溃（`offset(99) out of bounds [0,97]`，MuMu 实测）——两处调用点统一钳制。
> v1.14 变更：**编辑器体验专项（功能大纲 A/B/C/D 四组，用户勾选后实现）**。① **代码字体系统**：`ui/theme/EditorFonts.kt` 清单驱动（`EDITOR_FONTS`，与 `THEME_CATALOG` 同套路，新增字体不改 UI 代码），内置「系统等宽」与「Cascadia Mono」（随包内置 363KB 可变字体，须为 Normal/Medium/Bold 各声明一条 `Font` 否则字重失效；许可为**基于 SIL OFL 的 Microsoft 字体许可**，见下条补测轮）；字号 10–22sp 与字体选择**统一收到设置页**（`LocalEditorFontId` / `LocalEditorFontSizeSp` 经 CompositionLocal 下发，编辑器页不再各自管字号），编辑器与只读 CodeView 共用。② **补全框改造**：删除「关键字／内建／标识符」三个汉字标签，改为 **3dp 分类色条**（分类信息移入 `contentDescription` 供读屏）；宽度由固定 220dp 改为**按最长候选实测自适应**并 `coerceIn(96,180)dp`，右侧越界改为钳回贴边；候选数 6→5；行高 34→30dp。③ **编辑动作引擎**（`ui/editor/CodeEditActions.kt`，纯函数零 UI 依赖）：自动补配对、换行继承缩进（上一行以 `{` 结尾再缩一级）、**跳过已存在的右括号**、手打 `}` 退回一级缩进、成对删除、**选区包裹**。④ **视觉增强**：配对括号高亮 + 当前行左侧竖线（Canvas 装饰层画在文本之下，**只加样式不改文本长度**，`OffsetMapping.Identity` 依赖此约束）；**符号条**（Tab `{` `}` `(` `)` `[` `]` `;` `"`，把只有硬件键盘才有的能力用按钮暴露给软键盘用户）。**踩坑实录**：`FontVariation.Settings(weight, style)` **自己会派生 `wght` 轴**，再显式传 `FontVariation.weight(x)` 会 `IllegalArgumentException: 'wght' must be unique`，且发生在 `<clinit>` → **App 启动即崩**；「输入 `)` 而右侧已有 `)`」时 diff 的公共前缀会归并两者使 `inserted` 为空，**跳过判据必须取自 `old.selection.end` 处 `old.text` 的字符**，用 diff 索引必然漏（实测打出 `int main())`）。5.0／6.1 的**「WebView + CodeMirror 6」编辑器方案已废弃**，编辑器为纯 Compose 自绘（6.1 已重写；文中 670／1138／1454 等处残留的 CodeMirror 表述为历史决策记录，以 6.1 为准）。
> v1.14 补测轮（同日）：① **候选框键盘路径补测通过** —— `adb input keyevent` 注入的是真实 `KeyEvent`，与实体键盘同一条 `onPreviewKeyEvent` 链路，故 ↑↓ 选择/环绕、Enter·Tab·小键盘回车采纳、Esc 关闭、←→ 关闭并放行**全部实测通过**（附录 E.6）；② **字号边界** 10/22 钳住且按钮正确禁用；③ **许可正文补齐** —— Cascadia Mono 的许可全文**逐字取自字体 name 表 ID 13**（非摘抄、非臆造），随 `res/raw/cascadia_mono_notice.txt` 打包，并更正措辞为「基于 SIL OFL 的 Microsoft 字体许可（含附加条款）」。**附录 E.5 遗留项全部关闭。**
> v1.14 补测轮②（同日，纯函数自检 + 回归）：① **交付离线自检工具 `tools/editor_actions_check/`** —— 本机离线缓存里**只有 junit-bom 没有 jar**，标准 JVM 单测路线不通；改为用缓存里的 `kotlin-compiler-embeddable` 把 `CodeEditActions.kt` 与表驱动用例直接编成可执行程序，**52 项用例全绿**（附录 E.7）。② **自检当场抓出一个真 bug**：`()` 中间退格能成对删除、**`""` 中间退格不能** —— 被删字符与其左侧字符相同时，diff 的公共前缀会把删除归并到右边那个，`new[prefix]` 直接越界。这与「跳过右括号」是**同一类歧义**，判据改为取自 `selection`；顺带用 `new.selection.end == old.selection.end - 1` 把「退格」与「前向删除」分开（E.4 第 5 条）。③ **真机回归全过**（A–G 七组，含新旧分支的判别性用例）。④ 记录一条**环境坑**：本机路径到站点所在 Cloudflare 边缘 IP 会大量丢包（设备侧 TCP 握手 8–30s 甚至超时），App 表现为「网络不可达」；换用**其它 Cloudflare IP** 立即 200 —— 属 anycast 单节点可达性问题，非站点故障（见 E.7 末节）。
> v1.14 补测轮③（同日，映射层/工具层/DTO 解析层/网络层/判题色自检）：① **交付第二批离线自检 `tools/pure_helpers_check/`** —— 把 E.7 那套纯函数自检扩到 `Mappers` + 工具层 + **DTO 解析层** + **四形态分发** + **判题状态色**，**19 组 245 项全绿**；两批合计 **297 项**（附录 E.8）。② **订正一条契约、修掉一处静默降级**：`avatar` 一直被记为「`qq:` 前缀 / `gravatar:` 前缀 / **完整 URL**」三种形态，实测更正是**前缀式三种** —— `qq:` ／ `gravatar:` ／ **`url:<站内相对路径>`**（`GET /ranking` 31 位用户实测：`qq:` 24、`gravatar:` 6、`url:` 1，**无一条 `https://` 绝对地址**）。原实现只认 `qq:`/`gravatar:`/`github:`，`url:` 落到 `else -> null` → 该用户头像**整块空白且不报错**；已补 `url:` 分支（相对路径拼站点根）。真机验证：修复前 #15「绪山真寻」空白，修复后渲染出真实头像（E.8）。③ **订正一处注释与实现不符**：`strList` 注释称「元素不是字符串时跳过」，实际是**标量一律转文本、只跳对象/数组/`null`** —— 与同文件 `str()` 的「类型漂移不丢数据」一致，故**改注释、实现未动**（E.8）。④ 抓出一处**死兜底**：`RecordListDto` 的总数候选键级联里第三个候选 `total` 是**永远不求值的死代码**（`int()` 返回非空 `Int`，`?:` 右侧无效，编译器只给警告），`{"total":42}` 静默读成 0；已逐级补 `.takeIf { it != 0 }`（E.8）。⑤ **把四形态分发变成可测的**：它原本是 `HydroClient` 的私有方法、绑死 OkHttp/Android，只能靠真机随手点验证，而它的误判会被读成完全不同的用户语义；已**逐字搬出**成纯对象 `data/net/ResponseDispatch.kt`（行为一字未改），并用 **30 项**钉住顺序判据（`200` 也可能是错误、软跳转优先于错误信封、`url` 不指向 `/login` 不算跳转、HTML 与坏 JSON 分开报）。真机回归：形态一（`JXAU_NET` 日志抓到真实入参 + 题库正常渲染）与形态四（`failed to connect … after 15000ms` + 重试恢复）均 ✅；形态二/三在游客态下 UI 走不到，按约定**不发 guest POST**，仅由离线用例（输入取自 curl 实测 body）覆盖。⑥ 本轮 4 项首跑失败中 3 项属**用例设计错误**（前缀/光标没走到目标分支），已作为教训记入 E.8 与构建自检流程。⑦ 记录一条**环境限制**：`gravatar` 域名（`s.gravatar.com` / `cn.gravatar.com`）国内网络 `code=000` 不可达，故用 gravatar 的用户头像恒为空白 —— 非算法问题（md5 已核对），需产品决策是否补首字母兜底（E.8）。

> v1.14 补测轮⑤（同日，题面 Markdown 解析器自检 + **「自检有没有牙」的验证手段**）：① **交付第四批离线自检用例 U 组 77 项**（U1 块级 36 项 ／ U2 行内 42 项）—— 把题面渲染用的 `ui/component/MarkdownParser.kt` 从 `MarkdownText.kt` 里搬出来，用例语料**全部取自 12 道线上题干的实测形态**（标题只有 `##`(60)、`###`(24)；`*` 共 164 处**全是长度 2 的 `**`**；另有反引号行内码与 `$...$` 公式；**没有**引用块／链接／分隔线／`_强调_`）；四批合计 **412 项全绿**（52 + 245 + 90 + 77，附录 E.9.5）。② **交付变异探针 `tools/pure_helpers_check/probe.sh`**：人为改坏被测源码（6 种变异），验证自检**确实抓得住** —— 因为「全绿」既可能来自实现正确，也可能来自用例没有判别力，而后者更危险。本次 6 种变异**全部被抓**（附录 E.9.6）。③ **探针自身踩了三个坑，已全部固化进脚本**：判据**必须用 ASCII**（脚本曾按 GBK 落盘，脚本里的「通过」二字成为 GBK 字节，永远匹配不上 UTF-8 的节目输出 → 5 次探针全被误报成「编译失败」，看起来像自检没牙）；每一步必须**校验 md5**（否则「变异根本没落盘」会被误读成「自检没牙」，反过来把好用例删掉）；**空变异要自己先推演掉**。④ **订正第三处「注释与实现不符」**：`inlineRegex` 的旧注释称「分支顺序敏感，`**` 必须排在 `*` 之前，否则粗体永远匹配不到」，此说**可证为假** —— 五个分支首字符互斥（`` ` ``／`*`／`[`／`$`），唯一同以 `*` 开头的两支在**第二字符**上又互斥（`\*[^*]+\*` 要求 `text[p+1] != '*'`，`\*\*[^*]+\*\*` 要求 `text[p+1] == '*'`），所以谁先谁后结果恒等；实测把两支对调当变异跑，412 项**全过**（是**空变异**，不是自检没牙）。同一处「已知限制」写的 `***粗斜体***` → `*粗斜体` 也漏了尾部，实测是 `*粗斜体*`（**首尾各漏一个 `*`**）。⑤ **顺手补上 U 组的一处真盲区**：`***粗斜体***` 与 `a * b * c` 两例此前只被子序列不变量覆盖，判据太松（实测把 `**粗**` 那一支整个删掉，子序列断言照样成立，而输出已从「输入」变成「*输入*」），已改为**逐字符行为锁定**断言。⑥ **真机回归**：题面整页渲染正常（标题／段落／列表项「·」／样例代码块），线上题干 `$r \times c$` → `r \times c`、`$1 \le r, c \le 10^4$` → `1 \le r, c \le 10^4`、`**sylth**` → `sylth` 与 U 组断言逐条对上；「去写代码」入口点开不崩；`logcat -b crash` 零 `FATAL EXCEPTION`；SharedPreferences 测前测后**零差异**（E.9.5）。⑦ 再遇 Cloudflare 单边缘节点不可达（`104.21.40.53` `code=000`，同刻换 `172.67.68.5` 得 0.28s／`200`），按 E.7／E.8 既有结论按环境问题处理，**未改 App 任何代码**。
> v1.14 补测轮④（同日，语法高亮器不变量自检 + **一次「构建成功却装着坏字节码」的事故**）：① **交付第三批离线自检用例 T 组 90 项** —— 搬出 `ui/editor/SyntaxHighlighter.kt` 后，把 `HighlightTransformation` 依赖的硬不变量「高亮**只附加样式、绝不改变文本长度**」（`OffsetMapping.Identity` 的前提）钉死：25 条「难缠片段」逐条断言逐字符一致（CRLF／`\t`／未闭合注释与字符串／字符串尾反斜杠／emoji 代理对／三引号原始串…）、25 条断言样式区间不越界、`HighlightTransformation.filter()` 的 `OffsetMapping` 在 `[0,length]` 上**逐点**恒等、20 条分色正确性（假高亮器的照妖镜）、13 条语言族分发。**合计 335 项全绿**（52 + 245 + 90，附录 E.9.1）。② **真机回归时抓出一个连续多轮重建都没发现的闪退**：点题目详情页「去写代码」→ `AbstractMethodError`。`javap` 对比发现接口是 `(…,Composer,I)V` 而实现类是 `(…,Composer,II)V`（多一个默认参数掩码）→ 实现类**没有实现**接口方法。用同版本编译器做最小复现证明**源码形状没问题**，真因是 **Kotlin 增量编译产出坏 class 后被 Gradle build cache 收下**，此后连 `clean` 都修不好（`compileDebugKotlin` 直接 `FROM-CACHE` 还原同一份坏产物）。修法两层：**`gradle.properties` 关掉 `org.gradle.caching`**（换取「`BUILD SUCCESSFUL` == 产物可信」），并交付**产物体检工具 `tools/abi_check/`**（断言「接口抽象方法在实现类里有相同描述符」，563 个 class / 4 个接口全绿）；该工具自身也做了**判别性验证**（人为造错配字节码必须报错）—— 第一版正因为把 `extends`/`implements` 混在一起而恒过，已修（附录 E.9.2）。③ 真机侧确认：编辑器可开、输入 36 字符逐字节往返一致、截图取样出 ≥5 种语法墨色、点击定位插入无偏移，`draft_5` 测完还原（E.9.3）。
> v1.15 变更：**换正式图标 + 打正式包（release）**。① **启动图标改用设计稿 `android-app/icon.ico`** —— 交付 `tools/gen_launcher_icon.py`，把 48×48 的「青色图形压在白底上」拆成**前景层（带 alpha 的图形）+ 底色层（`@color/ic_launcher_background` = `#F0F0F0`）**：这是自适应图标的硬要求，原图是不透明白底，直接当前景会让 Android 13+ 的**单色层变成一整块实心方块**（单色层只看 alpha，不看颜色）。拆分做法是「逐像素当作 white 与 cyan 的线性混合」最小二乘解出图形覆盖度，脚本末尾**断言把前景层叠回底色后与源图逐像素一致**（实测最大通道偏差 1）。放大走 NEAREST（48→240 是整数 5 倍，像素画不能插值），其余密度由 xxxhdpi 主图降采样。图形只占画布中间 **60dp** 而不是满铺 72dp —— 设计稿本身几乎没有留白，满铺会让顶栏两端正好落在圆形遮罩的切角上；60dp 时图形对角线刚好贴着 66dp 安全圆，**圆形／方圆／方形三种遮罩都不缺角**（真机桌面已确认）。旧的两支矢量占位 `res/drawable/ic_launcher_*.xml` 一并删除。② **配置正式签名** —— `keytool` 生成 `android-app/keystore/jxau-oj-release.jks`（RSA 2048／30 年）+ `keystore.properties` 存凭据，`app/build.gradle.kts` 新增 `signingConfigs.release`。⚠️ **这两个文件必须一起备份**，丢了就再也无法覆盖升级已装出去的 App。③ **修掉两个离线构建阻断点**：Kotlin DSL 里 `java.util.Properties` 会被 Gradle 的 `java` 扩展遮住，必须显式 `import`；`assembleRelease` 会卡在 `lintVitalAnalyzeRelease`（离线缓存里没有 `com.android.tools.lint:lint-gradle`，与代码无关），用 `android { lint { checkReleaseBuilds = false } }` 跳过（`:app:lint` 仍可单独跑）。**R8 故意没开**（显式 `isMinifyEnabled = false` 并写明理由）—— 这个工程有过「构建成功却装着坏字节码」的事故，混淆/裁剪造成的破坏同样是**运行期才暴露**的；要开得先补 keep 规则（尤其 `res/raw/cascadia_mono_notice.txt` 只被界面文案提及、不被代码引用，`shrinkResources` 会把它删掉），再做一遍完整真机回归。④ **清掉 App 里的测试草稿并换装正式包**：release 包与 debug 包签名不同，必须卸载重装（实测 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`）；装前备份 prefs，装后用 root 回写**清理过的** prefs —— 删掉测试时敲进去的 `draft_154("ac")` 等 6 条草稿，**保留用户自己写的 `draft_2` 题解**、设置与 Cookie（含属主／权限／SELinux 上下文）。⑤ **验收**：release 包 `debuggable=false`、`apksigner` v2 验签通过、Kotlin 编译零 `e:` 零 `w:`、字节码 ABI 体检 564 class / 4 接口全绿、离线自检 412 项仍全绿；真机（MuMu / Android 12）桌面图标已是新图、冷启动无崩溃、题库加载正常、题面 Markdown 渲染正常、进 #2 编辑器**正确恢复了 `draft_2`**。详见附录 F。
> v1.16 变更：**内置字体扩充到 6 款（用户点名 JetBrains Mono）**。新增 4 款可合法再分发的编程字体打包进 `res/font/`（9 个 TTF 合计 2.41 MB，逐档 md5 回执校验）：**Cascadia Code**（与 Mono 同源的连字版）、**JetBrains Mono**（三档字重）、**Fira Code**（三档字重）、**Source Code Pro**（常规/粗体）。选型硬标准：OFL 许可 + name 表含许可文本 + 编程符号覆盖齐全；**排除 Inconsolata**（仅 296 字形、13 个编程符号缺 10、name 表无许可文本）与 Droid Sans Mono（系统已自带）。OFL 正文逐字取自官方文本随包分发（4 份 `res/raw/*_notice.txt`，`tools/gen_font_notices.py` 幂等生成并自校验「正文与官方文本逐字一致」）。`EditorFonts.kt` 新增 `credit` 字段与 `BUNDLED_EDITOR_FONTS`；设置页「关于」段许可文案改为**按清单列举**（原「内置字体 Cascadia Mono…」的单一表述在 4 款后即为假话）。静态字重**不传 `variationSettings`**（那是可变字体才需要的）；自检 stub `R.kt` 同步补 9 个字段。自检新增 **V 组**（清单一致性）与 run.sh 的 **font assets 段**（清单点名的许可文件必须存在、`res/raw` 的声明必须被清单引用、未被引用的 ttf 报警），探针扩到 **10 种变异全部 CAUGHT**，总计 **447 项全绿**。本轮首次增量编译 release 失败（`compileReleaseKotlin` 无源码级报错），二分替换/还原多轮未锁定触发点，`clean` 全量构建后连续多次成功 —— 与 E.9.2 的「坏增量产物」同类，clean 修复。详见附录 G。
> v1.17 变更：**修复「普通用户看不了已参加竞赛的题目」**（用户实测报告，MuMu 100% 复现）。**根因**：竞赛题在 Hydro 里是**隐藏题**（`hidden=true`），普通用户无全局「查看隐藏题目」权限；实测契约 —— `GET /p/154` → **403 `PermissionError: View hidden problems`**，而 `GET /p/154?tid=<竞赛id>` → **200**（参赛者身份按 `tid` 授权解锁）。App 的题目跳转恰好漏传竞赛上下文。**修复**：路由 `problem/{docId}` / `editor/{docId}` 增加可选 `tid` 参数，竞赛/作业详情页跳转时携带；`ProblemRepository.detail` 与 `SubmissionRepository.submit/pretest` 均带 `tid`（**编辑器与提交的上下文一致，成绩才计入竞赛**）。**顺带把技术错误翻成人话**：新增 `data/net/ErrorMessages.kt`（`humanize(name, message)`，按错误 `name` 分类，未识别的原样透传**绝不编假话**；空文案兜底「请求失败」），`HydroResult.Failure` 增加 `friendly` 属性（`message` 保留站点原文供诊断），`errorText()` 改走 `friendly`，全部 UI 的 Failure 分支切换。自检新增 **W 组 18 项**（含「未知 name 原样透传」「译文不留 `{0}` 占位符与英文原句」等不变量）。真机复走：竞赛列表 → Week3 → A「拆机螺丝」题面正常渲染、编辑器正常打开，权限错误消失（提交链路同参数，未真提交以免留下竞赛痕迹）。详见附录 H。
> v1.18 变更：**编辑器二期两件套落地 + 头像兜底**。① **撤销/重做（C4-1）**：纯类 `CodeUndoStack`（快照栈，上限 100 条；打字与退格各按「光标连续」合并成一条，粘贴等多字符插入独立成条；**退格合并判据取 `selection`，与成对删除同源**，不用 diff 下标）；`CodeEditorHandle` 暴露 `undo()/redo()` 与 `canUndo/canRedo`（Compose state，按钮态随栈自动启停）；EditorScreen 底栏左下两按钮 + 硬件键 Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z。自检 Y 组 18 项，**首跑抓出退格合并差一 bug**（合并条件应 `lastCaret-1`）已修。② **查找替换（C4-2）**：纯类 `CodeFindReplace`（大小写敏感开关；`findAll` 不重叠前向；`replaceOne/replaceAll` 返回新文本与光标），查找条 UI 挂顶栏下方（查找框 / Aa 开关 / 上一个·下一个 / 展开替换 / 全部替换 / 关闭），匹配高亮画在 **Canvas 装饰层**（不碰文本管线，符合高亮既定约束；当前匹配加重底色）。自检 Z 组 20 项。③ **头像兜底**：`UserAvatar` 在真实头像加载失败时回落**用户名首字母 + 容器色色块**（v1.14 遗留的产品决策项），排行榜真机确认 gravatar 不可达的三位用户不再空白。**回归**：自检扩到 **28 组 497 项全绿**（412→497）；探针 10 变异全 CAUGHT；`abi_check` 590 class 全绿；assembleDebug/assembleRelease 双绿；debug→release 覆盖安装成功（**签名统一后首次双向验证**）；真机（#5，release 包）逐项验证 undo/redo/按钮态/查找高亮/全部替换/替换可撤销全过（**测试姿势坑：IconButton 热区≠dump 文本坐标，须按截图放大定位**）。详见附录 I。

---

## 0. 阅读说明：证据等级

本文档所有结论都标注来源等级。请按等级区别对待——**尤其不要把「推断」当成接口契约直接写代码**，那是联调阶段大面积返工的头号原因。

| 等级 | 含义 | 可否直接编码 |
|---|---|---|
| **【实测】** | 本次对线上站点发起了真实请求，字段名与取值均已采样确认 | 可以直接采信 |
| **【推断】** | 由页面结构、前端产物字符串或 Hydro 通用机制推导，未经真实响应验证 | 需在 M0 阶段验证后再编码 |
| **【未验证】** | 仅知命名存在，或完全未知 | 不可编码，先取证 |

本次实测共发起约 60 次只读请求（全部为 `GET`，除 2 次用于探测路由的匿名空 `POST`），未使用任何账号，未产生任何写操作。样本索引见附录 A。

---

## 1. 结论摘要（一页纸）

**这个 App 能不能做？能做，而且比预想的干净得多。**

最关键的一条发现：**该站点支持 `Accept: application/json` 内容协商机制**。任意页面路由只要带上这个请求头，就会直接返回该页面的原始数据 JSON，而不是渲染好的 HTML。这意味着 App 的接口底座是**原生 JSON API，完全不需要写 HTML 解析器**。

### 可以直接做的（【实测】有数据支撑）

- 匿名浏览题库、查看题目详情、搜索、分页 —— `/p`、`/p/:id` 直接返回结构化 JSON
- 登录 —— `POST /login` 表单字段已完整提取
- 提交代码 —— 服务端在题目详情里**主动下发** `postSubmitUrl`，App 无需硬编码路径
- 查评测结果 —— 服务端下发 `getSubmissionsUrl`；另有 `/ws` WebSocket 通道可用
- 竞赛 / 作业 / 榜单 / 用户主页 —— 均返回结构化 JSON

### 三个必须提前处理的技术坑（否则必踩）

1. **未登录访问受保护资源返回的是 HTTP 200**，响应体是 `{"url":"/login?redirect=..."}`。只看状态码会误判成"成功"。
2. **业务错误也常伴随 HTTP 200**，错误信息在 body 的 `error` 信封里（如权限不足 `code:403`）。必须解析 body，不能只看状态码。
3. **服务端返回在字段层面存在隐私泄露**：榜单、题目详情里的用户对象都带 `mail` 字段。客户端必须在 Mapper 层丢弃，不能进模型、不能落盘、不能进日志。

### 需要开工前先确认的（阻断项）

一期闭环里，**「提交代码 → 拿到评测结果」这一段是唯一没有实测数据覆盖的环节**（没有账号，无法提交）。它的请求体字段名、返回的提交 ID 字段名、记录详情的实时性，都必须在 M0 阶段用一个真实账号跑通，其余部分才可放心动工。详见第 8 章。

### 已锁定的风格约束

**App 采用原生 Android 观感（Material 3 / Material You），不复刻网页 UI。** 原有界面的组织方式与视觉元素一律重做，功能等价即可。这条是硬约束：站点用户对象里的外观偏好字段（字体、背景图）**不迁移**到 App，只迁移语义型偏好（主题初始值、默认代码语言、时区）。唯一的判定标准是——截图拿给没看过这个 OJ 的人，他应该认为这是一个 Android 应用，而不是网页套壳。详见 5.0。

> 提示：这一条会**否决一批常见的"快速实现"做法**（如整页 WebView 套壳、复刻站点主题换皮）。如果开发中有人提这类方案，直接按 5.0 驳回。

### 明确不做的（一期）

竞赛内做题、作业内做题、讨论区、训练题单、题目收藏、代码云同步。这些都有接口支撑，但要放到后续里程碑，理由见 4.1 的优先级矩阵。

---

## 2. 站点实测全景

### 2.1 站点基本信息【实测】

| 项 | 值 | 来源 |
|---|---|---|
| 站点地址 | `https://oj.wbhtqlorz.cv` | — |
| 站点名称 | `Jxau OJ`（域名 ID `system`） | `/api/domain` |
| 引擎 | Hydro **4.58.4** | 静态资源 `hydro-4.58.4.js`、`theme-4.58.4.css` |
| 站点改造 | **无**。默认主题、默认语言包（`lang-en.js`），`builtInLogin: true` | 站点公告 + 登录页 JSON |
| CDN / 边缘 | Cloudflare 前置（`server: cloudflare`，`cf-ray` 存在） | 响应头 |
| 协议 | HTTP/1.1 + `alt-svc: h3`（支持 HTTP/3） | 响应头 |
| 缓存策略 | 页面 `Cache-Control: private, no-cache, no-store` | 响应头 |

> ⚠️ Cloudflare 前置意味着可能存在**速率限制与 bot 风控**。App 的请求频率（尤其评测状态轮询）必须克制，不能按"本地局域网"的假设去设计。这是**风险项**，不是细节。

### 2.2 数据体量【实测】

| 项 | 数量 | 数据来源 |
|---|---|---|
| 题目总数 | **183** 题（默认每页 100 条，共 2 页） | `/p` → `pcount: 183` |
| 题目标签总数 | **133** 个 | `/p` 全量采样去重 |
| 注册用户 | 榜上有名 **31** 人 | `/ranking` → `ucount: 31` |
| 竞赛 | **3** 场（规则均为 IOI） | `/contest` → `tdocs[3]` |
| 作业（题单） | **4** 份（JXAU2026 系列） | `/homework` → `tdocs[4]` |
| 训练题单 | 0（模块已启用但为空） | `/training` |
| 讨论区 | 已启用，当前 0 帖 | `/discuss` |

**标签结构值得单独说**：133 个标签不是散乱的关键词，而是**章节化的教学体系**，形如：

```
一 · 报到那天       字符串输出      for 循环        一维数组
二 · 食堂与生活费   加减            嵌套循环
三 · 晨跑与体测     ...
```

这对 App 是个好消息：**标签可以直接当作"知识点导航"用**，比单纯的难度排序更适合学生刷题。建议 App 的题库页把标签做成按序号分组的筛选面板，而不是一坨平铺的 chip。

### 2.3 站点路由总表【实测】

下表是本次逐个探测过的路由，以及它在 App 里的用途映射。

| 路由 | 实测结果 | 数据形态 | App 用途 |
|---|---|---|---|
| `/` | 200 | 页面 JSON | （App 不需要首页） |
| `/p` | 200 | `{page, pcount, ppcount, pdocs[], psdict, qs, sort}` | **题库列表** |
| `/p/:pid`（如 `/p/1`、`/p/P1000`） | 200 | `{pdoc, udoc, psdoc, ...}` | **题目详情** |
| `/p/:pid/submit` | 200（匿名返回登录跳转） | `{url}` / 提交结果 | **代码提交** |
| `/record` | 200（匿名返回登录跳转） | `{url}` / 记录列表 | **我的提交记录** |
| `/record/:rid` | 403（匿名） | `error` 信封 | **评测结果详情** |
| `/ranking` | 200 | `{udocs[], upcount, ucount, page}` | **排行榜** |
| `/user/:uid` | 200 | `{isSelfProfile, udoc}` | **用户主页** |
| `/contest` | 200 | `{page, tpcount, tdocs[], tsdict, rule}` | 竞赛列表 |
| `/contest/:tid` | 200 | `{tdoc}` | 竞赛详情 |
| `/contest/:tid/scoreboard` | 200 | `{tdoc, ...}` | 竞赛榜单 |
| `/homework` | 200 | `{tdocs[], calendar, tpcount, page}` | 作业列表 |
| `/homework/:tid` | 200 | `{tdoc, tsdoc, udict, ddocs, page}` | 作业详情 |
| `/discuss` | 200 | `{ddocs[], dpcount, vnodes[], ...}` | 讨论区 |
| `/training` | 200 | `{tdocs[], tpcount, tdict}` | 训练题单 |
| `/d/:domain/p` | 200 | 同 `/p` | 多域预留（本站只有 `system`） |
| `/login`、`/register` | 200 | `{redirect, builtInLogin, loginMethods}` | 登录 / 注册 |
| `/api/user` | 200 | 当前用户对象 JSON | **登录态探针** |
| `/api/domain` | 200 | 当前域对象 JSON | 站点元信息 |
| `/ws` | **101 Switching Protocols** | WebSocket | 实时评测状态（待验证） |

**探测未命中的路由**（注意：**"未命中"不等于"不存在"**，仅说明该路径拼写没匹配到路由，不要据此断定功能缺失）：

- `/discussion`、`/discussion/:id` → 404。讨论区的真实路由是 **`/discuss`**（已实测可用）
- `/api/problem`、`/api/record`、`/api/contest`、`/api/user/:id`、`/api/domain/system` → 404。`/api/*` 命名空间只覆盖**当前上下文**（当前用户、当前域），**不是**实体级 REST API。实体的读取走 2.3 表的页面路由 + JSON 协商
- `/api/status`、`/api/config`、`/api/plugins`、`/api/perm`、`/api/language`、`/api/fs`、`/api/judge` → 均未命中

> 这一条很关键：**不要试图去找一套"标准 REST API"**。这个站点的 API 风格是"页面路由 + 内容协商"，这是 Hydro 的设计。按这个风格写客户端，一切顺畅；按 REST 思维去找端点，会白白浪费几天。

---

## 3. 接口底座（本次实测最重要的发现）

### 3.1 JSON 内容协商【实测】

**请求任意页面路由时，加上请求头 `Accept: application/json`，服务端返回该页面的原始数据 JSON，而不是 HTML。**

验证样本（同一路径，仅请求头不同）：

```
GET /p/1                                    → Content-Type: text/html      （SSR 渲染页）
GET /p/1  + Accept: application/json        → Content-Type: application/json
                                              body: {"pdoc":{...},"udoc":{...},"psdoc":null,...}
```

已在以下路由全部验证生效：`/p`、`/p/1`、`/ranking`、`/user/1`、`/contest`、`/contest/:tid`、`/homework`、`/homework/:tid`、`/discuss`、`/training`、`/record`、`/login`。

**对照测试（以下方式无效，不要浪费时间）**：

| 尝试方式 | 结果 |
|---|---|
| `Accept: application/json` | ✅ 返回 JSON |
| `X-Requested-With: XMLHttpRequest` | ❌ 仍返回 HTML |
| `?json` 查询参数 | ❌ 仍返回 HTML |
| `Accept: application/json` + `X-Requested-With` | ✅ 返回 JSON（与单独用 Accept 等价） |

> **给客户端的硬性约定**：网络层必须统一注入 `Accept: application/json`。建议封装成拦截器，禁止任何单个接口自行设置。

### 3.2 四种必须处理的响应形态【实测】

这是客户端最容易做错的地方。同一个接口在不同状态下，会返回结构完全不同的 body，而且**HTTP 状态码与"是否成功"没有必然关系**。

**形态一：正常数据**（HTTP 200）

```json
{ "page": 1, "pcount": 183, "pdocs": [ ... ] }
```

**形态二：未登录"软跳转"** —— **HTTP 状态码是 200**

```json
{ "url": "/login?redirect=%2Fp%2F1%2Fsubmit" }
```

实测自：`GET /record`、`GET /record?uid=1`、`POST /p/1/submit`、`GET /p/1/submit`、`POST /logout`（全部是 HTTP **200** 且只含 `url` 字段）。

**形态三：业务错误信封** —— HTTP 400 / 403 / 404，`code` 在 body 里

```json
{ "error": { "message": "Field {0} validation failed.",
             "stack": "", "params": ["rid"],
             "name": "ValidationError", "code": 403 } }
```

实测自：
- `GET /record/1`（`rid` 格式非法）→ `ValidationError`, `code: 403` —— 注意**参数校验优先于权限判断**
- `GET /p/NOSUCHPROBLEM` → `ProblemNotFoundError`, `code: 404`
- `GET /zzz-not-a-route-xyz` → `NotFoundError`, `code: 404`
- `GET /discuss/new`（缺 required 参数）→ `ValidationError`, `code: 403`
- `GET /p?sort=submit`（枚举值非法）→ `ValidationError`

**形态四：网络层 / 网关异常**

超时、连接失败，或 Cloudflare 拦在中间返回 5xx / 1035 之类的 **HTML 页**。此时 body 不是 JSON。

> **v1.3 更正（重要）**：v1.0 记录过"路由未命中返回 HTML 错误页（非 JSON）"，**该结论是错的**。
> 实测同一未命中路由 `/zzz-not-a-route-xyz`：
> `Accept: application/json` → **404 + JSON 错误信封**；`Accept: text/html` 或 `*/*` → 404 + HTML 错误页。
> 也就是说，**响应格式由 `Accept` 协商决定，与路由是否命中无关**。只要网络层恒发 `Accept: application/json`，
> Hydro 的错误一律是 JSON，客户端不需要为"错误页是 HTML"做特殊分支。
> 但形态四仍然存在，首字符 / Content-Type 防御要继续保留 —— 理由换了，从"路由未命中"换成"网关层可能介入"。

**响应形态矩阵**（M0 脚本每跑一次就会重新验证一遍）：

| 请求 | 状态码 | 形态 |
|---|---|---|
| `GET /p`、`GET /api/user` | 200 | 正常数据 |
| `GET /record`、`POST /p/1/submit`（匿名） | **200** | **未登录软跳转** |
| `GET /p/NOSUCHPROBLEM`、`GET /zzz-not-a-route-xyz` | 404 | 业务错误信封 |
| `GET /record/1`（rid 非法） | 403 | 业务错误信封 |
| 超时 / Cloudflare 5xx | 5xx / — | 非 JSON（HTML 或空） |

**结论：客户端的响应解析必须是"先看 body 结构，再看状态码"，而不是反过来。** 这段逻辑收敛到一个统一的响应分发器，返回值四个分支：

```kotlin
sealed interface HydroResult<out T> {
    data class Success<T>(val data: T) : HydroResult<T>
    data class NeedLogin(val redirectUrl: String) : HydroResult<Nothing>   // HTTP 200！
    data class Failure(val code: Int, val name: String, val message: String) : HydroResult<Nothing>
    data class TransportError(val cause: Throwable) : HydroResult<Nothing> // 含网关 HTML
}
```

### 3.3 登录契约【实测】

**登录页数据**（`GET /login` + JSON）：

```json
{ "redirect": "", "builtInLogin": true, "loginMethods": [] }
```

- `builtInLogin: true` → **站内账号密码登录可用**（这是最省事的路径）
- `loginMethods: []` → 没有配置第三方 OAuth 登录方式（如 QQ / 洛谷等），App 不需要做第三方登录
- `redirect` → 登录成功后的回跳地址

**登录表单字段**（从 `POST /login` 表单提取，完整字段）：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `uname` | text | 用户名 |
| `password` | password | 密码 |
| `rememberme` | checkbox | 记住登录 |
| `tfa` | hidden | 二次验证令牌（One-Time Password） |
| `authnChallenge` | hidden | WebAuthn / Passkey 挑战值 |
| `login_submit` | submit | 提交按钮标识 |

**给 App 的设计含义：**
- `tfa` 字段存在 → 站点**可能**开启了二次验证。App 的登录流程必须预留"需要验证码"的分支（登录响应要求 tfa 时弹输入框），不能假设一次 POST 就成功。**是否真的开启属于【未验证】，M0 确认。**
- `authnChallenge` → 支持 Passkey。一期**不做**（移动端 WebAuthn 复杂度高、收益低），但登录时应优先走 `uname/password` 路径。
- **表单中未发现 CSRF token 字段**【实测】；但 JSON POST 是否要求额外 CSRF 请求头**【未验证】**，M0 用真实账号验证。

**`POST /login` 实际契约**【实测 2026-09-13 + Hydro 源码 `UserLoginHandler.post` 交叉确认】：
- `application/x-www-form-urlencoded`，必填只有 `uname` / `password`；`rememberme`（布尔）、
  `redirect`、`tfa`、`authnChallenge` 均可选（**`login_submit` 并不需要**）。App 发送
  `rememberme=true` 换取持久会话。
- 用户不存在 → 404 信封 `UserNotFoundError`（`"User {0} not found."`，`params` 带用户名）；
  密码错误 → 403 信封 `VerifyPasswordError`（`"Passwords don't match."`）。
- **成功 → HTTP 200 + `{"url":"<首页>"}` + `Set-Cookie` 新会话**（服务端会重建 session）。
  无 `Referer` 时回跳首页 `/`，不会软跳回 `/login`。
- 限流：每用户名 60 秒 5 次（`user_login_id`）、每 IP 60 秒 30 次，超限抛
  `OpcountExceededError`。连续重试登录时留意。

**登录态探针**（`GET /api/user` + JSON）【实测】：

```json
{ "_id": 0, "uname": "Guest", "mail": "Guest@hydro.local", "perm": "BigInt::37483536664552833153",
  "role": "guest", "priv": 8,
  "timeZone": "Asia/Shanghai", "codeLang": "bash",
  "preferredEditorType": "...", "avatar": "gravatar:Guest@hydro.local",
  "theme": "light", "formatCode": true, "gender": 2, ... }
```

这是**判断登录态的标准方式**：`_id > 0 && role != "guest"` 表示已登录。

> **v1.11 更正（关键）**：`/api/user` 的响应**无论登录与否都没有 `authn` 字段**。Hydro 里的
> `authn` 是用户对象上"是否启用了 WebAuthn"的属性，**与会话登录态完全无关**——v1.3 的
> "用 `authn == true` 判已登录"是把字段语义搞混了（由此产生 App 侧"登录成功却显示未登录/
> 报密码错误"的 bug，2026-09-13 实测定位并修复）。
> 已登录响应（实测样本）：`{"_id":5,"uname":"...","role":"root","avatar":"qq:...",...}`；
> 游客响应（实测样本）：`{"_id":0,"uname":"Guest","role":"guest",...}`。
> 判定统一写 `_id > 0 && role != "guest"`。

**用户偏好字段可服务端同步**（都是【实测】存在的字段）：`timeZone`、`codeLang`（默认代码语言）、`theme`、`formatCode`、`showTimeAgo`、`fontFamily`、`codeFontFamily`、`preferredEditorType`、`backgroundImage`。

但**不是全部都要同步**——按 5.0 的迁移边界分两类：

| 迁移（语义型，影响行为） | 不迁移（外观型，会破坏原生观感） |
|---|---|
| `timeZone`（时间显示）、`codeLang`（编辑器默认语言）、`theme`（仅作首启初值）、`formatCode`、`showTimeAgo` | `fontFamily`、`codeFontFamily`、`backgroundImage`、`preferredEditorType` |

学生在网页端调好的**默认代码语言**能带进 App，这是体验连贯性的来源；而把网页的 Open Sans / Source Code Pro 搬进 App，正是"一眼看出是套壳"的典型原因。

### 3.4 核心数据契约

#### 3.4.1 题目列表 `/p`【实测】

```
请求参数：
  page    int     页码，从 1 开始
  limit   int     每页条数，默认 100（实测 limit=5 生效）
  q       string  搜索关键词（中文需 URL 编码，实测「签到」命中 1 条）
  sort    string  排序，枚举受限（实测 sort=submit → ValidationError，需枚举试用）
  tid     string  按竞赛筛选【推断】

响应：
{
  "page": 1,
  "pcount": 183,              // 题目总数
  "ppcount": 2,               // 总页数
  "pcountRelation": "eq",     // 计数关系，可能是 eq / gte 等【推断】
  "pdocs": [ ProblemBrief ],  // 当前页题目数组，最多 limit 条
  "psdict": {},               // 当前用户的每题解题状态字典（未登录为空）
  "qs": "",                   // 回显的搜索词
  "sort": "default"
}

ProblemBrief（列表项，字段已全量采样）：
{
  "_id": "6a8c447b58de462e1709be25",  // 内部文档 ID
  "owner": 8,                          // 出题人 uid
  "domainId": "system",
  "docType": 10,                       // 10 = 题目
  "docId": 2,                          // 数字题目号（对应 /p/2）
  "title": "两台机器",
  "tag": ["一 · 报到那天", "两数读入", "加减"],
  "hidden": false,
  "nSubmit": 26,                       // 提交次数
  "nAccept": 13,                       // 通过次数
  "stats": { "AC":15,"WA":2,"TLE":0,"MLE":0,"RE":1,"SE":0,"IGN":0,"CE":8,
             "s0":11,"s100":15 }       // 各评测状态计数 + 分数分桶
}
```

**两个必须注意的点：**

1. **列表项没有 `difficulty`（难度）字段**【实测，183 条全量采样确认】。网页端表格有"Difficulty"列，但 JSON 里没有对应字段。**App 一期不展示难度**，或标记为"待确认"后补。**不要凭猜测去伪造难度值。**
2. **`stats` 是分数制**（存在 `s0` / `s100` 分桶）。这个 OJ 用的是打分判题而不是纯 AC/WA 判定。列表页展示"通过率"时，用 `nAccept / nSubmit` 更稳妥，`stats` 留给详情页做分布图。

#### 3.4.2 题目详情 `/p/:pid`【实测】

`pid` 支持两种写法：数字（`/p/1`）和字符串题号（`/p/P1000`），**两者等价**。

```
响应顶层：
{
  "pdoc": { ... },        // 题目正文
  "udoc": { ... },        // 出题人用户对象
  "psdoc": null,          // 当前用户对该题的解题状态（未登录为 null）
  "title": "A+B Problem",
  "solutionCount": 0,
  "discussionCount": 0,
  "owner_udoc": null,
  "mode": "normal",
  "page_name": "problem_detail",
  "ctdocs": [],           // 包含此题目的竞赛
  "htdocs": []            // 包含此题目的作业
}

pdoc 字段（全量采样）：
  _id, owner, domainId, docType(10), docId(1),
  title: "A+B Problem",
  content: "{\"en\":\"This is the example A+B problem....\"}",   // ← 注意！是 JSON 字符串
  pid: "P1000",                                                  // 字符串题号
  tag: [...], hidden: false,
  nSubmit: 1, nAccept: 1,
  data: [ {_id:"1.out", name:"1.out", size:2, lastModified:"...", etag:"..."} ],  // 样例文件元数据
  additional_file: [],
  config: {
    count: 2,                    // 测试点数量
    timeMin: 1000, timeMax: 1000,     // 时间限制（毫秒）
    memoryMin: 64, memoryMax: 64,     // 内存限制（MiB）
    type: "default",
    langs: ["bash","c","cc","cc.cc98", ...]   // 本题允许的语言（29 项）
  },
  stats: { "AC":1, "WA":0, ..., "s100":1 }
```

**`content` 字段是嵌套 JSON 字符串**【实测】，格式为 `{"<语言代码>": "<Markdown 正文>"}`：

```json
"{\"en\":\"This is the example A+B problem.\\nIf you didn't see ...\"}"
```

客户端解析流程必须是**两步**：先取出 `content` 字符串 → 再 `JSON.parse` 一次 → 得到 `{语言: 正文}` 映射 → 按用户语言取正文（无 `zh` 时回落到 `en`）。**这一步做错会直接把 JSON 字符串渲染到屏幕上。**

**页面级附加字段**（不在 JSON API 响应里，但在 SSR 页面的 `window.UiContextNew` 中，客户端需要知道它们的存在）

由于这几个字段决定了 App 的行为，这里一并记录【实测，取自 SSR 页面的 `window.UiContextNew`】：

| 字段 | 实测值 | App 用途 |
|---|---|---|
| `postSubmitUrl` | `/p/1/submit` | **提交地址，服务端下发** |
| `getSubmissionsUrl` | `/record?fullStatus=true&pid=1` | **查本题提交记录，服务端下发** |
| `problemId` | `P1000` | 字符串题号 |
| `codeLang` | `bash` | 用户偏好的语言 |

> **设计建议（重要）**：`postSubmitUrl` 和 `getSubmissionsUrl` 应当**优先从响应中读取**，而不是在客户端拼接 `/p/{id}/submit`。原因很简单——站点没做改造，未来 Hydro 升级若调整路径，硬编码的客户端会整体失效。这是几乎零成本的防御性设计。
>
> 但这带来一个工程问题：JSON API 响应里**没有**这两个字段（【实测】`/p/1` JSON 响应顶层只有 `pdoc/udoc/psdoc/title/...`，无 `postSubmitUrl`）。所以一期先用**客户端常量 + 从 JSON 里读 `docId` 拼接**的保守做法，并在 M0 验证是否有办法拿到服务端下发的 URL（比如某个查询参数下会带出来）。**这条列为 M0 待办。**

#### 3.4.3 提交记录（部分实测，缺口在 M0 补）

| 接口 | 状态 |
|---|---|
| `GET /record` （列表） | 【实测】匿名返回 `{"url":"/login?redirect=%2Frecord"}` → **需要登录** |
| `GET /record?uid=1` | 【实测】同样返回登录跳转 |
| `GET /record?pid=1` | 【实测】同上（从 `getSubmissionsUrl` 得知此参数形式） |
| `GET /record/:rid` （详情） | 【实测】匿名 → `{"error":{...,"params":["rid"],"code":403}}` → **需要登录且需权限** |
| `GET /record?fullStatus=true&pid=1` | 【未验证】服务端下发的 URL，含义推测为"带完整状态" |

> **记录详情的字段结构是本次实测的最大缺口**——没有账号，无法获取任何一条真实记录。评测结果怎么展示（各测试点状态、时间/内存占用、编译错误信息、分数），必须在 M0 用真实提交打通后才能定稿。App 的"评测结果页"设计应按**最坏情况**预留：假定记录详情字段足够多、且包含富文本错误信息。

> **v1.12 实测更正**：按用户过滤记录的参数名是 **`uidOrName`**（Hydro RecordListHandler）。
> 传 `uid` 会被服务端**静默忽略**、退化为全站记录——App 排行榜→用户主页曾因此"看到所有人的
> 提交"。改为 `uidOrName=<uid>` 后实测：uid=3 返回 0 条、uid=5 返回 8 条且全部为本人记录。

#### 3.4.4 排行榜与用户【实测】

```
GET /ranking
{
  "udocs": [ { "_id":3, "uname":"tiansuo66623", "mail":"3475086817@qq.com",
               "perm":"BigInt::1370624076369558733505", "role":"default",
               "priv":16842756, "regat":"...", "loginat":"...",
               "avatar":"qq:3475086817" } ],
  "upcount": 1, "ucount": 31, "page": 2
}

GET /user/:uid
{ "isSelfProfile": false, "udoc": { "_id":1, "uname":"Hydro", "mail":"...",
                                    "perm":"BigInt::-1", "role":"root",
                                    "priv":4, "regat":"...", "loginat":"...",
                                    "avatar":"gravatar:Hydro@hydro.local" } }
```

**两个坑：**

1. **`mail` 字段直接暴露在榜单里**【实测】。这是服务端的字段裁剪问题，客户端必须在 Mapper 层**主动丢弃**——不进领域模型、不落本地库、不进日志、不上报。属于隐私红线。
2. **榜单 JSON 里没有 RP（积分）字段**【实测，采样确认】。但网页端榜单明确显示 RP 值（如 `tiansuo66623 / 297`）。RP 很可能来自另一个插件接口，或在 HTML 里单独渲染。**App 一期排行榜不显示 RP**，标记为待确认。

#### 3.4.5 竞赛与作业【实测】

两者共用一套 `tdoc` 结构，但字段集**不同**（互不覆盖），客户端需要分开建模：

```
GET /contest
{ "page":1, "tpcount":1, "qs":"", "rule":"",
  "tdocs":[ ... ], "tsdict":{...}, "groups":[], "group":"", "q":"" }

contest tdoc 字段：
  _id, content, owner, domainId, docType(30), docId, assign[], title, rule,
  duration, beginAt, endAt, pids[], attend, rated,
  allowPrint, allowViewCode, autoHide, keepScoreboardHidden,
  langs, lockAt, maintainer, files, privateFiles

GET /homework
{ "tdocs":[...], "calendar":{...}, "tpcount":4, "page":1, "qs":"", "groups":[], "group":"", "q":"" }

homework tdoc 字段：
  _id, content, owner, domainId, docType(30), docId, assign[], title, rule,
  beginAt, endAt, pids[], attend, rated, penaltySince, penaltyRules, files
```

**注意**：字段集不同意味着**不能共用一个 DTO**（除非所有字段都给默认值）。竞赛有 `duration` / `allowViewCode` / `lockAt`，作业有 `penaltySince` / `penaltyRules`。共用 DTO 会导致反序列化时字段错位或缺失报错。

> **v1.10 实测更正（MuMu 模拟器 / Android 12）**：`/contest` 与 `/homework` **列表**响应的
> `tdocs[]` 里 **没有 `docId`**（上表字段集来自详情响应样本，列表版被站点裁剪了）。
> 客户端曾因此把所有条目的 docId 映射成默认 0 → LazyColumn key 重复 → 打开列表即崩
> （`Key "0" was already used`）。修复：tid 一律用 `_id` 原样字符串（Hydro 的 `:tid`
> 即文档 `_id`，`/contest/<_id>` 详情实测可用），路由参数改为 String，列表 key 用 `_id`。

> **v1.12 实测补充（作业详情与认领）**：
> - `GET /homework/:tid` 完整响应：`{tdoc, tsdoc, udict, ddocs, page, dpcount, dcount[, pdict, psdict, rdict]}`。
>   **`ddocs` 是讨论区文档，不是题目**（踩过：曾把它当题目列表解析）。题目认领后（或已截止时）
>   才附 **`pdict`**（docId → pdoc 精简版）；未认领且未截止时不给 `pdict`，题目列表为空。
> - **认领 = `POST /homework/:tid` + 表单 `operation=attend`**。Hydro 框架对 POST 请求按
>   body 里的 `operation` 字段分发到对应方法（如 `postAttend`）；**空 body POST 直接 405
>   MethodNotAllowedError**。成功返回 `{"url":"/"}`。
> - **`tsdoc.attend` 是数字 1/0，不是布尔**。`boolOrNull` 会得到 null，必须
>   `boolOrNull ?: (int == 1)` 兜底——否则已认领仍显示"认领"按钮。
> - `GET /contest/:tid/problems`【实测】：`{pdict, psdict, rdict, rdocs, tdoc, tsdoc, ...}`，
>   pdict 为 docId → 题名；字母标号按 `tdoc.pids` 顺序（Hydro `getAlphabeticId`：0→A…25→Z、
>   26→AA，26 进制）。未开始 / 进行中未报名会报 ContestNotLiveError / ContestNotAttendedError。

> **v1.13 实测补充（成绩表）**：`GET /contest/:tid/scoreboard` →
> `{tdoc, tsdoc, rows, udict, pdict, page_name, groups, availableViews}`，**匿名可访问**，
> 单响应约 150KB（19 人 × 26 题）。`rows` 为站点渲染好的矩阵：`rows[0]` 表头
> （`{type:"rank"|"user"|"total_score"|"problem", value, raw}`，problem 格 `raw`=docId、
> `value`=字母标号——站点直接下发，App 无需自行推算）；数据行的 user 格 `raw`=uid；
> record 格 `{value:"100"|"0"|"-", raw:rid|null, score?:int}`。⚠️ `udict` 明文含 `mail`，
> App 端**不解析 udict**；站点 `style`（HTML 内联背景色）不解析，判定色由客户端按分数表达。

#### 3.4.6 自测（pretest）契约【实测】

```
POST /p/:pid/submit        （form-urlencoded）
  lang=<语言键>             如 c / cc.cc20o2
  code=<源代码>
  pretest=true
  input[]=<第 1 组输入>      数组用重复键编码，每组一个 input[]
  input[]=<第 2 组输入>
→ 200 { "rid": "<ObjectId>", "url": "/record/<rid>" }

GET /record/:rid  → rdoc.testCases[]:
  { id, subtaskId, status, score, time, memory, message, input }
  - time 是浮点毫秒（如 0.588）；message 即该组程序 stdout（编译错误时看 compilerTexts）
  - status 与正式评测同一套状态码（1=AC, 7=编译错误, …）
```

- 自测**不计入提交数、不出现在记录列表**（contest=哨兵 ObjectId，被记录页过滤）
- 站点 `langs.yaml` **无 pretest 语言重映射**（源码有 `if (...pretest) lang = ...` 分支，但本站未配置）
- 限流：`limit.pretest` 60 次/分钟
- 自测提交同样**绝不自动重试**（与正式提交同理）

### 3.5 解析陷阱清单

按"踩了会出事"的严重程度排序。**建议在 Mapper 层一次性处理干净，绝不要让 UI 层去判断这些。**

| # | 陷阱 | 证据 | 处理方式 |
|---|---|---|---|
| 1 | 未登录返回 HTTP **200** + `{"url":"/login?..."}` | 【实测】 | 统一分发器识别 `url` 字段 → 判定为待登录，导航到登录页并带上 redirect |
| 2 | 业务错误 HTTP **200** + `error` 信封 | 【实测】 | 统一分发器识别 `error` 字段 → 按 `code` 分派提示 |
| 3 | 业务错误 / 未命中路由的 body 形态**由 `Accept` 决定** | 【实测】**v1.3 更正**：`Accept: json` 时未命中路由返回 JSON 404；`Accept: text/html` 或 `*/*` 才返回 HTML 错误页 | 网络层恒发 `Accept: application/json` → 错误必为 JSON。**首字符 / Content-Type 防御仍要保留**，但只需覆盖"网关吐 HTML"这一种情况（Cloudflare 5xx / 1035），不必为路由未命中的假象写分支 |
| 4 | `content` 是嵌套 JSON 字符串 | 【实测】 | 两步解析；解析失败时降级为纯文本展示，不要崩 |
| 5 | `perm` 是 `"BigInt::<数字>"` 字符串 | 【实测】 | **原样透传，不解析**。客户端不需要权限位运算 |
| 6 | `mail` 在榜单/详情里泄露 | 【实测】 | Mapper 层丢弃，禁止进入 model |
| 7 | `avatar` 有多种形态 | 【实测】**v1.14 补测轮③更正**：是**前缀式**而非"完整 URL"。共三种前缀 —— `qq:3475086817`、`gravatar:<邮箱>`、**`url:/file/9/.avatar.jpg`**（`url:` 后面是**站内相对路径**）。更正依据：`GET /ranking` 全量 31 位用户实测分布为 `qq:` 24 ／ `gravatar:` 6 ／ `url:` 1，**无一条是 `https://` 开头的绝对地址**；`GET /user/9` 同为 `url:/file/9/.avatar.jpg`。旧表里那条"完整 URL `https://q1.qlogo.cn/...`"是**转换后的结果**被误记成了原始值 —— 本站客户端对 `qq:` 前缀恰好也生成该域名的 URL，故极易混淆 | Mapper 统一转成可加载 URL。**四种形态都要处理**：`qq:` → `q1.qlogo.cn`；`gravatar:` → `s.gravatar.com`（⚠️ 该域名国内网络不可达，见 E.8）；`url:` → **相对路径须拼站点根**（`BuildConfig.SITE_BASE_URL`），否则整类头像静默丢失；裸绝对 URL 原样放行（站点换对象存储直链时的保险）。⚠️ 原实现缺 `url:` 分支 → `else -> null` → 头像整块空白且不报错 |
| 8 | 时间为 ISO8601 UTC，用户时区为 `Asia/Shanghai` | 【实测】`"2026-08-24T06:44:34.653Z"` + `timeZone` 字段 | 统一按用户 `timeZone` 转换展示；解析失败不要显示 `1970` |
| 9 | 分页存在 `pcountRelation: "eq"` | 【实测】 | **不要用 `pcount` 精确判断"还有没有下一页"**（内容增删会导致总数漂移）。用 `page * limit < pcount` 且对结果做 `_id` 去重 |
| 10 | 题目列表**无难度字段** | 【实测】 | 一期不展示，或标"待确认" |
| 11 | 榜单**无 RP 字段** | 【实测】 | 一期不展示 |
| 12 | 富文本正文可能含 HTML / LaTeX / 图片外链 | 【推断】 | 渲染层需过滤 `script` / `iframe` 等风险标签；外链图片统一走懒加载与失败占位 |
| 13 | `docType` 是魔数（10=题目，30=竞赛/作业） | 【实测】 | 定义为枚举常量，不散落魔法数字 |
| 14 | 竞赛 tdoc 与作业 tdoc 字段集不同 | 【实测】 | 分开建模，各自给默认值 |

---

## 4. 功能方案

### 4.1 功能全景与优先级矩阵

优先级定义：
- **P0** = 一期必做，缺了做题闭环就不成立
- **P1** = 一期之后第一优先，接口已实测可用
- **P2** = 值得做但有前置条件
- **不做** = 明确排除并说明理由

| 优先级 | 功能 | 数据来源 | 证据等级 | 验收标准 |
|---|---|---|---|---|
| **P0** | 登录 / 登出 / 游客浏览 | `POST /login`、`GET /api/user` | 【实测】表单字段 | 登录成功进入题库；杀进程重开仍保持登录；游客可浏览题库 |
| **P0** | 题库列表（分页 + 下拉刷新 + 触底加载） | `GET /p?page&limit` | 【实测】 | 183 题可完整翻完，无重复、无丢失 |
| **P0** | 题库搜索 | `GET /p?q=` | 【实测】 | 中文关键词命中正确结果 |
| **P0** | 标签筛选（按章节分组） | `GET /p` 的 `tag[]` 聚合 | 【实测】 | 133 个标签按序号分组展示，可多选筛选 |
| **P0** | 题目详情（题干 + 样例 + 限制 + 标签 + 统计） | `GET /p/:pid` | 【实测】 | Markdown + LaTeX 正确渲染；代码块可横向滚动 |
| **P0** | 代码编辑器（高亮 + 行号 + 符号栏 + 字号 + 草稿） | 本地实现 + `config.langs` | 【实测】语言清单 | 29 种语言可选；输入中文/符号不错乱；切页草稿不丢 |
| **P0** | 代码提交 | `POST /p/:pid/submit` | **【未验证】** | M0 打通；提交后能拿到提交 ID |
| **P0** | 评测状态实时反馈 | `GET /record?...` 轮询 + `/ws` | **【未验证】** | 从"等待"到"评测中"到"完成"状态可见且自动刷新 |
| **P0** | 评测结果详情（各测试点 + 时间/内存 + 错误信息 + 分数） | `GET /record/:rid` | **【未验证】** | CE / WA / TLE / RE 各自有清晰可读的反馈 |
| **P0** | 我的提交记录（按题目 / 全部分组） | `GET /record?pid=`、`GET /record` | **【未验证】** | 列表分页正常，可点进详情 |
| **P0** | 设置（主题、编辑器字号、默认语言、服务器地址） | 本地 + `/api/user` 偏好 | 【实测】字段 | 偏好持久化；默认语言从服务端同步 |
| **P1** | 排行榜 | `GET /ranking` | 【实测】 | 分页展示；**不显示 mail / 不显示 RP** |
| **P1** | 用户主页 | `GET /user/:uid` | 【实测】 | 展示用户名、头像、注册时间；不展示邮箱 |
| **P1** | 竞赛列表 + 详情 | `GET /contest`、`/contest/:tid` | 【实测】 | 展示赛制、起止时间、题目列表 |
| **P1** | 作业（题单）列表 + 详情 | `GET /homework`、`/homework/:tid` | 【实测】 | 展示截止时间、题目列表 |
| **P1** | 从题单/竞赛点进题目做题并提交 | 复用 P0 链路 | 【推断】 | 需处理 `tid` 上下文 |
| **P2** | 讨论区 | `GET /discuss` | 【实测】 | 只读浏览 + 发帖 |
| **P2** | 训练题单 | `GET /training` | 【实测】（当前为空） | 站点无数据，可后置 |
| **P2** | 题目收藏 | 【未验证】 | — | 需先确认接口存在 |
| **P2** | 离线草稿 + 云同步 | 本地 + 【未验证】 | — | 依赖服务端草稿接口 |
| **不做** | 注册 | `POST /register` | 【实测】存在 | 注册流程涉及邮箱/验证，移动端收益低，引导到网页 |
| **不做** | Passkey / WebAuthn | `authnChallenge` | 【实测】存在 | 移动端复杂度高、收益低 |
| **不做** | 二次验证（tfa）完整流程 | `tfa` 字段 | 【未验证】是否启用 | 若站点未开启则一期不做；M0 确认 |

### 4.2 一期（M1）功能详述

#### 4.2.1 登录

**流程**

1. 启动 → `GET /api/user` 探针 → `_id > 0 && role != "guest"` 则直接进题库，否则进游客态
2. 游客态下所有页面可浏览（题库/详情/榜单/竞赛/作业都能匿名看）
3. 触发需登录操作（提交代码、看记录、看详情里的个人状态）→ 跳登录页，带上 `redirect`
4. `POST /login`（`uname` + `password` + `rememberme`）→ 成功则写 Cookie 持久化 → 回跳原页面
5. 若响应要求 `tfa` → 弹出验证码输入（预留分支，M0 确认是否启用）

**设计要点**

- **游客优先，登录不前置**。这个站的题库、题目详情、榜单全部匿名可看。一上来就挡登录墙，会白白劝退用户。只有在"提交代码"和"看我的记录"时才要求登录。
- **Cookie 持久化**：登录态靠 Cookie 维持，需要自定义 `CookieJar`（OkHttp）+ 加密落盘（EncryptedSharedPreferences / DataStore + Keystore）。
- **【待确认】Cookie 名称**：本次无账号，未观察到 `Set-Cookie`。M0 必须记录真实的 session cookie 名（Hydro 通常为 `sid`）。**这是登录功能的前置阻断项。**
- **登出**：`POST /logout`【未验证路由】，M0 确认；兜底方案是清空本地 Cookie 即可。

#### 4.2.2 题库列表

**构成**（自上而下）

```
┌─────────────────────────────────┐
│ 搜索框（常驻顶部，可折叠）        │
│ 标签筛选入口 → 打开筛选抽屉       │
├─────────────────────────────────┤
│ 题目卡片                         │
│   P1000  A+B Problem             │
│   [一 · 报到那天] [两数读入]      │
│   通过 13 / 提交 26              │
│   状态点：AC / 尝试过 / 未做      │
├─────────────────────────────────┤
│ 触底自动加载下一页               │
└─────────────────────────────────┘
```

**设计要点**

- **卡片而非表格**。手机宽度装不下网页那张 5 列表格（ID / 名称 / AC-Tried / 难度 / 标签）。改成两行卡片：第一行题号 + 标题，第二行标签 + 通过率 + 状态圆点。
- **状态圆点**：`psdict`（当前用户每题解题状态）【实测存在，未登录为空 dict】。已登录时用圆点区分 AC（绿）/ 尝试过 / 未做。**游客态隐藏状态圆点，而不是显示"未做"**——否则用户看到"未做"会以为是自己没做，实际是没登录。
- **标签筛选按章节分组**。133 个标签平铺是灾难。按 `一 ·`、`二 ·`、`三 ·` 这类序号前缀分组，第一层显示章节，第二层显示该章节下的知识点标签。这是一次"把网页端的列表变成移动端导航"的升级。
- **难度不做**（列表 JSON 无此字段）。
- **分页**：`limit` 用 20（移动端不需要一次 100 条，减少流量与渲染压力），`page` 递增，结果按 `_id` 去重。

#### 4.2.3 题目详情

**构成**

```
┌─────────────────────────────────┐
│ 标题 + 题号 + 标签 chips          │
├─────────────────────────────────┤
│ Tabs: 题目 | 提交记录(n)          │
├─────────────────────────────────┤
│ 题干（Markdown + LaTeX + 表格）   │
│   时间限制 1000ms / 内存 64MiB    │
│   样例输入 / 输出（可复制）        │
│   统计数据（AC/WA/TLE 分布）      │
├─────────────────────────────────┤
│ [底部固定栏] 语言▾   [去写代码]    │
└─────────────────────────────────┘
```

**设计要点**

- **底部固定操作栏**。手机竖屏下滚动题干时，"去写代码"必须随时可点。这是移动端最重要的一个操作入口。
- **先展示，再优化**：题干里 `config.timeMin/timeMax` 是毫秒（`1000`），要转成人类可读的 `1000 ms`；`memoryMin/Max` 是 MiB（`64`）→ `64 MiB`。注意是 **min/max 区间**，取值上限作为"限制"展示即可（M0 确认是否真存在区间差异）。
- **样例**：`data[]` 只给了样例文件的**元数据**（文件名、大小、etag），**没有内容**【实测】。样例的输入输出内容需要从 `content` 的 Markdown 正文里解析（题目作者通常把样例写在正文中），或走文件下载接口【未验证】。**这一点要在 M0 前确认清楚**——如果样例只存在于正文 Markdown 里，解析逻辑就得能识别正文中的样例代码块。
- **题干里的图片**：可能是相对路径或外链，需要统一处理成绝对地址 + 带 Cookie 加载【推断】。

#### 4.2.4 代码编辑器

这是整个 App 的**体验胜负手**。手机写代码的所有摩擦都在这里。

**方案选型：WebView + CodeMirror 6**

| 候选 | 结论 | 理由 |
|---|---|---|
| **WebView + CodeMirror 6** | ✅ **推荐** | 移动端成熟、体积小、29 种语言高亮开箱可用、可深度定制符号栏；与本站 `LANGS` 的 `highlight` 字段天然对应 |
| WebView + Monaco | ❌ 排除 | Monaco 是桌面编辑器（VS Code 内核），移动端性能与体积都不可接受 |
| 原生自绘（Compose 文本编辑器） | ❌ 排除 | 语法高亮、缩进、撤销、IME 兼容全部要自己写，工作量巨大且效果更差 |

**关键：本站已经提供了高亮映射**【实测，取自 `entry.js` 的 `window.LANGS`】。每种语言都带有 `highlight` 与 `monaco` 字段，直接拿来映射 CodeMirror 的 mode：

| OJ 语言 key | 显示名 | highlight |
|---|---|---|
| `c` | C | `c` |
| `cc` / `cc.cc11` / `cc.cc14` / `cc.cc17` / `cc.cc20` / `cc.cc98`（各含 `o2` 变体） | C++ / C++11 / C++14 / C++17 / C++20 / C++98 | `cpp` |
| `cs` | C# | `csharp` |
| `go` | Golang | `go` |
| `hs` | Haskell | `haskell` |
| `java` | Java | `java` |
| `kt` / `kt.jvm` | Kotlin / Kotlin(JVM) | `kotlin` |
| `js` | NodeJS | `javascript` |
| `pas` | Pascal | `pascal` |
| `php` | PHP | `php` |
| `py` / `py.py2` / `py.py3` / `py.pypy3` | Python / Python2 / Python3 / PyPy3 | `python` |
| `r` | R | `r` |
| `rb` | Ruby | `ruby` |
| `rs` | Rust | `rust` |
| `bash` | Bash | `bash` |

共 **29 个语言 key**（C++ 的 13 个变体是同一门语言的不同标准/O2 开关）。

**注意：题目详情里有 `pdoc.config.langs`（本题允许的语言，实测 A+B 题为 29 项）**。语言选择器必须**以 `config.langs` 为准**，不能显示全局 29 种——某些题目会限制语言。这是很容易漏的一步。

**编辑器必备能力（按重要性排序）**

1. **行号 + 语法高亮 + 自动缩进**（基础）
2. **底部符号栏**（移动端刚需，网页端不需要）：`Tab`、`{}`、`()`、`[]`、`;`、`""`、`''`、`<`、`>`、`+`、`-`、`=`、`:`、`#`，以及**左右方向键**（手机上精确定位光标极难）。符号栏可横向滚动。
3. **撤销 / 重做**
4. **字号调节**（手指缩放或设置项；范围 10–20sp）
5. **全屏 / 展开编辑区**（横屏时题干与编辑区左右分栏）
6. **草稿自动保存**（本地，切页/杀进程不丢；按题目 + 语言分别保存）
7. **代码模板**（C++ 提供 `#include` 骨架、Python 提供 `input()` 读入骨架）——降低新手门槛
8. **复制 / 粘贴 / 全选**
9. **深浅色主题跟随**

**必须处理的移动端坑**

- **中文输入法**：CodeMirror 在 WebView 里的 IME 组合字（拼音候选阶段）容易错位或重复插入。需要在 `compositionstart` / `compositionend` 期间禁用高亮重排。这是移动端 Web 编辑器的头号坑。
- **软键盘遮挡光标**：键盘弹出后需滚动让光标可见。
- **WM 尺寸变化**：键盘弹出触发 `resize`，需保持滚动位置与光标位置。
- **字体的等宽性**：用户偏好里 `codeFontFamily: "Source Code Pro"`【实测】，但 App 不能依赖该字体已安装，需内嵌等宽字体（如 JetBrains Mono / Fira Code）。
- **横竖屏切换**：编辑内容必须保留，不能重建 WebView。

#### 4.2.5 提交与评测状态

**提交流程**

```
用户点「提交」
  → 本地校验（代码非空、语言已选）
  → POST {postSubmitUrl}   body: {lang, code, ...}   ← 字段名待 M0 确认
  → 响应给出提交 ID（字段名待 M0 确认）
  → 跳转「评测结果」页，状态 = 等待
  → 实时刷新（轮询 1s，指数退避至 3~5s；或 WS 推送）
  → 终态（AC / WA / TLE / MLE / RE / CE / SE）→ 停止刷新
```

**实时状态的两条路径**

| 方式 | 状态 | 建议 |
|---|---|---|
| **轮询** `GET /record?fullStatus=true&pid=:id` 或 `/record/:rid` | 【实测】路由存在、可用；但需要登录才能观察真实响应 | **一期主方案**。实现简单、可控。频率必须克制（见下） |
| **WebSocket** `/ws` | 【实测】握手成功（`101 Switching Protocols`） | **二期优化**。协议细节（是否需要订阅帧、消息格式）**完全未知** |

> ⚠️ **轮询频率必须克制**。站点前有 Cloudflare，高频轮询有触发风控的风险。建议策略：
> - 前 10 秒：每 1 秒一次
> - 10–30 秒：每 2 秒一次
> - 30 秒后：每 5 秒一次
> - 页面切后台：暂停轮询；回到前台：立即刷新一次
> - 达到最大时长（如 120 秒）仍未出结果：停止并提示"评测排队中，稍后回来查看"
>
> 这个策略应该写进代码，而不是留给"以后优化"。

**评测结果页展示**

必须让"为什么错了"一目了然。按终态分支设计：

| 状态 | 展示重点 |
|---|---|
| **AC** | 通过测试点数、耗时、内存、得分；给庆祝反馈（这是学生最有成就感的瞬间） |
| **WA** | 第一个失败的测试点编号、输入/输出（若站点允许展示）、期望输出 vs 实际输出 |
| **TLE** | 超时的测试点、时间限制 vs 实际耗时 |
| **MLE** | 内存限制 vs 实际占用 |
| **RE** | 运行时错误信息（stderr） |
| **CE** | **编译错误信息（完整高亮展示，可复制）**——这是学生最常遇到的，体验必须好 |
| **SE** | 系统错误，提示"评测机异常，请重试"，并允许一键重提 |

> **【M0 必须确认】** 记录详情里各测试点的输入/输出、错误信息是否对非出题人可见。这直接决定"WA 时能看到什么"——如果看不到数据和期望输出，页面的文案设计完全不同（要引导学生去网页端或找老师）。**不要假设能拿到全部信息。**

#### 4.2.6 我的提交记录

- **两个入口**：全部记录（`GET /record`）+ 单题记录（`GET /record?pid=:id`，从题目详情 Tab 进入）
- 列表项：结果状态色块、题号 + 标题、语言、耗时 / 内存、提交时间（按用户时区）
- 支持下拉刷新 + 触底加载
- **空态与游客态区分**：游客态显示"登录后查看我的提交"，而不是"暂无记录"

#### 4.2.7 设置

| 设置项 | 来源 | 说明 |
|---|---|---|
| 主题（跟随系统 / 浅色 / 深色） | 本地 + `/api/user.theme` | 首次启动读服务端偏好 |
| 默认代码语言 | 本地 + `/api/user.codeLang` | 服务端偏好优先 |
| 编辑器字号 | 本地 | 10–20sp |
| 编辑器字体 | 本地 | 等宽字体选择 |
| 服务器地址 | 本地（高级项，默认锁定） | 便于以后指向测试环境 |
| 登出 | `POST /logout` | 【未验证】路由 |

---

## 5. 界面与信息架构

### 5.0 视觉风格总纲：原生 Material 3，不复刻网页

**这是硬约束，不是偏好。以下每一条都不可协商。**

- **不复刻网站 UI**：网页端是桌面优先的 Bootstrap 式布局（顶部横向导航、侧边抽屉、数据表格、悬浮 dialog、直角小按钮）。App **不搬运它的任何视觉元素**——不搬配色、不搬字体、不搬组件形态、不搬信息密度。
- **一律使用 Android 原生形态**：Material 3（Material You）设计语言 + Jetpack Compose 原生组件。用户在系统里形成的操作直觉（返回手势、底部面板、Snackbar、下拉刷新、涟漪反馈）在 App 里必须一致。
- **信息架构重新设计**，不是"把网页塞进手机"。功能等价即可，**路径与呈现方式按移动端重做**。原有界面的组织方式（尤其是表格、整页跳转、横向菜单）一律废弃。

**唯一的判定标准**：把一个页面截图发给没见过这个 OJ 的人，他应该认为"这是一个 Android 应用"，而不是"这是一个网页的套壳"。

#### 网页端 → App 的形态对照

| 网页端（Hydro 默认主题） | App（原生 Material 3） |
|---|---|
| Bootstrap 风格顶部横向导航 + 侧边抽屉 | `NavigationBar`（底部 4 Tab）+ `NavigationDrawer` 收纳次级入口 |
| 数据表格（`<table>` + 横向滚动） | `ListItem` 卡片 + `LazyColumn` |
| 悬浮居中 `dialog` | `ModalBottomSheet` |
| 页面顶部 alert 消息条 | `Snackbar` / 内联 banner |
| 直角小圆角按钮（`.rounded.button`） | Material 3 `Button` / `FilledTonalButton` / `OutlinedButton` 三档层级 |
| 站点蓝色系主题 | Material 3 配色（Android 12+ 支持 Material You 动态取色） |
| Open Sans + Source Code Pro | 系统无衬线字体 + 内嵌等宽代码字体 |
| 整页刷新式跳转 | 单 Activity + Compose Navigation，支持预测式返回手势 |
| 无过渡动效 | Material 3 标准动效（共享轴过渡、容器变换、涟漪） |

#### 站点偏好字段的迁移边界（别搬错）

站点用户对象里有一批外观偏好字段【实测存在】。**其中大部分不应迁移到 App**，否则会直接破坏原生观感：

| 站点字段 | 是否迁移 | 理由 |
|---|---|---|
| `theme`（light / dark） | ✅ 迁移 | 语义一致，仅作为首次启动的初始值 |
| `codeLang`（默认代码语言） | ✅ 迁移 | 纯语义，与视觉无关 |
| `timeZone` | ✅ 迁移 | 影响时间展示，与视觉无关 |
| `fontFamily`（Open Sans） | ❌ **不迁移** | 网页字体，搬到原生会显得"不是安卓应用"。改用系统字体 |
| `codeFontFamily`（Source Code Pro） | ❌ **不迁移** | 改用内嵌现代等宽字体（JetBrains Mono 等） |
| `backgroundImage`（个人主页背景图） | ❌ **不迁移** | 网页个人主页的装饰，与原生列表式「我的」页不兼容 |
| `preferredEditorType` | ❌ **不迁移** | 网页编辑器选项（Monaco / Ace 之类），与 App 的 CodeMirror 方案无关 |
| `formatCode` / `showTimeAgo` | ⚠️ 可选 | 语义无害，可作为设置项，但属于行为而非外观 |

> **结论：只迁移"语义型"偏好，不迁移"外观型"偏好。** App 的外观完全由「本地设置 + 系统主题」决定，**不回读、不回写站点的外观字段**。

### 5.1 导航结构

```
底部导航（4 项）
├── 题库        /problems        ← 默认首页
│   └── 题目详情 /problem/{id}
│       ├── Tab 题目
│       ├── Tab 提交记录
│       └── 编辑器（全屏页）/editor/{id}
│           └── 评测结果 /submission/{rid}
├── 比赛        /contests
│   └── 竞赛详情 /contest/{tid}
│   └── 作业列表 /homework → /homework/{tid}
├── 记录        /submissions     ← 需登录
│   └── 评测结果详情
└── 我的        /profile
    ├── 设置
    ├── 排行榜（入口）
    └── 用户主页 /user/{uid}（内部跳转）
```

**为什么是 4 个 Tab**：题库、比赛、记录、我的。这四个覆盖了学生的完整使用路径：做题 → 打比赛 → 复盘 → 管自己。讨论区和训练题单在当前站点数据量为 0，不值得占 Tab 位，放到后续版本用别的方式承载。

**登录不放 Tab**。登录是个动作而不是目的地，用抽屉或"我的"页里的入口触发。

### 5.2 各页面构成（摘要）

> 下表中所有界面元素均使用 **Material 3 原生组件**，不存在网页式布局。具体组件选择见 5.5。

| 页面 | 核心区块 | 关键交互 |
|---|---|---|
| 题库 | 搜索框、标签筛选入口、题目卡片流、加载态 | 下拉刷新、触底加载、筛选抽屉 |
| 题目详情 | 标题栏、题干（Markdown）、样例、限制、统计、底部操作栏 | 代码块复制、Tab 切换、去写代码 |
| 编辑器 | 语言选择、代码区、符号栏、底部提交栏 | 全屏、撤销、字号、提交 |
| 评测结果 | 状态大卡、测试点列表、错误信息、操作按钮 | 复制错误、重新提交、返回题目 |
| 记录列表 | 状态筛选 chips、记录卡片流 | 下拉刷新、触底加载 |
| 比赛 / 作业 | 时间状态标签、题单列表 | 进入详情、点题做题 |
| 我的 | 头像昵称、数据概览、功能入口列表 | 登出、进设置 |

### 5.3 小屏优化要点

**核心原则：把网页端的"表格 + 横向滚动"改成"卡片 + 纵向滚动"。**

| 网页端问题 | 移动端做法 |
|---|---|
| 题目列表是 5 列表格，手机上要横向拖动 | 改成两行卡片，全部信息纵向排布 |
| 题目详情与编辑器同页，手机上都不够用 | 拆成两个页面，用"去写代码"衔接；横屏时左右分栏 |
| 底部导航菜单是横向长菜单 | 改成 4 Tab 底栏 + 抽屉收纳次级入口 |
| 代码区固定小字号 | 字号可调 + 全屏编辑模式 |
| 表格类内容（统计、测试点）横向溢出 | 卡片化或允许局部横向滚动 |
| 悬浮 dialog 表单在窄屏挤压 | 改用底部弹出面板（BottomSheet） |

**具体数值建议**
- 触控目标：所有可点击元素最小 **48dp × 48dp**
- 底部安全区：编辑器底部提交栏需避让手势条（`WindowInsets.navigationBars`）
- 状态栏：题目详情与编辑器页建议沉浸式 + 隐藏状态栏（编辑时）
- 列表项密度：卡片间距 8dp，卡片内边距 12–16dp
- 字号：正文 15–16sp，代码 13–14sp（可调）

### 5.4 主题：Material 3 + 动态取色 + 深色模式

- **基座**：Compose Material 3。Android 12+ 优先启用 `dynamicColor`（读取系统壁纸取色，即 Material You）；不满足时回落到自定义种子色（`ColorScheme` 由种子色派生）
- **深浅色三档**：跟随系统 / 常亮 / 常暗
  - 首次启动且本地无记录时，取站点 `theme` 字段作为初始值【实测字段】
  - 之后以本地设置为准，**不再回读、不再跟随站点**
- **种子色不是"选一个"，而是一份内置主题目录**（14 项，用户可在设置页直接切换）——见 5.4.1；无论选哪套，深浅两模式的正文对比度都 ≥ 4.5:1
- **代码高亮主题必须与 App 主题同步**，但**不能直接用站点的高亮配色**——那是网页主题的一部分。应在 CodeMirror 侧单独维护浅色/深色两套主题，色值从 Material 3 令牌派生，确保与 App 其余部分同源
- **WebView 内容必须"看不出是 WebView"**（本项目唯一的两处 WebView：题干、编辑器）：
  - 题干 WebView 背景**透明**，文字颜色/字号从 `MaterialTheme.typography` 与 `colorScheme` 注入
  - 编辑器 WebView 去掉边框、圆角与超出容器，背景色与容器一致
  - 切换深色时通过 JS 桥**实时注入新令牌**，绝不允许出现"App 是深色、WebView 是白底"的割裂
- 深色模式下题干里的 LaTeX 公式、表格边框、代码块背景、行内代码底色都要单独适配（最容易漏的地方）

#### 5.4.1 主题目录：清单驱动，可扩展

不做"选定一个种子色"，而是**内置一组主题供用户选**。理由：手机是私人设备，而 OJ 用户里有相当比例的人对界面色相当敏感；给一组，比替他们决定一个更合适。同时这也不增加维护成本 —— 整套配色由**一份清单生成**。

**主题清单（14 项）**

| # | id | 名称 | 种子色 | 变体 | 说明 |
|---|---|---|---|---|---|
| 1 | `jxau-blue` | 江农蓝 | `#185FA5` | TonalSpot | **默认**，站点主色 |
| 2 | `lake-cyan` | 湖蓝 | `#0E7490` | TonalSpot | 比江农蓝更青，清爽 |
| 3 | `celadon` | 青瓷 | `#0F6E56` | TonalSpot | 青绿釉色，长时间看不累 |
| 4 | `bamboo` | 竹青 | `#3B6D11` | TonalSpot | 偏黄的绿，与 AC 绿拉开明度 |
| 5 | `amber` | 琥珀 | `#BA7517` | TonalSpot | 暖橙黄，像台灯光 |
| 6 | `coral` | 深珊瑚 | `#D85A30` | TonalSpot | 暖橙红，醒目 |
| 7 | `rosewood` | 玫红 | `#993556` | TonalSpot | 偏深的玫色，沉稳 |
| 8 | `sakura` | 樱粉 | `#D4537E` | TonalSpot | 浅粉，明亮通透 |
| 9 | `indigo` | 靛紫 | `#534AB7` | TonalSpot | 夜间模式最舒服 |
| 10 | `violet-vivid` | 紫藤 | `#7C4DFF` | Vibrant | 高饱和，偏张扬 |
| 11 | `graphite` | 石墨 | `#44546A` | Neutral | 低饱和灰蓝，最不打扰 |
| 12 | `monochrome` | 单色 | `#4A4A4A` | Monochrome | 纯灰阶，黑白印刷质感 |
| 13 | `contrast-blue` | 高对比蓝 | `#185FA5` | TonalSpot @ 对比度 0.5 | 视力不佳者友好 |
| 14 | `follow-wallpaper` | 跟随壁纸 | — | 动态取色 | Android 12+ 从壁纸取色，**最原生**；12 以下回落到 `jxau-blue` |

**选色的一条硬约束**：主题色不得与判题状态色撞语义。见下。

**判题状态色不跟随主题（重要）**

AC / WA / TLE / MLE / RE / CE 这些状态色是**信息**，不是装饰。如果它们从主题派生，换成"竹青"主题后"答案错误"会变绿、"通过"会变红 —— 语义直接被破坏。

| 状态 | 语义 | 浅色模式 | 深色模式 |
|---|---|---|---|
| AC 通过 | 成功 | 固定绿 `#2E7D32` | `#81C784` |
| WA 答案错误 | 错误 | 固定红 `#C62828` | `#EF9A9A` |
| TLE / MLE | 超限 | 固定橙 `#E65100` | `#FFB74D` |
| RE / CE | 运行或编译异常 | 固定紫棕 `#6A1B9A` | `#CE93D8` |
| 评测中 / 等待 | 进行中 | 用主题 `primary`（非状态语义，可跟随） | 同左 |

**因此**：状态色定义在 `ui/theme/VerdictColors.kt`，**不进 `ColorScheme`**，任何页面都不得用 `colorScheme.primary` 表达判题结果。且状态**不能只靠颜色区分**——必须同时有文字或图标（10.2 有对应验收项）。

**生成流程（改一个 JSON 就能加主题）**

```
design/theme-seeds.json     ← 改这里（加一行就是加一个主题）
        │
        ▼  python tools/gen_themes.py          （依赖 Material 官方算法库）
        │
        ├── design/theme-catalog.json                    全部色值，供其他工具消费
        ├── android-app/.../ui/theme/ThemeCatalog.kt     Compose 直接引用（直接产出到工程内，避免忘记同步）
        └── design/theme-gallery.html                    单文件预览页，浅色深色并排，可离线打开
```

- **色值由算法推导，不是手调**。用 Material 官方的 `material-color-utilities`（`tone_from_argb` / HCT 色调板），保证角色之间对比度合规。实测 **13 套色板 × 深浅两模式 × 5 组关键配色 = 130 组，全部 ≥ 4.5:1**。
- **生成器会输出所有可传入 `ColorScheme` 的角色**（36 个命名参数），包括 `surfaceContainer*` / `inverse*`。这是刻意的：漏掉的角色会回落到 Compose 基线的紫色，届时某几个控件突然变紫，极难排查。
  除外的只有 `*Fixed` 系列（12 个）：`lightColorScheme()` / `darkColorScheme()` 的命名参数里**没有**它们，传进去会直接编译失败，因此生成器显式过滤掉这 12 个，它们保留 Material 默认值 —— 常规 M3 组件不使用它们，影响可忽略。（角色总数 = 36 可传 + 12 `*Fixed` = 48。）
- `ThemeCatalog.kt` 里 `THEME_CATALOG` 是数据，设置页直接遍历渲染即可 —— **新增主题不用动任何 UI 代码**。
- 卡片上标注的 `id` 一旦发布就不要改（本地存储与用户设置按它索引）。



### 5.5 Material 3 组件与视觉规范

#### 设计令牌（全部走 Material 3，不自造）

| 类别 | 取值 |
|---|---|
| 颜色 | `MaterialTheme.colorScheme` 全量语义色（`primary` / `surface` / `surfaceVariant` / `outlineVariant` / `error` …）。**禁止在 UI 层硬编码十六进制色值** |
| 字体 | `MaterialTheme.typography`：`headlineSmall` 页面标题、`titleMedium` 卡片标题、`bodyMedium` 正文、`labelSmall` 元信息 |
| 代码字体 | 内嵌等宽字体，字号 13–16sp 可调 |
| 圆角 | `shape.small / medium / large`（8 / 12 / 16dp），卡片统一用 `medium` |
| 间距 | 4dp 基准栅格；页面水平内边距 16dp；卡片内边距 16dp；卡片间距 8dp |
| 触控目标 | 不小于 48dp × 48dp |
| 图标 | Material Symbols（Rounded），不引第三方图标库 |

#### 页面骨架统一用 Scaffold 标准槽位

```
Scaffold(
  topBar     = TopAppBar（题库、比赛页用 LargeTopAppBar 以获得滚动收起效果）
  bottomBar  = NavigationBar（仅 4 个一级页面）
  fab        = 情境化：题库页无；题目详情页用 ExtendedFAB「写代码」
  snackbarHost = SnackbarHost
  contentWindowInsets = WindowInsets.safeDrawing   // 自动处理状态栏与手势条
)
```

#### 状态与反馈（一律用原生约定）

| 场景 | 原生做法 |
|---|---|
| 加载中 | 首屏居中 `CircularProgressIndicator`；翻页在列表底部用小尺寸指示器 |
| 空数据 | 空态插画 + 一句解释 + 一个行动按钮（如"换个关键词"） |
| 网络错误 | 错误态 + `Retry` 按钮，**不要弹窗** |
| 操作成功 | `Snackbar` |
| 轻量二次确认 | `ModalBottomSheet`；破坏性操作用 `AlertDialog` |
| 表单输入 | `OutlinedTextField` + `KeyboardOptions` 指定 `ImeAction` |
| 语言选择 | `ExposedDropdownMenuBox` 或 `ModalBottomSheet` + 单选列表 |
| 筛选 | `FilterChip`（可多选）+ `ModalBottomSheet` 容器 |
| 下拉刷新 | Material 3 `PullToRefreshBox` |
| 长按 | 上下文菜单 / 多选模式，不用右键式浮层 |

#### 动效

- 页面切换用 Navigation Compose 默认的共享轴过渡，**不自定义花哨转场**
- 列表项用 `animateItem`；状态变化用 `Crossfade` / `AnimatedContent`
- **尊重系统"移除动画"设置**：读取 `Settings.Global.ANIMATOR_DURATION_SCALE`，按系统缩放系数调整动画时长；系统关闭动画时不做过渡
- 提交按钮点击后立即置为 loading 并禁用，避免重复提交（与 4.2.5 呼应）

#### 要刻意避免的"网页味"

- ❌ 用整页 `WebView` 加载 OJ 页面（一眼就能看出是套壳）
- ❌ 复刻网页的"蓝白卡片 + 细边框表格"观感
- ❌ 页面里塞满等宽对齐的"信息表"
- ❌ 什么都用 `dialog`（原生优先 `ModalBottomSheet`）
- ❌ 顶部横向下拉菜单（用 `DropdownMenu` 或 BottomSheet）
- ❌ 无动效的瞬间跳转

#### 无障碍（平台要求，也是上架审核关注点）

- 所有图标按钮必须有 `contentDescription`
- `TalkBack` 下题目卡片、提交按钮、语言选择器可正常朗读
- 支持系统字体缩放：文字一律用 `sp`，**不要用 `dp` 写文字**（这会失去字体缩放能力）
- 支持外接键盘导航与 `Switch Access`
- **颜色不能是唯一的信息载体**：评测状态（AC / WA / TLE …）除颜色外必须有文字或图标标识，保证色盲用户可辨

---

## 6. 移动端专项方案

### 6.1 代码编辑器（纯 Compose 自绘）

> ⚠️ **v1.14 更正**：本节旧版写的是「WebView + CodeMirror 6」方案，**该方案已废弃**。
> 实际实现是**纯 Compose 自绘**（`BasicTextField` + `VisualTransformation` 语法高亮 + `Canvas` 装饰层）。
> 取舍理由：本项目对「原生观感」的要求（5.0）优先于 Web 编辑器带来的便利，
> 且本站 29 种语言的高亮映射可以自己维护（实测 5 类高亮已够用）。下文是**当前实现**的规格。

**分层契约（改编辑器前先认这一条）**

编辑器能力按「来源」分四层，**能落在哪一层取决于软键盘是否可用**——
IME 不产生按键事件，这是手机上做 IDE 功能的硬约束：

| 层 | 能力来源 | 软键盘可用 | 本项目的功能 |
| --- | --- | --- | --- |
| **文本层** | `onValueChange` 差异分析 | ✅ | 自动补配对、换行自动缩进、成对删除、选区包裹、Tab 缩进 |
| **样式层** | `Canvas` 装饰层 / `VisualTransformation` | ✅ | 语法高亮、配对括号高亮、当前行左侧竖线 |
| **按键层** | `onPreviewKeyEvent` | ❌ 仅硬件键盘 | 候选 ↑↓ 选择、Enter/Tab 采纳、Esc 关闭 |
| **工具条** | Compose 按钮 | ✅ | 符号条（把按键层能力暴露给软键盘用户） |

- **高亮只附加样式，绝不改变文本长度** —— `VisualTransformation` 的 `OffsetMapping` 必须用 `Identity`。
  任何「把 Tab 展开成对齐空格」都会让光标与选区**整体错位**，所以**刻意不做制表符对齐**
  （这条写在 `ComposeCodeEditor.kt` 文件头注释里，别走回头路）。
- **文本层为什么必须用差异分析**：软键盘（IME）根本不产生按键事件，`onValueChange` 是软键盘与
  实体键盘**唯一**都会经过的地方。`ui/editor/CodeEditActions.kt` 的 `apply(old, new)` 先算公共前/后缀
  得出 `inserted` / `deleted`，再按分支决定是否改写；**纯函数、零 UI 依赖**，可直接单测。
- 缩进单位是 **4 个空格**（`CodeEditActions.INDENT`），**刻意不用 Tab 字符**：Tab 的显示宽度
  取决于阅读环境，代码贴到别处会歪。
- ⚠️ **同一字符的插入歧义（实测踩到）**：当「插入的字符」与「它右侧已存在的字符」相同时
  （输入 `)` 而右侧已有自动补出的 `)`），diff 的公共前缀会把两者**归并到同一位置**，
  `inserted` 为空 → 用 diff 索引判断「跳过右括号」必然漏，表现为打出 `int main())`。
  判据**必须取自 `old.selection.end` 处 `old.text` 的字符**，而不是 diff 出来的插入位置。

**编辑动作清单**（`CodeEditActions.apply` 的分支顺序）

1. 删除单字符且右侧恰为其配对符 → **成对删除**（`()` 中间按一次退格删掉两个）
2. 插入单个字符：
   - `\n` → **换行继承当前行前导空白**；若光标前本行以 `{` 结尾则**再缩一级**
   - 插入的是闭符、且编辑前光标右侧就是同一个字符 → **跳过**（不算插入，只把光标移过去）；
     若跳过的是 `}` 且它所在行前面只有空白，顺手**退回一级缩进**
   - 插入左括号/引号 → **补配对**，光标停在括号内。补配对有三道闸门：
     预处理行（`#include` / `#define`）整行不管、已在字符串或行注释内不补、
     仅当插入点右侧是空白/行尾/`;`/`,` 时才补
   - 手打 `}` 且本行前面只有空白 → **退回一级缩进**
3. 选中一段内容后输入左括号/引号 → **用括号把选区包起来**（而不是替换掉），光标落在闭括号之前
4. 其余（粘贴、多字符替换、拖动选区）一律**原样放行** —— 那些是用户明确意图

**词法高亮**

- 只识别**注释 / 字符串 / 数字 / 关键字 / 内建**五类，不做完整语法分析。
  五类已覆盖「读代码找结构」的主要需求，且不会因语言细节写错而误染色。
- 附加规则：标识符后紧跟 `(` → 视作**函数名**并加粗。
- 配色统一走 `syntaxColorsOf(colorScheme)`，编辑器与只读 `CodeView`（评测详情页）共用同一套；
  **不引第三方高亮库** —— 第三方库默认配色是网页味的，会直接破坏 5.0 的原生观感要求。

**字体与字号**

- **清单驱动**：`ui/theme/EditorFonts.kt` 的 `EDITOR_FONTS`（与 5.4.1 的 `THEME_CATALOG` 同套路，
  **新增字体不用改任何 UI 代码**）。内置「系统等宽」（`FontFamily.Monospace`，不占包体）
  与「Cascadia Mono」（微软开源，SIL OFL 1.1，随包内置 363KB）。
- **归口在设置页**：经 `LocalEditorFontId` / `LocalEditorFontSizeSp`（`AppRoot` 提供）下发，
  编辑器页不再各自管字号；编辑器与只读 CodeView **共用同一份设置**。字号范围 10–22sp。
- ⚠️ **可变字体**（Cascadia Mono 的 `wght` 轴 200–700）**必须为 Normal/Medium/Bold 各声明一条
  `Font`**，否则 `FontWeight.Medium` 会取到 default instance 而失效。
- ⚠️ `FontVariation.Settings(weight, style)` **自己会派生 `wght` 轴**，再显式传
  `FontVariation.weight(x)` 会 `IllegalArgumentException: 'wght' must be unique`，
  且发生在类静态初始化期 → **App 启动即崩**。

**自动补全弹层**

- 候选 = 语言保留字（含忽略大小写次级匹配）+ 内建类型/常用函数 + 当前代码标识符（按出现次数加权）。
- 弹层锚定光标行（上方优先、越界翻下方并钳回），硬件键盘 ↑↓ 选择、Enter/Tab 采纳、Esc 关闭，点按亦可。
- **不用文字标签区分候选种类**（汉字标签挤占宽度，且对读代码没有帮助）：
  改画 **3dp 分类色条**，分类信息放进 `contentDescription` 供读屏使用。
- 宽度**按最长候选实测自适应**（`rememberTextMeasurer()`）并 `coerceIn(96,180)dp`；
  右侧越界时**钳回贴边**，不越出编辑器边界。

**符号条**

- 编辑区下方一排横滑按钮：`Tab`、`{`、`}`、`(`、`)`、`[`、`]`、`;`、`"`。
- 存在的意义：这些字符在软键盘上要切换两三层面板才能找到，而**按键层能力（Tab 缩进）软键盘根本给不了**。
  符号条把它们变成一次点按。
- 实现上走 `CodeEditorHandle.insert(text)` → 复用 `CodeEditActions.apply`，
  **与手打字符同一套规则**（所以点 `(` 也会自动补出 `)`），不另写一份逻辑。

**草稿策略**

- **本地优先**：编辑防抖 **600ms** 落 `SharedPreferences`（`SessionStore.draft` / `saveDraft`，**每道题一份**），
  不等网络 —— 手机做题最怕切出去回来代码没了。
- 站点的外观型偏好（`codeFontFamily` / `preferredEditorType`）**不迁移**，只迁语义型的 `codeLang`（见 5.4）。


### 6.2 题干渲染

**渲染链**：`content` 嵌套 JSON 字符串 → 按语言取正文 → Markdown → HTML → WebView 展示

- 用 Markdown 渲染 + KaTeX（数学公式）+ 代码高亮（highlight.js，与编辑器同款主题）
- **样式必须由 Material 3 令牌驱动**（依据 5.0）：正文字号/行高/字色、标题层级、链接色、引用条、表格边框色、行内代码底色，全部从 `typography` 与 `colorScheme` 派生后注入。**直接套 Markdown 库的默认样式是最典型的"网页味"来源**
- 标题层级映射到 Material 3 的 `titleLarge` / `titleMedium` / `bodyLarge`，而不是浏览器默认的 `h1`~`h6`
- 代码块：使用与编辑器一致的等宽字体与高亮主题，圆角 `shape.medium`，提供**原生**"复制"按钮（不依赖网页按钮）
- 图片：懒加载 + 失败占位 + 点击进入原生图片查看页（不使用网页式灯箱）
- 表格：允许**局部**横向滚动，不要撑破页面；窄屏下优先考虑卡片化重排
- **安全**：过滤 `script` / `iframe` / `on*` 事件属性【推断——源于题干是用户/出题人生成的富文本】
- **降级**：Markdown 解析失败时按纯文本展示，不白屏
- **备选方案**：若 WebView 的视觉融合成本过高，可评估改用 Compose 原生 Markdown 渲染库，代价是 LaTeX 与复杂表格支持较弱。**一期先用 WebView，把样式注入做扎实**

### 6.3 评测状态实时化

见 4.2.5。补充工程细节：

- 状态机：`Idle → Submitting → Pending → Judging → Finished(status)`
- 轮询放在 `ViewModel` 的 `CoroutineScope`，页面销毁时取消
- 切后台暂停（`Lifecycle.State.STARTED` 才轮询）
- 失败重试：网络错误按指数退避重试 3 次，之后显示"网络异常，点击重试"
- **幂等关注**：提交请求超时后**不要自动重试**（可能产生重复提交）；改为提示用户"可能已提交，去记录页确认"

### 6.4 网络与鉴权层

- **OkHttp + kotlinx.serialization**（或 Retrofit + 转换器）
- 拦截器统一注入 `Accept: application/json`
- 自定义 `CookieJar`：内存缓存 + 加密持久化；登录成功后持久化 cookie
- 统一响应分发器（四种形态，见 3.2）：
  ```
  Success(data)          ← body 无 url / error 字段
  NeedLogin(redirectUrl) ← body 含 "url"（HTTP 状态码是 200！）
  Failure(code, name, message) ← body 含 "error"
  TransportError(cause)  ← 超时 / 网关 HTML / 非 JSON
  ```
- **Content-Type 校验**：响应不是 JSON 时归入 `TransportError`（网关层 HTML），抛业务异常而不是让反序列化崩溃
- 超时：连接 10s / 读 30s（评测页轮询单独设更短）
- User-Agent：建议带上 App 标识（如 `JxauOJ-Android/1.0`），便于运维识别
- **域名固定为 `https://oj.wbhtqlorz.cv`**；`UiContext` 里的 `url_prefix` / `cdn_prefix` / `ws_prefix` 实测均为 `/`，说明是单域部署，App 可简化处理，但**保留读取这些字段的能力**以便未来换域

### 6.5 隐私与安全

| 项 | 措施 |
|---|---|
| `mail` 泄露 | Mapper 层丢弃，不允许进入 model / 日志 / 数据库 |
| Cookie / 密码 | 加密存储（Keystore）；禁止写入日志 |
| 代码内容 | 提交前不经日志；草稿加密存储可选 |
| 网络 | 强制 HTTPS；不做证书 pinning（站点在 Cloudflare 后，证书轮换风险高于收益） |
| WebView | 禁用 `setJavaScriptEnabled` 之外的无关能力；限制 `file://` 访问；题干 WebView 不注入任何原生接口 |
| 日志 | Release 构建关闭网络日志；错误上报前脱敏 |

---

## 7. 客户端技术方案

### 7.1 技术选型与依据

**本机开发环境已实测确认**（无需再装任何东西）：

| 组件 | 实测结果 | 用途 |
|---|---|---|
| Android SDK | `D:\IO\sdk`（`ANDROID_SDK_ROOT`），已装 `platforms/android-35`、`build-tools/34.0.0`+`35.0.0`、`cmdline-tools/latest`、`platform-tools`(adb) | 编译与安装 |
| JDK | `D:\IO\jdk17`（Temurin **17.0.20.1**） | AGP 8.x 要求 JDK 17 |
| Gradle | 已缓存 wrapper 发行版 **8.10.2**（`~/.gradle/wrapper/dists/gradle-8.10.2-bin`） | 构建 |
| Kotlin | 本机有独立 `D:\IO\kotlinc`（运行异常，但不影响——Gradle 用 Kotlin 插件，不依赖它） | — |
| Flutter / Dart | **未安装** | 故排除 Flutter 方案 |

**结论：采用 Android 原生 + Kotlin + Jetpack Compose，Gradle wrapper 命令行构建。**

| 选型 | 依据 |
|---|---|
| **Kotlin + Jetpack Compose (Material 3)** | 环境已就绪（JDK17 + SDK 35 + Gradle 8.10.2）；Compose 对响应式布局、深浅色、安全区的处理成本远低于 XML 布局 |
| **minSdk 26** | 覆盖 Android 8.0+，保证 `EncryptedSharedPreferences`、现代 WebView、协程等基础能力 |
| **targetSdk 35 / compileSdk 35** | 本机已装 `android-35`；满足当前应用商店上架要求 |
| **OkHttp + kotlinx.serialization** | 比 Retrofit 更轻；该站点的"四种响应形态"（3.2）需要自定义解析，手写分发器反而更清晰 |
| **Compose Navigation** | 单 Activity 架构，配合底部导航 |
| **ViewModel + StateFlow** | 四态（Loading / Empty / Error / Success）显式建模 |
| **DataStore（偏好）+ Room（草稿/缓存）** | 轻量持久化 |
| **Koin 或手动 DI** | 模块不多，手动 DI 更轻；若觉得繁琐则用 Koin |
| **WebView + CodeMirror 6** | 见 4.2.4 |
| **Material 3（含动态取色）** | 本项目风格基座，见 5.0／5.4／5.5。Android 12+ 启用 Material You 动态取色，低版本回落到种子色派生的 `ColorScheme` |
| **不引入任何 HTML/CSS 框架换皮方案** | 风格要求是原生 Material，任何"网页换皮"路线都与目标相悖 |

**明确排除项**

| 排除 | 原因 |
|---|---|
| Flutter | 本机未安装，且用户已有原生 Android 环境 |
| Jetpack Compose Multiplatform | 一期只需 Android，过度设计 |
| HTML 抓取（Jsoup） | 已实测存在 JSON 内容协商，抓 HTML 是自找麻烦且极易随站点改版失效 |
| 直接内嵌 WebView 加载整个网页 | 与"优化移动端做题体验"的目标背道而驰 |
| 复刻站点主题（Bootstrap 配色 / Open Sans / 站点高亮配色） | 会直接破坏原生观感，见 5.0 的迁移边界表 |
| 第三方 UI 组件库（各类"设计系统"） | Material 3 已提供完整组件与令牌，引入第三方只会造成风格混杂与包体膨胀 |

### 7.2 架构分层与目录结构

```
app/src/main/java/<pkg>/
├── data/
│   ├── remote/
│   │   ├── HydroApiClient.kt        // OkHttp + 拦截器 + CookieJar
│   │   ├── HydroResponse.kt         // 四形态响应分发器（核心，见 3.2）
│   │   └── endpoint/
│   │       ├── ProblemApi.kt        // /p, /p/:id, /p/:id/submit
│   │       ├── RecordApi.kt         // /record, /record/:rid
│   │       ├── UserApi.kt           // /api/user, /user/:id, /login, /logout
│   │       └── ContestApi.kt        // /contest, /homework, /ranking
│   ├── dto/                          // 与原文字段一一对应，全部给默认值
│   │   ├── ProblemBriefDto.kt
│   │   ├── ProblemDetailDto.kt
│   │   ├── SubmissionDto.kt
│   │   └── UserDto.kt
│   ├── mapper/                       // 所有解析陷阱在这里一次性处理干净
│   │   ├── ProblemMapper.kt          // content 两步解析、tag、统计
│   │   ├── UserMapper.kt             // ★ 丢弃 mail、avatar 形态归一化
│   │   └── TimeMapper.kt             // ISO8601 → 用户时区
│   └── local/
│       ├── PreferenceStore.kt        // DataStore：主题、字号、默认语言
│       ├── DraftDao.kt               // Room：代码草稿
│       └── CookieStore.kt            // 加密 Cookie
├── domain/
│   ├── model/                        // 干净的领域模型（无 mail、无 BigInt 字符串）
│   ├── repository/                   // ProblemRepository, AuthRepository, ...
│   └── LangCatalog.kt                // 29 种语言 key ↔ 显示名 ↔ highlight 映射
├── ui/
│   ├── theme/                        // Material 3：Color.kt / Type.kt / Shape.kt
│   │                                 // + ThemeCatalog.kt（由 design/ 生成）+ VerdictColors.kt（状态色，独立于主题）
│   │                                 // + 动态取色 + 站点外观字段的"只读不写"适配
│   ├── component/                    // 通用组件：题目卡片、状态徽标、空态、加载、错误、Retry
│   ├── problem/                      // 题库列表、题目详情
│   ├── editor/                       // 编辑器页 + WebView 桥 + 令牌注入
│   ├── submission/                   // 评测结果、记录列表
│   ├── contest/                      // 竞赛、作业
│   ├── ranking/, profile/, settings/
│   └── nav/                          // 导航图
└── assets/editor/                    // CodeMirror 6 产物 + 主题
```

**三条分层纪律**（直接来自本方案的实测发现）：

1. **所有解析陷阱集中在 `mapper/`**，UI 层只拿规整好的领域模型。绝不能让 UI 去判断 avatar 是不是 `qq:` 前缀。
2. **DTO 所有字段给默认值**。字段裁剪在这个站的接口里很常见（列表版 / 详情版 / 搜索版字段集不同，竞赛与作业 tdoc 字段集也不同）。
3. **四形态响应分发器是唯一的网络出入口**。任何绕过它的直接解析都会漏掉"200 但其实是登录跳转"这种情况。

**一条 UI 纪律**（来自 5.0 总纲）：

4. **UI 层禁止硬编码色值与字体**。所有颜色走 `MaterialTheme.colorScheme`，所有文字走 `MaterialTheme.typography` + `sp`，所有圆角走 `MaterialTheme.shapes`。WebView 侧通过 JS 桥接收同一套令牌。**唯一允许硬编码的地方是 `ui/theme/`**——那里放 `ThemeCatalog.kt`（生成物）、两套代码高亮主题，以及 `VerdictColors.kt`（判题状态色，**刻意不跟随主题**，见 5.4.1）。

### 7.3 数据层设计要点

- **分页封装**：统一的 `PagedData<T>(items, page, total, hasMore)`；`hasMore` 用 `page * limit < total` 计算，并对追加结果按 `_id` 去重
- **字段裁剪容错**：`pdoc.config` 缺失时用站点默认限制兜底展示，不崩
- **语言目录**：把附录 B 的映射表固化为 `LangCatalog` 常量，同时保留从 `pdoc.config.langs` 过滤的能力
- **时间**：统一存 UTC，展示时按 `userTimeZone`（默认 `Asia/Shanghai`）格式化

### 7.4 构建与运行（命令行）

```bash
# 环境（本机实测路径）
export ANDROID_SDK_ROOT=D:/IO/sdk
export JAVA_HOME=D:/IO/jdk17

# 构建 Debug
./gradlew assembleDebug

# 直接装到设备
./gradlew installDebug

# Release（需配置签名）
./gradlew assembleRelease
```

`gradle/wrapper/gradle-wrapper.properties` 指向已缓存的 **8.10.2**，首次构建无需联网下载 Gradle 发行版。

---

## 8. M0 阻断项验证清单（开工前必须完成）

一期闭环中"提交 → 评测"这一段缺失实测数据。下表是**必须先用一个真实账号跑通**的验证项。建议写成一个一次性脚本（可用 Python/curl），跑完即弃。

| # | 验证项 | 方法 | 判定标准 | 阻断级别 |
|---|---|---|---|---|
| 1 | ~~Session Cookie 名称与属性~~ ✅ | 登录后检查 `Set-Cookie` | 通用 CookieJar 存取，实测登录态可维持 | 已闭环（v1.11） |
| 2 | ~~登录成功响应形态~~ ✅ | `POST /login` | 成功 → 200 + `{"url":"<首页>"}` + 新 Set-Cookie（源码+实测） | 已闭环（v1.11） |
| 3 | ~~是否需要 CSRF 头~~ ✅ | 实测 POST | 无需 CSRF（表单 POST 直达处理器） | 已闭环（v1.11） |
| 4 | 是否强制二次验证（tfa） | 用未配置 2FA 的账号登录 | 该账号未触发（实测登录成功） | 不阻断 |
| 5 | ~~提交请求体字段名~~ ✅ | App 内真实提交 | `lang` + `code` form-urlencoded 实测命中 | 已闭环（v1.11） |
| 6 | ~~提交响应中的提交 ID 字段~~ ✅ | 同上 | `{"rid":"...","url":"/record/..."}` | 已闭环（v1.11） |
| 7 | ~~记录详情完整字段~~ ✅ | `GET /record/:rid` + JSON | `rdoc` 包裹，状态/语言/内存/代码实测渲染 | 已闭环（v1.11） |
| 8 | 各测试点详情可见性 | 用普通学生账号提交一次 WA | AC 提交未覆盖（非 AC 时才下发） | 🟡 低风险（结构已容错） |
| 9 | ~~评测状态实时性~~ ✅ | 提交后轮询 | 退避轮询实测工作，AC 约 2 秒出结果 | 已闭环（v1.11） |
| 10 | 记录列表 JSON 结构 | `GET /record` + JSON | 字段、分页参数、总数分页字段名 | 🔴 **阻断记录页** |
| 11 | `psdict` 已登录时的结构 | 登录后 `GET /p` | 得到"我的状态"字段与取值枚举 | 🟡 影响状态圆点 |
| 12 | `difficulty` 字段来源 | 登录后再查 `/p` | 确认是否仍无难度字段 | 🟢 缺失则不做 |
| 13 | RP 字段来源 | 登录后 `/ranking` | 确认是否出现 | 🟢 缺失则不做 |
| 14 | 登出路由 | 试探 `POST /logout` | 200 / 302 | 🟡 可用清 Cookie 兜底 |
| 15 | 样例内容来源 | 检查题目正文 Markdown 与外链 | 确认样例是否只在正文里 | 🟡 影响题干渲染 |
| 16 | WS 协议 | 连接 `/ws` 并观察帧 | 是否需要订阅帧、消息格式 | 🟢 一期用轮询兜底 |
| 17 | 竞赛/作业内提交的 URL 形态 | 从竞赛/作业点进题目 | 确认 `postSubmitUrl` 是否带 `tid` | 🟡 二期相关 |
| 18 | 请求频率容忍度 | 观察 Cloudflare 是否限流 | 确认轮询间隔安全阈值 | 🟡 影响轮询策略 |

**M0 的验收就是这张表全部有明确结论**（含"确认不存在"这种结论）。表里 9 个 🔴 项未清零，不要开始写业务代码。

#### 8.1 验证脚本（已就绪）

脚本已写好并**用真实站点跑通了只读部分**：`tools/m0_probe.py`（纯标准库，无需 pip 安装）。

```bash
# 零风险预演：只发只读 GET，不需要账号，12 个请求
python tools/m0_probe.py --anon

# 完整验证：登录 + 提交一次（故意 WA 的代码）+ 轮询到终态
python tools/m0_probe.py --submit --ask

# 顺带探 WebSocket 实时通道（非阻断）
python tools/m0_probe.py --submit --ws
```

产出 `docs/M0-契约验证报告.md`（逐项结论 + 证据 + 请求流水），可直接替换上表的空白结论。

**脚本的行为边界**（刻意保守，不会有副作用）：只读 GET + 一次登录 POST + 一次提交 POST（需显式 `--submit`）；
不做压力测试、不遍历敏感路由、不猜密码；凭据只在内存，报告与样本落盘前自动脱敏邮箱与 Cookie 值。
提交用 `py.py3` 写一份输出 `sum+1` 的代码 —— **必然 WA，不会污染题目的 AC 统计**。

#### 8.2 已被脚本前置验证的结论（v1.3 更新）

跑 `--anon` 就已确认，**可以从事前清单里划掉**：

| 原序号 | 项 | 结论 |
|---|---|---|
| 前置 | JSON 内容协商 | ✅ 可用，全程免 HTML 解析 |
| 前置 | 未登录软跳转形态 | ✅ `GET /record`、`POST /p/1/submit` 均为 HTTP 200 + `{"url":"/login?redirect=..."}` |
| 前置 | 未命中路由的 body 形态 | ✅ **由 `Accept` 决定**，恒发 JSON 则错误也是 JSON（推翻 v1.0 的"HTML 错误页"结论，详见 3.2） |
| 前置 | 游客判定字段 | ✅ 判定统一为 `_id > 0 && role != "guest"`（`authn` 字段不存在，详见 3.3 v1.11 更正） |
| 11 | `psdict` 匿名时结构 | 🟡 匿名时为空对象 `{}`，登录后应含"我的状态"，仍需登录复验 |
| 12 | `difficulty` 是否存在 | ✅ 登录前已确认**不存在**（列表 11 个字段、详情均无） |
| 13 | RP 字段 | ✅ `/ranking` 的 `udocs[0]` 字段集 `[_id, avatar, loginat, mail, perm, priv, regat, role, uname]`，**无 RP** |
| 17 | 竞赛/作业子路由 | 🟡 路由是**精确匹配**的：`/contest/{tid}/scoreboard` 存在（149KB），`/contest/{tid}/ranking` 不存在 |
| — | 题目列表字段集 | ✅ 列表仅 11 字段，**无 `pid`**（只有 `docId`），故详情链接要用 `docId`；详情页两者都有 |

---

## 9. 里程碑规划

| 里程碑 | 交付物 | 内容 | 出口标准 |
|---|---|---|---|
| **M0 契约验证** | 验证脚本 + 结论表 | 第 8 章全部 18 项；产出 DTO 定稿 | 9 个 🔴 项全部有结论 |
| **M1 核心做题闭环** | 可安装 APK | **设计系统地基（Material 3 令牌 + 动态取色 + 通用组件库）**、登录、题库（列表/搜索/筛选）、题目详情、编辑器、提交、实时状态、评测结果、提交记录、设置 | 能用手机完成"从打开 App 到看到评测结果"的全流程；**且四类核心页面通过 10.2 的套壳感检查** |
| **M2 基础功能补全** | 可安装 APK | 排行榜、用户主页、竞赛与作业的浏览、从题单点进做题 | 覆盖站点全部浏览类功能；新页面全部复用 M1 组件库，无自造控件 |
| **M3 体验优化** | 可安装 APK | WS 实时推送替代轮询、编辑器能力增强、离线草稿、代码模板 | 提交后状态更新延迟 < 1s |
| **M4 打磨与发布** | 签名 APK | 深浅色完整适配、无障碍、性能优化、崩溃收集 | 无 P0/P1 缺陷，冷启动 < 2s，**10.2 风格验收全项通过** |

> **设计系统必须落在 M1，不能推迟。** 若先在 M1 随手写页面、到 M4 再统一风格，等于所有 UI 重写一遍。M1 的第一件事就是把 `ui/theme/` 与 `ui/component/` 建起来，之后所有页面都从组件库拼。

---

## 10. 验收标准

### 10.1 功能验收（逐条）

- [ ] 游客可浏览题库、题目详情、排行榜、竞赛、作业
- [ ] 登录后 Cookie 持久化，杀进程重开仍是登录态
- [ ] 题库 183 题可完整翻完，无重复、无丢失、无空白页
- [ ] 中文关键词搜索命中正确结果
- [ ] 标签筛选按章节分组可用
- [ ] 题干 Markdown + LaTeX + 代码块渲染正确（拿至少 5 道不同类型的题目验收）
- [ ] 29 种语言可选，且受 `config.langs` 限制
- [ ] 代码编辑器：中文输入不错乱、符号栏可用、草稿不丢、字号可调
- [ ] 提交代码成功并拿到提交 ID
- [ ] 评测状态可见并自动更新到终态
- [ ] CE 报告完整可读、可复制
- [ ] WA/TLE/MLE/RE 各自有清晰反馈
- [ ] 我的提交记录可分页、可进详情
- [ ] 切换深色模式后主题、代码高亮、题干同步变化

### 10.2 风格验收（原生 Material 3，依据 5.0 总纲）

这是**独立于功能验收的一组硬指标**。功能全通但风格不达标，同样算未完成。

- [ ] **不复刻网页**：全局搜索确认没有任何页面搬运站点的配色、字体、表格布局或按钮形态
- [ ] **无硬编码色值**：代码中除 `ui/theme/` 外，不存在十六进制色值字面量
- [ ] **无 `dp` 写文字**：所有文字尺寸使用 `sp`
- [ ] **动态取色生效**：Android 12+ 上更换系统壁纸后，App 主色调随之变化
- [ ] **14 项主题逐一切换可用**：设置页能列出全部 `THEME_CATALOG` 项，切换后即时生效且重启后保持
- [ ] **状态色与主题解耦**：切到"竹青 / 玫红 / 单色"等主题后，**判题状态色完全不变**（AC 仍绿、WA 仍红）；不得出现 `colorScheme.primary` 被用来表示判题结果的页面
- [ ] **主题对比度合规**：每套主题的浅色与深色下，正文对比度均 ≥ 4.5:1（`design/theme-catalog.json` 可脚本复核）
- [ ] **跟随壁纸的降级**：Android 12 以下选择"跟随壁纸"时，正确回落到默认主题而不是崩溃或显示紫色
- [ ] **深浅色两套完整**：以下位置在深色下均无异常（逐个截图核对）
  - [ ] 题干里的 LaTeX 公式、表格边框、代码块背景、行内代码底色
  - [ ] 编辑器（背景、行号、光标、当前行、选中色）
  - [ ] 评测状态的色块与文字对比度
  - [ ] 空态插画、加载指示器、错误态
- [ ] **WebView 融合**：题干页与编辑器页**无白底方块、无边框、无字体不一致**；深色切换时 WebView 不闪白
- [ ] **无"网页味"元素**：无整页 WebView、无横向下拉菜单、无居中悬浮 dialog（确认改用 BottomSheet）
- [ ] **动效**：页面切换、列表增删、状态变化均有 Material 3 过渡；系统关闭动画后 App 不做过渡
- [ ] **无障碍**
  - [ ] 所有图标按钮有 `contentDescription`（TalkBack 可朗读）
  - [ ] 系统字体放大到最大后，页面不错位、文字不截断
  - [ ] 评测状态除颜色外有文字/图标标识（色盲可辨）
- [ ] **48dp 触控目标**：所有可点元素满足最小尺寸（用开发者选项的布局边界核对）

### 10.3 稳定性验收

- [ ] 断网时所有页面有明确提示，不白屏、不崩溃
- [ ] 弱网（模拟 3G）下列表可加载，有加载态
- [ ] 接口返回 HTML 错误页时（模拟路由变更）不崩溃，提示"服务异常"
- [ ] 接口返回"未登录软跳转"时正确导航到登录页而非报错
- [ ] 提交请求超时不会产生重复提交
- [ ] 横竖屏切换编辑内容不丢
- [ ] 连续快速点击提交按钮不会发出多次请求

### 10.4 专项测试用例

| 用例 | 步骤 | 期望 |
|---|---|---|
| 中文输入法 | 在编辑器输入拼音并选词 | 无重复字符、光标位置正确 |
| 键盘遮挡 | 在代码末行输入 | 自动滚动使光标可见 |
| 超长代码 | 粘贴 2000 行代码 | 不卡顿、可滚动 |
| 空题干 | 找一道正文为空的题目 | 显示空态而非白屏 |
| 隐私检查 | 抓包 App 全部请求与日志 | 任何位置都不出现 `mail` 值 |
| 轮询克制 | 提交后保持页面 5 分钟 | 请求频率符合退避策略，无风控拦截 |
| **套壳感检查** | 对题库/详情/评测结果/记录四页截图，交给没看过该 OJ 的人判断 | 判断为"一个 Android 应用"，而不是"网页套壳" |
| **动态取色** | Android 12+ 更换系统壁纸后重进 App | 主色调跟随变化，且无对比度不足 |
| **WebView 融合（深色）** | 深色模式下打开题干页与编辑器页 | 无白底方块、无边框割裂、切换瞬间不闪白 |
| **字体缩放极限** | 系统字体设为最大后遍历主要页面 | 不错位、不截断、卡片不重叠 |
| **色盲可辨** | 用色觉模拟工具查看评测状态 | 除颜色外仍能区分 AC / WA / TLE / CE |
| **对比度** | 用对比度检查工具扫描深浅两套主题 | 正文 ≥ 4.5:1，大字号 ≥ 3:1 |
| **动画关闭** | 系统开启"移除动画"后浏览各页面 | 无过渡动画，功能正常，不出现空白卡顿 |

---

## 11. 风险登记册

| # | 风险 | 影响 | 概率 | 应对 |
|---|---|---|---|---|
| 1 | **站点零改造 → 无后端配合** | 高 | 确定 | 客户端做全部防御：动态 URL、字段默认值、降级 UI。**不要把"改站点"当解决方案** |
| 2 | Hydro 升级导致接口变更 | 高 | 中 | M0 定稿契约后，用集成测试固化；优先读服务端下发字段 |
| 3 | 提交/评测链路未实测 | 高 | 确定（当前） | M0 清零；这是唯一的一期阻断项 |
| 4 | Cloudflare 风控 / 限流 | 中 | 中 | 克制轮询频率、指数退避、带上 App UA；后台暂停轮询 |
| 5 | WS 协议未知 | 中 | 高 | 一期用轮询，WS 作为 M3 优化；不阻塞 |
| 6 | 评测状态实时性不足 | 中 | 中 | 退避轮询 + "稍后回来查看"入口 + 记录页手动刷新 |
| 7 | 富文本风险标签（XSS） | 中 | 低 | 渲染前过滤；WebView 不注入原生接口 |
| 8 | 移动端 WebView 编辑器 IME 兼容 | 中 | 高 | 优先在真机验证中文输入；准备"纯文本模式"降级开关 |
| 9 | 接口字段裁剪（列表/详情/搜索字段集不同） | 中 | 高 | DTO 全字段默认值 + 解析容错 |
| 10 | **WebView 破坏原生观感**（两处 WebView 是最大隐患） | 中 | 高 | 令牌双向注入 + 透明背景 + 无边框；M1 阶段就做真机深浅色对比截图，不要留到最后 |
| 11 | 隐私字段泄露（mail） | 中 | 确定 | Mapper 层丢弃 + 测试用例强制校验 |
| 12 | 无难度 / 无 RP 字段导致功能缩水 | 低 | 高 | 已确认不在 JSON 中，一期不做，不伪造 |
| 13 | 二次验证（tfa）若启用，登录流程变复杂 | 中 | 低 | 预留分支；M0 确认 |
| 14 | 提交超时导致重复提交 | 中 | 低 | 超时不自动重试 + 客户端防连点 |
| 15 | 站点为学生自建，可用性/稳定性无 SLA | 中 | 中 | 前端做好错误态；关键操作给明确反馈 |
| 16 | **风格漂移**：迭代中混入网页式设计，原生观感被逐渐稀释 | 中 | 中 | 组件全部沉淀在 `ui/component/`，禁止页面内自造控件；把 10.2 风格验收作为每次发版的必过项 |
| 17 | 站点外观偏好字段被误迁移（字体/背景图）破坏原生观感 | 低 | 中 | 严格执行 5.0 的迁移边界表：只迁语义型偏好 |

---

## 12. 待确认问题

### A. 需要向站点管理员 / 出题人确认

1. 站点后续是否计划升级 Hydro 版本？是否有启用二次验证（tfa）的计划？
2. 是否允许/欢迎第三方客户端？是否需要 App 在 User-Agent 中做标识以便运维区分？
3. 评测结果中，测试点的输入/输出、期望输出对普通学生是否应当可见？（影响结果页设计）
4. 题目的"难度"字段在网页端如何生成？是否有可读接口？RP 值呢？
5. 竞赛期间是否有额外限制（如禁止外部客户端、限制提交频率）？
6. 是否希望 App 提供注册入口，还是统一引导到网页注册？

### B. 靠取证自答（M0 清单）

见第 8 章，全部 18 项。核心是：Cookie 名、提交请求体字段、提交 ID 字段、记录详情字段、评测状态实时性。

### C. 开工前需要拍板的决策（产品方）

以下**不是取证问题，取不到答案**——只能由你决定。已给建议默认值，未拍板项按"建议值"推进不阻塞，但第 1、2 项越早定越好。

> **已拍板（2026-09-13，全部关闭）**：
> | 决策 | 结论 |
> |---|---|
> | App 显示名称 / 包名 | **JXAU OJ ／ `com.jxau.oj`** |
> | 编辑器内核 | **CodeMirror 6（WebView）**，但必须封装成 `CodeEditor` 接口 + 实现，保留切换余地 |
> | 主色策略 | **14 项内置主题目录**（5.4.1），默认 `jxau-blue`，由 `design/theme-seeds.json` 生成 |
> | 应用图标 | 自适应图标 + 单色占位（`res/drawable/ic_launcher_*.xml`），上线前换正式素材 |
> | 最低支持版本 | **minSdk 26 / targetSdk 35 / compileSdk 35** |
> | 界面语言 | **仅简体中文**，一期不做 i18n |
> | 分发方式 | **内部 sideload APK**（debug 包，暂不做签名与更新检查） |
> | 评测完成提醒 | **做**，本地通知，M3 实现（需申请通知权限） |
> | 游客能力边界 | **全浏览功能开放，写操作才要求登录** |
> | 注册入口 | **不内置**，引导到网页注册 |
> | 下一步 | 已进入 M1 实现，工程见附录 D |

| # | 决策项 | 结论 | 备注 |
|---|---|---|---|
| 1 | **包名 `applicationId`** | ✅ `com.jxau.oj` | 已落进 `app/build.gradle.kts` |
| 2 | **App 显示名称** | ✅ 「JXAU OJ」 | 已落进 `res/values/strings.xml` |
| 3 | **应用图标** | ✅ 自适应图标 + 单色占位 | 正式素材到位后替换 `ic_launcher_foreground.xml`，不动其它资源 |
| 4 | **主色** | ✅ 14 项主题目录 | 见 5.4.1 |
| 5 | **编辑器内核** | ✅ CodeMirror 6 + `CodeEditor` 接口封装 | 见下方专项说明 |
| 6 | **最低支持版本** | ✅ `minSdk 26`（Android 8.0+） | 若后续发现有 Android 7 设备，降到 24 需逐个核对所用 API |
| 7 | **App 界面语言** | ✅ 仅简体中文 | 站点是多语言，但 App UI 语言与站点语言是两件事 |
| 8 | **分发方式** | ✅ 内部 sideload APK | 若将来要上架，签名策略与更新检查需另行设计 |
| 9 | **评测完成提醒** | ✅ 做（本地通知） | 手机做题常切出 App 等待，M3 接 `POST_NOTIFICATIONS` |
| 10 | **游客能力边界** | ✅ 全浏览开放 | 已写入 10.1 |
| 11 | **是否内置注册入口** | ✅ 不做 | 登录页已标注"注册请在浏览器中完成" |

**第 5 项专项说明：编辑器内核是唯一"原生纯度"有张力的地方。**

| | 方案 A：WebView + CodeMirror 6（当前选择） | 方案 B：Sora Editor（原生） |
|---|---|---|
| 高亮 | 站点 `LANGS[].highlight` 直接映射，29 种语言开箱可用 | 需自行配 TextMate grammar，29 种语言映射工作量大，冷门语言可能无现成语法 |
| 原生纯度 | 编辑区是一个 WebView（外观由 M3 令牌完全控制，但"零 WebView"无法满足） | 纯 Kotlin / View，可嵌入 Compose，真正原生 |
| 风险 | 中文 IME 组合字错位（已有成熟规避手段，见 6.1） | 冷门 OJ 语言高亮缺失；库的维护活跃度需评估 |
| 建议 | **一期用 A**，但把编辑器封装为 `CodeEditor` 接口 + 实现，真机验证 IME 不可接受时只换实现层，不动页面代码 | 作为备选，M0 阶段可花半天做个真机对比 |

> 这一项现在不拍板也不阻塞 M0 取证，但**必须写进 M1 的接口设计**，否则后期切换会牵动编辑器页整体重写。

---

## 附录 A：本次实测样本索引

样本保存在临时目录（未写入项目仓库），采集时间 2026-09-13。

| 样本 | 说明 |
|---|---|
| 首页 HTML | 34,577 B，站点公告、竞赛/作业/榜单区块 |
| `/p` HTML | 147,320 B（SSR 全量题目表格） |
| `/p` JSON | 30,858 B（100 条题目） |
| `/p?page=2` JSON | 23,492 B（83 条题目） |
| `/p/1` HTML | 20,640 B（含 `window.UiContextNew`） |
| `/p/1` JSON | 2,633 B（`pdoc` 全字段） |
| `/ranking` JSON | 7,189 B（31 个用户对象） |
| `/user/1` JSON | 584 B |
| `/contest` JSON | 2,220 B |
| `/homework` JSON | 6,705 B |
| `/contest/:tid` JSON | 983 B |
| `/homework/:tid` JSON | 1,130 B |
| `/login` JSON | 53 B |
| `entry.js` | 9,630 B（`window.LANGS`，29 种语言定义） |
| `hydro-4.58.4.js` | 248,454 B（主前端产物） |
| 各路由探测响应 | 约 40 次，记录状态码与 Content-Type |

## 附录 B：语言映射表（App 内置常量）

站点实测共 **29 个语言 key**，取自 `window.LANGS`。`highlight` 字段可直接映射到代码编辑器的高亮模式。

| key | 显示名 | highlight |
|---|---|---|
| `bash` | Bash | `bash` |
| `c` | C | `c` |
| `cc` | C++ | `cpp` |
| `cc.cc98` | C++98 | `cpp` |
| `cc.cc98o2` | C++98(O2) | `cpp` |
| `cc.cc11` | C++11 | `cpp` |
| `cc.cc11o2` | C++11(O2) | `cpp` |
| `cc.cc14` | C++14 | `cpp` |
| `cc.cc14o2` | C++14(O2) | `cpp` |
| `cc.cc17` | C++17 | `cpp` |
| `cc.cc17o2` | C++17(O2) | `cpp` |
| `cc.cc20` | C++20 | `cpp` |
| `cc.cc20o2` | C++20(O2) | `cpp` |
| `cs` | C# | `csharp` |
| `go` | Golang | `go` |
| `hs` | Haskell | `haskell` |
| `java` | Java | `java` |
| `js` | NodeJS | `javascript` |
| `kt` | Kotlin | `kotlin` |
| `kt.jvm` | Kotlin/JVM | `kotlin` |
| `pas` | Pascal | `pascal` |
| `php` | PHP | `php` |
| `py` | Python | `python` |
| `py.py2` | Python 2 | `python` |
| `py.py3` | Python 3 | `python` |
| `py.pypy3` | PyPy3 | `python` |
| `r` | R | `r` |
| `rb` | Ruby | `ruby` |
| `rs` | Rust | `rust` |

**App 设计建议**：语言选择器不要平铺这 29 项（C++ 有 13 个变体会淹没列表）。按语言族折叠：一级显示 `C++ / C / Python / Java / Kotlin / Go / Rust / ...`，二级再选具体标准（`C++17` / `C++20` / …）。同时**必须以题目下发的 `config.langs` 做过滤**。

## 附录 C：术语对照

| 术语 | 含义 |
|---|---|
| `pdoc` | 题目文档（problem document） |
| `tdoc` | 竞赛/作业文档（二者共用 `docType: 30`） |
| `udoc` | 用户文档 |
| `psdoc` | 当前用户对某题的解题状态 |
| `psdict` | 列表页的解题状态字典 |
| `tdocs` / `pdocs` / `udocs` / `ddocs` | 对应实体的文档数组 |
| `docType` | 文档类型魔数：`10` = 题目，`30` = 竞赛/作业 |
| `pcount` / `ppcount` | 题目总数 / 总页数 |
| `rp` | Rating Points，站点积分（JSON 中未见） |

---

## 附录 D：工程落地与构建（M1 进行中）

### D.1 位置与技术栈

工程根目录：**`android-app/`**（与 `docs/`、`design/`、`tools/` 平级，互不污染）。

| 项 | 值 |
|---|---|
| 包名 / 命名空间 | `com.jxau.oj` |
| JDK | 17（`D:\IO\jdk17`） |
| Android SDK | `D:\IO\sdk`（platforms 35 / build-tools 35.0.0） |
| Gradle | 8.10.2 |
| AGP | 8.7.3 |
| Kotlin | 2.0.21（Compose 编译器随 Kotlin 版本走） |
| Compose BOM | 2024.12.01 → ui 1.7.6 / material3 1.3.1 |
| compileSdk / minSdk / targetSdk | 35 / 26 / 35 |
| 网络 | OkHttp 4.12.0 + kotlinx.serialization 1.6.3 |
| 图片 | Coil 2.7.0 |
| 本地存储 | SharedPreferences（会话 Cookie + 主题 + 深色偏好） |

**刻意没用的东西**：Hilt / Koin（依赖图只有一层，注解处理器不划算）、
DataStore（要存的东西极少）、Retrofit（本站在响应形态上的特殊处理让手写分发器更清晰）。

### D.2 已实现的骨架

```
android-app/app/src/main/java/com/jxau/oj/
├── JxauOjApp.kt / MainActivity.kt
├── core/AppContainer.kt                       手写依赖容器
├── data/
│   ├── net/  HydroResult ← 四形态分发（核心）
│   │         HydroClient ← 唯一网络出入口
│   │         SessionStore + PersistentCookieJar（含 per-题目草稿）
│   ├── dto/  Dtos.kt / SubmissionDtos.kt  全字段默认值；刻意不声明 mail
│   │         JsonSupport.kt  宽松读取扩展（不抛异常）
│   ├── mapper/Mappers.kt   字段裁剪 / 嵌套 JSON / avatar 前缀 / ObjectId 时间反解
│   ├── model/Models.kt     UI 只接触这些；statusKeyOf 状态码映射
│   └── repo/  AuthRepository / ProblemRepository / SubmissionRepository / UserRepository
└── ui/
    ├── theme/  ThemeCatalog.kt（生成物，14 主题 × 36 角色）
    │           AppTheme.kt（LocalIsDarkTheme）/ VerdictColors.kt（判题色与主题解耦）
    ├── component/ StateViews / ProblemCard / MarkdownText.kt / UserAvatar.kt
    ├── problem/  题库列表（搜索 + 触底分页）、题目详情（题干渲染 + 本题提交入口）
    ├── editor/   ComposeCodeEditor.kt（VisualTransformation 高亮）
    │             CodeEditorEngine.kt / EditorViewModel / EditorScreen（语言分组选择、字号、草稿）
    ├── submission/ SubmissionViewModel（退避轮询）/ SubmissionScreen（结果页）
    ├── record/   RecordListViewModel（三种范围共用）/ RecordListScreen（触底分页）
    ├── ranking/  RankingViewModel / RankingScreen（客户端推算名次，触底分页）
    ├── user/     UserProfileViewModel / UserProfileScreen（用户主页）
    ├── contest/  ContestListViewModel / ContestListScreen / ContestDetail*
    ├── homework/ HomeworkListViewModel / HomeworkListScreen / HomeworkDetail*
    ├── util/     TimeFormat.kt（含 ObjectId 反解入口）/ RoleLabel.kt
    ├── auth/     登录
    ├── profile/  我的（我的提交 / 排行榜入口）
    ├── settings/ 外观设置（14 主题 + 深色模式）
    ├── nav/AppNav.kt（records?uid=&pid= 可选参数路由）
    └── AppViewModel.kt / AppRoot.kt
```

**方案里的三条纪律在代码中的落点：**

1. **四形态分发器是唯一网络出入口** → `HydroClient.dispatch()`，UI 层拿不到 OkHttp 对象。
2. **隐私字段在类型层面就进不来** → `UserDto` 不声明 `mail`，Mapper 也无从映射。
3. **UI 层禁止硬编码色值** → 唯一允许例外是 `ui/theme/`（主题令牌与判题语义色）。

**编辑器为什么用 `VisualTransformation` 而不是等宽自定义控件：**

高亮只附加样式、**绝不改变文本长度**，因此 `OffsetMapping` 用 `Identity` 就是正确的。
反过来说，任何"把 Tab 展开成空格"这类看起来更漂亮的处理都会让光标与选区整体错位 ——
所以这里刻意不做制表符对齐。这条约束写在 `ComposeCodeEditor.kt` 的文件头注释里。

### D.3 构建方式

当前环境**完全离线**（沙箱内所有外网不可达），因此：

- Gradle wrapper 未能生成（`gradle wrapper` 需要访问 `services.gradle.org` 校验分发包）。
  **构建时直接用本机 Gradle 分发包**：
  ```bash
  cd android-app
  export JAVA_HOME="D:/IO/jdk17"
  "$HOME/.gradle/wrapper/dists/gradle-8.10.2-bin/*/gradle-8.10.2/bin/gradle" \
      --offline :app:assembleDebug
  ```
- 依赖版本**必须与本地 Gradle 缓存一致**（`~/.gradle/caches/modules-2/files-2.1`），
  否则离线解析失败。改版本前先确认缓存里有对应版本。
- `com.google.guava:listenablefuture` 已全局排除：它是仅含注解的空 jar，
  且不在本地缓存中，保留会让离线构建无法通过。

一旦有网络，补上 wrapper 后即可用标准的 `./gradlew assembleDebug`。

**构建结果（2026-09-13 实测）**：`BUILD SUCCESSFUL`，产出
`android-app/app/build/outputs/apk/debug/app-debug.apk`（约 18 MB，包名确认为 `com.jxau.oj`）。

> 18 MB 对 debug 包属正常：`material-icons-extended` 会引入全量图标资源，
> 而当前 release 也关着 `isMinifyEnabled`。上线前打开 R8 后体积会大幅下降 ——
> 这一项记入 M4 打磨。

**本轮新代码踩到的两个坑（已修，记下来避免重复）：**

| 现象 | 原因 | 处理 |
|---|---|---|
| `Unresolved reference 'withStyle'` | `AnnotatedString.Builder.withStyle` 是 `androidx.compose.ui.text` 下的**扩展函数**，不是成员，必须显式 import | 两个文件各补一行 import |
| `The feature "break continue in inline lambdas" is experimental` | 把 `continue` 写在了 `regex.find(line)?.let { ... }` 的 inline lambda 里 | 改写为显式 `if (m != null) { ...; continue }`。比开实验特性开关干净 |

### D.4 本阶段尚未实现

| 项 | 原因 |
|---|---|
| 讨论区 | 站点当前 **0 帖**（无内容可展示）；`/discuss`列表契约实测但帖子详情路由与 `ddoc` 字段集未采样。等有帖子或 M0 补采样后再接，避免写一条纯推测的链路 |
| 竞赛榜单（scoreboard） | 路由存在【实测】（149KB 大响应），但需要登录且数据量大，排在登录态完善之后 |
| 收藏、训练题单 | 登录态完善之后 |
| 评测完成本地通知 | 排在 M3 |
| KaTeX 公式真实排版 | 需要 WebView 通道；当前 `$...$` 只做等宽高亮占位（`MarkdownText.kt` 文件头已注明） |

**M2 第二批已落地（2026-09-13）**：竞赛列表/详情、作业列表/详情（契约均【实测】）。
竞赛 tdoc 与作业 tdoc 字段集不同 → 分开建模（`ContestTdocDto` / `HomeworkTdocDto`）。
阶段徽标（未开始/进行中/已结束）由起止时间推算；竞赛时长用起止差值计算而非
`duration` 字段（其单位未采样，不赌）。`pids` / `ddocs` 元素形态未采样 →
防御映射，能解析出 docId 的才给跳转，解析不了的条目不渲染。

**M2 收尾打磨已落地（2026-09-13）**：评测详情页「提交的代码」区块
（`ui/editor/CodeView.kt`，复用编辑器高亮，可选中复制，横向滚动不折行；
`code` 字段契约未验证 → 为 null 时整块不渲染）；
登录成功返回后提交记录/评测详情自动重载（只监听登录态 false→true 翻转，
不与首次加载重复请求）。自适应启动图标确认已就位
（`mipmap-anydpi-v26` + 站点主色底 + 尖括号占位前景，minSdk 26 无需位图回落）。

### D.5 契约待验证点清单（已全部销账，留档）

> **v1.11 终态：全部实测通过。** 2026-09-13 用真实账号在 App 内完成一次
> 「编辑 → 选语言 → 提交 → 轮询 → AC 判定卡」全链路实测（题目 #2「两台机器」，C 语言，
> rid `6aa69a3c…`），所有原假设无一需要修改。下表保留作为验证记录。

> **v1.11 销账（2026-09-13，MuMu + 真实账号实测）—— 清单全部关闭**：
> 登录判定、会话 Cookie、记录列表（数组/总数键名、过滤参数）、记录详情 `rdoc` 包裹、
> 评测状态码映射、**提交动作本身**（`POST /p/2/submit` form `lang`+`code` → 200 +
> `{"rid":"...","url":"/record/..."}`，App 内实测一次真实 AC 提交）、评测轮询与判定卡展示 ——
> **全部实测通过，无一处需要修改假设**。`compilerTexts`/`judgeTexts` 仅在非 AC 时才下发，
> 本次 AC 未覆盖，但结构容错已内置，风险可忽略。**M0 契约验证就此闭环。**

这是当前工程剩余的实质性风险。记录/评测**浏览**链路已实测闭环；
剩余不确定点被**刻意收敛到尽量少的文件**，跑完后改这几处即可定稿：

| 待验证项 | 落点文件 | 当前假设 | 若不符怎么改 |
|---|---|---|---|
| ~~提交请求体字段名~~ ✅ 已实测 | `data/repo/SubmissionRepository.kt` | `lang` + `code` 的 form-urlencoded | 无需改（实测命中） |
| ~~提交响应里记录编号的键名~~ ✅ 已实测 | `data/dto/SubmissionDtos.kt` `SubmitResultDto` | 响应 `{"rid":"...","url":"/record/..."}`，首个候选键 `rid` 命中 | 无需改 |
| ~~记录详情是否包在 `rdoc` 里~~ ✅ 已实测 | 同上 `RecordDto.from` | 包在 `rdoc` 里或平铺，两种都兼容 | 无需改 |
| ~~记录列表的数组与总数键名~~ ✅ 已实测 | 同上 `RecordListDto.from` | 数组依次试 `rdocs` / `docs`；总数依次试 `rcount` / `count` / `total`；同时记录 `sawKnownArray` 以区分「没有记录」与「契约不符」 | 无需改 |
| 记录列表的过滤参数名 | `data/repo/SubmissionRepository.records` | `uid` 与 `pid`；`fullStatus=true` 来自服务端下发的 `getSubmissionsUrl`【实测】 | 改 `params` 的键名 |
| ~~评测状态码 → 语义映射~~ ✅ 已实测 | `data/model/Models.kt` `statusKeyOf` | Hydro 通用约定（1=AC / 2=WA / 3=TLE / 4=MLE / 5=RE / 6=CE / 7=SE / 8=IGN / 0,20,21,22=评测中） | 无需改（实测样本吻合） |
| ~~登录成功判定~~ ✅ 已实测 | `data/repo/AuthRepository.kt` | `GET /api/user` 回查，判据 `_id > 0 && role != "guest"`（3.3 v1.11） | 无需改 |
| ~~会话 Cookie 名~~ ✅ 已实测 | `data/net/SessionStore.kt` | 不硬编码任何 Cookie 名，按 name+domain+path 通用存取 | 无需改 |
| `compilerTexts` / `judgeTexts` 元素结构 | `data/dto/JsonSupport.kt` `textList` | 字符串或 `{"message"}` / `{"text"}` 都容错 | 通常无需改 |

**UI 层的处理原则**：上表任一项不符时，界面必须明确区分"**契约不符**"与"**用户操作失败**"。
例如提交后拿不到 rid 时，提示语是「提交请求已发出，但没有取到评测记录编号，
这通常意味着提交接口的字段契约与预期不符（该链路尚未经 M0 验证）」，
而不是含糊地说「提交失败」—— 否则会被误读成站点故障。

**跑完 M0 的命令**（需要一个真实账号）：

```bash
python tools/m0_probe.py --submit --ask
```

> 顺带说明：产物 APK 可以正常安装、浏览题库、看题目详情、写代码（含高亮与草稿），
> 只有**提交**这一步的结果在 M0 之前无法保证。这个边界在界面上是明说的，不是悄悄吞掉。


---

## 附录 E：v1.14 编辑器体验专项（落地与验收记录）

> 本轮流程与以往不同：**先出功能大纲供用户勾选，再进实现**。
> 大纲按 A（补全框）/ B（编辑动作）/ C（视觉与工具条）/ D（字体）四组逐条列出，
> 用户确认的组合原文如下（留档）：
>
> > A1② + A2① + B1① + B2 + B3 + B4 + B5 + C1-1 + C1-2 + C1-3 + C1-4 + C2-1 + C2-2(4 空格)
> > + C3-1 + C3-2 + C5-1 + D1① + D2(系统等宽 + Cascadia Mono) + D3② + D4① + D5 + D6(关)
>
> ~~**明确放二期**：C4-1 撤销栈、C4-2 查找替换。~~ **已实施（v1.18，附录 I）**。

### E.1 本轮交付的功能

| 分组 | 功能 |
| --- | --- |
| 补全框 | 去掉「关键字／内建／标识符」汉字标签，改 3dp 分类色条（分类移入 `contentDescription` 供读屏）；宽度由固定 220dp 改为按最长候选实测自适应并 `coerceIn(96,180)dp`；右侧越界钳回贴边；候选数 6→5；行高 34→30dp |
| 编辑动作 | 自动补配对（含**跳过已存在的右括号**）；换行继承缩进、上一行以 `{` 结尾再多缩一级；成对删除；**选区包裹**；手打 `}` 退回一级缩进；Tab 缩进（4 空格） |
| 视觉增强 | 配对括号高亮、当前行左侧竖线（`Canvas` 装饰层，画在文本之下，**只加样式不改文本长度**） |
| 符号条 | 编辑区下方横滑按钮 `Tab` `{` `}` `(` `)` `[` `]` `;` `"`，走 `CodeEditorHandle.insert` 复用同一套编辑规则 |
| 字体系统 | 设置页新增「编辑器字体」分组：2 款字体（系统等宽 / Cascadia Mono）+ 字号 10–22sp；编辑器与只读 CodeView 共用；清单驱动，新增字体不改 UI 代码 |

### E.2 落地文件

| 文件 | 变更 |
| --- | --- |
| `ui/editor/CodeEditActions.kt` | **新增**。编辑动作纯函数引擎（`apply` / `matchingBrackets`），零 UI 依赖，可直接单测 |
| `ui/theme/EditorFonts.kt` | **新增**。字体清单 + `LocalEditorFontId` / `LocalEditorFontSizeSp` + 字号归一化 |
| `res/font/cascadia_mono.ttf`、`res/raw/cascadia_mono_notice.txt` | **新增**。内置可变字体（363KB）与来源/许可声明 |
| `ui/editor/ComposeCodeEditor.kt` | 补全框改造；新增装饰层 `Canvas`（配对高亮 + 当前行竖线）；接入 `CodeEditActions`；新增 `insertAtCursor` |
| `ui/editor/EditorScreen.kt` | 新增符号条 `SymbolBar`；字号/字体改为从 CompositionLocal 读取 |
| `ui/editor/CodeEditorEngine.kt` | `Editor()` 新增可选 `handle`；新增 `CodeEditorHandle` |
| `ui/editor/EditorViewModel.kt` | 删除 `fontSizeSp` 与 `increase/decreaseFontSize`（字号归口设置页） |
| `ui/settings/SettingsScreen.kt` | 新增「编辑器字体」分组（字体样张 + 字号 −/+）；「关于」补字体版权与许可说明 |
| `ui/AppViewModel.kt`、`ui/AppRoot.kt`、`data/net/SessionStore.kt` | 新增 `editorFontId` / `editorFontSizeSp` 偏好字段与下发链路 |
| `ui/editor/CodeView.kt`、`ui/submission/SubmissionScreen.kt` | 只读代码块改用同一套字体/字号 |
| `ui/editor/CodeCompletion.kt` | `MAX_CANDIDATES` 6 → 5 |
| `tools/editor_actions_check/`（`run.sh` + `CheckEditorActions.kt`） | **新增**（补测轮②）。`CodeEditActions` 的离线表驱动自检，**52 项用例**，不依赖 junit；`bash tools/editor_actions_check/run.sh` 一键跑，见 E.7 |
| `tools/pure_helpers_check/`（`run.sh` + `CheckPureHelpers.kt` + `stub/R.kt`、`stub/BuildConfig.kt`、`stub/LocalIsDarkTheme.kt`） | **新增**（补测轮③）。`Mappers` + 工具层 + DTO 解析层 + 四形态分发 + 判题状态色 + 语法高亮器（④）+ 题面 Markdown 解析器（⑤）的离线自检，现为 **21 组 412 项用例**（③ 19 组 245 + ④ T 组 90 + ⑤ U 组 77）；用 `-Xplugin=` 挂上缓存里的 Compose 编译器插件后，含 `@Composable` 的文件也能编；`bash tools/pure_helpers_check/run.sh` 一键跑，见 E.8 / E.9.1 / E.9.5 |
| `data/dto/JsonSupport.kt` | `strList` 注释订正（注释称「非字符串跳过」，实际「标量转文本、只跳对象/数组/null」；**实现未动**，见 E.8） |
| `data/dto/SubmissionDtos.kt` | `RecordListDto.totalCount` 候选键级联补 `.takeIf { it != 0 }` —— 原写法里第三个候选 `total` 是**死代码**（`int()` 返回非空 `Int`，`?:` 永不求值右侧），`{"total":42}` 静默读成 0（见 E.8） |
| `data/net/ResponseDispatch.kt` | **新增**（补测轮③）。把四形态分发从 `HydroClient` 里**逐字搬出**成不依赖 OkHttp / Android 的纯对象，使其首次可进离线自检（`HydroClient.dispatch` 变成一行委托）。顺序判据与措辞未改 |
| `data/net/HydroClient.kt` | 仅删除已搬走的 `dispatch` / `substitute` 与随之失效的 import；新增一行委托。**未改任何行为** |
| `data/mapper/Mappers.kt` | `avatarUrl` 补 **`url:` 前缀分支**（实测形态为 `url:/file/9/.avatar.jpg`，**站内相对路径**，须拼 `BuildConfig.SITE_BASE_URL`）+ 裸绝对 URL 原样放行。原实现只认 `qq:`/`gravatar:`/`github:`，`url:` 落到 `else -> null` → 该类用户头像整块空白且不报错（方案 3.4 第 7 条已同步更正，见 E.8） |
| `ui/editor/SyntaxHighlighter.kt` | **新增**（补测轮④）。把 `SyntaxColors` / `HighlightTransformation` / `SyntaxHighlighter` / `Language` 从 `ComposeCodeEditor.kt` **逐字搬出**（`HighlightTransformation` 由 `private` 改 `internal`），使高亮器首次可离线自检；文件顶部写明「只附加样式、绝不改文本长度」这条硬不变量，见 E.9.1 |
| `ui/editor/ComposeCodeEditor.kt` | 裁到 424 行（高亮部分已搬走），删除 6 个随之失效的 import。**未改任何行为** |
| `tools/abi_check/`（`check.py` + `run.sh`） | **新增**（补测轮④）。字节码 ABI 自检：断言「接口声明的抽象方法在实现类里有一模一样的方法描述符」，专抓「编译期零提示、运行时才 `AbstractMethodError`」的坏产物；`bash tools/abi_check/run.sh` 一键跑，见 E.9.2 |
| `android-app/gradle.properties` | `org.gradle.caching=true → false`（补测轮④）。Kotlin 增量编译产出的坏 class 会被 build cache 收下，`clean` 也无法清除（`FROM-CACHE` 原样还原），实测导致编辑器一打开就崩，见 E.9.2 |
| `ui/component/MarkdownParser.kt` | **新增**（补测轮⑤）。把 `MdBlock` / `parseBlocks()` / `inline()` 与全部正则从 `MarkdownText.kt` **逐字搬出**（`MdBlock` / `inline` 由文件私有改 `internal`），使题面解析器首次可离线自检；文件头写明「行内解析**会**改变文本长度」（与编辑器高亮器的硬不变量**正好相反**，两者极易混淆），并更正两处与实现不符的旧注释，见 E.9.5 |
| `ui/component/MarkdownText.kt` | 裁到 170 行（解析部分已搬走），删除 7 个随之失效的 import，文件头加指向 `MarkdownParser.kt` 的说明。**未改任何行为** |
| `tools/pure_helpers_check/probe.sh` | **新增**（补测轮⑤）。**变异测试探针**：人为改坏 `MarkdownParser.kt`（6 种变异）验证 U 组确实抓得住；每步硬校验 md5、判据只用 ASCII、并预先排除「空变异」。`bash tools/pure_helpers_check/probe.sh` 一键跑，见 E.9.6 |
| `tools/gen_launcher_icon.py` | **新增**（v1.15）。从 `android-app/icon.ico` 生成自适应图标的前景层（带 alpha）与单色层，5 档密度；末尾自带「把前景层叠回底色、与源图逐像素一致」的自检，见附录 F |
| `android-app/icon.ico` | **新增**。设计稿原件（48×48，青色图形 `#26C1D8` 压在 `#F0F0F0` 白底上），是图标的唯一真源；改图只需换掉它再跑上面那个脚本 |
| `res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png`、`ic_launcher_monochrome.png` | **新增**（v1.15，**由脚本生成，不要手改**）。10 个 PNG 合计 8.7KB；`monochrome` 必须是「图形不透明、背景透明」的剪影，故与 `foreground` 分开生成 |
| `res/mipmap-anydpi-v26/ic_launcher.xml`、`res/values/colors.xml` | 背景层改为 `@color/ic_launcher_background`（`#F0F0F0`，即设计稿留白色）；foreground / monochrome 指向新的 PNG；文件头写明改图流程。旧的两支矢量占位 `res/drawable/ic_launcher_{background,foreground}.xml` **已删除** |
| `android-app/keystore/jxau-oj-release.jks`、`android-app/keystore.properties` | **新增**（v1.15）。release 签名密钥库（RSA 2048／30 年，DN `CN=JXAU OJ, OU=Android App, O=JXAU OJ, L=Nanchang, ST=Jiangxi, C=CN`）与凭据。⚠️ **必须一起备份**，丢失后无法覆盖升级已装出去的 App |
| `android-app/app/build.gradle.kts` | 新增 `signingConfigs.release`（`keystore.properties` 缺失时不报错，只是不配签名，别人拿源码仍能编 debug 包）；`release` 显式 `isMinifyEnabled = false` 并附理由；新增 `lint { checkReleaseBuilds = false }`（离线环境缺 `lint-gradle` 产物，会阻断 `assembleRelease`，与代码无关）；顶部补 `import java.util.Properties`（Kotlin DSL 里 `java` 被 Gradle 的 java 扩展遮住，写全限定名会 Unresolved reference） |
| `AndroidManifest.xml` | 补 `android:roundIcon="@mipmap/ic_launcher"`（不设 roundIcon 时部分启动器会自行降采样位图） |

### E.3 真机验收（MuMu，Android 12，2026-09-14）

测试题用 **#5「座位排布」**（会话开始时无草稿），**全程未触碰 #2 的真实草稿**。

| 用例 | 期望 | 实测 |
| --- | --- | --- |
| 输入 `int main` → 点符号条 `(` | `int main(\|)` | ✅ 一致，配对高亮同帧可见 |
| 紧接输入 `)` | **不重复插入**，光标前移 | ✅ 仍为 `int main()` |
| 输入 `{` → 回车 | `int main(){\n    \|` | ✅ 缩进恰好多一级 |
| 输入 `return 0;` → 回车 → 输入 `}` | 末行 `}` **退回行首** | ✅ `int main(){\n    return 0;\n}` |
| `(` 后按一次退格 | 一次删掉 `()` | ✅ |
| `#include <stdio.h>` 行内输入 `(` | **不补配对** | ✅ 只插入 `(`；其后 `)` 也正常插入 |
| `"` 内输入 `(` | **不补配对** | ✅ 得 `"("`；再输入 `"` 走跳过分支 |
| 长按选中 `world` → 输入 `[` | 包住选区而非替换 | ✅ `hello [world] note` |
| 输入 `pr` | 弹层出现、**无汉字**、宽度收窄、右侧不越界 | ✅ 3 条候选 `private` / `protected` / `printf`；3dp 分类色条按类型异色（`printf` 偏蓝） |
| 设置页切 Cascadia Mono | 即时生效并落盘 | ✅ `editor_font_id=cascadia-mono` |
| 字号 −/+ | 范围 10–22，落盘 | ✅ 13→(++)15→(−)14→(++)16 |
| 冷启 App | 字体偏好保持 | ✅ 冷启后仍为 `cascadia-mono` / 16 |
| 编辑器内字体渲染 | 与设置一致 | ✅ 截图确认（`int`=关键字色、`123`=数字色、`// note`=注释色、Cascadia 字形） |

**验收后已还原**：字体偏好复位为默认（删除 `editor_font_id` / `editor_font_size_sp` 两个键，
即回落到 `system-mono` / 13sp）；`draft_5` 与本次误触产生的 `draft_33` 清空。

### E.4 本轮新增的坑（已修，记下来避免重复）

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| **App 启动即崩**：`ExceptionInInitializerError` → `IllegalArgumentException: 'wght' must be unique` | `FontVariation.Settings(weight, style)` **自己会派生 `wght` 轴**，又显式传了 `FontVariation.weight(400)` | 只传 `(FontWeight.X, FontStyle.Normal)`，不传轴值 |
| 跳过右括号失效，打出 `int main())` | 插入的 `)` 与右侧已有的 `)` 被 diff 的公共前缀**归并到同一位置**，`inserted` 为空 → 用 diff 索引判断必然漏 | 判据改取 `old.selection.end` 处 `old.text` 的字符，并**直接基于 `old` 计算**（把这次输入当没发生） |
| 手打 `}` 缩在块内不退回 | `apply` 分支顺序漏了 `}` 的退缩进分支 | 补 `if (ch == '}') return dedentBefore(...)` |
| 复测时「跳过右括号」仍复现失败 | **是测试手法问题**：输入前又 tap 了一次编辑区，把光标踢到文本末尾，`old.text[cursor]` 处没有 `)` | 复测时不点编辑区（`adb input text` 无需重新聚焦），结果一次通过 |
| **`""` 中间退格只删掉一个引号**（`()` 却正常） | 被删字符与**左侧字符相同**时 `commonPrefix` 把删除归并到右边那个 → `prefix` 越过闭符、`new.text[prefix]` 越界 → 成对删除分支走不到。**与上一条是同一类「相邻同字符」歧义** | 退格分支改为先按 `selection` 定位被删字符（`new.selection.end == old.selection.end - 1`），这条前置条件同时把**前向删除**排除在外（见 E.7） |
| 离线跑自检时编译器/运行期连环 `NoClassDefFoundError` | 用 `-cp` 直接启动 `K2JVMCompiler` 没有 "Kotlin home"，缺 coroutines（初始化）、annotations（**代码生成**阶段）、Compose 各 AAR 的 `classes.jar`（运行期） | 逐个补进 classpath；Compose 是 AAR，要先解出 `classes.jar`。完整清单见 E.7 |

### E.5 已知遗留

| 项 | 说明 |
| --- | --- |
| 题干 `$...$` 公式 | 仍只做等宽高亮占位，**未接 KaTeX**（见 D.4 与 6.2）。#5 页面上可见 `r \times c` 的原始写法 |
| 撤销栈 / 查找替换 | 已实施（v1.18，C4-1 / C4-2，附录 I） |
| ~~`Cascadia Mono` 许可全文~~ ✅ **已补齐** | v1.14 补测轮：许可正文**逐字取自字体自身 name 表 ID 13**（License Description），随 `res/raw/cascadia_mono_notice.txt` 打包（3564 字符）。顺带更正措辞：该字体是**基于 SIL OFL 的 Microsoft 字体许可**（含附加条款），不是纯 OFL 1.1 —— 设置页「关于」与 `EditorFonts.kt` 注释已同步改口 |
| ~~硬件键盘专属能力~~ ✅ **已补测** | 见 E.6 —— `adb shell input keyevent` 注入的就是真实 `KeyEvent`，走同一条 `onPreviewKeyEvent` 链路，**无需实体键盘即可验证** |
| `input keycombination` | MuMu 的 `input` 不支持该子命令（会退化成按键事件），故无法用 Ctrl+A 造选区；改用长按选词验证。**另一个坑**：DEL 等编辑键需要视图有**活动的输入焦点**，点过符号条后焦点可能已不在文本区（`dumpsys input_method` 的 `mServedView=null`），此时发 DEL 静默无效 —— 清空文本前要先 tap 回编辑区。详见技能 `mumu-ui-driven-testing` |

### E.6 补测轮（同日，收口 E.5 的两项遗留）

**动机**：E.5 把「硬件键盘专属能力」记为"模拟器上无法验证"。这个判断是错的 ——
`adb shell input keyevent` 注入的**就是真实 `KeyEvent`**，与实体键盘走同一条
`onPreviewKeyEvent` 链路，无需实体键盘。据此补测如下（测试题仍是 #5）：

| 用例 | 操作 | 实测 |
| --- | --- | --- |
| 弹层默认选中 | 输入 `in` | ✅ 弹 3 条候选（`inline` / `interface` / `int`），默认高亮第 1 条 |
| ↓ 移动选择 | `keyevent 20` | ✅ 高亮移到 `interface`，**草稿不变**（仍是 `in`） |
| Enter 采纳 | `keyevent 66` | ✅ 草稿变 `interface`，**且未插入换行**（事件被消费） |
| Tab 采纳 | `keyevent 61` | ✅ 采纳默认项 → `inline` |
| 小键盘回车采纳 | `keyevent 160` | ✅ 采纳默认项 → `inline` |
| ↑ 环绕 | `keyevent 19`（从第 1 条往上） | ✅ 跳到**末项** `int`，Enter 后为 `int` |
| Esc 关闭 | `keyevent 111` | ✅ 截图确认弹层消失，草稿仍是 `in` |
| ←/→ 关闭并放行 | `keyevent 21` 后按 Enter | ✅ 得 `i\nn` —— 弹层关闭、光标左移一位、回车插入了换行 |

**符号条与字号边界**（同轮补测）：

| 用例 | 实测 |
| --- | --- |
| 符号条横滑后最右的 `"` 按钮 | ✅ 插入引号；右侧是标识符字符时**按设计不补配对**（`"n`）。注：该按钮在 uiautomator dump 里 `text` 为空（单独的双引号没被序列化），只能按 bounds 点 |
| 字号下限 | ✅ 连点 `−` 后停在 **10**；截图确认 `−` 变灰禁用、`+` 仍可用 |
| 字号上限 | ✅ 连点 `+` 后停在 **22**；截图确认 `+` 变灰禁用、`−` 仍可用 |
| 默认回落 | ✅ 删掉两个偏好键后，设置页回到「系统等宽 / 13sp」 |

**踩坑**：字号变化会让样张行高度改变、**整行控件下移**（实测 `+` 从 y=1094 漂到 y=1146），
按固定坐标连点会点空 —— 应**按标签实时定位**再点。

### E.7 离线自检与回归轮（补测轮②）

**动机**：`CodeEditActions` 虽是纯函数，但有几条分支**只有特定输入组合才走得到**（异族嵌套括号、字符串转义、
`inserted == ""` 的歧义…），真机一条条摆成本高且容易漏。故补一套**离线表驱动自检**。

**为什么不用 JVM 单元测试框架**：本机离线缓存 `org.junit` 下**只有 `junit-bom`（pom，没有 jar）**，
引不进测试框架。而 `CodeEditActions` 只依赖 `TextFieldValue`/`TextRange`，于是用缓存里的
`kotlin-compiler-embeddable` 直接编成可执行程序跑 —— `tools/editor_actions_check/run.sh`。

**编译器 classpath 的坑**（少一个就 `NoClassDefFoundError`，且报错点看着毫不相关）：

| 缺失依赖 | 报错特征 |
| --- | --- |
| stdlib / script-runtime / daemon / reflect / trove4j | embeddable 的常规依赖 |
| `kotlinx-coroutines-core-jvm` | `CoreApplicationEnvironment.createApplication` → `NoClassDefFoundError: kotlinx/coroutines/CoroutineScope` |
| `org.jetbrains:annotations` | **代码生成阶段** → `NoClassDefFoundError: org/jetbrains/annotations/NotNull` |
| Compose 各 AAR（ui-text / runtime / runtime-saveable / ui-unit / ui-geometry / ui-util…） | 运行期 `TextFieldValue.<clinit>` 找不到 `SaverKt` 等 |

两点易忽略：Compose 模块以 **AAR** 分发，要先把里面的 `classes.jar` 解出来；
**脚本自己的 echo 一律用 ASCII** —— Java 进程按 Windows 默认 GBK 输出，混排中文会让日志两端编码对不上。

还有一条**教训**：解出来的 jar 名要用脚本自己算出来的（`basename xxx.aar` → `xxx.jar`），
别在 classpath 里硬编码名字 —— 本次先写成固定的 `ui-text.jar`，**靠上一轮遗留的同名文件"看着能跑"**，
清掉缓存后立刻编不过，而且报的是一堆无关的 `unresolved reference`。
**判断脚本是否自举正确，要 `rm -rf` 掉缓存目录从零跑一遍。**

**用例分布（52 项全绿）**：换行缩进 5 ／ 跳过闭符 8 ／ 补配对 13 ／ 手打 `}` 退缩进 3 ／ 成对删除 6 ／
选区包裹 3 ／ 原样放行 3 ／ 括号配对定位 11。

**自检当场抓出一个真 bug（已修）**：

- 现象：`()` 中间按退格能一次删掉两个，**`""` 中间按退格只删掉一个**。
- 根因：被删字符与它**左侧字符相同**时，`commonPrefix` 会把这次删除归并到**右边**那个字符上，
  `prefix` 于是指到闭符之后，`new.text[prefix]` 直接越界 → 成对删除分支整个走不到。
  这与 v1.14 那条「跳过右括号」是**同一类歧义**：**位置结论必须取自 `selection`，不能取自 diff 下标**。
- 修法：退格分支先按语义定位 —— `old.selection.collapsed && new.selection.collapsed &&
  new.selection.end == old.selection.end - 1` ⇒ 被删的就是 `old.text[old.selection.end - 1]`，
  再看它右侧是否为配对；不满足才回落到原 diff 判据。这个前置条件**同时把「退格」与「前向删除」分开**
  （前向删除光标不左移，不该被成对删除吞掉）。

**真机回归（MuMu，Android 12，测试题 #5，全程未碰 #2 的真实草稿）**：

| 组 | 操作 | 期望 | 实测 |
| --- | --- | --- | --- |
| A1 | `int main` + 符号条 `(` | `int main(\|)` | ✅ `int main()` |
| A2 | 紧接手打 `)` | 不重复插入 | ✅ 仍 `int main()` |
| B | `a` + 符号条 `(` → 退格 | 一次删掉 `()` | ✅ `a` |
| C | `ab` + 手打 `"` → 退格 | **两侧同字符也要成对删** | ✅ `ab`（修复前只会留 `ab"`） |
| D | `int main()` + `{` + 回车 + `}` | 缩进一级、`}` 退回行首 | ✅ `int main(){\n    }` → `int main(){\n}` |
| E | `#define X` + `(` | 预处理行不补配对 | ✅ `#define X(` |
| F | `""` 中间插 `(` | 字符串内不补配对 | ✅ `"("` |
| G | `a()` 中间按**前向删除** | 只删 `)`，不成对 | ✅ `a(` |

**本轮环境坑（下次别先怀疑 App）**：调试期间站点从模拟器侧大量丢包 ——
同设备访问 `baidu.com` 0.2s 建连、200 正常，**唯独站点所在的 Cloudflare 边缘 IP 连不上**：
DNS 解析到 `104.21.40.53` 后 `curl` 表现为 TCP 握手 8–30s 甚至超时，成功率约 2/7，随后连续 10 次全失败；
App 内即表现为「网络不可达：failed to connect … after 15000ms」／「Read timed out」。
用 `curl --resolve` 换其它 Cloudflare IP（`172.67.68.5`、`104.26.10.229`）**立刻 0.2s + 200** ——
Cloudflare 是 anycast，可见是**单个边缘节点对该出口不可达**，不是站点故障、更不是 App 的问题。
（临时定位手段：`adb root` 后 `iptables -t nat -A OUTPUT -d <坏IP> --dport 443 -j DNAT --to-destination <好IP>:443`；
**用完已删规则**。DNS 会在多个 CF IP 间轮换，故此法只是权宜。）

### E.8 第二批离线自检：映射层、工具层与 DTO 解析层（补测轮③）

**动机**：把 E.7 那套「纯函数离线自检」从编辑器扩到**映射层与工具层**。挑这些函数的理由很具体 ——
它们的共同点是**错了不会崩，只会静默给错结果**：26 进制标号差一位 → 整列竞赛题目标号全偏；
ObjectId 反解边界判错 → 提交时间整列空白；avatar 前缀漏一种 → 头像静默变占位图；
相对时间/阶段边界判错 → 用户看到的是「看着不对劲但说不清哪不对」。这类缺陷**跑真机基本发现不了**。

**工具**：`tools/pure_helpers_check/`（`run.sh` + `CheckPureHelpers.kt` + `stub/R.kt`），
与 `tools/editor_actions_check/` 同一套路（缓存里只有 `junit-bom` 没有 jar，故不用测试框架）。
`bash tools/pure_helpers_check/run.sh` 一键跑，**19 组 245 项全绿**（补测轮④加入 T 组 90 项、补测轮⑤加入 U 组 77 项，现为 **21 组 412 项**，见 E.9.1 / E.9.5）。

`stub/R.kt` 是给 `EditorFonts.kt` 递的最小替身 —— 它引用 `R.font.cascadia_mono`，
而离线编译没有 AGP 生成的 `R` 类。只声明用到的那个字段即可，不必生成整个 R。

**用例分布（19 组 245 项）**：

| 组 | 被测对象 | 项数 | 关键边界 |
| --- | --- | --- | --- |
| A | `Mappers.alphabeticLabel` | 13 | 双射 26 进制：`Z`→`AA`、`ZZ`→`AAA`，负下标是契约违反 |
| B | `Mappers.objectIdTimestamp` | 12 | 只认前 8 位十六进制；区间 `1e9..4102444800` 两端各自 ±1 都试 |
| C | `Mappers.avatarUrl` | 19 | `qq:`／`gravatar:`／`github:`／**`url:`+相对路径**／裸绝对 URL／未知前缀不猜／站点根可注入 |
| D | `Mappers.statement` | 11 | 嵌套 JSON 二次解析；`zh`→首个非空→原文，**宁可丑不可白屏** |
| E | `TimeFormat.relative` | 13 | 59s／60s／59min／1h／23h59m／1d／30d／31d 退化；未来时间不倒计时；毫秒与 ISO 入口同结果 |
| F/G | `Phase.of` / `durationLabel` | 3+4 | 未开始／进行中／已结束；时长用起止差值 |
| H | `roleLabel` | — | 角色名映射 |
| I | `normalizeEditorFontSp` / `editorFontById` | — | 字号钳位与字体 id 命中/回落 |
| J | `CodeCompleter` | 15 | 光标越界、数字开头、全词不再候选、忽略大小写次级匹配、三级候选排序与去重、`limit` 截断 |
| K | `JsonSupport` 读取容错 | 16 | 类型漂移、缺失回落、元素过滤（含**未验证的 `compilerTexts` 形态**） |
| L | `Mappers.toLabeledProblems` | 2 | 按 `pids` 顺序打标号；拉不到题目名时 `title` 为 `null` |
| **M** | `UserDto.from` + `Mappers.toUser` | 12 | **登录判定**：`_id>0 且 role!=guest`。三条"只看一半就会判错"的组合都钉住（`_id>0` 但 `role=guest`／`role` 非 guest 但 `_id=0`／**`role` 键缺失 → 默认 guest**）；uname 空回落、`timeZone` 空白→null |
| **N** | `ProblemDto.from` + `toSummary`/`toDetail` | 13 | `_id` 是字符串 / `docId` 是数字；**`docId==0` 时采用调用方给的 `fallbackDocId`**（竞赛列表 key 重复崩溃的守门用例）；config 缺失 → 0/0/空；`content` 二次解析；`nSubmit=0` → 通过率 `null` |
| **O** | 竞赛 / 作业 tdoc | 9 | `docId` 缺失 → 回落 `_id` 数字形式；`pids` 非数字元素剔除；`pdict` 独有项追加尾部；`claimed=null` 不猜；时长用起止差值 |
| **P** | `Mappers.toScoreboard` | 4 | 格子少于列数 → 补 `-` 且 `score`/`rid` 为 null；多于列数 → 截断且 `rid` 不错位 |
| **Q** | 提交与记录 DTO | 22 | `rid`/`_id`/`id` 候选键；`rdoc` 包裹与平铺两形态；`status` 缺失 → **-1**（0 是「等待中」）；`title` 回落 `pdocTitle`；`sawKnownArray` 区分「没记录」与「契约不符」；**总数候选键级联**；自测 `testCases` 按 id 排序、`message` 即 stdout、`memory=0` → null |
| **R** | `ResponseDispatch`（**四形态分发**） | 30 | 形态一 Success（对象／数组／前导空白／3xx）；形态二 NeedLogin（**判据是 body 里 `url` 指向 `/login`，不是状态码**；有 `url` 但不指向 `/login` 不算）；形态三 Failure（**200 也是错误**、code 回落、message 模板替换）；形态四 TransportError（网关 HTML／空 body／坏 JSON 分开报）；**优先级**：body 不是 JSON 时先报形态四，不去猜状态码 |
| **S** | `VerdictColors`（判题状态色） | 14 | 只测**不变量**不抄色值：九类状态色两两不同、浅深两套逐位不同、别名等价、大小写不敏感、**被认作已知状态的键必须有非「未知」的文字**（方案 10.2 无障碍硬要求） |

`CheckPureHelpers.kt` 编译时需要 `stub/R.kt`（`EditorFonts` 的 `R.font.cascadia_mono`）
与 `stub/BuildConfig.kt`（`avatarUrl` 默认参数 `SITE_BASE_URL`）—— 两者都是 AGP 生成物，
离线直编时不存在。**stub 的值要与 `build.gradle.kts` 保持一致**，是唯一的重复点。

**自检抓出的静默降级（已修，且真机验证）**：`Mappers.avatarUrl` 原来只认
`qq:` / `gravatar:` / `github:` 三种前缀，其余一律 `null`。这一步本身没问题 ——
**错的是"只有三种"这个前提**：

- 拉实测数据核对：`GET /ranking` 全量 **31 位**用户的 `avatar` 分布为
  `qq:` **24** ／ `gravatar:` **6** ／ `url:` **1**，**没有一条是 `https://` 开头的绝对地址**；
  `GET /user/9` 同为 `url:/file/9/.avatar.jpg`。
  即 `url:` 后面是**站内相对路径**，旧文档把它记成「完整 URL」是**把转换后的结果当成了原始值**
  （本站对 `qq:` 恰好也生成 `q1.qlogo.cn` 的 URL，两者极易混淆）。
- 后果：那 1 位用户（#15「绪山真寻」）的 `url:` 落到 `else -> null` → **头像整块空白，不报错不回落**。
- 修法：补 `url:` 分支 —— 绝对地址原样用，相对路径 `siteBase.trimEnd('/') + "/" + value.trimStart('/')`；
  `siteBase` 做成带默认值的参数，默认取 `BuildConfig.SITE_BASE_URL`，线上调用点无需改动，自检可注入。
- **真机验证（MuMu / Android 12）**：
  1. 修复前截图：#15 头像区**空白**；
  2. 修复后：#15 渲染出真实头像。为排除"其实是缓存命中"，先 `run-as com.jxau.oj sh -c 'rm -f cache/image_cache/*'`
     清空 Coil 图片缓存再冷启动 —— qq 头像全部**重新抓到**（证明网络与图片链路正常），
     滚到 #15 后缓存文件数 19 → 41，且 #15 显示真实头像。

**自检抓出的第二处缺陷：候选键级联里的死代码（已修）**。`RecordListDto` 的总数键名不确定，
代码写成依次尝试 `rcount` → `count` → `total`：

```kotlin
// ❌ 原来：看起来是三级兜底，其实第三级永远不会被求值
totalCount = o.int("rcount").takeIf { it != 0 } ?: o.int("count") ?: o.int("total")
// ✅ 现在：每一级都要显式判空，否则 `?:` 的左侧是非空 Int，右侧直接是死代码
totalCount = o.int("rcount").takeIf { it != 0 }
    ?: o.int("count").takeIf { it != 0 }
    ?: o.int("total")
```

根因值得单独记：`JsonSupport.int()` 返回的是**非空** `Int`，所以 `?:` 左边一旦不是
`takeIf` 那种可空表达式，右侧就永远不求值。Kotlin 只给一句 "Elvis operator always returns
the left operand" 的**警告**，编译照过、CI 照绿 —— 而字段真缺失时静默读成 `0`。
自检用 `{"total":42}` 直接钉住这条：期望 42，修复前得到 0。
（全工程扫了一遍同类写法，只有这一处。`.str(...)` / `.toIntOrNull()` 那些是真可空，不受影响。）

**把"最该被钉死的一段"变成可测的（顺带做了一次纯搬移）**：四形态分发原本是
`HydroClient` 的私有方法，绑着 OkHttp / `android.util.Log` / `BuildConfig`，**只能靠真机随手点来验证**。
而它的每一处误判都会被上层读成完全不同的用户语义（"没交过题" vs "App 没适配"、
"登录过期" vs "站点点错了"）。故把它**逐字搬出**成 `data/net/ResponseDispatch.kt`
（`internal object`，不依赖 OkHttp / Android），`HydroClient.dispatch` 变成一行委托 ——
判据顺序与措辞**一字未改**，只删掉了随之失效的 import。

于是 R 组 30 项把它第一次钉住了，其中几条是真正的判别性用例：
- `{"url":"/contest/1"}` **不算**软跳转（判据是 `url` 指向 `/login`，不是"有 url 字段"）；
- `{"url":"/login","error":{…}}` → 软跳转**优先于**错误信封（顺序敏感）；
- **200 + `error` 信封必须判 Failure** —— 本站校验失败就是 200，只看状态码会把错误当成功；
- 502 + `text/html` 且 body 是 JSON → 仍按信封走 Failure 而非 TransportError（状态码不是判据）；
- body 是 HTML 时先报"不是 JSON"，且"不是 JSON"与"JSON 解析失败"**分开报**（排查方向完全不同）。

**真机回归（本轮改了网络层，必须验）**：
- 形态一 ✅ —— `adb logcat -s JXAU_NET` 抓到真实入参
  `GET /p -> 200 ct=application/json; charset=utf-8 body={…}`，且题库正常渲染出题目条目；
- 形态四 ✅ —— 恰逢站点又落到已知坏边缘 IP，界面显示
  `网络不可达：failed to connect to oj.wbhtqlorz.cv/104.21.40.53 (port 443) … after 15000ms`，
  点「重试」一次后加载成功（错误分类与恢复行为都正确）；
- 形态二 / 三 ⚠️ **游客态下 UI 走不到**（`提交记录` 入口登录后才显示）。按项目约定
  **不用 guest 身份发 POST 去试**，故这两形态只有离线用例覆盖 ——
  用例的输入取自早前 curl 实测的真实 body（`{"url":"/login?redirect=%2Frecord%3Fpage%3D1"}`、
  `UserNotFoundError` / `VerifyPasswordError` 信封），不是编造的。

**顺带解锁了一项能力：`@Composable` 文件也能离线编了。** `VerdictColors.kt` 里有
`current()` / `colorOf()` 两个 `@Composable` 函数，直接编会在 IR lowering 阶段抛
`Exception while generating code`。解法是把缓存里已有的
`kotlin-compose-compiler-plugin-embeddable-2.0.21.jar` 用 `-Xplugin=` 传给编译器 ——
**Compose 编译器插件在本机缓存里是齐的**（Gradle 构建本来就在用它）。
另用 `stub/LocalIsDarkTheme.kt` 顶替 `AppTheme.kt`（后者会把整套 Material 3 主题拖进编译）。

S 组的 14 项刻意**不抄实现里的色值**，只测不变量：九类状态色两两不同（防复制粘贴串色）、
浅/深两套逐位不同（防深色模式下看不清）、别名等价、大小写不敏感，以及
**"凡是被 `of()` 认作已知状态的键，`label()` 都必须给出非「未知」的文字"** ——
这条正是方案 10.2「颜色 + 文字双重表达」硬要求的守门用例。
（另记录一处有意的非对称：`IGN` 有文字但颜色走 unknown 灰，因为它不算判题结果。）

**顺带发现的环境限制（未修，需产品决策）**：`gravatar` 域名在**国内网络不可达** ——
设备侧实测 `s.gravatar.com` 与 `cn.gravatar.com` **均 `code=000`**（超时/连接失败），
而同一时刻 `oj.wbhtqlorz.cv`、`q1.qlogo.cn` 均 200。md5 算法本身已核对无误（与 Python `hashlib.md5` 逐位一致），
即**不是算法问题、也不是我们的 bug**：站点用 gravatar 的那 6 位用户（含 #2 / #9 / #10 / #19 / #21 / #23）
头像恒为空白。当前 UI 在图片加载失败时什么都不画，**视觉上像渲染缺陷**。
已实施（v1.18）：加载失败时回落成用户名首字母的色块占位（`UserAvatar`，见附录 I）。

**首轮跑出 4 项失败，分类如下 —— 这个分类本身值得记下来**：

- **3 项是我把期望值算错了，不是实现的问题。** 更有价值的一条是**用例设计**把被测分支绕过去了：
  ① 前缀写 `al`，内建 `aligned` 也命中，于是这条根本不在测「字母序」；
  ② `a aa` 光标取 1，保留字把 5 条候选占满，**单字符过滤那条压根没轮到**；
  ③ `alph alpha` 光标取 5 —— 那是第二个词的**词首**（前缀为空直接返回空），
  原意「标识符与保留字同名不重复出」要的是落在第二个词**词尾**。
  → 教训：**用例的前缀/光标要专门设计成能走到目标分支**，否则用例本身的错误会伪装成实现缺陷。
- **1 项是注释与实现不符**：`strList` 注释写「元素不是字符串时跳过」，
  实际是**标量（字符串/数字/布尔）一律转文本**，只跳对象/数组/`null`。
  判断依据：同文件 `str()` 的既定策略是「数字/布尔也转字符串，避免类型漂移导致整条数据丢失」，
  且 `strList` 的真实调用点是 `langs` / `tag` / `loginMethods` / `pids` / `input` —— 全部是字符串数组，
  数字只在字段漂移时出现，此时**留个 `"1"` 比丢整条好**。
  → 结论：**实现是对的，注释错了**，改注释（并补上「对象/数组/null 才跳过」的准确表述），实现一行没动。
  用例也相应改成判别性的：`["a",1,true,null,{"x":1},["y"]] → ["a","1","true"]`。

**自举验证**：`rm -rf .workbuddy/tmp/checkout_pure` 后从零复跑，仍 245 项全绿。
（E.7 那条教训在这里同样适用：中间产物名由脚本算出，缓存目录随时可删。）

**回归**：两批合计 **297 项全绿**（`editor_actions_check` 52 + `pure_helpers_check` 245），
并重建 APK 四轮 —— 依次使 `avatarUrl` 绝对 URL 放行、`url:` 分支、`totalCount` 死兜底修复、
四形态分发搬移进包（设备侧 `lastUpdateTime` 与本地 APK 时间戳逐轮核对一致）。
四轮构建均零 `e:` 零 `w:`。

### E.9 第三批离线自检：语法高亮器的硬不变量（补测轮④）+ 一次「构建成功却装着坏字节码」的事故

#### E.9.1 T 组：把「高亮绝不改文本长度」这条不变量钉死

**动机**：`HighlightTransformation` 用的是 `OffsetMapping.Identity`，即「变换后文本与原文逐字符
一一对应」。高亮器一旦多输出或少输出一个字符，光标与选区就**整体错位**，而且**不抛异常**；
真机上表现为「点哪儿删哪儿都不对」，极难定位。这类不变量正是离线自检最划算的目标。

**做法**（同样是纯搬移，行为一字未改）：把 `SyntaxColors` / `HighlightTransformation` /
`SyntaxHighlighter` / `Language` 从 `ComposeCodeEditor.kt` 抽到 `ui/editor/SyntaxHighlighter.kt`
（`HighlightTransformation` 由 `private` 改 `internal` 以跨文件可见；`ComposeCodeEditor.kt` 710 → 424 行）。
文件顶部写明这条硬不变量与「只允许 `append(code[原样区间])`，不允许 `replace`/`trim`/展开 Tab」。

**用例设计**：先造一组「难缠片段」，每一条都对应一类**容易被顺手改写**的字符 ——
CRLF 行尾（不能被归一成 LF）、`\t` 缩进（不能被展开成空格）、未闭合块注释（吃到文件尾且不多吃）、
未闭合字符串（只吃到行尾）、字符串内转义引号、**字符串以反斜杠结尾**（`scanString` 里 `i += 2` 的越界嫌疑）、
中文与 emoji（代理对，长度 ≠ 字符数）、只有 CR 的老 Mac 行尾、三引号原始串、孤立 `#`、预处理指令整行……

| 组 | 内容 | 项数 |
| --- | --- | --- |
| **T1** | 25 条难缠片段，逐条断言 `highlight(code).text == code`（失败时打印**首个差异位置**及其上下文） | 25 |
| **T2** | 同样的 25 条，断言所有 `spanStyle` 区间落在 `[0, length]` 内且 `start <= end`（越界要到排版阶段才炸，更难查） | 25 |
| **T3** | 走真实入口 `HighlightTransformation.filter()`：400 行文本逐字符一致、`OffsetMapping` 在 `[0, length]` 上**逐点**恒等（不是抽样）、末界点单独钉一条（越界敏感点） | 7 |
| **T4** | 分色正确性 —— 这组是「假高亮器」的照妖镜：只断言长度的话，一个原样返回文本的高亮器能全绿 | 20 |
| **T5** | 语言族分发：同族不同标准等价、族名取第一个 `.` 之前、`py` 的 `#` 是注释而 `//` 不是、`rs` 无内建表、`pas` 的块注释是 `{ }` 而非 `/* */`、未知族「宁可朴素」只染注释与字符串 | 13 |

合计 **90 项**，两批合并后 **335 项**（52 + 245 + 90）。

**顺带验证了「制表符不被展开」这条**：`\t` 在 T1/T2/T3 中都以原字符通过，
而真机侧（E.9.3）用符号条 `Tab` 插入的制表符也让草稿逐字节一致。

#### E.9.2 事故：`BUILD SUCCESSFUL` 的 APK 一点「去写代码」就闪退

**这是本轮最有价值的产出** —— 一个**连续多轮重建 APK 都没被发现**的真崩溃。

**现象**：在 MuMu 上点题目详情页的「去写代码」，App 立刻回到桌面。日志：

```
java.lang.AbstractMethodError: abstract method "void com.jxau.oj.ui.editor.CodeEditorEngine.Editor(
    java.lang.String, kotlin.jvm.functions.Function1, java.lang.String, androidx.compose.ui.text.TextStyle,
    boolean, androidx.compose.ui.Modifier, com.jxau.oj.ui.editor.CodeEditorHandle, Composer, int)"
    at com.jxau.oj.ui.editor.CodeEditorEngine$ComposeDefaultImpls.Editor$default(CodeEditorEngine.kt:33)
```

**定位**（`javap -p -s` 对比两侧描述符）：接口声明的是 `(…, Composer, I)V`（9 个参数），
实现类 `ComposeCodeEditor.Editor` 却是 `(…, Composer, II)V`（多一个 `int`，即「默认参数掩码」）。
描述符不同 ⇒ 实现类**没有实现**接口方法 ⇒ 调用 `ComposeDefaultImpls.Editor$default` 时抛 `AbstractMethodError`。

**排除「源码写错了」**：写了一个 3 行的最小复现（接口带默认值 + 实现类 override），
用同一套离线编译器 + Compose 插件编出来，两侧描述符**完全一致**。
即源码形状没问题，问题在**构建产物**。

**真因**：**Kotlin 增量编译 + Gradle build cache**。坏产物是在某次增量编译里生成的，
随后被 build cache 收下；此后每次构建（包括 `clean` 之后）都命中缓存把**同一份坏产物原样还原** ——
`clean` 根本没删掉它，因为 `compileDebugKotlin` 直接标 `FROM-CACHE`。
实测证据：

```bash
gradle :app:clean :app:assembleDebug --offline          # BUILD SUCCESSFUL，但 compileDebugKotlin FROM-CACHE
javap -s … CodeEditorEngine | grep -A1 Editor           # (…, Composer, I)V
javap -s … ComposeCodeEditor | grep -A1 'Editor('       # (…, Composer, II)V   ← 仍然错配
gradle :app:compileDebugKotlin --offline --no-build-cache --rerun-tasks
javap -s … ComposeCodeEditor | grep -A1 'Editor('       # (…, Composer, I)V    ← 一致了
```

**修法与防复发**（两层）：

1. **关掉本工程的 build cache**：`android-app/gradle.properties` 里 `org.gradle.caching=true → false`，
   并写明理由。代价极小（离线全量 `compileDebugKotlin` 约 1~2 分钟，且日常增量仍是 `UP-TO-DATE`），
   换来一条更值钱的契约：**`BUILD SUCCESSFUL` == 产物可信**。
2. **交付产物体检工具 `tools/abi_check/`**（`check.py` + `run.sh`）：
   反汇编 `kotlin-classes/debug` 下所有 class，对每个「接口 → 实现类」配对断言
   **接口声明的抽象方法在实现类里有一模一样的方法描述符**，命中就非零退出并打印两侧描述符。
   `bash tools/abi_check/run.sh` 一键跑（当前产物：563 个 class / 4 个接口 / 全绿）。

**工具的判别力也是验证过的**（这条不写下来下次还会踩）：先人为造一对错配字节码 ——
用离线编译器编两版接口（`f(String,int)` 与 `f(String,int,long)`），把新版 `I.class` 覆盖到
旧版 `Impl.class` 旁边，再跑自检。**第一版自检在这里漏报了**：它把 `extends` 与 `implements`
并成一个列表去递归收集「已声明方法」，递归走进了接口，于是**接口自己声明的方法被算成实现类已声明**，
永远不报错 —— 换句话说，那个自检**看起来全绿，其实恒过**。
把两者拆开后：一致的一对 → `OK`（退出码 0），人为错配 → 报出
`Impl 未实现 I.f｜接口描述符 (Ljava/lang/String;IJ)V｜实现类同名方法 ['(Ljava/lang/String;I)V']`（退出码 1）。
→ 教训：**自检工具本身也要有「判别性用例」（能造出一个必须失败的输入），否则它只是一段恒真的代码。**

#### E.9.3 真机回归（MuMu / Android 12，测试题 #5）

- **编辑器可正常打开**：`pidof` 存活、`logcat -b crash` 零 `FATAL EXCEPTION`，
  符号条（`Tab { } ( ) [ ] ;`）、语言键 `cc.cc20o2`、`自测` / `提交` 均正常渲染；
- **输入逐字符往返**：`adb input text` 送入
  `int a=42; char s="hi"; return 0; // ok`（含 `"` 与 `;`），读回 SharedPreferences 草稿
  **完全一致**（36 字符一个不差）—— 这是 `OffsetMapping.Identity` 不变量在真机上的端到端证据；
- **高亮分层可见**：截图取样出 ≥5 种墨色（`#166B54` / `#2B6765` / `#406375` / `#4C635A` / `#171D1A`），
  对应关键字 / 内建 / 字符串与数字 / 注释 / 普通标识符；
- **光标定位与插入一致**：点文本中段（`42` 与 `;` 之间）后再输入 `Z`，
  草稿变成 `int a=42;Z char …` —— 字符**恰好落在点击处的字符边界**，没有偏移；
  换言之高亮没有把文本长度改掉；
- **清理**：`draft_5` 测完还原为空（会话开始时它本就是空），**全程未触碰 #2 的真实草稿**，
  设备侧 `/sdcard/ui.xml`、`/sdcard/shot.png` 与本地临时脚本、截图全部删除。

⚠️ **记一条测试台限制（不是 App 缺陷）**：`adb shell input keyevent 21`（`DPAD_LEFT`）
**不会移动编辑器光标**（`DEL` 却有效，说明焦点正常）。所以「挪光标再删」这类用例不要用方向键，
**点文本区定位**即可（已实测有效）。这与技能里那条「`keyevent 123`（MOVE_END）会把 App 切到桌面」
是同一类 MuMu 注入怪癖。

#### E.9.4 本轮教训（两条，都可复用）

1. **`BUILD SUCCESSFUL` ≠ 产物可信**。增量编译 + build cache 可以产出「编译期零提示、
   运行时才崩」的字节码，而 `clean` 也修不好。判定手段只有一个：**把出错的那个界面真的点开一次**。
   → 已固化为两条动作：本工程关闭 build cache；构建后跑 `tools/abi_check/run.sh`。
2. **离线自检的期望值必须手推，工具的判别力也要用「必失败的输入」验证过。**
   本轮的 T 组用例（手推 25 条难缠片段的期望字符流）与 ABI 自检的阳性用例（人为错配字节码）
   都是同一件事的两面。

#### E.9.5 第四批离线自检：题面 Markdown 解析器（补测轮⑤，U 组 77 项）

**为什么挑它**：站点每道题的题干（`pdoc.content` 里的 `zh`）都走这条路径。它的失败模式与
E.7／E.8 里那些函数同一类 —— **不崩、只是显示得不对**：块级判错会让整段变成列表或标题，
行内判错会让标记字符漏出来、或把正文吃掉。而它在真机上**极难一眼看出**：题面看着“差不多”，
只有逐字读才会发现 `**` 漏出来了、或某句话少了一半。

**先实测语料，再写用例**（不凭想象写）。本机到站点全 `code=000`，故用设备侧 `curl` 抓了
12 道线上题干（`/p/2`…`/p/15`）的 `content.zh` 后统计：

| 观测项 | 结果 |
| --- | --- |
| 标题 | 只有 `##`（60 处）与 `###`（24 处）；`#`／`####`+ 未出现 |
| `*` | 共 164 处，**全部是长度为 2 的 `**`**；没有裸 `*`，没有 `***` |
| 行内代码 | 反引号 |
| 公式 | `$...$` |
| **未出现的** | 引用块、链接、分隔线、`_强调_` |

→ 结论：**只实现这个子集，不做完整 CommonMark**。用例语料全部取自这些实测形态，
其中 3 条是**逐字取自线上题干的整句**（改动它们等于改契约）。

**用例结构**（U1 块级 36 项 ／ U2 行内 42 项）：

| 组 | 覆盖点 |
| --- | --- |
| U1 块级 36 | 空输入；`#`…`######` 六级与「井号后必须有空格」；七连 `#` 不是标题；分隔线（`---`／`- - -`／`***`／`***粗斜体***`）；三种无序符号与两种有序符号（`1.`／`3)`）；缩进 2 空格 → depth 1、4 空格 → depth 2；`1.5` 不是有序项；引用（`> x` 与 `>x`）；代码块（无语言／带语言／**未闭合围栏回落成段落且内容一个不丢**／块内 `#` 不当标题／块内空行保留）；空行分段；相邻行合成段落（软换行）；CRLF 归一；首尾空白 trim；段落里 `- ` 行会切成列表；标题／段落／块交替的真实结构 |
| U2 行内 42 | 未配对标记一律原样（裸 `*`／裸 `$`／未闭合 `**`／未闭合反引号／下划线不做强调）；公式**不跨行**；四种标记各自丢标记；链接只显示文字；一行混三种标记；3 条线上真实语料；**不变量**「只丢标记、不改序」10 条；两处已知限制的**逐字符行为锁定**；相邻标记不吞正文；四种标记各自挂对样式（Monospace／Bold／Italic／Underline）且**不串行** |

**两条判据，一条比一条严**：

1. **不变量「只丢标记、不改序」**：`mdt(src)` 必须是 `src` 的**子序列**。新增标记类型时自动覆盖，
   比逐条断言耐用。
2. ⚠️ **但它单独用太松** —— 实测把 `**粗**` 那一支整个删掉，`*输入*` 仍是 `**输入**` 的子序列，
   而输出已经从「输入」变成了「*输入*」。所以对两处「有意不修」的已知限制
   （`***粗斜体***` → `*粗斜体*`、`a * b * c` → `a  b  c`）额外做**逐字符**锁定。
   这条盲区是**变异测试抓出来的**，见 E.9.6。

**搬出来才可测**：`parseBlocks()` / `inline()` 本来就只吃 `String`、吐数据（`List<MdBlock>` /
`AnnotatedString`），抽成 `ui/component/MarkdownParser.kt` 后即可进自检，
`MarkdownText.kt` 只剩 Compose 渲染（367 → 170 行，删 7 个失效 import）。**行为一字未改**，
`compileDebugKotlin --rerun` 零 `e:` 零 `w:`。

**顺带订正两处「注释与实现不符」**（本项目第三、第四例）：

- `MarkdownText.kt` 旧注释称行内解析「刻意不改文本长度 —— 这样将来接到编辑器上光标偏移不会错位」，
  与实现**正好相反**（`**输入**` → `输入`，长度从 6 变 2）。已改为「**会**改变文本长度」，
  并显式划清与 `SyntaxHighlighter.kt` 那条硬不变量的界线：**这里**的输出只喂只读 `Text`，
  不参与任何光标偏移计算；**那里**才必须逐字符不变（`OffsetMapping.Identity` 的前提）。
- 「已知限制」里 `***粗斜体***` → `*粗斜体` **漏了尾部**，实测是 `*粗斜体*`（首尾各漏一个 `*`）。

**真机回归（MuMu / Android 12，题目 #5「座位排布」）**：

| 检查项 | 结果 |
| --- | --- |
| 题面整页渲染 | ✅ 标题（题目描述／输入格式／输出格式）／段落／`- ` 列表项渲染成「·」／样例代码块（`3 5`／`15`／`10 8`／`80`）全部正常 |
| 线上公式与 U 组契约一致 | ✅ 题干原文 `$r \times c$` 渲染成 `r \times c`（丢 `$`、**保留 `\`**）；`$1 \le r, c \le 10^4$` → `1 \le r, c \le 10^4`；`**sylth**` → `sylth`（标记被丢掉） |
| 「去写代码」入口 | ✅ 编辑器正常打开（符号条、语言键 `cc.cc20o2`、`自测`／`提交` 齐全） |
| 崩溃 | ✅ `logcat -b crash` 零 `FATAL EXCEPTION` |
| SharedPreferences | ✅ 测前 9 键 / 测后 9 键，**零差异**；`draft_5` 始终为空，**未触碰 #2 的真实草稿** |
| 清理 | ✅ 设备侧临时截图、本地临时脚本与截图、`iptables` 导流规则全部删除 |

⚠️ 本轮**又**遇到 Cloudflare 单边缘节点不可达：App 首屏卡在
`failed to connect to oj.wbhtqlorz.cv/104.21.40.53 (port 443) … after 15000ms`，
同刻设备侧 `curl --resolve` 实测 `104.21.40.53` → `code=000`（12s 超时），
而 `172.67.68.5` → `code=200 conn=0.28s`、`104.26.10.229` → `code=200 conn=0.22s`；
按标准动作临时 `iptables -t nat` 把坏节点 DNAT 到好节点后秒开，测完已删除并复核
（`iptables -t nat -S OUTPUT` 只剩 `-P OUTPUT ACCEPT`）。**属环境问题，未改 App 任何代码。**

#### E.9.6 变异探针：怎么证明「自检有牙」（补测轮⑤）

**动机**：一次「412 项全绿」有两种解释 —— ① 实现是对的；② 用例没有判别力。
②更危险，因为它给出的是**虚假安全感**。E.9.2 里 ABI 自检第一版「看起来全绿其实恒过」
就是活生生的例子。所以在 U 组写完后，**人为把被测实现改坏**，看自检抓不抓得住。

交付 `tools/pure_helpers_check/probe.sh`（`bash tools/pure_helpers_check/probe.sh [变异名…]`），
本轮 6 种变异**全部被抓**：

| 变异 | 改动 | 结果 |
| --- | --- | --- |
| `A-heading-level` | 标题级别 `groupValues[1].length` → `+ 1` | 抓 7 项失败 |
| `B-no-bold-alt` | 删掉行内 `**粗**` 那一支 | 抓 6 项 |
| `C-keep-bold-markers` | 粗体不再丢标记（`append(token)` 取代 `trim('*')`） | 抓 5 项 |
| `D-depth-div4` | 列表缩进层级除数 2 → 4 | 抓 2 项 |
| `E-divider-too-loose` | 分隔线正则放宽成 `^\s*[-*_].*$` | 抓 8 项 |
| `F-no-formula-alt` | 公式分支不再排除换行（`[^$\n]+` → `[^$]+`） | 抓 1 项 |

**⚠️ 探针自身踩了三个坑，全部已固化进脚本（否则会得出完全相反的结论）**：

1. **判据必须用 ASCII**。脚本最初用 `grep "通过"` 判断「跑没跑起来」，而这个脚本文件
   被**按 GBK 落盘**（本项目老坑，见 MEMORY），脚本里的「通过」二字成了 GBK 字节，
   永远匹配不上 UTF-8 的节目输出 → **5 次探针全被误报成「编译失败（变异本身无效）」**，
   看起来像「自检连编译都过不了」，实际自检照常执行、结论完全相反。
   现在判据只用 `^  PASS` / `^  FAIL` / `error:` 三个 ASCII 标记。
2. **每一步都要校验 md5**（起始 = 原版、变异后 ≠ 原版、还原后 = 原版）。
   第一版探针没有校验，结果一次循环把**带变异的源码留在了工作区**，而汇报却是绿的 ——
   「变异没落盘」与「自检没牙」必须能分开。任一条不成立脚本立即 `exit 9`。
3. **空变异要自己先推演掉**。见下。

**一次真实的空变异（并因此订正了一处注释）**：最初设计的变异是
「把 `inlineRegex` 里 `*斜*` 与 `**粗**` 两支对调」—— 看着像大改，跑完却 **412 项全过**。
先怀疑自检，推演后发现是变异本身无效，而且**旧注释就是错的**：

`MarkdownParser.kt` 旧注释称「五个分支**顺序敏感**：`**` 必须排在 `*` 之前，否则粗体永远匹配不到」。
实际上 **顺序可证无关**：五个分支首字符互斥（`` ` ``／`*`／`[`／`$`），
唯一同以 `*` 开头的两支在**第二字符**上又互斥 ——
`\*[^*]+\*` 要求 `text[p+1] != '*'`，`\*\*[^*]+\*\*` 要求 `text[p+1] == '*'`。
同一位置上不可能两支都成立，所以谁先谁后结果恒等；`***三连***` 也不受影响
（位置 0 两支都配不上，到位置 1 才由 `**` 支命中）。注释已改为准确表述，
并把「对调是空变异」这件事写进去 —— 免得后人再把它当「覆盖」写进探针。

**因为这个假变异，反而发现了 U 组一处真盲区**：`***粗斜体***` 与 `a * b * c` 此前
**只被子序列不变量覆盖**。补上逐字符锁定后再跑，`B-no-bold-alt` 这类变异才被抓出来
（6 项失败）。也就是说：**假变异逼出了真用例。**

**教训（三条，都可复用）**：

1. **「全绿」必须配一个「必失败的输入」才有意义** —— 对自检是人为造一个必须报错的用例
   （E.9.2 的错配字节码），对用例集是人为改坏实现（本轮探针）。
2. **验证工具的脚本本身也要被怀疑**：编码（GBK 落盘 → 中文模式失配）、
   路径（Git Bash 的 `$(pwd)` 给出 `/d/...`，Windows python 解析不了）、
   判据（用会随输出措辞变化的字符串做模式）三者都能让结论**静默翻转**。
   → 已固化为规矩：脚本判据只用 ASCII、只写字面量 `D:/...` 路径、每步校验 md5。
3. **变异可能本身是空变异**。写探针前先把「这个改动到底改变了哪条可观测行为」推演一遍；
   推不出可观测差异的，不进探针（进注释）。

---

## 附录 F：正式包（release）打包 —— 图标、签名与离线构建（v1.15）

### F.1 启动图标：为什么不能直接把 `icon.ico` 丢进 `mipmap/`

设计稿 `android-app/icon.ico` 只有 **48×48**、8bpp，且是**不透明白底 + 青色图形**（实测底 `#F0F0F0`、
图形 `#26C1D8`）。直接缩放塞进 `mipmap-*dpi` 有两个问题：

1. **尺寸不够**：`xxxhdpi` 要求 192×192 的位图，48×48 拉上去必糊。
2. **自适应图标要求分层**：API 26+ 的图标是 `background` + `foreground` 两层，
   `foreground` 必须是**带 alpha 的图形**。把不透明白底整张当前景，
   会让 Android 13+ 的 `monochrome` 层**变成一整块实心方块** —— 因为**单色层只看 alpha**，不看颜色，
   而整张图 alpha 全是 255。

所以交付 `tools/gen_launcher_icon.py`，做三件事：

| 步骤 | 做法 | 为什么这么做 |
| --- | --- | --- |
| **拆层** | 把每个像素当作 `(1-t)·white + t·cyan` 的线性混合，用最小二乘解出覆盖度 `t`，令 `alpha = t`、`RGB = cyan` | 不能简单按颜色阈值二值化：原图有 130 多个抗锯齿过渡色（`(190,228,234)`、`(227,237,238)`…），硬切会让斜边出现锯齿缺口 |
| **放大** | 48 → 240 px 用 **NEAREST** | 240 = 48 × 5，**整数倍**；像素画插值就会糊 |
| **降采样** | 先出 `xxxhdpi`(432) 主图，再用 **BOX**（面积平均）缩到 324 / 216 / 162 / 108 | 保证各密度之间一致，且比"每档各自放大"少一层误差 |

脚本末尾带自检：**把前景层叠回底色，与源图逐像素比**，最大通道偏差必须 ≤ 2 ——
实测 **1**。这条断言是这个脚本唯一的价值保证：拆层要是算歪了，图标会整体偏色而肉眼很难发现。

**图形尺寸取 60dp 而不是满铺 72dp**。自适应图标的画布是 108dp，中间 72dp 为可见区、
其中 66dp 圆是**安全区**。设计稿几乎没留白（图形占满自身画布的 40/48），
按 72dp 满铺时图形对角线 ≈ 42dp > 33dp（安全圆半径），**圆形遮罩会切掉顶栏两角**；
压到 60dp 后对角线 ≈ 35dp，肉眼已无缺角（模拟圆形/方圆/方形三种遮罩确认，真机桌面复核）。
另外 60dp × 4 = 240px = 48 × 5 是整数倍，正好满足上面「NEAREST 必须整数倍」的约束 ——
这两个条件是同时定下 60 的。

**验收**：真机桌面（MuMu 自带启动器）图标已是设计稿图形，方圆遮罩、无缺角、无偏色。

### F.2 正式签名

```bash
keytool -genkeypair -v -keystore android-app/keystore/jxau-oj-release.jks   -alias jxau-oj -keyalg RSA -keysize 2048 -validity 10950 -storetype PKCS12   -dname "CN=JXAU OJ, OU=Android App, O=JXAU OJ, L=Nanchang, ST=Jiangxi, C=CN"
```

凭据写在工程根的 `keystore.properties`，`app/build.gradle.kts` 里读它配 `signingConfigs.release`。
**`keystore.properties` 不存在时不报错**，只是 release 不配签名 —— 这样别人拿到源码仍能编 debug 包。

⚠️ **`keystore/*.jks` 与 `keystore.properties` 是一对，必须一起备份。** Android 只认同一把私钥覆盖安装，
丢了两者就无法再给已装出去的 App 升级（只能卸载重装、用户数据全丢）。

### F.3 离线构建的两个阻断点（都与代码无关）

| 报错 | 根因 | 解法 |
| --- | --- | --- |
| `build.gradle.kts: Unresolved reference: util`（写 `java.util.Properties`） | Kotlin DSL 脚本作用域里的 `java` 是 **Gradle 的 java 扩展**，全限定名会被解析成该扩展的成员 | 文件顶部显式 `import java.util.Properties` |
| `lintVitalAnalyzeRelease FAILED: Could not resolve com.android.tools.lint:lint-gradle:31.7.3` | `assembleRelease` 默认顺带跑 vital lint，而**离线缓存里没有 lint 产物**（debug 包不触发这个任务，所以此前没暴露） | `android { lint { checkReleaseBuilds = false; abortOnError = false } }`；`:app:lint` 单独跑仍可用（需联网拉产物） |

**R8 故意没开**（`isMinifyEnabled = false`，已写进构建脚本注释）。理由是这个工程有过
E.9.2 那次「`BUILD SUCCESSFUL` 却装着坏字节码」的事故 —— 混淆与资源裁剪造成的破坏同样是
**运行期才暴露**的。真要开，得先补两条 keep 规则：

- `res/raw/cascadia_mono_notice.txt` **只被设置页的文案提及、不被任何代码引用** ——
  一旦开 `shrinkResources`，它会被当成无用资源删掉，而关于页仍然写着"许可声明全文随应用打包"，
  变成一句假话。
- 工程内**没有**反射、也**没有**按名字查资源（`getIdentifier` 等已全量搜过，为零），
  所以 R8 的主要风险在库侧（OkHttp / Coil / Compose 的 consumer rules 是否齐），
  开之前必须做一遍完整真机回归。

### F.4 换装：为什么必须卸载重装

release 包与已装的 debug 包**签名不同**，`adb install -r` 直接失败：

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.jxau.oj signatures do not match
```

而卸载会清空应用数据。本轮按产品方要求「删掉 App 里的测试草稿、保留真实草稿」，做法是：

1. 卸载前用 `run-as`（debug 包可调试）**备份整个 prefs**；
2. 卸载 debug 包 → 安装 release 包；
3. release 包**不可调试**，`run-as` 失效，故启动一次建好数据目录后 `adb root`，
   把**清理过**的 prefs 写回，并逐项修好 ⚠️ **属主（`u0_a55:u0_a55`）、权限（660）、
   SELinux 上下文（`restorecon` 成 `app_data_file`）** —— 只 `cp` 不 `chown` 的话，
   App 读不到自己刚被写进去的配置，且**不会报错**，只会静默退回默认值；
4. 启动后核验。

删除的是测试时敲进去的草稿（`draft_154 = "ac"` 及 5 条空草稿），
**保留用户自己写的 `draft_2` 题解**；`theme_id` / `dark_mode` / `default_lang` / `cookies` 原样保留。
`draft_2` 的值含 `<` 与 `&`，回写时必须重新做 XML 转义（脚本里做了"写回 → XML 解析 → 比对原文"的往返自检）。

### F.5 验收结果（MuMu / Android 12，2026-09-14）

| 检查项 | 结果 |
| --- | --- |
| 产物 | `app/build/outputs/apk/release/app-release.apk`，12.2 MB（debug 为 18.6 MB，差值主要来自 `ui-tooling` 只在 debug 引入） |
| 签名 | `apksigner verify` 通过，v2 方案；`CN=JXAU OJ, OU=Android App, …`；SHA-256 `37b932294fb9670ffe1ae4f68efa87a71c3d934178fcd7fe25ea2480766238c7` |
| 调试位 | `dumpsys package` 的 `flags` 中**无 `DEBUGGABLE`**；`aapt2 dump badging` 无 `application-debuggable` |
| 编译 | `:app:compileReleaseKotlin --rerun` **零 `e:` 零 `w:`** |
| 字节码 ABI | `tools/abi_check/run.sh android-app/app/build/tmp/kotlin-classes/release` → 564 class / 4 接口，**全绿** |
| 离线自检 | `tools/pure_helpers_check/run.sh` → **412 项全绿**（本轮未动 Kotlin，做回归确认） |
| 图标 | 桌面图标为新设计稿；实测 `aapt2 dump resources` 里 `mipmap/ic_launcher{,_foreground,_monochrome}` 与 `color/ic_launcher_background` 均在（release 会重命名资源路径，故 `badging` 显示为 `res/BW.xml`，属正常） |
| 冷启动 | 无 `FATAL EXCEPTION`；题库列表正常加载 |
| 题面渲染 | #2「两台机器」标题／段落／样例代码块正常，`$a+b$` → `a+b`（与 E.9.5 的 U 组契约一致） |
| **草稿** | 进 #2 编辑器**正确恢复 `draft_2`**（`#include<stdio.h> …`，语法高亮正常）—— 证明 root 回写的 prefs 真的被读到了 |
| 清理 | `iptables` 导流规则已删除并复核（`iptables -t nat -S OUTPUT` 只剩 `-P OUTPUT ACCEPT`）；设备侧 `/sdcard/ui.xml` 等临时文件已删；`adbd` 已 `unroot` |

⚠️ 本轮**又**遇到 Cloudflare 单边缘节点不可达（`104.21.40.53` → `code=000`，
同刻 `172.67.68.5` 0.38s/`200`、`104.26.10.229` 0.21s/`200`），
按 E.7／E.8／E.9.5 的既有结论按环境问题处理，**未改 App 任何代码**。

### F.6 本轮教训（三条）

1. **「自适应图标」的 `monochrome` 层只看 alpha。** 拿彩色前景直接顶替 `monochrome`
   会得到一整块实心色块 —— 而且**只在 Android 13+ 的主题图标里出现**，
   在 Android 12 的真机上根本看不到。本轮是靠读透图层语义发现的，不是靠验证。
2. **图标不能只看「有没有图」，要看遮罩下的形状。** 像素画的四角往往就是设计的一部分，
   而圆形遮罩一定会切四角。定尺寸时要反推安全圆（66dp）而不是可见方形（72dp）。
3. **改 App 私有数据目录要连元数据一起修。** `cp` 只挪内容：属主错 → 读不到；
   权限错 → 读不到；SELinux 上下文错 → 读不到。三者都**不报错**，只表现为"配置没生效"——
   与这个工程反复踩到的「静默给错结果」是同一类。

---

## 附录 G：内置字体扩充（v1.16，2026-09-15）

### G.1 选型与排除理由

来源：本机 `D:/Acg/Ani/runtime/lib/fonts/` 的整套 OFL 字体（fontsource 渠道只有 woff2，
Android `res/font` 只吃 ttf/otf，用不了）。逐款用 python 读 name 表 + cmap 验证：

| 字体 | 收录 | 理由 |
| --- | --- | --- |
| JetBrains Mono | ✅ r/m/b 三档 | 用户点名；字高偏大、易混字符区分明显 |
| Fira Code | ✅ r/m/b 三档 | 编程连字代表 |
| Source Code Pro | ✅ r/b 两档 | Adobe 经典等宽 |
| Cascadia Code | ✅ 单档 | 与已内置的 Mono 同源，补连字版 |
| Inconsolata | ❌ | 仅 296 字形；13 个编程符号缺 10；**name 表无许可文本**，来源不明 |
| Droid Sans Mono | ❌ | 系统已自带，打包白占体积 |

### G.2 落地

- `res/font/` 新增 9 个 TTF（2.41 MB），逐档 **md5 回执校验**（源=Dst）。
- `res/raw/` 新增 4 份许可声明 `*_notice.txt`：OFL 正文**逐字取自官方文本**（`tools/gen_font_notices.py`
  幂等生成，自校验「生成文件里从 `SIL OPEN FONT LICENSE…` 起的正文与官方文本逐字节一致」）；
  版权行逐字取自各字体 name 表 ID 0；`cascadia_code_notice.txt` 全文取自其 name 表 ID 13
  （同 Cascadia Mono 的「基于 SIL OFL 的 Microsoft 字体许可」）。
- `EditorFonts.kt`：静态字体直接 `Font(resId, weight)`，**不传 `variationSettings`**
  （可变字体才需要钉 wght 轴）；新增 `credit` 字段与 `BUNDLED_EDITOR_FONTS` 过滤清单；
  新增 id：`cascadia-code` / `jetbrains-mono` / `fira-code` / `source-code-pro`（**发布后不可改**）。
- `SettingsScreen.kt`「关于」段改为按清单列举各款授权署名。
- 自检 stub `tools/pure_helpers_check/stub/R.kt` 同步补 9 个 `R.font.*` 字段
  （`EditorFonts.kt` 在自检编译单元内，漏补即编译失败）。

### G.3 自检与验收

- 自检新增 **V 组**（id 唯一、默认 id 在清单内、样张特征、许可文件名与 id 对得上）与
  run.sh 的 **font assets 段**（三个方向：清单点名的声明必须存在、`res/raw` 的声明必须被引用、
  未被清单引用的 ttf 报警）。⚠️ 判据只用 ASCII，脚本可能按 GBK 落盘。
- 探针扩到 **10 种变异全部 CAUGHT**；纯函数自检 **447 项全绿**。
- `assembleRelease` 成功；APK 内逐字节核对 10 个 TTF 与 5 份声明全部在包、登记在 `resources.arsc`。
- 真机（MuMu/Android 12）：设置页 6 款齐全、说明文案正确、切到 JetBrains Mono 落盘
  `editor_font_id=jetbrains-mono` 并即时生效。
- ⚠️ 首次增量编译 release 失败且无源码级报错，二分未锁定触发点，`clean` 全量构建后连续多次成功
  —— 与 E.9.2「坏增量产物」同类，再次印证「`BUILD SUCCESSFUL` ≠ 产物可信」。

---

## 附录 H：竞赛题目 403 —— 隐藏题的 `tid` 授权（v1.17，2026-09-15）

### H.1 现象与根因（100% 复现）

普通用户（114514）已参加进行中的竞赛 Week3（`tid=6aa017a4dae06c1b91a46da5`，IOI 赛制，
`tsdoc.attend=1`），竞赛详情题目列表（26 题 A~Z）**正常**，但点开任一题：

```
You don't have the required permission (View hidden problems)
```

设备端带会话只读探测（模拟器 `/system/bin/curl` + `Cookie: sid=…`，恒 `Accept: application/json`）：

| 请求 | 结果 |
| --- | --- |
| `GET /contest/<tid>` | 200，`tdoc.pids` 26 项 |
| `GET /contest/<tid>/problems` | 200，`pdict` 完整 |
| `GET /p/154` | **403** `PermissionError: View hidden problems` |
| `GET /p/154?tid=<tid>` | **200**，`pdoc.title='拆机螺丝'`，content 616 字符 |

**结论**：竞赛题是隐藏题（`pdoc.hidden=true`），普通用户没有全局「查看隐藏题目」权限；
**竞赛上下文 `?tid=` 是参赛者的授权通道**。App 的题目跳转漏传 `tid`，是唯一根因。

### H.2 修复

1. **路由**：`Routes.PROBLEM_DETAIL` / `Routes.EDITOR` 增加可选 `tid` 查询参数；
   竞赛/作业详情页的 `onOpenProblem` 传入 `tdoc._id`。
2. **数据层**：`ProblemRepository.detail(docId, tid?)`、`SubmissionRepository.submit/pretest`
   均带 `tid`（有才追加 `?tid=`）。
3. **错误文案**：新增 `data/net/ErrorMessages.kt` —— `humanize(name, message)` 按**错误 name** 分类
   （`PermissionError`/`ContestNotAttendedError`/`ContestNotLiveError`/`HomeworkNotLiveError`/
   `*NotFoundError`…），未识别的原样透传；`HydroResult.Failure` 新增 `friendly`（`message`
   保留站点原文供诊断），`errorText()` 走 `friendly`，所有 UI 的 Failure 分支 `.message` → `.friendly`。

### H.3 自检与真机验收

- **W 组 18 项**钉住：翻译到位、**未知 name 原样透传（不猜）**、空 message 兜底、
  **分类只看 name 不看 message 撞词**、译文不留 `{0}` 占位符与英文原句、
  `Failure.message` 原文 / `friendly` 中文 / `errorText()` 走 `friendly` 的三分工。
  自检 **447 项全绿**；`compileReleaseKotlin` 零 `e:` 零 `w:`。
- 真机复走：竞赛列表 → Week3 → A「拆机螺丝」**题面正常渲染**（题干/格式/样例/Note），
  「去写代码」→ **编辑器正常打开**（顶栏题面、语言 cc、符号条、自测/提交入口齐全），
  权限错误消失。**未真提交/自测**，避免在进行中的竞赛里留下评测痕迹
  （提交链路与 detail 同参数，契约 v1.11 已闭环）。

### H.4 教训

1. **列表可见 ≠ 详情可达。** 竞赛题目列表走 `/contest/:tid/problems`，天然带竞赛上下文；
   详情走 `/p/:docId` 就丢了。授权按「请求上下文」而非「用户身份」判定时，
   **每个入口都要把上下文带全**。
2. **探测优先用设备自身的网络与会话**（模拟器里 `/system/bin/curl` + root 读出的 cookie），
   与宿主机 anycast 抖动完全解耦，且视角与用户真实路径一致。
3. 错误文案的映射键用**错误的 `name`**（稳定枚举）而不是 message 文本 —— 站点改文案不会翻错，
   未识别的原样透传，宁可显示真话也不编假话。

---

## 附录 I：撤销栈 + 查找替换 + 头像兜底（v1.18，2026-09-15）

### I.1 撤销/重做（C4-1）

**纯逻辑** `ui/editor/CodeUndoStack.kt`：快照栈（`TextFieldValue` 整帧入栈，上限 100 条）。

- **合并规则**：`push(old, new)` 时判断本次编辑的 `kind` —— 打字（单字符插入，光标恰在旧光标 +1）与退格（`new.selection.end == lastCaret - 1`）在「光标连续」时并入上一条，**替换/粘贴等多字符变化独立成条**；换向（打字↔退格）即断条。
- **判据来源**：`kind` 与合并条件全部取自 `selection` / `old.text`，**绝不用 diff 下标** —— 与 `CodeEditActions` 的成对删除是同一条硬约束（「被删字符与左侧相同」时 diff 下标会错位）。
- **接线**：`ComposeCodeEditor` 的 `onValueChange` 在 `CodeEditActions.apply` 之后 push；`undo()/redo()` 恢复整帧（文本 + 光标）并回写 `lastEmitted`；外部重置 `value` 时**清栈**（外部重置语义上不是可撤销的本地编辑）。
- **手柄**：`CodeEditorHandle` 增加 `undo()/redo()` 与 `canUndo/canRedo`（`mutableStateOf`，编辑器组合时双向接线、离场清空），EditorScreen 底栏按钮直接读状态自动启停。
- **硬件键**：`onPreviewKeyEvent` 里 Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z（预览阶段拦截，先于 IME）。

**教训**：Y 组首跑两个 FAIL 均为**用例设计错误**（复用了被上段用例污染的栈状态），重写为各自独立干净栈；第三个 FAIL 是**真 bug** —— 退格合并条件写成 `lastCaret`（差一），连打两个退格本应合成一条却断成两条，已修并用用例钉住。

### I.2 查找替换（C4-2）

**纯逻辑** `ui/editor/CodeFindReplace.kt`：

- `findAll(text, query, ignoreCase)`：不重叠、前向、返回区间列表；`query` 为空返回空表。
- `replaceOne / replaceAll`：返回新文本与新光标；`replaceAll` 一次替换全部。
- **高亮**：匹配区间画在 `Canvas` 装饰层（当前匹配加重、其余淡色），**不进 `VisualTransformation`、不改文本长度** —— 高亮既定约束的又一次应用。

**UI**：顶栏搜索图标开合查找条（查找框 / `Aa` 大小写开关 / 上一个·下一个 / 展开替换 / 全部替换 / 关闭）。计数「n/m」随输入即时刷新；替换条展开后出现「替换为」与「全部替换」。

### I.3 头像兜底（v1.14 遗留决策）

`ui/component/UserAvatar.kt`：真实头像（`qq:`/`url:` 均可加载）失败时回落**首字母 + 容器色**色块。`gravatar:` 六人因域名国内不可达恒走兜底 —— 排行榜真机确认不再空白，观感原生无违和。

### I.4 回归与真机验证

| 项 | 结果 |
| --- | --- |
| 自检 `pure_helpers_check` | 28 组 **497 项全绿**（Y 组 18 + Z 组 20 新增） |
| 探针 `probe.sh` | 10 变异全 CAUGHT，md5 复原 |
| `abi_check` | 590 class 全绿 |
| 双构建 | assembleDebug + assembleRelease 均绿 |
| 安装 | debug→release 覆盖安装成功（同签名，双向验证） |
| 真机 #5（release） | undo 一次撤光；redo 逐像素恢复；按钮态随栈启停；查找高亮；全部替换；替换可整体撤销 —— 全过 |

**真机测试姿势坑**：`uiautomator` dump 拿不到自绘编辑器文本（截图对比代替）；**IconButton 热区 ≠ dump 文本坐标** —— 底栏撤销 ≈(64,1540)、重做 ≈(163,1540)，点 (105,1520) 落两按钮间隙无反应，曾伪装成「重做失效」假 bug；题面面板展开时顶栏查找被遮，先点「收起」(≈860,205)。
