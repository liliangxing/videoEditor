# Claude / Monkey‑Code 长期记忆文件

> 本文件记录了项目的技术事实、用户的工作流偏好和 AI 行为指令。  
> 每次开始新任务前，AI 应主动读取本文件并严格遵守其中的 `Instructions`。

## 格式约定
- 每个条目包含：摘要标题、Date、Context、Category（可选）、Instructions（逐条列表）。
- 添加新条目前检查重复，合并相似内容。
- 修改或禁用某条规则时，可直接编辑 Instructions 或在标题后加 `[DISABLED]`。

## 条目

### 读取项目上下文文档（必须优先执行）
- Date: 2026-06-13
- Context: 用户创建了 `AI_CONTEXT.md` 作为项目静态地图
- Category: 工作流协作
- Instructions:
  - 每次开始分析代码、写代码或回答问题前，必须先读取项目根目录下的 `AI_CONTEXT.md`
  - 该文件包含目录结构、技术栈、核心命令、模块职责
  - 未读取该文件前，不得给出任何代码修改建议或执行命令

### 发布流程规范（Android App）
- Date: 2026-06-13
- Context: 用户要求标准化发布步骤
- Category: 工作流协作
- Instructions:
  - 步骤1：确保所有代码已提交 → `git add .` + `git commit` + `git push`
  - 步骤2：编译 Release APK → `./gradlew assembleRelease`
  - 步骤3：APK 命名规则 → `videoEditor-v{版本号}.apk`（版本号如 `1.0.0`）
  - 步骤4：创建 GitHub Release（tag 与版本号一致），并上传 APK 作为附件
  - 注意：永远不要在没有 `git push` 的情况下直接编译 Release

### 视频归档功能业务规则
- Date: 2026-06-13
- Context: 用户描述了归档模块的完整需求
- Category: 功能规范
- Instructions:
  - 扫描目录：`/sdcard/Music/douyinguanjia/`
  - 只处理**非空**的 `.mp4` 文件（文件大小 > 0）
  - 每 500 个文件打包成一个 ZIP，命名格式：`douyinguanjia1.zip`，`douyinguanjia2.zip`，...
  - **去重规则**：比较文件大小，如果大小完全一致，则跳过（视为重复文件不打包）；如果大小不一致，则保留两个文件，第二个文件生成新名称（例如 `originalName_v2.mp4`）
  - 压缩完成后，删除已被打包的源文件（包括重复跳过的文件不删除？——澄清：仅删除成功打包进 ZIP 的文件，跳过的重复文件保留）
  - 归档操作**不需要用户先选择视频**，直接执行扫描和打包

### 音频提取核心参数（FFmpeg）
- Date: 2026-06-13
- Context: 从 `MainActivity.java` 中总结的技术实现
- Category: 技术事实
- Instructions:
  - FFmpeg 库：`com.arthenica:mobile-ffmpeg-full:4.4.LTS`（或项目实际使用的 `FFmpegAndroid 0.3.2`）
  - M4A 输出命令：`-y -i {inputPath} -vn -acodec aac -ar 44100 -ac 2 -b:a 128k {outputPath}.m4a`
  - MP3 输出命令：`-y -i {inputPath} -vn -ar 44100 -ac 2 -b:a 192k -f mp3 {outputPath}.mp3`
  - 输出保存路径：`/sdcard/Music/audio_{timestamp}.m4a`（或 mp3）
  - 处理前需将视频 URI 复制到应用缓存目录（`/data/data/包名/cache/`）

### 项目技术栈固定事实
- Date: 2026-06-13
- Context: 从代码分析和 README 提取
- Category: 技术事实
- Instructions:
  - 语言：Java 100%（非 Kotlin）
  - 目标 SDK：Android 14（API 34），最低兼容 API 24（Android 7.0）
  - 构建工具：Gradle 8.0 + Android Gradle Plugin 8.0.0
  - 依赖仓库：阿里云镜像（加快国内下载）
  - 日志位置：`/sdcard/douyinguanjia/Log/videoEdit.log`

### 禁止读取的目录（节省 Token）
- Date: 2026-06-13
- Context: 优化 AI 上下文效率
- Category: 环境配置
- Instructions:
  - AI 应避免读取以下目录/文件：`build/`、`.gradle/`、`*.iml`、`*.log`、`*.apk`、`local.properties`
  - 可以将这些规则加入 `.gitignore` 和 `.claudeignore`（如果支持）

### 当前对话中待补充的规则
- Date: 2026-06-13
- Context: 用户可以随时要求添加新记忆
- Category: 占位符
- Instructions:
  - 如果你希望 AI 记住额外的偏好（如代码风格、命名约定、测试要求），请直接说“记住：...”，我会帮你格式化后建议添加到本文件。