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
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "VideoToAudio";
    
    // UI 组件
    private ImageView imageView;
    private TextView txtStatus;
    private ProgressBar progressBar;
    private Button btnSelectVideo, btnExtractAudio, btnExtractMP3;
    
    // 文件和数据
    private Uri selectedVideoUri = null;
    private File tempVideoFile = null;
    private File logFile;
    
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
                                    btnExtractAudio.setEnabled(true);
                                    btnExtractMP3.setEnabled(true);
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
        new Handler().postDelayed(() -> {
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
        imageView = findViewById(R.id.imageView);
        txtStatus = findViewById(R.id.txtStatus);
        progressBar = findViewById(R.id.progressBar);
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnExtractAudio = findViewById(R.id.btnExtractAudio);
        btnExtractMP3 = findViewById(R.id.btnExtractMP3);
        
        btnExtractAudio.setEnabled(false);
        btnExtractMP3.setEnabled(false);
    }

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) pickVideo();
        });
        btnExtractAudio.setOnClickListener(v -> extractAudio("m4a", "audio/mp4", "AAC"));
        btnExtractMP3.setOnClickListener(v -> extractAudio("mp3", "audio/mpeg", "MP3"));
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

    private void extractAudio(String format, String mimeType, String formatName) {
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选择视频");
            return;
        }
        
        writeLog("===开始提取 " + formatName + "==");
        writeLog("源文件：" + tempVideoFile.getAbsolutePath() + " (" + tempVideoFile.length() + "B)");
        
        progressBar.setVisibility(ProgressBar.VISIBLE);
        txtStatus.setText("提取中...");
        btnExtractAudio.setEnabled(false);
        btnExtractMP3.setEnabled(false);
        
        File audioDir = new File(getCacheDir(), "audio");
        if (!audioDir.exists()) audioDir.mkdirs();
        
        long timestamp = System.currentTimeMillis();
        String outputPath = audioDir.getAbsolutePath() + "/audio_" + timestamp + "." + format;
        String srcPath = tempVideoFile.getAbsolutePath();
        
        String cmd;
        if ("mp3".equals(format)) {
            cmd = "-y -i " + quotePath(srcPath) + " -vn -ar 44100 -ac 2 -b:a 256k -f mp3 " + quotePath(outputPath);
        } else {
            cmd = "-y -i " + quotePath(srcPath) + " -vn -c:a aac -b:a 192k -ar 44100 -ac 2 " + quotePath(outputPath);
        }
        
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
                
                String fileName = "audio_" + timestamp + "." + format;
                saveToPublicMusic(audioFile, fileName, mimeType);
                
                writeLog("✅ 已保存到 Music 目录");
                audioFile.delete();
                
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    txtStatus.setText("提取完成");
                    btnExtractAudio.setEnabled(true);
                    btnExtractMP3.setEnabled(true);
                    Toast.makeText(this, formatName + "提取成功！", Toast.LENGTH_SHORT).show();
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
                    txtStatus.setText("提取失败");
                    btnExtractAudio.setEnabled(true);
                    btnExtractMP3.setEnabled(true);
                    Toast.makeText(this, "失败：" + output, Toast.LENGTH_LONG).show();
                    showErrorDialog("提取失败", output);
                });
                
                if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            }
        });
        
        if (session == null) {
            Log.e("FFmpeg", "❌ FFmpegSession 创建失败");
            writeLog("❌ FFmpegSession 创建失败");
            runOnUiThread(() -> {
                progressBar.setVisibility(ProgressBar.GONE);
                txtStatus.setText("FFmpeg 错误");
                btnExtractAudio.setEnabled(true);
                btnExtractMP3.setEnabled(true);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) {
            tempVideoFile.delete();
        }
        writeLog("应用结束");
    }
}
