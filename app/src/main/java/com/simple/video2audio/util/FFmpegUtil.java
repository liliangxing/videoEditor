package com.simple.video2audio.util;

import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

public class FFmpegUtil {

    public interface ExecuteCallback {
        void onSuccess(String output);
        void onFailure(String error);
    }

    public static void execute(String cmd, ExecuteCallback callback) {
        FFmpegSession session = FFmpegKit.executeAsync(cmd, completedSession -> {
            ReturnCode returnCode = completedSession.getReturnCode();
            String allLogs = completedSession.getAllLogsAsString();

            Log.d("FFmpeg", "ReturnCode: " + returnCode);
            Log.d("FFmpeg", "AllLogs: " + allLogs);

            if (ReturnCode.isSuccess(returnCode)) {
                callback.onSuccess(allLogs);
            } else {
                if (allLogs == null || allLogs.isEmpty()) {
                    allLogs = "FFmpeg 退出码：" + returnCode.getValue() + "，无输出";
                }
                callback.onFailure(allLogs);
            }
        });
    }
}
