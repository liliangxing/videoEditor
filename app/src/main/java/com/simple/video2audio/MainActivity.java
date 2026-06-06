package com.simple.video2audio;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
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
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
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
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "VideoToAudio";
    
    private VideoView videoView;
    private SeekBar seekBar;
    private TextView tvLeft, tvRight, tvCutStartTime, tvCutEndTime, txtStatus;
    private Button btnSelectVideo, btnCutVideo, btnExtractAudio, btnSetStartPoint, btnSetEndPoint;
    private ProgressBar progressBar;
    
    private Uri selectedVideoUri = null;
    private File tempVideoFile = null;
    private File logFile;
    
    private int duration = 0;
    private int cutStartSec = 0;
    private int cutEndSec = 0;
    private boolean isSeeking = false;
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
                    writeLog("视频已选择: " + uri);
                    new Thread(() -> {
                        try {
                            tempVideoFile = copyToCache(uri);
                            if (tempVideoFile != null && tempVideoFile.exists()) {
                                writeLog("✅ 缓存成功：" + tempVideoFile.length() + "B");
                                runOnUiThread(() -> {
                                    txtStatus.setText("已缓存:" + (tempVideoFile.length()/1024/1024) + "MB");
                                    playVideo();
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
                        Toast.makeText(MainActivity.this, "FFmpegKit 正常", Toast.LENGTH_LONG).show();
                    } else {
                        writeLog("❌ FFmpegKit 测试失败：" + output);
                        Toast.makeText(MainActivity.this, "FFmpegKit 失败：" + output, Toast.LENGTH_LONG).show();
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
        videoView = findViewById(R.id.videoView);
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
        progressBar = findViewById(R.id.progressBar);
        txtStatus = findViewById(R.id.txtStatus);
        
        btnCutVideo.setEnabled(false);
        btnExtractAudio.setEnabled(false);
        txtStatus.setVisibility(View.GONE);
    }

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) pickVideo();
        });
        
        btnSetStartPoint.setOnClickListener(v -> {
            if (videoView != null && videoView.getCurrentPosition() > 0) {
                cutStartSec = videoView.getCurrentPosition() / 1000;
                tvCutStartTime.setText("起点：" + getTime(cutStartSec));
                Toast.makeText(this, "起点已设为：" + getTime(cutStartSec), Toast.LENGTH_SHORT).show();
            }
        });
        
        btnSetEndPoint.setOnClickListener(v -> {
            if (videoView != null && videoView.getCurrentPosition() > 0) {
                cutEndSec = videoView.getCurrentPosition() / 1000;
                tvCutEndTime.setText("终点：" + getTime(cutEndSec));
                Toast.makeText(this, "终点已设为：" + getTime(cutEndSec), Toast.LENGTH_SHORT).show();
            }
        });
        
        btnCutVideo.setOnClickListener(v -> executeCutVideo());
        btnExtractAudio.setOnClickListener(v -> extractAudioAAC());
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && !isSeeking) {
                    videoView.seekTo(progress * 1000);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { isSeeking = true; }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isSeeking = false;
                int progress = seekBar.getProgress();
                videoView.seekTo(progress * 1000);
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
        
        // 错误监听器
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                writeLog("❌ VideoView 错误：" + what + ", " + extra);
                Toast.makeText(MainActivity.this, "视频播放出错 (" + what + ")", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        
        // 准备监听器
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
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
                mp.setLooping(true);
                seekBar.setMax(duration);
                seekBar.setProgress(0);
                seekBar.setEnabled(true);
                
                // 启用功能按钮
                btnCutVideo.setEnabled(true);
                btnExtractAudio.setEnabled(true);
                
                // 启动播放
                videoView.start();
                writeLog("▶️ 开始播放");
                
                // 进度更新
                handler.postDelayed(runnable = () -> {
                    int pos = videoView.getCurrentPosition() / 1000;
                    if (!isSeeking) seekBar.setProgress(pos);
                    handler.postDelayed(runnable, 1000);
                }, 1000);
            }
        });
        
        // 设置视频路径
        videoView.setVideoPath(videoPath);
        writeLog("设置视频路径：" + videoPath);
        
        // 添加 MediaController
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
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
    protected void onPause() {
        super.onPause();
        if (videoView != null) videoView.pause();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && videoView.isPlaying()) videoView.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
        writeLog("应用结束");
    }
}
