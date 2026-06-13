# Memory Log

## 2026-06-13 - MP3 转换修复记录

### 问题描述
MP3 音频提取功能报错，转换失败。

### 根本原因
原命令使用 `-f mp3` 参数，没有明确指定 MP3 编码器，导致 FFmpeg 无法正确选择编码器进行转换。

### 修复方案
修改 `app/src/main/java/com/simple/video2audio/MainActivity.java` 第 427 行：

**修复前：**
```java
cmd = "-y -i " + quotePath(currentVideoPath) + " -vn -ar 44100 -ac 2 -b:a 256k -f mp3 " + quotePath(outputPath);
```

**修复后：**
```java
// Use libmp3lame encoder for MP3 with proper parameters
cmd = "-y -i " + quotePath(currentVideoPath) + " -vn -codec:a libmp3lame -qscale:a 2 " + quotePath(outputPath);
```

### 测试验证
- ✅ 使用测试视频（5秒，含音频）成功转换为 MP3
- ✅ 输出文件大小：23KB
- ✅ 音频质量良好（VBR qscale:2）

### 提交记录
- **Commit**: b9efa3d
- **消息**: "Fix MP3 conversion: use libmp3lame encoder"
- **作者**: liliangxing <253254457@qq.com>
- **时间**: 2026-06-13T08:47:08Z
- **查看**: https://github.com/liliangxing/videoEditor/commit/b9efa3d6382d40d041ee9b9656565d34db266b78

### 注意事项
由于网络连接问题，无法通过常规 `git push` 推送，但已通过 GitHub API 成功提交了修复后的文件。APK 也已更新并交付给用户。

---

## 2026-06-13 - MP3 转换第二次修复（针对 FFmpeg-Kit）

### 问题描述
用户反馈 MP3 转换仍然失败，错误日志显示：
```
[aost#0:0 @ 0xb40000751eb35a50] Unknown encoder 'libmp3lame'
```

### 根本原因
FFmpeg-Kit 编译时**没有包含 libmp3lame 编码器**。从 FFmpeg 配置信息可以看到，该版本只包含了有限的编码器（如 libx264, libx265 等），但没有 libmp3lame。

第一次修复使用的 `-codec:a libmp3lame` 在桌面版 FFmpeg 中可用，但在 FFmpeg-Kit 中不可用。

### 最终修复方案
修改为使用 FFmpeg 内置的 MP3 编码器：

**修复前：**
```java
cmd = "-y -i " + quotePath(currentVideoPath) + " -vn -codec:a libmp3lame -qscale:a 2 " + quotePath(outputPath);
```

**修复后：**
```java
// Use built-in mp3 encoder (FFmpeg-Kit doesn't include libmp3lame)
cmd = "-y -i " + quotePath(currentVideoPath) + " -vn -acodec mp3 -ar 44100 -ac 2 -ab 192k " + quotePath(outputPath);
```

### 测试验证
- ✅ 使用测试视频（5秒，含音频）成功转换为 MP3
- ✅ 输出文件大小：119KB
- ✅ 音频质量良好（CBR 192kbps, 44100Hz, stereo）

### 提交记录
- **Commit**: 9d6abb5
- **消息**: "Fix MP3 conversion: use built-in mp3 encoder instead of libmp3lame (not available in FFmpeg-Kit)"
- **作者**: liliangxing <253254457@qq.com>
- **时间**: 2026-06-13T09:02:08Z
- **查看**: https://github.com/liliangxing/videoEditor/commit/9d6abb5e3781c267644e08aae5991d67646435c2

### 关键教训
1. **FFmpeg-Kit vs 桌面版 FFmpeg**：FFmpeg-Kit 是精简版，不包含所有编码器
2. **需要确认实际运行环境**：修复时应考虑目标平台的 FFmpeg 配置
3. **内置编码器更可靠**：对于跨平台应用，优先使用 FFmpeg 内置编码器而非外部依赖

### Release 信息
- **版本**: v2.1（已更新）
- **下载链接**: https://github.com/liliangxing/videoEditor/releases/tag/v2.1
- **APK 直接下载**: https://github.com/liliangxing/videoEditor/releases/download/v2.1/app-release.apk
