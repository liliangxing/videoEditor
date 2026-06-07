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

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "VideoToAudio";
    
    private ImageView videoPreviewImg, pauseIcon;
    private SurfaceView surfaceView;
    private SeekBar seekBar;
    private TextView txtStartTime, txtEndTime;
    private ProgressBar progressBar;
    private Button btnPlay, btnSetStart, btnSetEnd, btnSelectVideo, btnCutVideo, btnExtractAudio, btnExtractMP3, btnArchive;
    
    private VideoPlayerManager videoPlayerManager;
    private String currentVideoPath = null;
    private File tempVideoFile = null;
    private File logFile;
    
    private int cutStartMs = 0;
    private int cutEndMs = 0;
    private int videoDurationMs = 0;

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
        videoPreviewImg = findViewById(R.id.videoPreviewImg);
        pauseIcon = findViewById(R.id.pauseIcon);
        surfaceView = findViewById(R.id.surfaceView);
        seekBar = findViewById(R.id.seekBar);
        txtStartTime = findViewById(R.id.txtStartTime);
        txtEndTime = findViewById(R.id.txtEndTime);
        progressBar = findViewById(R.id.progressBar);
        btnPlay = findViewById(R.id.btnPlay);
        btnSetStart = findViewById(R.id.btnSetStart);
        btnSetEnd = findViewById(R.id.btnSetEnd);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnCutVideo = findViewById(R.id.btnCutVideo);
        btnExtractAudio = findViewById(R.id.btnExtractAudio);
        btnExtractMP3 = findViewById(R.id.btnExtractMP3);
        btnArchive = findViewById(R.id.btnArchive);
        
        btnPlay.setEnabled(false);
        btnCutVideo.setEnabled(false);
        btnExtractAudio.setEnabled(false);
        btnExtractMP3.setEnabled(false);
        btnArchive.setEnabled(false);
        
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (videoPlayerManager == null) {
                    videoPlayerManager = new VideoPlayerManager(MainActivity.this, surfaceView, videoPreviewImg, new VideoPlayerManager.OnPlaybackListener() {
                        @Override
                        public void onPrepared(int duration) {
                            runOnUiThread(() -> {
                                videoDurationMs = duration;
                                cutEndMs = duration;
                                seekBar.setMax(duration);
                                updateTimeText(0, duration);
                                btnPlay.setText("⏸ 暂停");
                                hidePauseIcon();
                                videoPreviewImg.setVisibility(View.GONE);
                                
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
                                seekBar.setProgress(currentPosition);
                                updateTimeText(currentPosition, duration);
                            });
                        }

                        @Override
                        public void onCompletion() {
                            runOnUiThread(() -> {
                                seekBar.setProgress(0);
                                btnPlay.setText("▶ 播放");
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
                    btnPlay.setText("▶ 播放");
                    showPauseIcon();
                } else {
                    videoPlayerManager.start();
                    btnPlay.setText("⏸ 暂停");
                    hidePauseIcon();
                }
            }
        });
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && videoPlayerManager != null && videoPlayerManager.isPrepared()) {
                    videoPlayerManager.seekTo(progress);
                    updateTimeText(progress, videoDurationMs);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void updateTimeText(int currentPosition, int duration) {
        txtStartTime.setText(formatTime(currentPosition));
        txtEndTime.setText(formatTime(duration));
    }

    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) pickVideo();
        });
        
        btnPlay.setOnClickListener(v -> {
            if (videoPlayerManager.isPlaying()) {
                videoPlayerManager.pause();
                btnPlay.setText("▶ 播放");
            } else {
                videoPlayerManager.start();
                btnPlay.setText("⏸ 暂停");
            }
        });
        
        btnSetStart.setOnClickListener(v -> {
            if (videoPlayerManager.isPrepared()) {
                cutStartMs = videoPlayerManager.getCurrentPosition();
                if (cutStartMs >= cutEndMs) {
                    Toast.makeText(this, "起点不能大于终点", Toast.LENGTH_SHORT).show();
                } else {
                    txtStartTime.setText(formatTime(cutStartMs));
                    Toast.makeText(this, "起点已设：" + formatTime(cutStartMs), Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        btnSetEnd.setOnClickListener(v -> {
            if (videoPlayerManager.isPrepared()) {
                cutEndMs = videoPlayerManager.getCurrentPosition();
                if (cutEndMs <= cutStartMs) {
                    Toast.makeText(this, "终点不能小于起点", Toast.LENGTH_SHORT).show();
                } else {
                    txtEndTime.setText(formatTime(cutEndMs));
                    Toast.makeText(this, "终点已设：" + formatTime(cutEndMs), Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        btnCutVideo.setOnClickListener(v -> executeCutVideo());
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
                                    videoPreviewImg.setVisibility(View.VISIBLE);
                                    btnCutVideo.setEnabled(true);
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
                            writeLog("❌ 异常:" + e.getMessage());
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
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            })
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show();
    }

    private void executeCutVideo() {
        if (currentVideoPath == null || cutStartMs >= cutEndMs) {
            showErrorDialog("错误", "请先设置有效的切割范围");
            return;
        }
        
        writeLog("===开始切割视频===");
        writeLog("源文件：" + currentVideoPath);
        writeLog("切割范围：" + formatTime(cutStartMs) + " - " + formatTime(cutEndMs));
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        enableButtons(false);
        
        File outputDir = new File(getCacheDir(), "cut_video");
        if (!outputDir.exists()) outputDir.mkdirs();
        
        long timestamp = System.currentTimeMillis();
        String outputPath = outputDir.getAbsolutePath() + "/cut_" + timestamp + ".mp4";
        
        double startSec = cutStartMs / 1000.0;
        double endSec = cutEndMs / 1000.0;
        
        String cmd = "-y -i " + quotePath(currentVideoPath) + 
                     " -ss " + startSec + 
                     " -to " + endSec + 
                     " -c copy " + 
                     quotePath(outputPath);
        
        writeLog("FFmpeg 命令：" + cmd);
        
        final long startTime = System.currentTimeMillis();
        FFmpegSession session = FFmpegKit.executeAsync(cmd, completedSession -> {
            ReturnCode returnCode = completedSession.getReturnCode();
            String output = completedSession.getOutput();
            long duration = System.currentTimeMillis() - startTime;
            
            if (ReturnCode.isSuccess(returnCode)) {
                File outputFile = new File(outputPath);
                writeLog("✅ 切割成功，耗时：" + duration + "ms, 文件大小：" + outputFile.length() + "B");
                
                saveToPublicVideo(outputFile, "cut_" + timestamp + ".mp4", "video/mp4");
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    enableButtons(true);
                    Toast.makeText(this, "视频切割成功！", Toast.LENGTH_SHORT).show();
                    showSuccessDialog("已保存：cut_" + timestamp + ".mp4");
                });
            } else {
                writeLog("❌ 切割失败，耗时：" + duration + "ms");
                writeLog("错误详情：" + output);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    enableButtons(true);
                    Toast.makeText(this, "失败：" + output, Toast.LENGTH_LONG).show();
                    showErrorDialog("切割失败", output);
                });
            }
        });
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
        btnPlay.setEnabled(enabled && videoPlayerManager != null);
        btnSetStart.setEnabled(enabled && videoPlayerManager != null);
        btnSetEnd.setEnabled(enabled && videoPlayerManager != null);
        btnCutVideo.setEnabled(enabled && currentVideoPath != null);
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

    private void saveToPublicVideo(File sourceFile, String fileName, String mimeType) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);
            }
            
            Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
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
                writeLog("✓ 已保存到 Movies: " + fileName);
            }
        } catch (Exception e) {
            writeLog("✗ 保存失败：" + e.getMessage());
            e.printStackTrace();
        }
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
            btnPlay.setText("⏸ 暂停");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoPlayerManager != null && videoPlayerManager.isPlaying()) {
            videoPlayerManager.pause();
            btnPlay.setText("▶ 播放");
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
                
                File finalZip = saveToPublicDocuments(zipFile, zipFileName, "application/zip");
                
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
    
    private File saveToPublicDocuments(File sourceFile, String fileName, String mimeType) {
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
                return new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName);
            }
        } catch (Exception e) {
            writeLog("✗ 保存失败：" + e.getMessage());
            e.printStackTrace();
        }
        return sourceFile;
    }
    
    private void deleteDir(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDir(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
