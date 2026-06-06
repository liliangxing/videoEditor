package com.simple.video2audio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VideoToAudio";

    private Button btnSelectVideo, btnExtractAudio;
    private ProgressBar progressBar;
    private TextView txtStatus;
    private FFmpeg ffmpeg;
    private Uri selectedVideoUri = null;
    private File tempVideoFile = null;
    private File logFile;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) pickVideo();
                else showErrorDialog("需要权限", "请授权存储权限");
            });

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedVideoUri = uri;
                    btnExtractAudio.setEnabled(true);
                    txtStatus.setText("视频已选");
                    new Thread(() -> {
                        try {
                            tempVideoFile = copyToCache(uri);
                            if (tempVideoFile != null && tempVideoFile.exists()) {
                                writeLog("✅ 缓存成功：" + tempVideoFile.length() + "B");
                                runOnUiThread(() -> txtStatus.setText("已缓存:" + (tempVideoFile.length()/1024/1024) + "MB"));
                            } else {
                                runOnUiThread(() -> {
                                    txtStatus.setText("缓存失败");
                                    showErrorDialog("错误", "无法缓存视频文件");
                                });
                            }
                        } catch (Exception e) {
                            writeLog("❌ 异常:" + e.getMessage());
                            runOnUiThread(() -> showErrorDialog("错误", e.getMessage()));
                        }
                    }).start();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initLogFile();
        initViews();
        setupListeners();
        loadFFmpeg();
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageStoragePermission();
                return false;
            }
            return true;
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return false;
            }
            return true;
        }
    }

    private void requestManageStoragePermission() {
        try {
            new AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("Android 11+ 需要打开「所有文件访问权限」\n\n请在设置页面打开开关。")
                .setPositiveButton("去设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("取消", (d, w) -> Toast.makeText(this, "未授权将无法使用", Toast.LENGTH_SHORT).show())
                .setCancelable(false)
                .show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void initLogFile() {
        File logDir = new File(Environment.getExternalStorageDirectory(), "douyinguanjia/Log");
        if (!logDir.exists()) logDir.mkdirs();
        logFile = new File(logDir, "videoEdit.log");
        writeLog("===启动===");
        writeLog("Android " + Build.VERSION.RELEASE + " SDK" + Build.VERSION.SDK_INT);
    }

    private void writeLog(String msg) {
        try {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            FileWriter w = new FileWriter(logFile, true);
            w.append("[").append(ts).append("] ").append(msg).append("\n");
            w.flush(); w.close();
            Log.d(TAG, msg);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void initViews() {
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnExtractAudio = findViewById(R.id.btnExtractAudio);
        progressBar = findViewById(R.id.progressBar);
        txtStatus = findViewById(R.id.txtStatus);
        btnExtractAudio.setEnabled(false);
    }

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) pickVideo();
        });
        btnExtractAudio.setOnClickListener(v -> extractAudioAAC());
    }

    private void pickVideo() {
        videoPickerLauncher.launch("video/*");
    }

    private File copyToCache(Uri uri) throws Exception {
        File f = File.createTempFile("video_", ".mp4", getCacheDir());
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(f)) {
            if (in == null) { f.delete(); return null; }
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            out.flush();
            return f.exists() && f.canRead() && f.length() > 0 ? f : null;
        }
    }

    private void loadFFmpeg() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
        try {
            ffmpeg = FFmpeg.getInstance(this);
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override public void onSuccess() {
                    progressBar.setVisibility(ProgressBar.GONE);
                    txtStatus.setText("就绪");
                    writeLog("✓ FFmpeg 加载成功");
                }
                @Override public void onFailure() {
                    progressBar.setVisibility(ProgressBar.GONE);
                    writeLog("✗ FFmpeg 加载失败");
                    showErrorDialog("不支持", "设备不支持 FFmpeg");
                }
            });
        } catch (Exception e) {
            progressBar.setVisibility(ProgressBar.GONE);
            writeLog("✗ FFmpeg 错误:" + e.getMessage());
        }
    }

    private void extractAudioAAC() {
        if (!checkStoragePermission()) return;
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选视频"); return;
        }

        writeLog("===开始提取 AAC===");
        writeLog("源文件:" + tempVideoFile.getAbsolutePath() + " (" + tempVideoFile.length() + "B)");

        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnExtractAudio.setEnabled(false);

        File outputDir = new File(getCacheDir(), "audio");
        if (!outputDir.exists()) outputDir.mkdirs();
        
        File outputFile = new File(outputDir, "audio_" + System.currentTimeMillis() + ".m4a");
        
        String srcPath = tempVideoFile.getAbsolutePath();
        String[] cmd = {
            "-y",
            "-i", srcPath,
            "-vn",
            "-c:a", "aac",
            "-b:a", "192k",
            "-ar", "44100",
            "-ac", "2",
            outputFile.getAbsolutePath()
        };
        
        String cmdStr = String.join(" ", cmd);
        writeLog("FFmpeg 命令：" + cmdStr);

        try {
            final long startTime = System.currentTimeMillis();
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override
                public void onSuccess(String message) {
                    long duration = System.currentTimeMillis() - startTime;
                    writeLog("✅ 转换成功，耗时:" + duration + "ms, 大小:" + outputFile.length() + "B");
                    Log.d("FFMPEG_RAW", "完整日志:\n" + message);
                    
                    File publicFile = copyToPublicDir(outputFile, ".m4a");
                    if (publicFile != null) {
                        scanMediaFile(publicFile);
                        writeLog("✅ 已保存:" + publicFile.getAbsolutePath());
                        runOnUiThread(() -> {
                            progressBar.setVisibility(ProgressBar.GONE);
                            btnExtractAudio.setEnabled(true);
                            showSuccessDialog(publicFile.getAbsolutePath());
                        });
                    } else {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(ProgressBar.GONE);
                            btnExtractAudio.setEnabled(true);
                            showErrorDialog("失败", "无法保存");
                        });
                    }
                    cleanup();
                }

                @Override
                public void onFailure(String message) {
                    writeLog("❌ 转换失败:" + message);
                    Log.d("FFMPEG_RAW", "完整原始日志:\n" + message);
                    Log.e("FFMPEG_ERROR", "转换失败\n" + message);
                    
                    runOnUiThread(() -> {
                        progressBar.setVisibility(ProgressBar.GONE);
                        btnExtractAudio.setEnabled(true);
                        showErrorDialog("FFmpeg 失败", message);
                    });
                    
                    if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
                }

                @Override public void onProgress(String message) {
                    writeLog("📝 输出:" + message);
                    txtStatus.setText("处理中...");
                }

                @Override public void onStart() {
                    writeLog("▶️ 开始执行");
                    txtStatus.setText("提取中...");
                }

                @Override public void onFinish() {
                    writeLog("⏹️ 执行结束");
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            writeLog("✗ FFmpeg 正在运行");
            progressBar.setVisibility(ProgressBar.GONE);
            btnExtractAudio.setEnabled(true);
            showErrorDialog("错误", "上一个任务未完成");
        }
    }

    private File copyToPublicDir(File srcFile, String extension) {
        try {
            File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (!publicDir.exists()) publicDir.mkdirs();
            String fileName = "audio_" + System.currentTimeMillis() + extension;
            File destFile = new File(publicDir, fileName);
            
            try (FileInputStream in = new FileInputStream(srcFile);
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
                out.flush();
            }
            writeLog("✓ 已复制:" + destFile.length() + "B");
            return destFile;
        } catch (Exception e) {
            writeLog("✗ 复制失败:" + e.getMessage());
            return null;
        }
    }

    private void scanMediaFile(File file) {
        try {
            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scanIntent.setData(Uri.fromFile(file));
            sendBroadcast(scanIntent);
        } catch (Exception ignore) {}
    }

    private void cleanup() {
        progressBar.setVisibility(ProgressBar.GONE);
        btnExtractAudio.setEnabled(true);
        if (tempVideoFile != null && tempVideoFile.exists()) {
            tempVideoFile.delete();
            tempVideoFile = null;
        }
    }

    private void showSuccessDialog(String filePath) {
        new AlertDialog.Builder(this)
            .setTitle("✓ 转换成功")
            .setMessage("已保存:\n" + filePath + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null).setCancelable(false).show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage((message != null && !message.isEmpty() ? message : "错误") + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null).setCancelable(false).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
        writeLog("应用结束");
    }
}
