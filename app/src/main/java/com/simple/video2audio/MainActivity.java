package com.simple.video2audio;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.MediaController;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.ReturnCode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_VIDEO = 100;
    private static final int PERMISSION_CODE = 100;
    private static final String TAG = "VideoEditor";
    
    private VideoView videoView;
    private SeekBar seekBar;
    private TextView tvLeft, tvRight, uploadVideo, tvCutStartTime, tvCutEndTime;
    private android.widget.Button btnSetStartPoint, btnSetEndPoint;
    private Dialog progressDialog;
    private Uri selectedVideoUri;
    private String cachedVideoPath;
    private String filePath;
    private int duration;
    private int cutStartSec = 0;
    private int cutEndSec = 0;
    private boolean isSeeking = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.videoView);
        seekBar = findViewById(R.id.seekBarProgress);
        tvLeft = findViewById(R.id.tvLeft);
        tvRight = findViewById(R.id.tvRight);
        uploadVideo = findViewById(R.id.uploadVideo);
        tvCutStartTime = findViewById(R.id.tvCutStartTime);
        tvCutEndTime = findViewById(R.id.tvCutEndTime);
        btnSetStartPoint = findViewById(R.id.btnSetStartPoint);
        btnSetEndPoint = findViewById(R.id.btnSetEndPoint);
        progressDialog = new Dialog(this);
        progressDialog.setCancelable(false);
        progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        progressDialog.setContentView(R.layout.dialog_singleoption_text);
        seekBar.setEnabled(false);
        
        setupListeners();
    }
    
    private void setupListeners() {
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
        
        uploadVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) pickVideo();
        });
        
        findViewById(R.id.cutVideo).setOnClickListener(v -> {
            if (selectedVideoUri != null) executeCutVideo();
            else Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show();
        });
        
        findViewById(R.id.extractAudio).setOnClickListener(v -> {
            if (selectedVideoUri != null) extractAudio();
            else Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show();
        });
        
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
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_CODE);
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
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("取消", (d, w) -> Toast.makeText(this, "未授权将无法使用", Toast.LENGTH_SHORT).show())
            .setCancelable(false)
            .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_CODE && grantResults.length > 0 && 
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickVideo();
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        startActivityForResult(Intent.createChooser(intent, "选择视频"), REQUEST_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_VIDEO && data != null && data.getData() != null) {
            selectedVideoUri = data.getData();
            Log.d(TAG, "Video selected, Uri: " + selectedVideoUri);
            
            // 先复制视频到缓存目录，再播放
            String tempPath = copyVideoToCache(selectedVideoUri);
            if (tempPath != null) {
                cachedVideoPath = tempPath;
                Log.d(TAG, "Video cached to: " + cachedVideoPath);
                playVideo(cachedVideoPath);
            } else {
                Log.e(TAG, "Failed to copy video to cache");
                Toast.makeText(this, "无法加载视频，请重试", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void playVideo(String videoPath) {
        if (videoPath == null) {
            Log.e(TAG, "videoPath is null");
            return;
        }
        
        File videoFile = new File(videoPath);
        if (!videoFile.exists()) {
            Log.e(TAG, "Video file does not exist: " + videoPath);
            Toast.makeText(this, "视频文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (videoFile.length() == 0) {
            Log.e(TAG, "Video file is empty (0 bytes): " + videoPath);
            Toast.makeText(this, "视频文件为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Video file OK, size=" + videoFile.length() + " bytes");
        
        // 设置错误监听器
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, "VideoView Error: what=" + what + ", extra=" + extra);
                Toast.makeText(MainActivity.this, "视频播放出错 (" + what + ")", Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        
        // 设置准备监听器
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, "Video prepared, duration=" + mp.getDuration() + "ms");
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
                
                // 在 onPrepared 中启动播放
                videoView.start();
                Log.d(TAG, "Playback started");
                
                handler.postDelayed(runnable = () -> {
                    int pos = videoView.getCurrentPosition() / 1000;
                    if (!isSeeking) seekBar.setProgress(pos);
                    handler.postDelayed(runnable, 1000);
                }, 1000);
            }
        });
        
        // 设置视频路径
        videoView.setVideoPath(videoPath);
        Log.d(TAG, "setVideoPath called: " + videoPath);
        
        // 添加 MediaController 以便用户控制播放
        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);
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

    private String getTime(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private void executeCutVideo() {
        if (cachedVideoPath == null) {
            Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File cacheFile = new File(cachedVideoPath);
        if (!cacheFile.exists() || cacheFile.length() == 0) {
            Toast.makeText(this, "视频缓存文件无效", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (!dir.exists()) dir.mkdirs();
        File dest = new File(dir, "cut_video_" + System.currentTimeMillis() + ".mp4");
        filePath = dest.getAbsolutePath();
        
        int videoLen = cutEndSec - cutStartSec;
        if (videoLen <= 0) {
            Toast.makeText(this, "剪切时长必须大于 0", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Log.d(TAG, "Cut video: start=" + cutStartSec + ", end=" + cutEndSec + ", len=" + videoLen);
        
        String[] cmd = {
            "-ss", String.valueOf(cutStartSec),
            "-y",
            "-i", cachedVideoPath,
            "-t", String.valueOf(videoLen),
            "-c:v", "libx264",
            "-c:a", "aac",
            "-b:v", "2M",
            "-b:a", "128k",
            "-avoid_negative_ts", "make_zero",
            filePath
        };
        executeFFmpeg(cmd, 1);
    }

    private void extractAudio() {
        if (cachedVideoPath == null) {
            Toast.makeText(this, "请先选择视频", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File cacheFile = new File(cachedVideoPath);
        if (!cacheFile.exists() || cacheFile.length() == 0) {
            Toast.makeText(this, "视频缓存文件无效", Toast.LENGTH_SHORT).show();
            return;
        }
        
        File audioDir = getExternalFilesDir(null);
        if (audioDir == null) {
            audioDir = new File(getFilesDir(), "audio");
        }
        if (!audioDir.exists()) {
            audioDir.mkdirs();
        }
        
        long timestamp = System.currentTimeMillis();
        File dest = new File(audioDir, "audio_" + timestamp + ".mp3");
        filePath = dest.getAbsolutePath();
        
        Log.d(TAG, "Extract audio to: " + filePath);
        
        String[] cmd = {
            "-y",
            "-i", cachedVideoPath,
            "-vn",
            "-ar", "44100",
            "-ac", "2",
            "-b:a", "256k",
            "-f", "mp3",
            filePath
        };
        executeFFmpeg(cmd, 2);
    }

    private void executeFFmpeg(String[] cmd, int type) {
        StringBuilder sb = new StringBuilder();
        for (String s : cmd) sb.append(s).append(" ");
        final String command = sb.toString();
        
        Log.d(TAG, "FFmpeg command: " + command);
        
        showProgress("处理中...");
        FFmpegKit.executeAsync(command, session -> {
            runOnUiThread(() -> {
                hideProgress();
                ReturnCode rc = session.getReturnCode();
                if (ReturnCode.isSuccess(rc)) {
                    Log.d(TAG, "FFmpeg completed successfully");
                    Toast.makeText(this, "处理完成", Toast.LENGTH_SHORT).show();
                    if (type == 1) {
                        startActivity(new Intent(this, PreviewActivity.class)
                            .putExtra("filepath", filePath)
                            .putExtra("filename", "cut_video_" + System.currentTimeMillis() + ".mp4"));
                    } else {
                        startActivity(new Intent(this, AudioPreviewActivity.class)
                            .putExtra("filepath", filePath)
                            .putExtra("filename", "audio_" + System.currentTimeMillis() + ".mp3"));
                    }
                } else {
                    String errorMsg = "处理失败";
                    if (session.getFailStackTrace() != null) {
                        errorMsg += ": " + session.getFailStackTrace();
                        Log.e(TAG, "FFmpeg error: " + session.getFailStackTrace());
                    }
                    Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                }
            });
        }, null, null);
    }

    private String copyVideoToCache(Uri videoUri) {
        if (videoUri == null) {
            Log.e(TAG, "videoUri is null");
            return null;
        }
        
        File cacheFile = new File(getCacheDir(), "temp_video_" + System.currentTimeMillis() + ".mp4");
        
        // 删除旧的缓存文件
        File[] oldFiles = getCacheDir().listFiles((dir, name) -> name.startsWith("temp_video_"));
        if (oldFiles != null) {
            for (File f : oldFiles) f.delete();
        }
        
        try {
            Log.d(TAG, "Copying video to cache: " + cacheFile.getAbsolutePath());
            
            try (InputStream inputStream = getContentResolver().openInputStream(videoUri);
                 FileOutputStream outputStream = new FileOutputStream(cacheFile)) {
                
                if (inputStream == null) {
                    Log.e(TAG, "Failed to open InputStream for Uri: " + videoUri);
                    return null;
                }
                
                byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
                int length;
                long totalBytes = 0;
                
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                    totalBytes += length;
                }
                
                Log.d(TAG, "Copied " + totalBytes + " bytes");
            }
            
            // 验证文件大小
            if (cacheFile.length() == 0) {
                Log.e(TAG, "Copied file is empty");
                cacheFile.delete();
                return null;
            }
            
            Log.d(TAG, "Cache file created, size=" + cacheFile.length() + " bytes");
            return cacheFile.getAbsolutePath();
            
        } catch (Exception e) {
            Log.e(TAG, "copyVideoToCache error", e);
            if (cacheFile.exists()) cacheFile.delete();
            return null;
        }
    }

    private void showProgress(String msg) {
        runOnUiThread(() -> {
            TextView tv = progressDialog.findViewById(R.id.tvDialogText);
            TextView heading = progressDialog.findViewById(R.id.tvDialogHeading);
            TextView btn = progressDialog.findViewById(R.id.tvDialogSubmit);
            if (heading != null) heading.setText("处理中");
            if (tv != null) tv.setText(msg);
            if (btn != null) { btn.setText("取消"); btn.setOnClickListener(v -> hideProgress()); }
            progressDialog.show();
        });
    }

    private void hideProgress() {
        runOnUiThread(() -> { if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss(); });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }
}
