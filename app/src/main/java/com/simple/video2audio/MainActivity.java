package com.simple.video2audio;

import android.Manifest;
import android.content.Intent;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnSelectVideo;
    private Button btnExtractAudio;
    private Button btnTestLog;
    private ProgressBar progressBar;
    private TextView txtStatus;
    
    private Uri selectedVideoUri = null;
    private File tempVideoFile = null;
    private File logFile;
    
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) pickVideo();
                else showErrorDialog("需要权限", "请授权存储权限");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initLogFile();
        initViews();
        setupListeners();
    }
    
    private void initLogFile() {
        writeLog("=== 应用启动 ===");
        writeLog("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        writeLog("手机：" + Build.BRAND + " " + Build.MODEL);
        
        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                writeLog("请求 MANAGE_EXTERNAL_STORAGE 权限");
                requestManageStoragePermission();
            } else {
                writeLog("MANAGE_EXTERNAL_STORAGE 已授权");
            }
        }
        if (!musicDir.exists()) musicDir.mkdirs();
        logFile = new File(musicDir, "VideoToAudio.log");
        writeLog("日志：" + logFile.getAbsolutePath());
    }
    
    private void writeLog(String msg) {
        try {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            FileWriter w = new FileWriter(logFile, true);
            w.append("[").append(ts).append("] ").append(msg).append("\n");
            w.flush();
            w.close();
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    private void initViews() {
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnExtractAudio = findViewById(R.id.btnExtractAudio);
        btnTestLog = findViewById(R.id.btnTestLog);
        progressBar = findViewById(R.id.progressBar);
        txtStatus = findViewById(R.id.txtStatus);
        btnExtractAudio.setEnabled(false);
    }
    
    private void requestManageStoragePermission() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            writeLog("跳转设置失败：" + e.getMessage());
        }
    }
    
    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            writeLog("点击选择视频");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                requestManageStoragePermission();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO);
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        });
        
        btnExtractAudio.setOnClickListener(v -> {
            writeLog("点击提取音频");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                requestManageStoragePermission();
            } else if (selectedVideoUri != null) {
                extractAudio();
            }
        });
        
        btnTestLog.setOnClickListener(v -> {
            writeLog("点击测试文件");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                requestManageStoragePermission();
            } else {
                generateTestFile();
            }
        });
    }
    
    private void pickVideo() {
        videoPickerLauncher.launch("video/*");
    }
    
    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    writeLog("选择视频：" + uri);
                    selectedVideoUri = uri;
                    btnExtractAudio.setEnabled(true);
                    txtStatus.setText("视频已选");
                    try {
                        tempVideoFile = copyVideoToCache(uri);
                        writeLog(tempVideoFile != null ? 
                            "✓ 复制到缓存：" + tempVideoFile.length() + " 字节" : 
                            "✗ 复制失败");
                    } catch (Exception e) {
                        writeLog("✗ 复制异常：" + e.getMessage());
                    }
                }
            });
    
    private File copyVideoToCache(Uri uri) throws Exception {
        File f = new File(getCacheDir(), "temp_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(f)) {
            if (in == null) return null;
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
        return f.exists() && f.canRead() ? f : null;
    }
    
    private void extractAudio() {
        writeLog("=== 开始提取 ===");
        if (selectedVideoUri == null || tempVideoFile == null || !tempVideoFile.exists()) {
            writeLog("✗ 文件无效");
            showErrorDialog("错误", "请重新选择视频");
            return;
        }
        writeLog("源文件：" + tempVideoFile.getAbsolutePath() + " (" + tempVideoFile.length() + " 字节)");
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnExtractAudio.setEnabled(false);
        txtStatus.setText("正在提取...");
        
        new Thread(() -> {
            try {
                File outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
                if (!outDir.exists()) outDir.mkdirs();
                File outFile = new File(outDir, "audio_" + System.currentTimeMillis() + ".m4a");
                
                writeLog("使用 Android 原生 MediaExtractor 提取音频");
                writeLog("输出文件：" + outFile.getAbsolutePath());
                
                MediaExtractor extractor = new MediaExtractor();
                extractor.setDataSource(tempVideoFile.getAbsolutePath());
                
                int audioTrackIndex = -1;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    android.media.MediaFormat format = extractor.getTrackFormat(i);
                    String mime = format.getString(android.media.MediaFormat.KEY_MIME);
                    writeLog("轨道 " + i + ": " + mime);
                    if (mime != null && mime.startsWith("audio/")) {
                        audioTrackIndex = i;
                        writeLog("找到音频轨道：" + i);
                        break;
                    }
                }
                
                if (audioTrackIndex == -1) {
                    writeLog("✗ 未找到音频轨道");
                    runOnUiThread(() -> {
                        progressBar.setVisibility(ProgressBar.GONE);
                        btnExtractAudio.setEnabled(true);
                        showErrorDialog("提取失败", "视频中未找到音频轨道");
                    });
                    return;
                }
                
                extractor.selectTrack(audioTrackIndex);
                android.media.MediaFormat audioFormat = extractor.getTrackFormat(audioTrackIndex);
                
                MediaMuxer muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int muxerTrackIndex = muxer.addTrack(audioFormat);
                muxer.start();
                
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(4 * 1024 * 1024);
                android.media.MediaCodec.BufferInfo bufferInfo = new android.media.MediaCodec.BufferInfo();
                long totalBytes = 0;
                
                while (true) {
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        writeLog("提取完成");
                        break;
                    }
                    
                    bufferInfo.offset = 0;
                    bufferInfo.size = sampleSize;
                    bufferInfo.presentationTimeUs = extractor.getSampleTime();
                    bufferInfo.flags = extractor.getSampleFlags();
                    
                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo);
                    totalBytes += sampleSize;
                    
                    if (totalBytes % (1024 * 1024) < 10240) {
                        writeLog("已提取：" + (totalBytes / 1024 / 1024) + " MB");
                        final long progressMb = totalBytes / 1024 / 1024;
                        runOnUiThread(() -> txtStatus.setText("提取中... " + progressMb + "MB"));
                    }
                    
                    extractor.advance();
                }
                
                muxer.stop();
                muxer.release();
                extractor.release();
                
                writeLog("✓ 提取成功：" + outFile.getAbsolutePath() + " (" + outFile.length() + " 字节)");
                
                if (tempVideoFile != null && tempVideoFile.exists()) {
                    tempVideoFile.delete();
                    writeLog("已删除临时文件");
                }
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudio.setEnabled(true);
                    showSuccessDialog(outFile.getAbsolutePath());
                });
                
            } catch (Exception e) {
                writeLog("✗ 提取异常：" + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudio.setEnabled(true);
                    showErrorDialog("提取失败", e.getClass().getName() + ": " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void showSuccessDialog(String path) {
        new AlertDialog.Builder(this)
            .setTitle("✓ 成功")
            .setMessage("已保存:\n" + path + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null)
            .setCancelable(false)
            .show();
    }
    
    private void showErrorDialog(String title, String msg) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage((msg == null || msg.isEmpty() ? "未知错误" : msg) + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null)
            .setCancelable(false)
            .show();
    }
    
    private void generateTestFile() {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, "123.6");
        try {
            FileWriter w = new FileWriter(f);
            w.write("123");
            w.flush();
            w.close();
            writeLog("测试文件：" + (f.exists() ? "成功 " + f.length() + " 字节" : "失败"));
            new AlertDialog.Builder(this).setTitle(f.exists() ? "✓ 成功" : "✗ 失败")
                .setMessage(f.getAbsolutePath() + "\n" + (f.exists() ? f.length() : 0) + " 字节")
                .setPositiveButton("确定", null).show();
        } catch (Exception e) {
            writeLog("测试文件异常：" + e.getMessage());
            showErrorDialog("失败", e.getMessage());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
        writeLog("应用关闭");
    }
}
