# 开源发布：Git 命令清单

> 命令全部由你自己执行，本文档只做指引。按顺序执行即可。
> `<>` 内为需要你替换的占位符。

## 第 0 步：安全自查（推送前必做）

本项目有两类内容**绝不能**进入公开仓库：

| 内容 | 位置 | 状态 |
| --- | --- | --- |
| 签名密钥与凭据 | `android-app/keystore/`（jks）+ `android-app/keystore.properties` | 已写入 `.gitignore` |
| 本机 SDK 路径 | `android-app/local.properties` | 已写入 `.gitignore` |
| 构建产物 | `android-app/**/build/`、`.gradle/`、`.kotlin/` | 已写入 `.gitignore` |
| 本地工作区（AI 记忆/临时文件） | `.workbuddy/` | 已写入 `.gitignore` |

仓库根已生成 `.gitignore`，推送前用下面命令**逐条确认**：

```bash
# 查看将被忽略的文件（应包含 keystore、local.properties、build、.workbuddy）
git check-ignore -v android-app/keystore/jxau-oj-release.jks                     android-app/keystore.properties                     android-app/local.properties                     android-app/app/build

# 反向确认：列出实际会提交的文件，人工过一遍
git ls-files --others --exclude-from=.gitignore | head -50
```

**另外两处需要你拍板**（不阻塞，但公开前想清楚）：

1. **站点地址**：`README`、代码 `BuildConfig.SITE_BASE_URL`、`docs/`、`tools/` 探测脚本中都出现了你的站点域名。代码里是硬编码的（必须保留 App 才能工作）；若不想公开域名，可把 README 里的站点描述改为泛化表述。
2. **docs/ 与 tools/**：`docs/JXAU-OJ-App-功能方案.md` 含完整的接口契约实测记录与站点内部信息；`tools/m0_probe.py` 等探测脚本含站点地址。二者对开源社区有参考价值，但也最「暴露底细」，可以选择不提交这两个目录（在 `.gitignore` 追加 `docs/`、`tools/` 即可）。

## 第 1 步：（可选但推荐）补充 LICENSE

开源必须有许可证，否则他人默认无权使用。README 已预留 `LICENSE` 链接。
常用选择：**MIT**（最宽松）/ **Apache-2.0**（含专利条款）/ **GPL-3.0**（强传染）。
可到 <https://choosealicense.com> 选择，把全文存为根目录 `LICENSE` 文件
（若选 MIT/Apache，版权行填你的名字与年份）。

## 第 2 步：初始化本地仓库

```bash
# 进入项目根目录
cd D:/IO/HTML/Hydro

# 在当前目录初始化一个空的 Git 仓库（生成 .git/）
git init

# 把默认分支命名为 main（新仓库主流约定；init 后执行一次即可）
git branch -M main
```

## 第 3 步：添加文件并提交首个版本

```bash
# 把工作区所有未忽略的文件加入暂存区（.gitignore 生效，敏感文件不会进来）
git add .

# 提交前最后检查：确认暂存区里没有 keystore / local.properties / build / .workbuddy
git status

# 创建首个提交；-m 后面是提交说明
git commit -m "chore: 初始开源版本 v1.0.0（对应内部迭代 v1.1-v1.18）"
```

## 第 4 步：关联远程仓库并推送

先在托管平台（GitHub / Gitee 等）**创建一个空仓库**（不要勾选自动生成 README，
否则首次推送会冲突），拿到仓库地址后：

```bash
# 把远程仓库注册为本地的 origin（地址换成你的）
git remote add origin <远程仓库地址>

# 确认关联成功（应列出 origin 与其 fetch/push 地址）
git remote -v

# 首次推送：把 main 分支推到远程并建立跟踪关系（-u 只需第一次）
git push -u origin main
```

## 第 5 步：打版本标签

CHANGELOG.md 的发布版本对应 7 个标签。打**附注标签**（annotated，带说明，推荐用于发布）：

```bash
# 逐个打标签（-a 附注标签，-m 标签说明；都在当前提交上，指向同一 commit）
git tag -a v0.1.0 -m "v0.1.0 工程骨架 + M1 核心闭环（内部 v1.5-v1.6）"
git tag -a v0.2.0 -m "v0.2.0 M2 社区页面（内部 v1.7-v1.9）"
git tag -a v0.3.0 -m "v0.3.0 真机稳定性专项（内部 v1.10-v1.12）"
git tag -a v0.4.0 -m "v0.4.0 成绩表 + 编辑器体验专项（内部 v1.13-v1.14）"
git tag -a v0.5.0 -m "v0.5.0 正式包 + 字体系统（内部 v1.15-v1.16）"
git tag -a v0.6.0 -m "v0.6.0 权限修复 + 错误文案（内部 v1.17）"
git tag -a v1.0.0 -m "v1.0.0 编辑器二期收官（内部 v1.18）"

# 查看全部标签确认
git tag -l

# 推送所有标签到远程（标签不会随 git push 自动上传，需单独推）
git push origin --tags
```

> 提示：这些标签都指向同一个提交（当前代码即 v1.0.0 的状态），历史版本没有对应的历史提交。
> 如果你希望标签指向各自时期的代码快照，需要回溯提交历史，工作量大且收益有限；
> 一般开源做法就是如上「首个提交 = 当前版本」，历史信息由 CHANGELOG 承载。

## 第 6 步：（可选）提交 Gradle Wrapper，方便他人构建

仓库目前**不含 `gradlew`**，他人克隆后需自装 Gradle。若想免去这一步：

```bash
# 在 android-app/ 下用本机 Gradle 生成 wrapper（需本机已装 8.10.2）
cd android-app
gradle wrapper --gradle-version 8.10.2

# 会生成 gradlew / gradlew.bat / gradle/wrapper/*，回到根目录提交推送
cd ..
git add android-app/gradlew android-app/gradlew.bat android-app/gradle/
git commit -m "build: 提交 Gradle Wrapper，克隆后可离线直接构建"
git push
```

## 日常更新流程（以后每次改完代码）

```bash
# 1. 查看改了什么（红=未暂存，绿=已暂存）
git status
git diff

# 2. 暂存 + 提交（说明写清「做了什么」）
git add .
git commit -m "feat: xxx"

# 3. 推送
git push

# 4. 发新版本时：更新 CHANGELOG.md → 提交 → 打标签 → 推送标签
git add CHANGELOG.md
git commit -m "docs: 发布 v1.1.0"
git tag -a v1.1.0 -m "v1.1.0"
git push origin main --tags
```

## 常用核查命令备忘

```bash
# 看某个文件为什么/是否被忽略
git check-ignore -v <路径>

# 看标签指向的提交与说明
git show v1.0.0 --no-patch

# 远程仓库与本地差异
git fetch && git status

# 误 add 了敏感文件、还没 commit 时，把它移出暂存区（文件保留在磁盘）
git restore --staged <路径>

# 误 commit 了敏感文件、还没 push 时：改完 .gitignore 后重写最后一次提交
git reset --soft HEAD~1 && git add . && git commit -m "..."
# ⚠️ 如果已经 push，密钥视为泄露：立即换密钥（keytool 重新生成）并改所有凭据
```
