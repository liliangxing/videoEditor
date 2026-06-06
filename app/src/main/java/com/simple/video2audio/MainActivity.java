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
                if (isGranted) {
                    pickVideo();
                } else {
                    showErrorDialog("需要权限", "请授权存储权限以选择视频文件");
                }
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
                                writeLog("✅ 缓存成功:" + tempVideoFile.getAbsolutePath() + " (" + tempVideoFile.length() + "B)");
                                runOnUiThread(() -> txtStatus.setText("已缓存:" + (tempVideoFile.length() / 1024 / 1024) + "MB"));
                            } else {
                                runOnUiThread(() -> {
                                    txtStatus.setText("缓存失败");
                                    showErrorDialog("错误", "无法缓存视频文件");
                                });
                            }
                        } catch (Exception e) {
                            writeLog("❌ 复制异常:" + e.getMessage());
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
                .setMessage("Android 11+ 需要「所有文件访问权限」才能正常工作。\n\n请在设置页面打开「允许管理所有文件」开关。")
                .setPositiveButton("去设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("取消", (d, w) -> Toast.makeText(this, "未授权将无法选择视频文件", Toast.LENGTH_SHORT).show())
                .setCancelable(false)
                .show();
            writeLog("请求 MANAGE_EXTERNAL_STORAGE 权限");
        } catch (Exception e) {
            writeLog("请求权限失败：" + e.getMessage());
        }
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
            if (checkStoragePermission()) {
                pickVideo();
            }
        });
        btnExtractAudio.setOnClickListener(v -> extractAudioAAC());
    }

    private void pickVideo() {
        videoPickerLauncher.launch("video/*");
    }

    private File copyToCache(Uri uri) throws Exception {
        File cacheDir = getCacheDir();
        File f = File.createTempFile("video_", ".mp4", cacheDir);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(f)) {
            if (in == null) {
                f.delete();
                return null;
            }
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) != -1) {
                out.write(b, 0, n);
            }
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
            showErrorDialog("错误", "请先选视频");
            return;
        }

        writeLog("===开始提取 AAC===");
        writeLog("源文件:" + tempVideoFile.getAbsolutePath() + " (" + tempVideoFile.length() + "B)");

        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnExtractAudio.setEnabled(false);

        // 1. 确定输出路径 (应用私有目录)
        File outputDir = new File(getCacheDir(), "audio");
        if (!outputDir.exists()) outputDir.mkdirs();
        
        // 2. 生成 AAC 格式文件
        File outputFile = new File(outputDir, "audio_" + System.currentTimeMillis() + ".m4a");
        String outputPath = outputFile.getAbsolutePath();
        writeLog("输出路径:" + outputPath);

        // 3. FFmpeg 命令（简洁模式）
        String srcPath = tempVideoFile.getAbsolutePath();
        String cmd = "-y -i \"" + srcPath + "\" -vn -c:a aac -b:a 192k \"" + outputPath + "\"";
        writeLog("FFmpeg 命令：" + cmd);

        try {
            ffmpeg.execute(new String[]{"-y", "-i", srcPath, "-vn", "-c:a", "aac", "-b:a", "192k", outputPath}, new ExecuteBinaryResponseHandler() {
                @Override
                public void onSuccess(String s) {
                    writeLog("✓ AAC 转换成功，大小:" + outputFile.length() + "B");
                    
                    // 复制到公共目录
                    File publicFile = copyToPublicDir(outputFile, ".m4a");
                    if (publicFile != null) {
                        scanMediaFile(publicFile);
                        runOnUiThread(() -> showSuccessDialog(publicFile.getAbsolutePath()));
                    } else {
                        runOnUiThread(() -> showErrorDialog("失败", "无法保存到 Music 目录"));
                    }
                    
                    cleanup(outputFile);
                }

                @Override
                public void onFailure(String s) {
                    writeLog("✗ AAC 转换失败：" + s);
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudio.setEnabled(true);
                    if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
                    showErrorDialog("失败", s != null && !s.isEmpty() ? s : "FFmpeg 失败");
                }

                @Override
                public void onProgress(String s) {
                    txtStatus.setText("处理中...");
                }

                @Override
                public void onStart() {
                    writeLog("开始转换");
                    txtStatus.setText("提取中...");
                }

                @Override
                public void onFinish() {
                    writeLog("转换结束");
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
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            
            writeLog("✓ 已复制到公共目录：" + destFile.getAbsolutePath() + " (" + destFile.length() + "B)");
            return destFile;
        } catch (Exception e) {
            writeLog("✗ 复制失败：" + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void scanMediaFile(File file) {
        try {
            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scanIntent.setData(Uri.fromFile(file));
            sendBroadcast(scanIntent);
            writeLog("已刷新媒体库：" + file.getName());
        } catch (Exception e) {
            writeLog("刷新媒体库失败：" + e.getMessage());
        }
    }

    private void cleanup(File... files) {
        for (File f : files) {
            if (f != null && f.exists()) f.delete();
        }
        if (tempVideoFile != null && tempVideoFile.exists()) {
            tempVideoFile.delete();
        }
        progressBar.setVisibility(ProgressBar.GONE);
        btnExtractAudio.setEnabled(true);
    }

    private void showSuccessDialog(String filePath) {
        new AlertDialog.Builder(this)
            .setTitle("✓ 转换成功")
            .setMessage("文件已保存:\n" + filePath + "\n\n日志文件:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null)
            .setCancelable(false)
            .show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage((message != null && !message.isEmpty() ? message : "错误") + "\n\n日志文件:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null)
            .setCancelable(false)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
        writeLog("应用结束");
    }
}
