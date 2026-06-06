package com.simple.video2audio.util;

import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.FFmpegKitConfig;

public class FFmpegUtil {

    public interface ExecuteCallback {
        void onSuccess(String output);
        void onFailure(String error);
    }

    public static void execute(String cmd, ExecuteCallback callback) {
        FFmpegSession session = FFmpegKit.executeAsync(cmd, completedSession -> {
            ReturnCode returnCode = completedSession.getReturnCode();
            String allLogs = completedSession.getAllLogsAsString();
            
            Log.d("FFmpeg", "ReturnCode: " + returnCode.getValue());
            Log.d("FFmpeg", "All logs:\n" + allLogs);

            if (ReturnCode.isSuccess(returnCode)) {
                if (callback != null) callback.onSuccess(allLogs);
            } else {
                if (allLogs == null || allLogs.isEmpty()) {
                    allLogs = "FFmpeg 退出码：" + returnCode.getValue() + "，无输出日志";
                }
                if (callback != null) callback.onFailure(allLogs);
            }
        });

        if (session == null) {
            Log.e("FFmpeg", "❌ FFmpegSession 创建失败");
            if (callback != null) callback.onFailure("FFmpegSession 创建失败");
            return;
        }

        Log.d("FFmpeg", "命令已提交，executionId: " + session.getSessionId());
    }
}
