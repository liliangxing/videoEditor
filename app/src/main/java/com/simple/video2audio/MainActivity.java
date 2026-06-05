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
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnSelectVideo;
    private Button btnExtractAudioM4A;
    private Button btnExtractAudioMP3;
    private ProgressBar progressBar;
    private TextView txtStatus;
    
    private FFmpeg ffmpeg;
    private Uri selectedVideoUri = null;
    private File tempVideoFile = null;
    private File logFile;
    private String outputFormat = "m4a"; // 默认 M4A
    
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    pickVideo();
                } else {
                    writeLog("权限被拒绝：READ_MEDIA_VIDEO");
                    showErrorDialog("需要权限", "请授权存储权限以选择视频文件。");
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
    
    private void initLogFile() {
        writeLog("=== 应用启动 ===");
        writeLog("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        writeLog("手机：" + Build.BRAND + " " + Build.MODEL);
        
        // 日志路径改为 /sdcard/douyinguanjia/Log/
        File logDir = new File(Environment.getExternalStorageDirectory(), "douyinguanjia/Log");
        if (!logDir.exists()) {
            boolean created = logDir.mkdirs();
            writeLog("创建日志目录：" + logDir.getAbsolutePath() + " = " + created);
        }
        logFile = new File(logDir, "videoEdit.log");
        writeLog("日志文件：" + logFile.getAbsolutePath());
    }
    
    private void writeLog(String msg) {
        try {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            FileWriter w = new FileWriter(logFile, true);
            w.append("[").append(ts).append("] ").append(msg).append("\n");
            w.flush();
            w.close();
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
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
    
    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            writeLog("跳转到设置页面请求 MANAGE_EXTERNAL_STORAGE 权限");
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                writeLog("启动设置页面失败：" + e.getMessage());
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                } catch (Exception ex) {
                    showErrorDialog("权限错误", "无法打开设置页面：" + ex.getMessage());
                }
            }
        }
    }
    
    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            writeLog("点击选择视频");
            checkPermissionAndPickVideo();
        });
        
        btnExtractAudioM4A.setOnClickListener(v -> {
            writeLog("点击提取 M4A 音频");
            outputFormat = "m4a";
            checkPermissionAndExtract();
        });
        
        btnExtractAudioMP3.setOnClickListener(v -> {
            writeLog("点击提取 MP3 音频");
            outputFormat = "mp3";
            checkPermissionAndExtract();
        });
    }
    
    private void checkPermissionAndPickVideo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                writeLog("无 MANAGE_EXTERNAL_STORAGE 权限，跳转设置");
                requestManageStoragePermission();
                return;
            }
        }
        
        // 检查媒体权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO);
                return;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return;
            }
        }
        
        pickVideo();
    }
    
    private void checkPermissionAndExtract() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                writeLog("无 MANAGE_EXTERNAL_STORAGE 权限，跳转设置");
                requestManageStoragePermission();
                return;
            }
        }
        
        if (selectedVideoUri != null && tempVideoFile != null && tempVideoFile.exists()) {
            extractAudio();
        } else {
            showErrorDialog("错误", "请先选择视频文件");
        }
    }
    
    private void pickVideo() {
        writeLog("启动视频选择器");
        videoPickerLauncher.launch("video/*");
    }
    
    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    writeLog("选择视频：" + uri);
                    selectedVideoUri = uri;
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    txtStatus.setText("视频已选择");
                    
                    try {
                        tempVideoFile = copyVideoToCache(uri);
                        if (tempVideoFile == null) {
                            writeLog("复制视频失败：inputStream 为 null");
                            showErrorDialog("读取失败", "无法读取视频文件");
                        } else {
                            writeLog("✓ 复制到缓存：" + tempVideoFile.getAbsolutePath());
                            writeLog("文件大小：" + tempVideoFile.length() + " 字节");
                            writeLog("文件可读：" + tempVideoFile.canRead());
                        }
                    } catch (Exception e) {
                        writeLog("✗ 复制异常：" + e.getMessage());
                        showErrorDialog("错误", e.getMessage());
                    }
                } else {
                    writeLog("用户取消选择");
                }
            });
    
    private File copyVideoToCache(Uri uri) throws Exception {
        File cacheDir = getCacheDir();
        writeLog("缓存目录：" + cacheDir.getAbsolutePath());
        
        String fileName = "temp_video_" + System.currentTimeMillis() + ".mp4";
        File cacheFile = new File(cacheDir, fileName);
        writeLog("目标文件：" + cacheFile.getAbsolutePath());
        
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        
        try {
            inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                writeLog("ERROR: 无法打开 input stream");
                return null;
            }
            writeLog("✓ input stream 打开成功");
            
            outputStream = new FileOutputStream(cacheFile);
            writeLog("✓ output stream 打开成功");
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            outputStream.flush();
            writeLog("✓ 复制完成：" + totalBytes + " 字节");
            
        } finally {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
        }
        
        // 校验文件
        if (!cacheFile.exists()) {
            writeLog("ERROR: 缓存文件不存在");
            return null;
        }
        if (!cacheFile.canRead()) {
            writeLog("ERROR: 缓存文件不可读");
            return null;
        }
        
        writeLog("✓ 文件校验成功：" + cacheFile.length() + " 字节");
        return cacheFile;
    }
    
    private void loadFFmpeg() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
        writeLog("加载 FFmpeg...");
        try {
            ffmpeg = FFmpeg.getInstance(this);
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onSuccess() {
                    progressBar.setVisibility(ProgressBar.GONE);
                    txtStatus.setText("FFmpeg 已加载，请选择视频");
                    writeLog("✓ FFmpeg 加载成功");
                }
                @Override
                public void onFailure() {
                    progressBar.setVisibility(ProgressBar.GONE);
                    writeLog("✗ FFmpeg 加载失败：设备不支持");
                    showErrorDialog("设备不支持", "您的设备不支持 FFmpeg");
                }
            });
        } catch (FFmpegNotSupportedException e) {
            progressBar.setVisibility(ProgressBar.GONE);
            writeLog("✗ FFmpeg 不支持：" + e.getMessage());
            showErrorDialog("设备不支持", e.getMessage());
        } catch (Exception e) {
            progressBar.setVisibility(ProgressBar.GONE);
            writeLog("✗ FFmpeg 异常：" + e.getMessage());
            showErrorDialog("错误", e.getMessage());
        }
    }
    
    private void extractAudio() {
        writeLog("=== 开始提取音频 ===");
        writeLog("格式：" + outputFormat);
        
        if (selectedVideoUri == null || tempVideoFile == null || !tempVideoFile.exists()) {
            writeLog("✗ 文件无效");
            showErrorDialog("错误", "请重新选择视频");
            return;
        }
        
        writeLog("源文件：" + tempVideoFile.getAbsolutePath());
        writeLog("源大小：" + tempVideoFile.length() + " 字节");
        writeLog("源可读：" + tempVideoFile.canRead());
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnExtractAudioM4A.setEnabled(false);
        btnExtractAudioMP3.setEnabled(false);
        txtStatus.setText("正在提取...");
        
        File outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (!outDir.exists()) outDir.mkdirs();
        
        String ext = outputFormat.equals("mp3") ? ".mp3" : ".m4a";
        File outFile = new File(outDir, "audio_" + System.currentTimeMillis() + ext);
        writeLog("输出文件：" + outFile.getAbsolutePath());
        
        String[] cmd;
        if (outputFormat.equals("mp3")) {
            cmd = new String[]{
                "-y", "-i", tempVideoFile.getAbsolutePath(),
                "-vn", "-ar", "44100", "-ac", "2", "-b:a", "192k", "-f", "mp3",
                outFile.getAbsolutePath()
            };
        } else {
            cmd = new String[]{
                "-y", "-i", tempVideoFile.getAbsolutePath(),
                "-vn", "-acodec", "aac", "-ar", "44100", "-ac", "2", "-b:a", "128k",
                outFile.getAbsolutePath()
            };
        }
        
        writeLog("FFmpeg 命令：" + String.join(" ", cmd));
        
        try {
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override
                public void onSuccess(String s) {
                    writeLog("✓ onSuccess: " + s);
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    
                    if (tempVideoFile != null && tempVideoFile.exists()) {
                        tempVideoFile.delete();
                        writeLog("已删除临时文件");
                    }
                    
                    writeLog("输出文件存在：" + outFile.exists());
                    if (outFile.exists()) {
                        writeLog("输出大小：" + outFile.length() + " 字节");
                    }
                    
                    showSuccessDialog(outFile.getAbsolutePath());
                }
                
                @Override
                public void onFailure(String s) {
                    writeLog("✗ onFailure: " + (s == null ? "null" : s));
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(false);
                    btnExtractAudioMP3.setEnabled(false);
                    
                    if (tempVideoFile != null && tempVideoFile.exists()) {
                        tempVideoFile.delete();
                    }
                    
                    showErrorDialog("提取失败", s == null ? "未知错误" : s);
                }
                
                @Override
                public void onProgress(String s) {
                    writeLog("progress: " + s);
                    txtStatus.setText("处理中...");
                }
                
                @Override
                public void onStart() {
                    writeLog("onStart");
                    txtStatus.setText("正在提取...");
                }
                
                @Override
                public void onFinish() {
                    writeLog("onFinish");
                    txtStatus.setText("完成");
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            writeLog("✗ 命令已在运行：" + e.getMessage());
            progressBar.setVisibility(ProgressBar.GONE);
            btnExtractAudioM4A.setEnabled(true);
            btnExtractAudioMP3.setEnabled(true);
            showErrorDialog("错误", "命令已在执行");
        }
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
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) {
            tempVideoFile.delete();
            writeLog("清理临时文件");
        }
        writeLog("应用关闭");
    }
}
