# JXAU OJ 移动端

一个为 [Hydro](https://hydro.ac/) 在线评测系统（OJ）打造的原生 Android 客户端。
**纯原生** Kotlin + Jetpack Compose（Material 3）实现，不使用 WebView 套壳，
针对校内 OJ 的日常刷题场景做了大量移动端交互优化：题面阅读、代码编辑、提交评测、
自测调试、竞赛与作业跟踪，一部手机全部完成。

> **免责声明**：本项目是与 Hydro OJ 及其部署站点无关的第三方客户端，
> 仅通过站点公开的 HTTP 接口以用户本人身份访问，不提供任何绕过权限或批量抓取的能力。

## 核心功能

### 刷题主线
- **题库浏览**：题号 / 题名 / 通过率 / 通过人数，按关键字搜索。
- **题面阅读**：自研 Markdown 渲染器（标题、粗体、行内代码、公式占位），支持在题目详情与编辑器间随时下拉/收起。
- **代码编辑器**：纯 Compose 实现的专业代码编辑器——
  - 语法高亮（注释 / 字符串 / 数字 / 关键字 / 内建函数五类，随深浅色主题与 14 款内置主题联动）；
  - 撤销 / 重做（连击自动合并，支持 Ctrl+Z/Y）；
  - 查找替换（大小写开关、逐个/全部替换、匹配高亮）；
  - 自动补全（保留字 + 内建函数 + 当前代码标识符，硬件键盘完整可用）；
  - 编辑辅助：自动补配对括号、跳过已有右括号、智能缩进、成对删除、选区包裹、成对高亮、符号条；
  - 6 款内置开源编程字体（Cascadia Mono/Code、JetBrains Mono、Fira Code、Source Code Pro）+ 字号调节；
  - 草稿本地自动保存，退出不丢代码。
- **提交与评测**：提交后退避轮询评测结果（封顶 90 秒），判题状态色与主题解耦、深浅色均可读；评测详情可回看并复制已提交代码。
- **自测（pretest）**：自定义多组输入直接运行，实时查看 stdout，不计提交数。

### 竞赛与作业
- 竞赛 / 作业列表与详情，未开始 / 进行中 / 已结束阶段徽标由起止时间本地推算。
- 竞赛题目 A~Z 标号列表，正确携带竞赛上下文访问隐藏题。
- **竞赛成绩表**：实时榜单（每 10 秒静默刷新），本人行高亮，格子直跳评测详情。

### 社区与个人
- 排行榜、用户主页、提交记录（全站 / 本人 / 单题三种范围）。
- 头像加载失败自动回落首字母色块，观感无违和。

### 个性化
- 14 款内置主题（Material You 动态取色风格），判题状态色恒定可读。
- 深色模式完整适配。

## 适用场景

- **学生**：通勤 / 课间用手机看题面、写代码、交题、盯竞赛榜，不依赖电脑。
- **教练 / 助教**：随时查看作业完成情况、竞赛成绩表与选手提交记录。
- **自建 Hydro 站点的管理员**：为站点成员提供一个轻量的移动端入口。

## 安装

### 方式一：下载 Release APK（推荐）

从本仓库的 [Releases](../../releases) 页面下载最新的 `app-release.apk`，安装即可。
要求 Android 8.0（API 26）及以上。

### 方式二：自行构建

环境要求：

| 依赖 | 版本 |
| --- | --- |
| JDK | 17 |
| Android SDK | Platform 35（compile/target）、Build-Tools 按 AGP 8.7.3 默认 |
| Gradle | 8.10.2 |
| Kotlin | 2.0.21（由构建脚本自动应用） |

```bash
# 克隆仓库
git clone <仓库地址>
cd <仓库目录>/android-app

# 生成 Gradle Wrapper（仓库不含 gradlew，需本机装有 Gradle 8.10.2 时执行一次）
gradle wrapper --gradle-version 8.10.2

# Debug 构建
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release 构建（需自行配置签名，见下）
./gradlew assembleRelease
```

**签名配置**（仅 release 需要）：在 `android-app/` 下创建 `keystore.properties`：

```properties
storeFile=<jks 文件路径>
storePassword=<keystore 口令>
keyAlias=<别名>
keyPassword=<key 口令>
```

不配置签名时 release 构建会使用 debug 签名，仅供本地测试。

> 项目默认**完全离线构建**：所有依赖来自 Gradle 缓存。若首次构建提示缺依赖，
> 联网执行一次 `./gradlew assembleDebug` 拉取即可；国内网络建议配置镜像源。

## 使用说明

1. **登录**：使用你在 OJ 站点的账号密码登录（记住登录状态可勾选「保持登录」）。游客也可浏览题库与题目。
2. **写题**：题库 → 题目详情 → 「去写代码」。编辑器顶栏下拉可看题面，底栏符号条提供常用符号，写完点「提交」或先「自测」。
3. **竞赛/作业**：「我的」页进入竞赛与作业列表；进行中的竞赛可看题、交题、看实时成绩表。
4. **个性化**：设置页可切换主题、编辑器字体与字号。

## 项目结构

```
android-app/          # Android 工程（Kotlin + Compose，单 Activity）
  app/src/main/java/com/jxau/oj/
    data/net/         # HTTP 客户端、四形态响应分发、错误文案
    data/             # DTO 与手写 JSON 映射
    ui/theme/         # 主题目录、字体目录（清单驱动）
    ui/editor/        # 编辑器引擎：编辑动作、高亮、补全、撤销栈、查找替换
    ui/component/     # Markdown 解析器等通用组件
    ui/               # 各页面（题库/题目/编辑器/竞赛/作业/排行/记录/设置）
design/               # 主题种子与生成物
tools/                # 开发工具：图标/字体许可/主题生成脚本、离线自检、ABI 体检
docs/                 # 功能方案与设计文档
```

## 开发

项目强调**可验证性**：`tools/` 下自带纯函数离线自检（数百项断言）、变异探针
（人为改坏源码验证自检有效性）与字节码 ABI 体检，详见 `tools/pure_helpers_check/`。

## 许可

- 项目代码采用的许可证见 [LICENSE](LICENSE) 文件。
- 随包分发的字体均为开源许可（SIL OFL 及基于其的字体许可），许可正文逐字保留于
  `app/src/main/res/raw/*_notice.txt`，并在应用内「设置 → 关于」展示。
