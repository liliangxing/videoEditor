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
import java.io.IOException;
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

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "VideoToAudio";
    
    private ImageView pauseIcon;
    private SurfaceView surfaceView;
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
    private boolean ffmpegReady = false;
    private FFmpegSession ffmpegInitSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initLogFile();
        initViews();
        setupListeners();
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
                                videoDurationMs = duration;
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
                            });
                        }

                        @Override
                        public void onProgressUpdate(int currentPosition, int duration) {
                            runOnUiThread(() -> {
                                txtCurrentTime.setText(formatTime(currentPosition));
                                
                                if (currentPosition >= cutEndMs) {
                                    videoPlayerManager.seekTo(cutStartMs);
                                }
                            });
                        }

                        @Override
                        public void onCompletion() {
                            runOnUiThread(() -> {
                                hidePauseIcon();
                                Toast.makeText(MainActivity.this, "播放完成", Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "播放错误：" + error, Toast.LENGTH_LONG).show();
                                Log.e("VideoEditor", error);
                            });
                        }
                    });
                    writeLog("VideoPlayerManager 已初始化");
                    
                    if (currentVideoPath != null) {
                        videoPlayerManager.loadVideo(currentVideoPath);
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
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
                    new Thread(() -> {
                        try {
                            tempVideoFile = copyToCache(uri);
                            if (tempVideoFile != null && tempVideoFile.exists()) {
                                writeLog("✅ 缓存成功：" + tempVideoFile.length() + "B");
                                currentVideoPath = tempVideoFile.getAbsolutePath();
                                runOnUiThread(() -> {
                                    btnExtractAudio.setEnabled(true);
                                    btnExtractMP3.setEnabled(true);
                                    btnArchive.setEnabled(true);
                                    if (videoPlayerManager != null) {
                                        videoPlayerManager.loadVideo(currentVideoPath);
                                    }
                                });
                            } else {
                                runOnUiThread(() -> {
                                    showErrorDialog("错误", "无法缓存视频文件");
                                });
                            }
                        } catch (Exception e) {
                            writeLog("❌ 异常：" + e.getMessage());
                            runOnUiThread(() -> showErrorDialog("错误", e.getMessage()));
                        }
                    }).start();
                }
            });

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
        
        // 检查存储权限
        if (!checkStoragePermission()) {
            showErrorDialog("需要权限", "请授权所有文件访问权限后重试");
            return;
        }
        
        // 延迟初始化 FFmpeg，到用户点击提取按钮时再初始化
        if (!ffmpegReady) {
            writeLog("正在初始化 FFmpeg...");
            try {
                FFmpegSession initSession = FFmpegKit.executeAsync("-version", completedSession -> {
                    ReturnCode returnCode = completedSession.getReturnCode();
                    if (ReturnCode.isSuccess(returnCode)) {
                        ffmpegReady = true;
                        writeLog("✅ FFmpeg 初始化成功");
                    } else {
                        writeLog("❌ FFmpeg 初始化失败");
                    }
                });
            } catch (Exception e) {
                writeLog("❌ FFmpeg 初始化异常：" + e.getMessage());
                showErrorDialog("错误", "FFmpeg 初始化失败");
                return;
            }
            // 等待 FFmpeg 初始化完成
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}
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
            // Use built-in mp3 encoder (FFmpeg-Kit doesn't include libmp3lame)
            cmd = "-y -i " + quotePath(currentVideoPath) + " -vn -acodec mp3 -ar 44100 -ac 2 -ab 192k " + quotePath(outputPath);
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
        btnArchive.setEnabled(enabled);
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
        // 检查存储权限
        if (!checkStoragePermission()) {
            showErrorDialog("需要权限", "请授权所有文件访问权限后重试");
            return;
        }
        
        new Thread(() -> {
            File sourceDir = new File(Environment.getExternalStorageDirectory(), "Music/douyinguanjia");
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                runOnUiThread(() -> Toast.makeText(this, "源目录不存在", Toast.LENGTH_SHORT).show());
                return;
            }

            File[] videoFiles = sourceDir.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".mp4") && new File(dir, name).length() > 0);
            if (videoFiles == null || videoFiles.length == 0) {
                runOnUiThread(() -> Toast.makeText(this, "没有找到需要归档的 MP4 文件", Toast.LENGTH_SHORT).show());
                return;
            }

            writeLog("===开始归档视频===");
            writeLog("源目录: " + sourceDir.getAbsolutePath());
            writeLog("找到 " + videoFiles.length + " 个 MP4 文件");

            runOnUiThread(() -> {
                progressBar.setVisibility(ProgressBar.VISIBLE);
                enableButtons(false);
                Toast.makeText(this, "开始归档 " + videoFiles.length + " 个文件，请稍候...", Toast.LENGTH_SHORT).show();
            });

            int maxPerZip = 500;
            int currentZipIndex = 1;
            File zipFile = new File(sourceDir.getParentFile(), "douyinguanjia" + currentZipIndex + ".zip");
            List<File> filesToArchive = new ArrayList<>(Arrays.asList(videoFiles));

            try {
                while (!filesToArchive.isEmpty()) {
                    int existingCount = getZipFileCount(zipFile);
                    if (existingCount >= maxPerZip) {
                        currentZipIndex++;
                        zipFile = new File(sourceDir.getParentFile(), "douyinguanjia" + currentZipIndex + ".zip");
                        existingCount = getZipFileCount(zipFile);
                    }

                    int remainingSlots = maxPerZip - existingCount;
                    List<File> batch = filesToArchive.subList(0, Math.min(remainingSlots, filesToArchive.size()));

                    addFilesToZipWithMerge(zipFile, batch, sourceDir);
                    batch.clear();
                    
                    writeLog("已归档到: " + zipFile.getName() + ", 当前数量: " + getZipFileCount(zipFile));
                }

                for (File video : videoFiles) {
                    if (video.exists() && video.delete()) {
                        Log.d(TAG, "已删除: " + video.getName());
                    }
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    enableButtons(true);
                    Toast.makeText(this, "归档完成，原始 MP4 已删除", Toast.LENGTH_LONG).show();
                });

                writeLog("归档完成：" + videoFiles.length + " 个文件");
            } catch (Exception e) {
                Log.e(TAG, "归档失败", e);
                writeLog("归档失败: " + e.getMessage());
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    enableButtons(true);
                    Toast.makeText(this, "归档失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private int getZipFileCount(File zipFile) {
        if (!zipFile.exists()) return 0;
        try (ZipFile zf = new ZipFile(zipFile)) {
            return zf.size();
        } catch (IOException e) {
            return 0;
        }
    }
    
    private void addFilesToZipWithMerge(File zipFile, List<File> files, File sourceDir) throws IOException {
        File tempDir = new File(getCacheDir(), "zip_temp");
        deleteDir(tempDir);
        tempDir.mkdirs();

        if (zipFile.exists()) {
            try (ZipFile existingZip = new ZipFile(zipFile)) {
                Enumeration<? extends ZipEntry> entries = existingZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    File outFile = new File(tempDir, entry.getName());
                    if (entry.isDirectory()) {
                        outFile.mkdirs();
                    } else {
                        outFile.getParentFile().mkdirs();
                        try (InputStream is = existingZip.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(outFile)) {
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                }
            }
        }

        for (File file : files) {
            String desiredName = file.getName();
            File targetFile = new File(tempDir, desiredName);
            if (targetFile.exists()) {
                if (targetFile.length() == file.length()) {
                    Log.d(TAG, "跳过已存在且大小相同的文件: " + desiredName);
                    continue;
                }
                int counter = 1;
                String baseName = desiredName.substring(0, desiredName.lastIndexOf("."));
                String extension = desiredName.substring(desiredName.lastIndexOf("."));
                do {
                    String newName = baseName + "(" + counter + ")" + extension;
                    targetFile = new File(tempDir, newName);
                    counter++;
                } while (targetFile.exists() && counter <= 100);
            }
            try (FileInputStream fis = new FileInputStream(file);
                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                }
            }
        }

        File newZip = new File(zipFile.getAbsolutePath() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(newZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            addDirToZipStream(zos, tempDir, "");
        }

        if (zipFile.exists() && !zipFile.delete()) {
            throw new IOException("无法删除旧 ZIP 文件");
        }
        if (!newZip.renameTo(zipFile)) {
            throw new IOException("无法重命名临时 ZIP 文件");
        }

        deleteDir(tempDir);
    }
    
    private void addDirToZipStream(ZipOutputStream zos, File dir, String parentPath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                addDirToZipStream(zos, file, parentPath + file.getName() + "/");
            } else {
                String entryName = parentPath + file.getName();
                ZipEntry zipEntry = new ZipEntry(entryName);
                zos.putNextEntry(zipEntry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }
    
    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
        dir.delete();
    }
}
