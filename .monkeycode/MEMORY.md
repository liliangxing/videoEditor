# 用户指令记忆

本文件记录了用户的指令、偏好和教导，用于在未来的交互中提供参考。

## 格式

### 用户指令条目
用户指令条目应遵循以下格式：

[用户指令摘要]
- Date: [YYYY-MM-DD]
- Context: [提及的场景或时间]
- Instructions:
  - [用户教导或指示的内容，逐行描述]

## 去重策略
- 添加新条目前，检查是否存在相似或相同的指令
- 若发现重复，跳过新条目或与已有条目合并
- 合并时，更新上下文或日期信息

## 条目

### 读取项目上下文文档
- Date: 2026-06-13
- Context: 用户创建了 AI_CONTEXT.md 作为项目上下文文档
- Category: 工作流协作
- Instructions:
  - 每次开始任务时，先读取 /workspace/videoEditor/AI_CONTEXT.md 了解项目结构、技术栈、核心命令
  - 读取项目上下文文档后再开始代码分析或修改

### 发布流程规范
- Date: 2026-06-13
- Context: 用户要求先推送代码再发布 Release
- Category: 工作流协作
- Instructions:
  - 先提交代码并 push 到远程
  - 再编译 APK: ./gradlew assembleRelease
  - APK 命名: videoEditor-v{版本号}.apk
  - 最后创建 GitHub Release 并上传 APK

### 归档视频功能
- Date: 2026-06-13
- Context: 归档功能扫描 /sdcard/Music/douyinguanjia/ 目录
- Category: 环境配置
- Instructions:
  - 归档功能扫描 /sdcard/Music/douyinguanjia/ 下的非空 .mp4 文件
  - 每 500 个文件打包成一个 douyinguanjiaN.zip
  - 大小一致的重复文件跳过，大小不一致生成新名称
  - 压缩完成后删除源文件
  - 归档不需要先选择视频
