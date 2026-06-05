# VideoToAudio - 视频转音频提取工具

一个简洁高效的 Android 视频转音频提取应用，使用 Android 原生 API 实现，无需任何第三方 FFmpeg 库。

## 下载

- [下载最新版本 (v2.0)](https://github.com/liliangxing/videoEditor/releases)
- 支持 Android 7.0 (API 24) 及以上版本

## 功能特点

- ✅ **一键提取**：选择视频文件，点击按钮即可提取音频
- ✅ **原生实现**：使用 Android 原生 MediaExtractor + MediaMuxer API
- ✅ **无需 FFmpeg**：不依赖任何第三方库，APK 体积小
- ✅ **Android 14 支持**：完全适配 Android 14 (SDK 34) 及存储权限
- ✅ **详细日志**：所有操作记录到日志文件，便于排查问题
- ✅ **简洁界面**：只有两个按钮，简单直观

## 技术架构

### 核心技术栈

| 组件 | 技术 | 说明 |
|------|------|------|
| 开发语言 | Java | 100% Java 实现 |
| 构建工具 | Gradle 8.0 | Android Gradle Plugin 8.0.0 |
| 目标 SDK | Android 14 (SDK 34) | 兼容 Android 7.0+ |
| 音频提取 | MediaExtractor | Android 原生媒体提取 API |
| 音频封装 | MediaMuxer | Android 原生媒体复用 API |
| 输出格式 | M4A (AAC) | MP4 容器封装 AAC 音频 |

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                          │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐ │
│  │  权限管理模块   │  │   UI 交互模块   │  │  日志模块   │ │
│  │                 │  │                 │  │             │ │
│  │ - 存储权限请求  │  │ - 视频选择      │  │ - 文件写入  │ │
│  │ - MANAGE_       │  │ - 音频提取      │  │ - 详细日志  │ │
│  │   EXTERNAL_     │  │ - 状态显示      │  │ - 错误追踪  │ │
│  │   STORAGE       │  │ - 结果弹窗      │  │             │ │
│  └─────────────────┘  └─────────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      音频提取流程                            │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐              │
│  │ 选择视频 │───▶│ 复制到缓存│───▶│ Media-   │              │
│  │          │    │          │    │ Extractor│              │
│  └──────────┘    └──────────┘    └──────────┘              │
│         │                                    │               │
│         │                                    ▼               │
│         │                           ┌──────────┐             │
│         │                           │ 查找音频 │             │
│         │                           │   轨道   │             │
│         │                           └──────────┘             │
│         │                                    │               │
│         │                                    ▼               │
│         │                           ┌──────────┐             │
│         │                           │ Media-   │             │
│         └──────────────────────────▶│  Muxer   │             │
│                                     │  封装输出│             │
│                                     └──────────┘             │
│                                           │                   │
│                                           ▼                   │
│                                     ┌──────────┐             │
│                                     │ Music/   │             │
│                                     │ audio_*.m4a│            │
│                                     └──────────┘             │
└─────────────────────────────────────────────────────────────┘
```

### 音频提取流程详解

1. **视频选择阶段**
   - 通过 `ActivityResultContracts.GetContent` 启动系统文件选择器
   - 获取用户选择视频的 `content://` URI
   - Android 13+ 请求 `READ_MEDIA_VIDEO` 权限
   - Android 10-12 请求 `READ_EXTERNAL_STORAGE` 权限

2. **缓存复制阶段**
   - 使用 `ContentResolver.openInputStream()` 读取视频流
   - 复制到应用缓存目录 `/data/data/包名/cache/`
   - 校验文件完整性和可读性

3. **音频提取阶段**（后台线程）
   ```java
   MediaExtractor extractor = new MediaExtractor();
   extractor.setDataSource(videoFile);
   
   // 遍历所有轨道，找到音频轨道
   for (int i = 0; i < extractor.getTrackCount(); i++) {
       MediaFormat format = extractor.getTrackFormat(i);
       String mime = format.getString(MediaFormat.KEY_MIME);
       if (mime.startsWith("audio/")) {
           audioTrackIndex = i;
           break;
       }
   }
   
   // 选择音频轨道
   extractor.selectTrack(audioTrackIndex);
   
   // 创建 Muxer
   MediaMuxer muxer = new MediaMuxer(outputFile, 
                                     MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
   int muxerTrackIndex = muxer.addTrack(audioFormat);
   muxer.start();
   
   // 读写数据
   while (true) {
       int sampleSize = extractor.readSampleData(buffer, 0);
       if (sampleSize < 0) break;
       
       muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo);
       extractor.advance();
   }
   
   muxer.stop();
   muxer.release();
   extractor.release();
   ```

4. **输出保存阶段**
   - 输出目录：`/storage/emulated/0/Music/`
   - 文件命名：`audio_时间戳.m4a`
   - 自动删除临时缓存文件

### 权限处理

| Android 版本 | 权限 | 处理方式 |
|------------|------|----------|
| Android 14+ (SDK 34) | `READ_MEDIA_VIDEO` | 运行时请求 |
| Android 13 (SDK 33) | `READ_MEDIA_VIDEO` | 运行时请求 |
| Android 11-12 (SDK 30-32) | `READ_EXTERNAL_STORAGE` | 运行时请求 |
| Android 11+ (SDK 30+) | `MANAGE_EXTERNAL_STORAGE` | 跳转到设置页面授权 |

### 日志系统

日志文件保存在：`/storage/emulated/0/Music/VideoToAudio.log`

记录内容包括：
- 应用启动信息（Android 版本、手机型号）
- 权限状态
- 用户操作（点击按钮、选择视频）
- 文件操作（复制、提取、删除）
- 异常堆栈信息

## 项目结构

```
VideoToAudio/
├── app/
│   ├── src/main/
│   │   ├── java/com/simple/video2audio/
│   │   │   └── MainActivity.java          # 主界面 + 核心逻辑
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml      # UI 布局
│   │   │   ├── values/
│   │   │   │   ├── strings.xml            # 字符串资源
│   │   │   │   └── themes.xml             # 主题样式
│   │   │   └── drawable/
│   │   │       └── ic_video.xml           # 图标
│   │   └── AndroidManifest.xml            # 应用清单
│   └── build.gradle                        # 应用级构建配置
├── build.gradle                            # 项目级构建配置
├── settings.gradle                         # 项目设置
├── gradle.properties                       # Gradle 属性
└── README.md                               # 本文档
```

## 构建说明

### 环境要求
- JDK 17+
- Android SDK 34
- Gradle 8.0+

### 构建步骤

```bash
# 克隆项目
git clone https://github.com/liliangxing/videoEditor.git
cd videoEditor

# 构建 Debug 版本
./gradlew assembleDebug

# APK 输出位置
app/build/outputs/apk/debug/app-debug.apk
```

### 配置说明

**app/build.gradle**
```groovy
android {
    namespace 'com.simple.video2audio'
    compileSdk 34

    defaultConfig {
        applicationId "com.simple.video2audio"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "2.0"
    }
}
```

## 技术支持

### 常见问题

**Q: 为什么提取失败？**
A: 请检查：
1. 视频文件是否包含音频轨道
2. 是否授权了存储权限
3. 查看 `/sdcard/Music/VideoToAudio.log` 日志文件

**Q: 输出是什么格式？**
A: M4A 格式（MP4 容器封装 AAC 编码），这是 Android 原生支持的最高效格式。

**Q: 为什么不用 FFmpeg？**
A: FFmpeg 库体积大（20MB+）、兼容性差、加载慢。原生 API 体积小、性能好、100% 兼容。

**Q: 可以输出 MP3 吗？**
A: 当前版本不支持。Android 原生 MediaMuxer 仅支持 MP4、WebM 容器。如需 MP3 需要集成 FFmpeg 编码器。

## 版本历史

### v2.0 (2026-06-05)
- ✅ 重写为 Android 原生 MediaExtractor + MediaMuxer 实现
- ✅ 移除 FFmpeg 依赖，APK 体积减小 80%
- ✅ 输出格式改为 M4A（更高效）
- ✅ 优化日志系统
- ✅ 完善错误处理

### v1.0-v1.6 (历史版本)
- 使用 FFmpegAndroid 库
- 存在兼容性问题
- 已废弃

## 开源协议

MIT License

## 贡献者

- @liliangxing

## 相关链接

- [GitHub 仓库](https://github.com/liliangxing/videoEditor)
- [Releases](https://github.com/liliangxing/videoEditor/releases)
- [Issues](https://github.com/liliangxing/videoEditor/issues)
