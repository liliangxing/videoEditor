# VideoToAudio - 项目上下文文档

本文件为 AI 辅助编程提供项目骨架、技术栈和核心命令，避免无效搜寻。

---

## 项目目录树

```
VideoToAudio/                              # 项目根目录
├── .gitignore                             # Git 忽略配置
├── AI_CONTEXT.md                          # 本文档 - AI 上下文
├── README.md                              # 项目说明文档
├── RELEASE_INSTRUCTIONS.md                # 版本发布指南
├── build.gradle                           # 项目级 Gradle 构建配置
├── settings.gradle                        # Gradle 项目设置
├── gradle.properties                      # Gradle 属性
├── gradlew                                # Gradle Wrapper 脚本
├── gradle/                                # Gradle Wrapper 文件
├── local.properties                       # 本地 SDK 配置
└── app/                                   # 主应用模块（Android App）
    ├── build.gradle                       # 模块级 Gradle 构建配置
    ├── proguard-rules.pro                 # ProGuard 混淆规则
    └── src/                               # 源代码目录
        └── main/                          # 主源码集
            ├── AndroidManifest.xml        # Android 清单文件
            ├── java/                      # Java 源代码
            │   └── com/simple/video2audio/
            │       ├── MainActivity.java          # 主界面与核心逻辑
            │       ├── VideoPlayerManager.java    # 视频播放管理
            │       ├── AudioPreviewActivity.java  # 音频预览界面
            │       ├── PreviewActivity.java       # 视频预览界面
            │       ├── util/FFmpegUtil.java       # FFmpeg 工具类
            │       └── views/VisualizerView.java  # 音频可视化控件
            └── res/                       # 资源文件
                ├── layout/                # 布局文件
                ├── drawable/              # 图片资源
                └── values/                # 字符串/主题/尺寸
```

---

## 技术架构速览

| 组件 | 技术/工具 | 说明 |
|------|----------|------|
| 开发语言 | Java (100%) | 纯 Java 实现 |
| 构建工具 | Gradle 8.0 | Android Gradle Plugin 8.0.0 |
| 目标 SDK | Android 14 (SDK 34) | 兼容 Android 7.0+ (API 24) |
| 最小 SDK | API 24 | Android 7.0 |
| 音频处理核心 | ffmpeg-kit-min-gpl 6.0-2 | FFmpeg 库 (通过 Maven) |
| Maven 仓库 | 阿里云镜像 | 加速国内依赖下载 |
| ABI 过滤 | arm64-v8a | 仅保留 arm64 架构 |

**FFmpeg 命令：**
- M4A: `-y -i INPUT -vn -acodec aac -ar 44100 -ac 2 -b:a 128k OUTPUT.m4a`
- MP3: `-y -i INPUT -vn -ar 44100 -ac 2 -b:a 256k -f mp3 OUTPUT.mp3`

---

## 核心业务流程

### 音频提取流程
1. 视频选择 → `ContentResolver.openInputStream()` 读取 URI
2. URI 类型判断 → PhotoPicker/MediaStore 直接复制，DocumentProvider 尝试获取持久化权限
3. 缓存复制 → 复制到 `/data/data/包名/cache/` 临时目录
4. FFmpeg 处理 → 延迟初始化 FFmpeg（用户首次点击提取时），执行音频提取命令
5. MP3 自动降级 → 编码器不可用时自动降级到 AAC
6. 输出保存 → 保存到 `/sdcard/Music/audio_时间戳.m4a/mp3`
7. 清理缓存 → 删除临时视频文件

### 归档视频流程
1. 扫描目录 → `/sdcard/Music/douyinguanjia/` 下非空 .mp4 文件
2. 分卷压缩 → 每 500 个文件打包成 `douyinguanjiaN.zip`
3. 去重处理 → 大小一致跳过，大小不一致生成新文件名
4. 删除源文件 → 压缩完成后删除原始 MP4 文件

---

## 关键模块与核心文件

| 文件 | 职责说明 |
|------|---------|
| `app/.../MainActivity.java` | 主界面 UI、权限处理、FFmpeg 调用、音频提取、视频归档核心逻辑 |
| `app/.../VideoPlayerManager.java` | MediaPlayer 视频播放管理（播放/暂停/进度回调） |
| `app/.../PreviewActivity.java` | 视频预览界面 |
| `app/.../AudioPreviewActivity.java` | 提取后的音频预览功能 |
| `app/.../util/FFmpegUtil.java` | FFmpeg 命令构建工具类 |
| `app/.../views/VisualizerView.java` | 音频可视化控件 |
| `app/.../AndroidManifest.xml` | 权限声明（存储/视频读取等）、应用组件注册 |
| `app/build.gradle` | 依赖管理、SDK 版本、签名配置等 |

---

## 开发常用命令

| 任务 | 命令 |
|------|------|
| 构建 Debug APK | `./gradlew assembleDebug` |
| 构建 Release APK | `./gradlew assembleRelease` |
| 安装 Debug 版 | `./gradlew installDebug` |
| 清理构建 | `./gradlew clean` |
| 查看应用日志 | `adb logcat` 或查看 `/sdcard/douyinguanjia/Log/videoEdit.log` |
| 创建 GitHub Release | `gh release create vX.X --title "videoEditor vX.X" --notes "..." APK路径` |

---

## 权限系统

| Android 版本 | 所需权限 | 处理方式 |
|-------------|---------|---------|
| Android 14+ (SDK 34) | `READ_MEDIA_VIDEO` | 运行时请求 |
| Android 11+ (SDK 30+) | `MANAGE_EXTERNAL_STORAGE` | 跳转设置页面授权 |
| Android 10 及以下 | `WRITE_EXTERNAL_STORAGE` | 运行时请求 |

---

## 日志系统

- **路径**: `/sdcard/douyinguanjia/Log/videoEdit.log`
- 记录权限状态、视频选择和复制、FFmpeg 命令执行、错误信息和堆栈

---

## 发布规范

1. 先推送代码 `git push origin main`
2. 编译 Release APK: `./gradlew assembleRelease`
3. APK 命名规范: `videoEditor-v{版本号}.apk`
4. 创建 Release 并上传 APK

---

## AI 辅助编程原则

1. **预加载上下文**: 本文件提供完整目录树、技术栈和核心命令，AI 无需 grep 或 find 结构信息
2. **过滤无用信息**: `build/`、`*.log`、`*.iml` 等编译/缓存文件通过 `.gitignore` 忽略
3. **代码简洁**: 不做无必要的注释，遵循项目已有风格
4. **先推送后发布**: 提交代码 → push → 编译 APK → 发布 Release
