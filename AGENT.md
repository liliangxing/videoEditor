# AGENT.md - Project Rules for videoEditor

本文件定义了 Qoder CLI CN 在处理 videoEditor 项目时应遵循的规范和行为准则。

## 📋 任务开始前必读

### 读取项目上下文
- **每次开始任务前**，必须先读取 `AI_CONTEXT.md` 了解：
  - 项目结构和技术栈
  - 核心命令和构建流程
  - 当前开发状态和待办事项
- 只有在理解项目上下文后，才能开始代码分析或修改

## 🔨 发布流程规范

创建 Release 时必须遵循以下顺序：

1. **先提交代码并 push 到远程**
   ```bash
   git add -A
   git commit -m "描述性提交信息"
   git push origin main
   ```
   
   **⚠️ 网络问题备选方案**：如果 `git push` 因网络问题失败，使用 GitHub API 推送：
   ```bash
   # 获取文件 SHA
   SHA=$(gh api repos/liliangxing/videoEditor/contents/{文件路径} --jq '.sha')
   
   # 通过 API 推送
   gh api repos/liliangxing/videoEditor/contents/{文件路径} \
     --method PUT \
     -f message="提交信息" \
     -f content="$(base64 -w0 {文件路径})" \
     -f branch=main \
     -f sha="$SHA"
   ```

2. **编译 Release APK**
   ```bash
   ./gradlew assembleRelease
   ```

3. **APK 命名规范**
   - 格式: `videoEditor-v{版本号}.apk`
   - 示例: `videoEditor-v2.1.apk`

4. **创建 GitHub Release**
   - 使用 `gh release create` 命令
   - 上传编译好的 APK
   - 包含详细的版本说明

## 🎵 MP3 转换技术要点

### FFmpeg-Kit 版本选择
- **必须使用**: `ffmpeg-kit-audio:6.0-2`
- **禁止使用**: `ffmpeg-kit-min-gpl`（不包含 MP3 编码器）

### 原因说明
- `ffmpeg-kit-min-gpl` 是最小包，连内置 MP3 编码器都被裁掉
- `ffmpeg-kit-audio` 专为音频处理设计，包含 `libmp3lame` 编码器
- 桌面版 FFmpeg 和 FFmpeg-Kit 的可用编码器不同，需在目标环境测试

### MP3 转换命令
```java
// 正确写法（使用 libmp3lame）
cmd = "-y -i " + quotePath(currentVideoPath) + " -vn -codec:a libmp3lame -qscale:a 2 " + quotePath(outputPath);
```

## 📦 归档视频功能规范

### 扫描目录
- 路径: `/sdcard/Music/douyinguanjia/`
- 文件类型: 非空 `.mp4` 文件

### 打包规则
- 每 **500 个文件** 打包成一个 ZIP
- ZIP 命名: `douyinguanjiaN.zip`（N 从 1 开始递增）

### 去重策略
- **大小一致**的重复文件 → 跳过
- **大小不一致**的同名文件 → 生成新名称（如 `file(1).mp4`）

### 清理操作
- 压缩完成后**删除源文件**
- 归档功能**不需要先选择视频**，直接扫描目录

## 🚫 禁止行为

1. **不要破解或反编译他人软件**
   - 涉及法律风险和道德问题
   - 建议寻找开源替代品或自行开发

2. **不要泄露敏感信息**
   - GitHub Token、密码等凭证不得在对话中暴露
   - 发现泄露应立即撤销并提醒用户

3. **不要假设环境配置**
   - 所有路径、依赖、版本都应通过实际检查确认
   - 特别是 FFmpeg 编码器等平台相关特性

## ✅ 最佳实践

### 代码修改
- 优先编辑现有文件，避免创建新文件
- 保持代码简洁，避免过度抽象
- 只添加必要的注释，解释 WHY 而非 WHAT

### 错误处理
- 只在系统边界（用户输入、外部 API）进行验证
- 信任内部代码和框架保证
- 不为不可能发生的场景添加错误处理

### 测试验证
- UI 或前端变更必须在浏览器中测试
- 确保黄金路径和边缘情况都通过
- 监控其他功能是否有回归问题

##  记忆管理

重要决策和问题修复应记录到 `memory.md` 文件中，包括：
- 问题描述和根本原因
- 修复方案和代码对比
- 测试验证结果
- 关键教训和经验总结

---

**最后更新**: 2026-06-13  
**维护者**: liliangxing <253254457@qq.com>
