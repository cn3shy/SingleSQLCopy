# Single Sql Copy

Single Sql Copy 是一个 IntelliJ IDEA 插件，用于把多行 SQL 压缩成紧凑的一行，并支持复制、替换编辑器中选中的内容、插入到当前光标处。插件优先适配 Oracle 方言，同时兼容通用 SQL。

## 功能

- 从编辑器选中内容读取 SQL；没有选中时读取当前编辑器全文。
- 手写词法状态机，不依赖 JSqlParser 等第三方 SQL 解析库。
- 识别并原样保留：
  - 单引号字符串，包括 SQL 的 `''` 转义；
  - 双引号标识符，包括 `""` 转义；
  - Oracle q-quote，例如 `q'[...]'`、`q'{...}'`、`q'(...)'`、`q'<...>'`、`q'!...!'`；
  - Oracle Hint，例如 `/*+ INDEX(t idx_t) */`。
- 可配置：
  - 方言：Oracle（默认）/ Generic；
  - 删除注释：默认开启；
  - 保留 Oracle Hint：默认开启；
  - 折叠空白：默认开启。
- 普通注释删除时会用空格分隔相邻 token，避免 SQL 粘连。
- 输出结果自动换行展示，并默认全选，可直接复制。
- 对话框为非模态窗口，打开后仍可继续操作 IDEA 主窗口，从其他文件复制内容后粘贴到 SQL 输入区。
- 字符串或 q-quote 内含换行时原样保留，绝不为了物理单行而修改字符串值。
- 对同一输入重复压缩保持幂等。

## 环境要求

- JDK 17（构建插件）
- IntelliJ IDEA 2026.1 或更高版本
- 插件构建使用 Gradle Kotlin DSL、Kotlin 2.x、IntelliJ Platform Gradle Plugin 2.x

插件以 IDEA 2026.1 系列的平台 API 编译，`sinceBuild=261`，并且不设置 `untilBuild` 上限，因此仅支持 IntelliJ IDEA 2026.1 及之后的版本（26+），不再兼容 2023–2025 版本。

## 构建与测试

在项目根目录执行：

```bash
./gradlew test buildPlugin
```

Windows 下也可以使用：

```bat
gradlew.bat test buildPlugin
```

构建产物位于：

```text
build/distributions/
```

## 本地运行插件

```bash
./gradlew runIde
```

该命令会启动一个带当前插件的 IntelliJ IDEA 沙箱实例。

## 使用方法

1. 在编辑器中选中一段 SQL；也可以不选中，此时使用当前编辑器全文。
2. 通过以下任一入口打开对话框：
   - 右键菜单中的 **Single Sql Copy**；
   - **Tools** 菜单中的 **Single Sql Copy**；
   - 默认快捷键 `Ctrl+Alt+Shift+M`。
3. 在对话框中检查输入内容、方言和选项。
4. 点击 **压缩** 生成结果；输出区自动换行展示，并可继续手工编辑。
5. 使用 **复制**、**替换选中** 或 **插入光标处** 处理结果。
6. 点击 **关闭** 退出对话框。

对话框为非模态窗口。打开后可以继续操作 IDEA 主窗口：在其他文件中复制内容，再回到对话框粘贴到 SQL 输入区。

**替换选中** 在编辑器存在选中内容时可用；打开对话框时没有选中会禁用，之后在编辑器中重新选中内容会自动启用。**插入光标处** 始终可用。

## 安全与语义说明

### Oracle Hint

- 默认保留 `/*+ ... */`，避免影响执行计划。
- 如果关闭 **保留 Oracle Hint** 并开启 **删除注释**，Hint 会被删除。
- 关闭 **删除注释** 时，所有注释（包括 Hint）都会保留。

### 字符串内换行

SQL 字符串或 q-quote 内部可能包含真实换行。此时插件不会删除或替换换行，因为那会改变字符串值。压缩结果会保留换行，输出区可能不是物理单行。

这是预期行为，不是压缩失败。对话框不展示警告或状态区域。

### 注释与空白

- 普通行注释 `-- ...`（Generic 方言还支持 `# ...`）和块注释 `/* ... */` 按选项删除。
- 删除注释处会保留一个空格，防止两侧 token 粘连。
- 字符串、q-quote、双引号标识符内部的 `--`、`#`、`/* */`、空白和分号不会被当作 SQL 结构处理。
- 不强制转换关键字大小写，不改变数字、标识符、函数名或运算符。

### 安全扫描

压缩核心仍包含 `SqlSafetyScanner`，可以检测 `UPDATE` / `DELETE` 是否缺少顶层 `WHERE`，并支持基础的 `WITH ... UPDATE/DELETE` 主语句识别。该能力用于核心 API 和单元测试，对话框不展示警告/状态区域，也不会阻止复制、替换或插入。

## GitHub Actions 打包与发布

仓库包含 `.github/workflows/build.yml`。推送形如 `v2610.19.2` 的 tag 时会自动：

1. 使用 JDK 17 运行 `./gradlew test buildPlugin`；
2. 上传插件 zip 到 Actions Artifact；
3. 通过 `publishPlugin` 上传到 JetBrains Marketplace；
4. 创建 GitHub Release 并附带构建出的 zip。

发布前需要在仓库 Secrets 中配置：

```text
PUBLISH_TOKEN
```

该 token 来自 JetBrains Marketplace 的 **My Tokens**。GitHub Release 使用自动提供的 `GITHUB_TOKEN`。

版本由 tag 去除前缀 `v` 后覆盖，例如 `v2610.19.2` 会构建 `2610.19.2`。本地默认版本在 `gradle.properties` 的 `pluginVersion` 中配置。

## 项目结构

```text
single-sql-copy/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  README.md
  .github/workflows/build.yml
  gradle/wrapper/
  gradlew
  gradlew.bat
  src/main/resources/META-INF/plugin.xml
  src/main/resources/META-INF/pluginIcon.svg
  src/main/kotlin/io/github/cn3shy/ssc/action/SingleSqlCopyAction.kt
  src/main/kotlin/io/github/cn3shy/ssc/ui/SingleSqlCopyDialog.kt
  src/main/kotlin/io/github/cn3shy/ssc/core/SqlDialect.kt
  src/main/kotlin/io/github/cn3shy/ssc/core/MinifyOptions.kt
  src/main/kotlin/io/github/cn3shy/ssc/core/MinifyResult.kt
  src/main/kotlin/io/github/cn3shy/ssc/core/SqlOneLineMinifier.kt
  src/main/kotlin/io/github/cn3shy/ssc/core/SqlSafetyScanner.kt
  src/test/kotlin/io/github/cn3shy/ssc/core/SqlOneLineMinifierTest.kt
  src/test/kotlin/io/github/cn3shy/ssc/core/SqlSafetyScannerTest.kt
```

## 实现边界

插件使用词法状态机而不是完整 SQL AST，目标是安全压缩，不尝试理解全部 PL/SQL 语义。复杂 `BEGIN ... END` 块中的 DML 不会被单独拆分扫描；需要完整语义分析时应使用数据库侧 SQL 审核工具。
