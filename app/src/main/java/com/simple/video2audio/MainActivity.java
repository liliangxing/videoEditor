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
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("请求权限失败：" + e.getMessage());
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

        File outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (!outDir.exists()) outDir.mkdirs();
        File out = new File(outDir, "audio_" + System.currentTimeMillis() + ".m4a");
        String srcPath = tempVideoFile.getAbsolutePath().replace(" ", "\\ ");
        String dstPath = out.getAbsolutePath().replace(" ", "\\ ");
        writeLog("dst:" + dstPath);

        String[] cmd = {"-y", "-i", srcPath, "-vn", "-c:a", "aac", "-b:a", "192k", dstPath};
        writeLog("cmd:" + String.join(" ", cmd));

        try {
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override public void onSuccess(String s) {
                    writeLog("✓M4A OK:" + out + " " + out.length() + "B");
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    if (tempVideoFile.exists()) tempVideoFile.delete();
                    scanMediaFile(out);
                    showSuccessDialog(out.getAbsolutePath());
                }
                @Override public void onFailure(String s) {
                    writeLog("✗M4A fail:" + s);
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

        File outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (!outDir.exists()) outDir.mkdirs();
        File out = new File(outDir, "audio_" + System.currentTimeMillis() + ".mp3");
        String srcPath = tempVideoFile.getAbsolutePath().replace(" ", "\\ ");
        String dstPath = out.getAbsolutePath().replace(" ", "\\ ");
        writeLog("dst:" + dstPath);

        String[] cmd = {"-y", "-i", srcPath, "-vn", "-ar", "44100", "-ac", "2", "-acodec", "libmp3lame", "-b:a", "192k", dstPath};
        writeLog("cmd:" + String.join(" ", cmd));

        try {
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override public void onSuccess(String s) {
                    writeLog("✓MP3 OK:" + out + " " + out.length() + "B");
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    if (tempVideoFile.exists()) tempVideoFile.delete();
                    scanMediaFile(out);
                    showSuccessDialog(out.getAbsolutePath());
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
