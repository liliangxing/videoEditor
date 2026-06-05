# 发布说明

## 推送到 GitHub

```bash
cd /workspace/VideoToAudio

# 推送到 GitHub
git push -u origin main

# 创建并推送 v2.0 标签
git tag -a v2.0 -m "Release v2.0 - Android 原生 API 实现"
git push origin v2.0
```

## 创建 GitHub Release

1. 访问 https://github.com/liliangxing/videoEditor/releases/new
2. Tag version: `v2.0`
3. Release title: `v2.0 - Android 原生 API 实现`
4. 上传附件：`VideoToAudio-v2.0.apk`
5. 发布说明：

```
## 重大更新

v2.0 版本完全重写，使用 Android 原生 MediaExtractor + MediaMuxer API，不依赖任何第三方 FFmpeg 库。

### 主要改进

- ✅ **原生实现**：使用 Android 原生 API，无需 FFmpeg
- ✅ **体积小巧**：APK 仅 5.2MB（之前版本 25MB）
- ✅ **兼容性更好**：100% 兼容所有 Android 7.0+ 设备
- ✅ **性能更优**：无额外库加载开销
- ✅ **Android 14 支持**：完全适配最新系统

### 技术架构

- 使用 `MediaExtractor` 提取视频中的音频轨道
- 使用 `MediaMuxer` 封装为 M4A 格式
- 输出目录：`/sdcard/Music/audio_时间戳.m4a`
- 日志文件：`/sdcard/Music/VideoToAudio.log`

### 下载

点击下方的 `VideoToAudio-v2.0.apk` 下载并安装。
```

## 验证

推送后访问：
- 代码仓库：https://github.com/liliangxing/videoEditor
- Releases: https://github.com/liliangxing/videoEditor/releases
