package com.simple.video2audio;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import android.view.ViewGroup;
import android.content.SharedPreferences;
import android.provider.DocumentsContract;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "VideoToAudio";
    
    private ImageView pauseIcon;
    private SurfaceView surfaceView;
    private SeekBar seekBar;
    private SeekBar seekBarStart, seekBarEnd;
    private TextView txtStartTime, txtCurrentTime, txtEndTime;
    private ProgressBar progressBar;
    private Button btnSelectVideo, btnExtractAudio, btnExtractMP3, btnArchive;
    
    private VideoPlayerManager videoPlayerManager;
    private String currentVideoPath = null;
    private File tempVideoFile = null;
    private File logFile;
    
    private int cutStartMs = 0;
    private int cutEndMs = 0;
    private int videoDurationMs = 0;
    private boolean isUserDragging = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initLogFile();
        initViews();
        setupListeners();
        
        new Handler().postDelayed(() -> {
            try {
                FFmpegSession session = FFmpegKit.executeAsync("-version", completedSession -> {
                    ReturnCode returnCode = completedSession.getReturnCode();
                    if (ReturnCode.isSuccess(returnCode)) {
                        writeLog("✅ FFmpegKit 测试成功");
                    } else {
                        writeLog("❌ FFmpegKit 测试失败");
                    }
                });
            } catch (Exception e) {
                writeLog("❌ FFmpegKit 测试异常：" + e.getMessage());
            }
        }, 500);
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
        pauseIcon = findViewById(R.id.pauseIcon);
        surfaceView = findViewById(R.id.surfaceView);
        seekBar = findViewById(R.id.seekBar);
        seekBarStart = findViewById(R.id.seekBarStart);
        seekBarEnd = findViewById(R.id.seekBarEnd);
        txtStartTime = findViewById(R.id.txtStartTime);
        txtCurrentTime = findViewById(R.id.txtCurrentTime);
        txtEndTime = findViewById(R.id.txtEndTime);
        progressBar = findViewById(R.id.progressBar);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnExtractAudio = findViewById(R.id.btnExtractAudio);
        btnExtractMP3 = findViewById(R.id.btnExtractMP3);
        btnArchive = findViewById(R.id.btnArchive);
        
        btnExtractAudio.setEnabled(false);
        btnExtractMP3.setEnabled(false);
        btnArchive.setEnabled(false);
        
        setupSeekBarListeners();
        
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (videoPlayerManager == null) {
                    videoPlayerManager = new VideoPlayerManager(MainActivity.this, surfaceView, pauseIcon, new VideoPlayerManager.OnPlaybackListener() {
                        @Override
                        public void onPrepared(int duration) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) {
                                    writeLog("⚠️ onPrepared: Activity已销毁");
                                    return;
                                }
                                try {
                                    videoDurationMs = duration;
                                    seekBar.setMax(duration);
                                    seekBarStart.setMax(duration);
                                    seekBarEnd.setMax(duration);
                                    seekBarStart.setProgress(0);
                                    seekBarEnd.setProgress(duration);
                                    cutStartMs = 0;
                                    cutEndMs = duration;
                                    updateTimeText(0, duration);
                                    hidePauseIcon();
                                    
                                    int videoWidth = videoPlayerManager.getVideoWidth();
                                    int videoHeight = videoPlayerManager.getVideoHeight();
                                    if (videoWidth > 0 && videoHeight > 0) {
                                        float aspectRatio = (float) videoWidth / videoHeight;
                                        int surfaceWidth = surfaceView.getWidth();
                                        int newHeight = (int) (surfaceWidth / aspectRatio);
                                        ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
                                        params.height = newHeight;
                                        surfaceView.setLayoutParams(params);
                                    }
                                    writeLog("✅ 视频已准备好，时长: " + duration + "ms");
                                } catch (Exception e) {
                                    writeLog("❌ onPrepared异常: " + e.getMessage());
                                    e.printStackTrace();
                                }
                            });
                        }

                        @Override
                        public void onProgressUpdate(int currentPosition, int duration) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                try {
                                    if (!isUserDragging) {
                                        seekBar.setProgress(currentPosition);
                                    }
                                    
                                    txtCurrentTime.setText(formatTime(currentPosition));
                                    
                                    if (currentPosition >= cutEndMs && videoPlayerManager != null) {
                                        videoPlayerManager.seekTo(cutStartMs);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "onProgressUpdate异常: " + e.getMessage());
                                }
                            });
                        }

                        @Override
                        public void onCompletion() {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                try {
                                    seekBar.setProgress(0);
                                    hidePauseIcon();
                                    Toast.makeText(MainActivity.this, "播放完成", Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    Log.e(TAG, "onCompletion异常: " + e.getMessage());
                                }
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                try {
                                    Toast.makeText(MainActivity.this, "播放错误：" + error, Toast.LENGTH_LONG).show();
                                    Log.e("VideoEditor", error);
                                } catch (Exception e) {
                                    Log.e(TAG, "onError显示异常: " + e.getMessage());
                                }
                            });
                        }
                    });
                    writeLog("VideoPlayerManager 已初始化");
                    
                    if (currentVideoPath != null && !isFinishing() && !isDestroyed()) {
                        videoPlayerManager.loadVideo(currentVideoPath);
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (videoPlayerManager != null) {
                    videoPlayerManager.release();
                }
            }
        });
        
        surfaceView.setOnClickListener(v -> {
            if (videoPlayerManager != null && videoPlayerManager.isPrepared()) {
                if (videoPlayerManager.isPlaying()) {
                    videoPlayerManager.pause();
                    showPauseIcon();
                } else {
                    videoPlayerManager.start();
                    hidePauseIcon();
                }
            }
        });
    }

    private void setupSeekBarListeners() {
        seekBarStart.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (progress >= cutEndMs) {
                        progress = cutEndMs - 1000;
                        seekBarStart.setProgress(progress);
                    }
                    cutStartMs = progress;
                    txtStartTime.setText(formatTime(cutStartMs));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserDragging = true;
                if (videoPlayerManager != null && videoPlayerManager.isPlaying()) {
                    videoPlayerManager.pause();
                    hidePauseIcon();
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserDragging = false;
                playCurrentSegment();
            }
        });
        
        seekBarEnd.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (progress <= cutStartMs) {
                        progress = cutStartMs + 1000;
                        seekBarEnd.setProgress(progress);
                    }
                    cutEndMs = progress;
                    txtEndTime.setText(formatTime(cutEndMs));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserDragging = true;
                if (videoPlayerManager != null && videoPlayerManager.isPlaying()) {
                    videoPlayerManager.pause();
                    hidePauseIcon();
                }
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserDragging = false;
                playCurrentSegment();
            }
        });
    }

    private void updateTimeText(int currentPosition, int duration) {
        txtStartTime.setText(formatTime(currentPosition));
        txtCurrentTime.setText(formatTime(currentPosition));
        txtEndTime.setText(formatTime(duration));
    }

    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void playCurrentSegment() {
        if (videoPlayerManager != null && videoPlayerManager.isPrepared()) {
            videoPlayerManager.seekTo(cutStartMs);
            videoPlayerManager.start();
            hidePauseIcon();
        }
    }

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) pickVideo();
        });
        
        btnExtractAudio.setOnClickListener(v -> extractAudio("m4a", "audio/mp4", "AAC"));
        btnExtractMP3.setOnClickListener(v -> extractAudio("mp3", "audio/mpeg", "MP3"));
        btnArchive.setOnClickListener(v -> archiveVideos());
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) pickVideo();
                else showErrorDialog("需要权限", "请授权存储权限");
            });
    
    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    writeLog("视频已选择：" + uri);
                    // 尝试获取持久化URI权限
                    try {
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        getContentResolver().takePersistableUriPermission(uri, takeFlags);
                        writeLog("✅ 持久化权限获取成功");
                    } catch (SecurityException e) {
                        writeLog("⚠️ 持久化权限获取失败: " + e.getMessage() + "，使用临时权限");
                    }
                    new Thread(() -> {
                        try {
                            tempVideoFile = copyToCache(uri);
                            if (tempVideoFile != null && tempVideoFile.exists()) {
                                String cachedPath = tempVideoFile.getAbsolutePath().trim();
                                writeLog("✅ 缓存成功：" + tempVideoFile.length() + "B, 路径: " + cachedPath);
                                currentVideoPath = cachedPath;
                                runOnUiThread(() -> {
                                    // 检查Activity生命周期状态
                                    if (isFinishing() || isDestroyed()) {
                                        writeLog("⚠️ Activity已销毁，跳过后续操作");
                                        return;
                                    }
                                    writeLog("=== 缓存回调开始 === isFinishing=" + isFinishing() + ", isDestroyed=" + isDestroyed());
                                    try {
                                        btnExtractAudio.setEnabled(true);
                                        btnExtractMP3.setEnabled(true);
                                        btnArchive.setEnabled(true);
                                        if (videoPlayerManager != null) {
                                            writeLog("调用loadVideo，路径: [" + cachedPath + "]");
                                            videoPlayerManager.loadVideo(cachedPath);
                                        } else {
                                            writeLog("⚠️ videoPlayerManager为null");
                                        }
                                    } catch (Exception e) {
                                        writeLog("❌ UI操作失败: " + e.getMessage());
                                        e.printStackTrace();
                                        showErrorDialog("错误", "加载视频失败: " + e.getMessage());
                                    }
                                });
                            } else {
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    showErrorDialog("错误", "无法缓存视频文件");
                                });
                            }
                        } catch (Exception e) {
                            writeLog("❌ 异常：" + e.getMessage());
                            e.printStackTrace();
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                showErrorDialog("错误", e.getMessage());
                            });
                        }
                    }).start();
                }
            });

    private File copyToCache(Uri uri) throws Exception {
        File cacheDir = getCacheDir();
        if (cacheDir == null) {
            throw new Exception("无法访问缓存目录");
        }
        writeLog("缓存目录: " + cacheDir.getAbsolutePath());
        
        File f = File.createTempFile("video_", ".mp4", cacheDir);
        writeLog("临时文件: " + f.getAbsolutePath());
        
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) { 
                f.delete(); 
                throw new Exception("无法打开输入流"); 
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                byte[] b = new byte[8192];
                int n;
                long total = 0;
                while ((n = in.read(b)) != -1) {
                    out.write(b, 0, n);
                    total += n;
                    if (total % (10 * 1024 * 1024) == 0) {
                        writeLog("已复制: " + (total / 1024 / 1024) + "MB");
                    }
                }
                out.flush();
            }
        }
        
        boolean readable = f.canRead();
        long size = f.length();
        writeLog("文件大小: " + size + "B, 可读: " + readable);
        
        if (!readable || size <= 0) {
            f.delete();
            throw new Exception("缓存文件无效");
        }
        
        return f;
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
        new AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage("Android 11+ 需要打开「所有文件访问权限」\n\n请在设置页面打开开关。")
            .setPositiveButton("去设置", (d, w) -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            })
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show();
    }

    private void extractAudio(String format, String mimeType, String formatName) {
        if (currentVideoPath == null) {
            showErrorDialog("错误", "请先选择视频");
            return;
        }
        
        writeLog("===开始提取 " + formatName + "==");
        writeLog("源文件：" + currentVideoPath);
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        enableButtons(false);
        
        File audioDir = new File(getCacheDir(), "audio");
        if (!audioDir.exists()) audioDir.mkdirs();
        
        long timestamp = System.currentTimeMillis();
        String outputPath = audioDir.getAbsolutePath() + "/audio_" + timestamp + "." + format;
        
        String cmd;
        if ("mp3".equals(format)) {
            cmd = "-y -i " + quotePath(currentVideoPath) + " -vn -ar 44100 -ac 2 -b:a 256k -f mp3 " + quotePath(outputPath);
        } else {
            cmd = "-y -i " + quotePath(currentVideoPath) + " -vn -c:a aac -b:a 192k -ar 44100 -ac 2 " + quotePath(outputPath);
        }
        
        writeLog("FFmpeg 命令：" + cmd);
        
        final long startTime = System.currentTimeMillis();
        FFmpegSession session = FFmpegKit.executeAsync(cmd, completedSession -> {
            ReturnCode returnCode = completedSession.getReturnCode();
            String output = completedSession.getOutput();
            long duration = System.currentTimeMillis() - startTime;
            
            if (ReturnCode.isSuccess(returnCode)) {
                File audioFile = new File(outputPath);
                writeLog("✅ 转换成功，耗时：" + duration + "ms, 文件大小：" + audioFile.length() + "B");
                
                saveToPublicMusic(audioFile, "audio_" + timestamp + "." + format, mimeType);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    enableButtons(true);
                    Toast.makeText(this, formatName + "提取成功！", Toast.LENGTH_SHORT).show();
                    showSuccessDialog("已保存到 Music 目录");
                });
            } else {
                writeLog("❌ 转换失败，耗时：" + duration + "ms");
                writeLog("错误详情：" + output);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    enableButtons(true);
                    Toast.makeText(this, "失败：" + output, Toast.LENGTH_LONG).show();
                    showErrorDialog("提取失败", output);
                });
            }
        });
    }

    private void pickVideo() {
        videoPickerLauncher.launch("video/*");
    }

    private void enableButtons(boolean enabled) {
        btnExtractAudio.setEnabled(enabled && currentVideoPath != null);
        btnExtractMP3.setEnabled(enabled && currentVideoPath != null);
        btnArchive.setEnabled(enabled && currentVideoPath != null);
    }

    private String quotePath(String path) {
        if (path != null && path.contains(" ")) {
            return "\"" + path + "\"";
        }
        return path;
    }

    private void saveToPublicMusic(File sourceFile, String fileName, String mimeType) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Audio.Media.MIME_TYPE, mimeType);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
            }
            
            Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri itemUri = getContentResolver().insert(collection, values);
            
            if (itemUri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(itemUri);
                     FileInputStream in = new FileInputStream(sourceFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                writeLog("✓ 已保存到 Music: " + fileName);
            }
        } catch (Exception e) {
            writeLog("✗ 保存失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showSuccessDialog(String message) {
        new AlertDialog.Builder(this)
            .setTitle("✓ 成功")
            .setMessage(message + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null)
            .setCancelable(false)
            .show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage((message != null && !message.isEmpty() ? message : "错误") + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null)
            .setCancelable(false)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoPlayerManager != null && videoPlayerManager.isPrepared() && !videoPlayerManager.isPlaying()) {
            videoPlayerManager.start();
            hidePauseIcon();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoPlayerManager != null && videoPlayerManager.isPlaying()) {
            videoPlayerManager.pause();
            showPauseIcon();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoPlayerManager != null) {
            videoPlayerManager.release();
            videoPlayerManager = null;
        }
        if (tempVideoFile != null && tempVideoFile.exists()) {
            tempVideoFile.delete();
        }
        writeLog("应用结束");
    }
    
    private void showPauseIcon() {
        if (pauseIcon != null) {
            pauseIcon.setVisibility(View.VISIBLE);
        }
    }
    
    private void hidePauseIcon() {
        if (pauseIcon != null) {
            pauseIcon.setVisibility(View.GONE);
        }
    }
    
    private void archiveVideos() {
        if (currentVideoPath == null) {
            showErrorDialog("错误", "请先选择视频");
            return;
        }
        
        writeLog("===开始归档视频===");
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        enableButtons(false);
        
        new Thread(() -> {
            try {
                File archiveDir = new File(getExternalFilesDir(null), "archives");
                if (!archiveDir.exists()) {
                    archiveDir.mkdirs();
                }
                
                String zipFileName = "archive_" + System.currentTimeMillis() + ".zip";
                File zipFile = new File(archiveDir, zipFileName);
                
                List<File> filesToArchive = new ArrayList<>();
                filesToArchive.add(new File(currentVideoPath));
                
                File cutVideoDir = new File(getCacheDir(), "cut_video");
                if (cutVideoDir.exists()) {
                    addDirToZip(cutVideoDir, filesToArchive);
                }
                
                File audioDir = new File(getCacheDir(), "audio");
                if (audioDir.exists()) {
                    addDirToZip(audioDir, filesToArchive);
                }
                
                addFilesToZip(filesToArchive, zipFile);
                
                saveToPublicDocuments(zipFile, zipFileName, "application/zip");
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    enableButtons(true);
                    Toast.makeText(this, "归档成功！共打包 " + getZipFileCount(zipFile) + " 个文件", Toast.LENGTH_SHORT).show();
                    showSuccessDialog("已保存到 Documents 目录：" + zipFileName);
                });
                
                writeLog("✅ 归档成功：" + zipFile.length() + "B, 文件数：" + getZipFileCount(zipFile));
            } catch (Exception e) {
                writeLog("❌ 归档失败：" + e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    enableButtons(true);
                    Toast.makeText(this, "归档失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    showErrorDialog("归档失败", e.getMessage());
                });
            }
        }).start();
    }
    
    private int getZipFileCount(File file) {
        int count = 0;
        try (ZipFile zip = new ZipFile(file)) {
            count = zip.size();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }
    
    private void addFilesToZip(List<File> files, File zipFile) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File file : files) {
                if (file.exists()) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    zos.putNextEntry(entry);
                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
    }
    
    private void addDirToZip(File dir, List<File> fileList) {
        File[] files = dir.listFiles();
        if (files != null) {
            fileList.addAll(Arrays.asList(files));
        }
    }
    
    private void saveToPublicDocuments(File sourceFile, String fileName, String mimeType) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS);
            }
            
            Uri collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri itemUri = getContentResolver().insert(collection, values);
            
            if (itemUri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(itemUri);
                     FileInputStream in = new FileInputStream(sourceFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
                writeLog("✓ 已保存到 Documents: " + fileName);
            }
        } catch (Exception e) {
            writeLog("✗ 保存失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
