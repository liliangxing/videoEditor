# VideoToAudio - 视频转音频提取工具

基于 FFmpeg-Video-Editor-Android 的音频提取功能开发的 Android 应用，支持提取视频中的音频并保存为 M4A 或 MP3 格式。

## 下载

- [下载最新版本 (v2.1)](https://github.com/liliangxing/videoEditor/releases/tag/v2.1)
- 支持 Android 7.0 (API 24) 及以上版本

## 功能特点

- ✅ **两种格式**：支持 M4A (AAC) 和 MP3 格式
- ✅ **FFmpeg 处理**：使用 FFmpegAndroid 0.3.2 库
- ✅ **Android 14 支持**：完全适配 Android 14 (SDK 34)
- ✅ **权限管理**：支持 Android 11+ MANAGE_EXTERNAL_STORAGE
- ✅ **详细日志**：日志保存在 `/sdcard/douyinguanjia/Log/videoEdit.log`
- ✅ **简洁界面**：只有 3 个按钮，简单直观

## 使用方法

1. 点击 **📁 选择视频** 选择视频文件
   - 首次使用会请求存储权限
   - Android 11+ 需要授权"所有文件访问权限"
   
2. 点击 **🎵 提取音频 (M4A)** 或 **🎶 提取音频 (MP3)**
   - M4A：使用 AAC 编码，体积更小音质更好
   - MP3：通用格式，兼容性最好
   
3. 提取完成后会弹窗显示保存路径
   - 输出目录：`/sdcard/Music/`
   - 日志文件：`/sdcard/douyinguanjia/Log/videoEdit.log`

## 技术架构

### 核心技术栈

| 组件 | 技术 | 说明 |
|------|------|------|
| 开发语言 | Java | 100% Java 实现 |
| 构建工具 | Gradle 8.0 | Android Gradle Plugin 8.0.0 |
| 目标 SDK | Android 14 (SDK 34) | 兼容 Android 7.0+ |
| 音频处理 | FFmpegAndroid 0.3.2 | FFmpeg 二进制库 |
| Maven 仓库 | 阿里云镜像 | 加速国内下载 |

### FFmpeg 命令

**M4A 提取**：
```bash
-y -i input.mp4 -vn -acodec aac -ar 44100 -ac 2 -b:a 128k output.m4a
```

**MP3 提取**：
```bash
-y -i input.mp4 -vn -ar 44100 -ac 2 -b:a 192k -f mp3 output.mp3
```

### 处理流程

1. **视频选择** → `ContentResolver.openInputStream()` 读取 URI
2. **缓存复制** → 复制到 `/data/data/包名/cache/` 临时目录
3. **FFmpeg 处理** → 执行音频提取命令
4. **输出保存** → 保存到 `/sdcard/Music/audio_时间戳.m4a/mp3`
5. **清理缓存** → 删除临时视频文件

### 权限处理

| Android 版本 | 权限 | 处理方式 |
|------------|------|----------|
| Android 14+ (SDK 34) | `READ_MEDIA_VIDEO` | 运行时请求 |
| Android 13 (SDK 33) | `READ_MEDIA_VIDEO` | 运行时请求 |
| Android 11-12 (SDK 30-32) | `READ_EXTERNAL_STORAGE` | 运行时请求 |
| Android 11+ (SDK 30+) | `MANAGE_EXTERNAL_STORAGE` | 跳转设置页面授权 |

### 日志系统

日志文件：`/sdcard/douyinguanjia/Log/videoEdit.log`

记录内容包括：
- 应用启动信息
- 权限状态
- 视频选择和复制
- FFmpeg 命令执行
- 错误信息和堆栈

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
│   │   │   └── values/
│   │   │       ├── strings.xml            # 字符串资源
│   │   │       └── themes.xml             # 主题样式
│   │   └── AndroidManifest.xml            # 应用清单
│   └── build.gradle                        # 应用级构建配置
├── build.gradle                            # 项目级构建配置
├── settings.gradle                         # 项目设置
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

### 配置文件

**settings.gradle** - 使用阿里云 Maven 仓库：
```groovy
pluginManagement {
    repositories {
        google()
        maven { url 'https://maven.aliyun.com/repository/google' }
        maven { url 'https://maven.aliyun.com/repository/public' }
        gradlePluginPortal()
    }
}
```

**app/build.gradle** - FFmpeg 依赖：
```groovy
dependencies {
    implementation 'com.writingminds:FFmpegAndroid:0.3.2'
}
```

## 常见问题

**Q: 为什么点击选择视频没反应？**
A: 请检查是否授权了 MANAGE_EXTERNAL_STORAGE 权限：
   1. 打开手机设置
   2. 查找"视频转音频"应用
   3. 开启"所有文件访问权限"

**Q: 提取失败怎么办？**
A: 请查看日志文件 `/sdcard/douyinguanjia/Log/videoEdit.log`，常见原因：
   - 视频文件损坏
   - 视频格式不支持
   - 存储空间不足

**Q: M4A 和 MP3 有什么区别？**
A: 
   - **M4A (AAC)**: 体积小音质好，适合苹果设备和现代播放器
   - **MP3**: 兼容性最好，适合所有设备和播放器

**Q: 为什么使用阿里云仓库？**
A: 加速国内下载速度，避免默认 Google 仓库连接超时。

## 版本历史

### v2.1 (2026-06-05)
- ✅ 整合 FFmpeg-Video-Editor-Android 音频提取功能
- ✅ 支持 M4A 和 MP3 两种格式
- ✅ 使用 FFmpegAndroid 0.3.2 库
- ✅ 日志路径改为 /sdcard/douyinguanjia/Log/
- ✅ 使用阿里云 Maven 仓库
- ✅ 添加 MANAGE_EXTERNAL_STORAGE 权限支持

### v2.0 (已废弃)
- 使用 Android 原生 MediaExtractor/MediaMuxer
- 仅支持 M4A 格式
- 无 FFmpeg 依赖

### v1.x (历史版本)
- FFmpeg 兼容性测试版本
- 已废弃

## 开源协议

MIT License

## 相关链接

- 原项目：https://github.com/bhuvnesh123/FFmpeg-Video-Editor-Android
- GitHub 仓库：https://github.com/liliangxing/videoEditor
- Releases: https://github.com/liliangxing/videoEditor/releases
- Issues: https://github.com/liliangxing/videoEditor/issues
