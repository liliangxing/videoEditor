package com.simple.video2audio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnSelectVideo, btnExtractAudioM4A, btnExtractAudioMP3;
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
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    txtStatus.setText("视频已选");
                    new Thread(() -> {
                        try {
                            tempVideoFile = copyToCache(uri);
                            if (tempVideoFile != null && tempVideoFile.exists()) {
                                writeLog("缓存:" + tempVideoFile.length() + "B");
                                runOnUiThread(() -> txtStatus.setText("已缓存:" + (tempVideoFile.length() / 1024 / 1024) + "MB"));
                            } else {
                                runOnUiThread(() -> {
                                    txtStatus.setText("缓存失败");
                                    showErrorDialog("错误", "无法缓存视频文件");
                                });
                            }
                        } catch (Exception e) {
                            writeLog("复制异常:" + e.getMessage());
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
            // Android 11+ 使用 MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                requestManageStoragePermission();
                return false;
            }
            return true;
        } else {
            // Android 10 及以下使用 WRITE_EXTERNAL_STORAGE
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
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("Android 11+ 需要「所有文件访问权限」才能正常工作。\n\n请在设置页面打开「允许管理所有文件」开关。")
                .setPositiveButton("去设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("取消", (d, w) -> {
                    Toast.makeText(MainActivity.this, "未授权将无法选择视频文件", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
            writeLog("请求 MANAGE_EXTERNAL_STORAGE 权限");
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("请求权限失败：" + e.getMessage());
            showErrorDialog("错误", "无法打开权限设置：" + e.getMessage());
        }
    }

    private void initLogFile() {
        File logDir = new File(Environment.getExternalStorageDirectory(), "douyinguanjia/Log");
        if (!logDir.exists()) logDir.mkdirs();
        logFile = new File(logDir, "videoEdit.log");
        writeLog("===启动===");
        writeLog("Android " + Build.VERSION.RELEASE + " SDK" + Build.VERSION.SDK_INT);
        writeLog("CPU " + Build.CPU_ABI);
    }

    private void writeLog(String msg) {
        try {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            FileWriter w = new FileWriter(logFile, true);
            w.append("[").append(ts).append("] ").append(msg).append("\n");
            w.flush(); w.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void initViews() {
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnExtractAudioM4A = findViewById(R.id.btnExtractAudioM4A);
        btnExtractAudioMP3 = findViewById(R.id.btnExtractAudioMP3);
        progressBar = findViewById(R.id.progressBar);
        txtStatus = findViewById(R.id.txtStatus);
        btnExtractAudioM4A.setEnabled(false);
        btnExtractAudioMP3.setEnabled(false);
    }

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) {
                pickVideo();
            }
        });
        btnExtractAudioM4A.setOnClickListener(v -> extractAudioM4A());
        btnExtractAudioMP3.setOnClickListener(v -> extractAudioMP3());
    }

    private void pickVideo() {
        videoPickerLauncher.launch("video/*");
    }

    private File copyToCache(Uri uri) throws Exception {
        File f = new File(getCacheDir(), "t_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(f)) {
            if (in == null) return null;
            byte[] b = new byte[4096]; int n; long t = 0;
            while ((n = in.read(b)) != -1) { out.write(b, 0, n); t += n; }
            out.flush();
            return f.exists() && f.canRead() ? f : null;
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
                    writeLog("✓FFmpeg OK");
                }
                @Override public void onFailure() {
                    progressBar.setVisibility(ProgressBar.GONE);
                    writeLog("✗FFmpeg fail");
                    showErrorDialog("不支持", "设备不支持 FFmpeg");
                }
            });
        } catch (Exception e) {
            progressBar.setVisibility(ProgressBar.GONE);
            writeLog("✗FFmpeg err:" + e.getMessage());
            showErrorDialog("错误", e.getMessage());
        }
    }

    private void extractAudioM4A() {
        if (!checkStoragePermission()) return;
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选视频"); return;
        }

        writeLog("===M4A===");
        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnExtractAudioM4A.setEnabled(false);
        btnExtractAudioMP3.setEnabled(false);

        // 1. 确定输出路径 (应用私有目录)
        File outputDir = new File(getCacheDir(), "Music");
        if (!outputDir.exists()) outputDir.mkdirs();
        
        // 2. 生成 AAC 格式文件
        String outputPath = new File(outputDir, "audio_" + System.currentTimeMillis() + ".m4a").getAbsolutePath();
        writeLog("output:" + outputPath);
        
        // 3. 简洁有效的 FFmpeg 命令
        String srcPath = tempVideoFile.getAbsolutePath();
        String cmd = "-y -i \"" + srcPath + "\" -vn -c:a aac -b:a 192k \"" + outputPath + "\"";
        writeLog("cmd:" + cmd);

        try {
            ffmpeg.execute(new String[]{"-y", "-i", srcPath, "-vn", "-c:a", "aac", "-b:a", "192k", outputPath}, new ExecuteBinaryResponseHandler() {
                @Override
                public void onSuccess(String s) {
                    writeLog("✓M4A OK, size:" + new File(outputPath).length() + "B");
                    if (copyToPublicDir(new File(outputPath), ".m4a")) {
                        String publicPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/audio_" + System.currentTimeMillis() + ".m4a";
                        progressBar.setVisibility(ProgressBar.GONE);
                        btnExtractAudioM4A.setEnabled(true);
                        btnExtractAudioMP3.setEnabled(true);
                        showSuccessDialog(publicPath);
                    } else {
                        progressBar.setVisibility(ProgressBar.GONE);
                        btnExtractAudioM4A.setEnabled(true);
                        btnExtractAudioMP3.setEnabled(true);
                        showErrorDialog("失败", "无法保存到 Music 目录");
                    }
                    if (tempVideoFile.exists()) tempVideoFile.delete();
                }
                @Override
                public void onFailure(String s) {
                    writeLog("✗M4A fail:" + s);
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    if (tempVideoFile.exists()) tempVideoFile.delete();
                    showErrorDialog("失败", s == null || s.isEmpty() ? "FFmpeg 失败" : s);
                }
                @Override
                public void onProgress(String s) { txtStatus.setText("..."); }
                @Override
                public void onStart() { writeLog("start"); txtStatus.setText("提取中..."); }
                @Override
                public void onFinish() { writeLog("finish"); }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            writeLog("✗running");
            progressBar.setVisibility(ProgressBar.GONE);
            btnExtractAudioM4A.setEnabled(true);
            btnExtractAudioMP3.setEnabled(true);
            showErrorDialog("错误", "执行中");
        }
    }

    private void extractAudioMP3() {
        if (!checkStoragePermission()) return;
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选视频"); return;
        }

        writeLog("===MP3===");
        writeLog("src:" + tempVideoFile.getAbsolutePath() + " " + tempVideoFile.length() + "B");

        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnExtractAudioM4A.setEnabled(false);
        btnExtractAudioMP3.setEnabled(false);

        // 先输出到应用私有目录（FFmpeg 有访问权限）
        File tempOutDir = new File(getCacheDir(), "audio_temp");
        if (!tempOutDir.exists()) tempOutDir.mkdirs();
        File tempOut = new File(tempOutDir, "audio_" + System.currentTimeMillis() + ".mp3");
        String srcPath = tempVideoFile.getAbsolutePath();
        String tempDstPath = tempOut.getAbsolutePath();
        writeLog("temp_dst:" + tempDstPath);

        // 使用 libmp3lame 编码器并指定 mp3 格式
        String[] cmd = {"-y", "-i", srcPath, "-vn", "-ar", "44100", "-ac", "2", "-c:a", "libmp3lame", "-b:a", "192k", "-f", "mp3", tempDstPath};
        writeLog("cmd:" + String.join(" ", cmd));

        try {
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override public void onSuccess(String s) {
                    writeLog("✓MP3 encoding OK, size:" + tempOut.length() + "B");
                    // 转换成功后复制到公共目录
                    if (copyToPublicDir(tempOut, ".mp3")) {
                        runOnUiThread(() -> showSuccess("MP3 提取成功", tempOut));
                    } else {
                        runOnUiThread(() -> showErrorDialog("失败", "无法保存到 Music 目录"));
                    }
                }
                @Override public void onFailure(String s) {
                    writeLog("✗MP3 fail:" + s);
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    if (tempVideoFile.exists()) tempVideoFile.delete();
                    showErrorDialog("失败", s == null || s.isEmpty() ? "FFmpeg 失败" : s);
                }
                @Override public void onProgress(String s) { txtStatus.setText("..."); }
                @Override public void onStart() { writeLog("start"); txtStatus.setText("提取中..."); }
                @Override public void onFinish() { writeLog("finish"); }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            writeLog("✗running");
            progressBar.setVisibility(ProgressBar.GONE);
            btnExtractAudioM4A.setEnabled(true);
            btnExtractAudioMP3.setEnabled(true);
            showErrorDialog("错误", "执行中");
        }
    }

    private boolean copyToPublicDir(File srcFile, String extension) {
        try {
            // 使用 MediaStore 写入公共目录（兼容 Android 10+）
            File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (!publicDir.exists()) publicDir.mkdirs();
            
            String fileName = "audio_" + System.currentTimeMillis() + extension;
            File destFile = new File(publicDir, fileName);
            
            // 直接复制文件（MANAGE_EXTERNAL_STORAGE 权限下可用）
            try (java.io.FileInputStream in = new java.io.FileInputStream(srcFile);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            
            writeLog("✓Copied to public dir: " + destFile.getAbsolutePath() + " (" + destFile.length() + "B)");
            
            // 刷新媒体库
            scanMediaFile(destFile);
            
            // 删除临时文件
            if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            if (srcFile.exists()) srcFile.delete();
            
            progressBar.setVisibility(ProgressBar.GONE);
            btnExtractAudioM4A.setEnabled(true);
            btnExtractAudioMP3.setEnabled(true);
            
            return true;
        } catch (Exception e) {
            writeLog("✗Copy to public dir failed: " + e.getMessage());
            e.printStackTrace();
            progressBar.setVisibility(ProgressBar.GONE);
            btnExtractAudioM4A.setEnabled(true);
            btnExtractAudioMP3.setEnabled(true);
            return false;
        }
    }

    private void showSuccess(String title, File tempFile) {
        progressBar.setVisibility(ProgressBar.GONE);
        btnExtractAudioM4A.setEnabled(true);
        btnExtractAudioMP3.setEnabled(true);
        
        File publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        String[] files = publicDir.list((d, name) -> name.endsWith(".mp3") || name.endsWith(".m4a"));
        String latestFile = "";
        if (files != null && files.length > 0) {
            java.util.Arrays.sort(files);
            latestFile = new File(publicDir, files[files.length - 1]).getAbsolutePath();
        }
        
        showSuccessDialog(latestFile.isEmpty() ? tempFile.getAbsolutePath() : latestFile);
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

    private void showSuccessDialog(String p) {
        new AlertDialog.Builder(this).setTitle("✓")
            .setMessage("已保存:\n" + p + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null).setCancelable(false).show();
    }

    private void showErrorDialog(String t, String m) {
        new AlertDialog.Builder(this).setTitle(t)
            .setMessage((m == null || m.isEmpty() ? "错误" : m) + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null).setCancelable(false).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
        writeLog("end");
    }
}
