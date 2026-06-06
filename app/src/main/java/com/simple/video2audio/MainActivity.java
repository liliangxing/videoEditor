package com.simple.video2audio;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.Intent;
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
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, 
        MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    
    private static final String TAG = "VideoToAudio";
    
    // UI 组件
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private SeekBar seekBar;
    private TextView tvLeft, tvRight, tvCutStartTime, tvCutEndTime, txtStatus;
    private Button btnSelectVideo, btnCutVideo, btnExtractAudio, btnSetStartPoint, btnSetEndPoint;
    private Button btnPlayPause, btnStop;
    private ProgressBar progressBar;
    
    // MediaPlayer
    private MediaPlayer mediaPlayer;
    private Surface surface;
    
    // 文件和数据
    private Uri selectedVideoUri = null;
    private File tempVideoFile = null;
    private File logFile;
    
    private int duration = 0;
    private int cutStartSec = 0;
    private int cutEndSec = 0;
    private boolean isPlaying = false;
    private boolean isSurfaceReady = false;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;
    
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) pickVideo();
                else showErrorDialog("需要权限", "请授权存储权限");
            });
    
    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedVideoUri = uri;
                    writeLog("视频已选择：" + uri);
                    new Thread(() -> {
                        try {
                            tempVideoFile = copyToCache(uri);
                            if (tempVideoFile != null && tempVideoFile.exists()) {
                                writeLog("✅ 缓存成功：" + tempVideoFile.length() + "B");
                                runOnUiThread(() -> {
                                    txtStatus.setText("已缓存:" + (tempVideoFile.length()/1024/1024) + "MB");
                                    if (isSurfaceReady) {
                                        playVideo();
                                    }
                                });
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
        
        // FFmpegKit 测试
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                FFmpegSession session = FFmpegKit.executeAsync("-version", completedSession -> {
                    ReturnCode returnCode = completedSession.getReturnCode();
                    String output = completedSession.getOutput();
                    if (ReturnCode.isSuccess(returnCode)) {
                        writeLog("✅ FFmpegKit 测试成功");
                    } else {
                        writeLog("❌ FFmpegKit 测试失败：" + output);
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
        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        
        seekBar = findViewById(R.id.seekBarProgress);
        tvLeft = findViewById(R.id.tvLeft);
        tvRight = findViewById(R.id.tvRight);
        tvCutStartTime = findViewById(R.id.tvCutStartTime);
        tvCutEndTime = findViewById(R.id.tvCutEndTime);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnCutVideo = findViewById(R.id.btnCutVideo);
        btnExtractAudio = findViewById(R.id.btnExtractAudio);
        btnSetStartPoint = findViewById(R.id.btnSetStartPoint);
        btnSetEndPoint = findViewById(R.id.btnSetEndPoint);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnStop = findViewById(R.id.btnStop);
        progressBar = findViewById(R.id.progressBar);
        txtStatus = findViewById(R.id.txtStatus);
        
        btnCutVideo.setEnabled(false);
        btnExtractAudio.setEnabled(false);
        btnPlayPause.setEnabled(false);
        btnStop.setEnabled(false);
        txtStatus.setVisibility(View.GONE);
    }

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) pickVideo();
        });
        
        btnSetStartPoint.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                cutStartSec = mediaPlayer.getCurrentPosition() / 1000;
                tvCutStartTime.setText("起点：" + getTime(cutStartSec));
                Toast.makeText(this, "起点已设为：" + getTime(cutStartSec), Toast.LENGTH_SHORT).show();
            }
        });
        
        btnSetEndPoint.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                cutEndSec = mediaPlayer.getCurrentPosition() / 1000;
                tvCutEndTime.setText("终点：" + getTime(cutEndSec));
                Toast.makeText(this, "终点已设为：" + getTime(cutEndSec), Toast.LENGTH_SHORT).show();
            }
        });
        
        btnCutVideo.setOnClickListener(v -> executeCutVideo());
        btnExtractAudio.setOnClickListener(v -> extractAudioAAC());
        
        btnPlayPause.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (isPlaying) {
                    mediaPlayer.pause();
                    btnPlayPause.setText("播放");
                } else {
                    mediaPlayer.start();
                    btnPlayPause.setText("暂停");
                }
                isPlaying = !isPlaying;
            }
        });
        
        btnStop.setOnClickListener(v -> stopVideo());
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress * 1000);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(progress * 1000);
                }
            }
        });
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

    private void playVideo() {
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            writeLog("❌ 视频文件不存在");
            return;
        }
        
        writeLog("开始播放视频：" + tempVideoFile.getAbsolutePath());
        writeLog("文件大小：" + tempVideoFile.length() + "B");
        
        String videoPath = tempVideoFile.getAbsolutePath();
        
        try {
            // 释放旧的 MediaPlayer
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }
            
            // 创建新的 MediaPlayer
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(videoPath);
            mediaPlayer.setDisplay(surfaceHolder);
            
            // 设置监听器
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setOnCompletionListener(this);
            
            // 准备播放
            mediaPlayer.prepareAsync();
            writeLog("MediaPlayer 准备中...");
            
        } catch (Exception e) {
            writeLog("❌ 播放失败：" + e.getMessage());
            Toast.makeText(this, "无法播放视频：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        writeLog("✅ 视频准备完成，时长：" + mp.getDuration() + "ms");
        duration = mp.getDuration() / 1000;
        cutStartSec = 0;
        cutEndSec = duration;
        tvLeft.setText("00:00:00");
        tvRight.setText(getTime(duration));
        tvCutStartTime.setText("起点：00:00:00");
        tvCutEndTime.setText("终点：" + getTime(duration));
        
        seekBar.setMax(duration);
        seekBar.setProgress(0);
        seekBar.setEnabled(true);
        
        // 启用功能按钮
        btnCutVideo.setEnabled(true);
        btnExtractAudio.setEnabled(true);
        btnPlayPause.setEnabled(true);
        btnStop.setEnabled(true);
        
        // 启动播放
        mediaPlayer.start();
        isPlaying = true;
        btnPlayPause.setText("暂停");
        writeLog("▶️ 开始播放");
        
        // 进度更新
        handler.postDelayed(runnable = () -> {
            if (mediaPlayer != null && isPlaying) {
                int pos = mediaPlayer.getCurrentPosition() / 1000;
                seekBar.setProgress(pos);
                handler.postDelayed(runnable, 1000);
            }
        }, 1000);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        writeLog("❌ MediaPlayer 错误：" + what + ", " + extra);
        Toast.makeText(this, "视频播放出错 (" + what + ")", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        writeLog("播放完成");
        isPlaying = false;
        btnPlayPause.setText("播放");
        seekBar.setProgress(duration);
    }

    private void stopVideo() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.seekTo(0);
            isPlaying = false;
            btnPlayPause.setText("播放");
            seekBar.setProgress(0);
            writeLog("⏹️ 停止播放");
        }
    }

    private void executeCutVideo() {
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选择视频");
            return;
        }
        
        int videoLen = cutEndSec - cutStartSec;
        if (videoLen <= 0) {
            Toast.makeText(this, "剪切时长必须大于 0", Toast.LENGTH_SHORT).show();
            return;
        }
        
        writeLog("===开始剪切视频===");
        writeLog("源文件：" + tempVideoFile.getAbsolutePath());
        writeLog("起点：" + cutStartSec + "s, 终点：" + cutEndSec + "s, 时长：" + videoLen + "s");
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        txtStatus.setVisibility(TextView.VISIBLE);
        txtStatus.setText("剪切中...");
        btnCutVideo.setEnabled(false);
        btnExtractAudio.setEnabled(false);
        
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (!dir.exists()) dir.mkdirs();
        String fileName = "cut_video_" + System.currentTimeMillis() + ".mp4";
        String outputPath = dir.getAbsolutePath() + "/" + fileName;
        String srcPath = tempVideoFile.getAbsolutePath();
        
        String cmd = "-y -ss " + cutStartSec + " -i " + quotePath(srcPath) + 
                     " -t " + videoLen + " -c:v libx264 -c:a aac -b:v 2M -b:a 128k -avoid_negative_ts make_zero " + 
                     quotePath(outputPath);
        
        writeLog("FFmpeg 命令：" + cmd);
        
        final long startTime = System.currentTimeMillis();
        FFmpegSession session = FFmpegKit.executeAsync(cmd, completedSession -> {
            ReturnCode returnCode = completedSession.getReturnCode();
            String output = completedSession.getOutput();
            String allLogs = completedSession.getAllLogsAsString();
            long duration = System.currentTimeMillis() - startTime;
            
            if (ReturnCode.isSuccess(returnCode)) {
                writeLog("✅ 剪切成功，耗时：" + duration + "ms");
                writeLog("输出文件：" + outputPath);
                
                File outputFile = new File(outputPath);
                writeLog("输出文件大小：" + outputFile.length() + "B");
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    txtStatus.setVisibility(TextView.GONE);
                    btnCutVideo.setEnabled(true);
                    btnExtractAudio.setEnabled(true);
                    Toast.makeText(this, "视频剪切成功！", Toast.LENGTH_SHORT).show();
                    showSuccessDialog("已保存到 Movies/" + fileName);
                });
            } else {
                writeLog("❌ 剪切失败，耗时：" + duration + "ms");
                writeLog("错误详情：" + output);
                Log.e("FFMPEG_ERROR", "剪切失败\n" + allLogs);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    txtStatus.setVisibility(TextView.GONE);
                    btnCutVideo.setEnabled(true);
                    btnExtractAudio.setEnabled(true);
                    Toast.makeText(this, "失败：" + output, Toast.LENGTH_LONG).show();
                    showErrorDialog("剪切失败", output);
                });
            }
        });
        
        if (session == null) {
            writeLog("❌ FFmpegSession 创建失败");
            runOnUiThread(() -> {
                progressBar.setVisibility(ProgressBar.GONE);
                txtStatus.setVisibility(TextView.GONE);
                btnCutVideo.setEnabled(true);
                btnExtractAudio.setEnabled(true);
                Toast.makeText(this, "FFmpegSession 创建失败", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void extractAudioAAC() {
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选择视频");
            return;
        }
        
        writeLog("===开始提取 AAC===");
        writeLog("源文件：" + tempVideoFile.getAbsolutePath() + " (" + tempVideoFile.length() + "B)");
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        txtStatus.setVisibility(TextView.VISIBLE);
        txtStatus.setText("提取中...");
        btnCutVideo.setEnabled(false);
        btnExtractAudio.setEnabled(false);
        
        File audioDir = new File(getCacheDir(), "audio");
        if (!audioDir.exists()) {
            boolean created = audioDir.mkdirs();
            writeLog("audio dir created: " + created);
        }
        
        long timestamp = System.currentTimeMillis();
        String outputPath = audioDir.getAbsolutePath() + "/audio_" + timestamp + ".m4a";
        String srcPath = tempVideoFile.getAbsolutePath();
        String cmd = "-y -i " + quotePath(srcPath) + " -vn -c:a aac -b:a 192k -ar 44100 -ac 2 " + quotePath(outputPath);
        
        writeLog("FFmpeg 命令：" + cmd);
        writeLog("输出文件：" + outputPath);
        
        final long startTime = System.currentTimeMillis();
        FFmpegSession session = FFmpegKit.executeAsync(cmd, completedSession -> {
            ReturnCode returnCode = completedSession.getReturnCode();
            String output = completedSession.getOutput();
            String allLogs = completedSession.getAllLogsAsString();
            long duration = System.currentTimeMillis() - startTime;
            
            if (ReturnCode.isSuccess(returnCode)) {
                writeLog("✅ 转换成功，耗时：" + duration + "ms");
                Log.i(TAG, "✅ 转换成功：" + output);
                Log.d("FFMPEG_RAW", "完整日志:\n" + allLogs);
                
                File audioFile = new File(outputPath);
                writeLog("输出文件大小：" + audioFile.length() + "B");
                
                String fileName = "audio_" + timestamp + ".m4a";
                saveToPublicMusic(audioFile, fileName, "audio/mp4");
                
                writeLog("✅ 已保存到 Music 目录");
                audioFile.delete();
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    txtStatus.setVisibility(TextView.GONE);
                    btnCutVideo.setEnabled(true);
                    btnExtractAudio.setEnabled(true);
                    Toast.makeText(this, "音频提取成功！", Toast.LENGTH_SHORT).show();
                    showSuccessDialog("已保存到 Music 目录/" + fileName);
                });
                cleanup();
            } else {
                writeLog("❌ 转换失败，耗时：" + duration + "ms");
                writeLog("错误详情：" + output);
                Log.e(TAG, "❌ 转换失败：" + output);
                Log.d("FFMPEG_RAW", "完整原始日志:\n" + allLogs);
                Log.e("FFMPEG_ERROR", "转换失败\n" + output);
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    txtStatus.setVisibility(TextView.GONE);
                    btnCutVideo.setEnabled(true);
                    btnExtractAudio.setEnabled(true);
                    Toast.makeText(this, "失败：" + output, Toast.LENGTH_LONG).show();
                    showErrorDialog("FFmpeg 失败", output);
                });
                
                if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            }
        });
        
        if (session == null) {
            Log.e("FFmpeg", "❌ FFmpegSession 创建失败");
            writeLog("❌ FFmpegSession 创建失败");
            runOnUiThread(() -> {
                progressBar.setVisibility(ProgressBar.GONE);
                txtStatus.setVisibility(TextView.GONE);
                btnCutVideo.setEnabled(true);
                btnExtractAudio.setEnabled(true);
                Toast.makeText(this, "FFmpegSession 创建失败", Toast.LENGTH_SHORT).show();
            });
        }
        
        Log.d("FFmpeg", "命令已提交，sessionId: " + (session != null ? session.getSessionId() : "null"));
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
            } else {
                writeLog("✗ 无法创建 Music 文件");
            }
        } catch (Exception e) {
            writeLog("✗ 保存失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    private void cleanup() {
        if (tempVideoFile != null && tempVideoFile.exists()) {
            tempVideoFile.delete();
            tempVideoFile = null;
        }
    }

    private void showSuccessDialog(String filePath) {
        new AlertDialog.Builder(this)
            .setTitle("✓ 成功")
            .setMessage("已保存:\n" + filePath + "\n\n日志:\n" + logFile.getAbsolutePath())
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

    private String getTime(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        writeLog("Surface 已创建");
        isSurfaceReady = true;
        surface = holder.getSurface();
        
        // 如果已经有视频文件，开始播放
        if (tempVideoFile != null && tempVideoFile.exists()) {
            playVideo();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        writeLog("Surface 已改变：" + width + "x" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        writeLog("Surface 已销毁");
        isSurfaceReady = false;
        if (surface != null) {
            surface.release();
            surface = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            btnPlayPause.setText("播放");
        }
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && !isPlaying && mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            isPlaying = true;
            btnPlayPause.setText("暂停");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (tempVideoFile != null && tempVideoFile.exists()) {
            tempVideoFile.delete();
        }
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
        writeLog("应用结束");
    }
}
