package com.simple.video2audio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.content.ContentValues;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.simple.video2audio.util.FFmpegUtil;

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

    private Button btnSelectVideo, btnExtractAudio;
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

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedVideoUri = uri;
                    btnExtractAudio.setEnabled(true);
                    txtStatus.setText("视频已选");
                    new Thread(() -> {
                        try {
                            tempVideoFile = copyToCache(uri);
                            if (tempVideoFile != null && tempVideoFile.exists()) {
                                writeLog("✅ 缓存成功：" + tempVideoFile.length() + "B");
                                runOnUiThread(() -> txtStatus.setText("已缓存:" + (tempVideoFile.length()/1024/1024) + "MB"));
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
        writeLog("✓ FFmpegKit 已初始化");
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
            new AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("Android 11+ 需要打开「所有文件访问权限」\n\n请在设置页面打开开关。")
                .setPositiveButton("去设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("取消", (d, w) -> Toast.makeText(this, "未授权将无法使用", Toast.LENGTH_SHORT).show())
                .setCancelable(false)
                .show();
        } catch (Exception e) { e.printStackTrace(); }
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
        btnSelectVideo = findViewById(R.id.btnSelectVideo);
        btnExtractAudio = findViewById(R.id.btnExtractAudio);
        progressBar = findViewById(R.id.progressBar);
        txtStatus = findViewById(R.id.txtStatus);
        btnExtractAudio.setEnabled(false);
    }

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (checkStoragePermission()) pickVideo();
        });
        btnExtractAudio.setOnClickListener(v -> extractAudioAAC());
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

    private void extractAudioAAC() {
        if (!checkStoragePermission()) return;
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选视频"); return;
        }

        writeLog("===开始提取 AAC===");
        writeLog("源文件:" + tempVideoFile.getAbsolutePath() + " (" + tempVideoFile.length() + "B)");

        progressBar.setVisibility(ProgressBar.VISIBLE);
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
        FFmpegUtil.execute(cmd, new FFmpegUtil.ExecuteCallback() {
            @Override
            public void onSuccess(String output) {
                long duration = System.currentTimeMillis() - startTime;
                writeLog("✅ 转换成功，耗时:" + duration + "ms");
                Log.i(TAG, "✅ 转换成功：" + output);
                Log.d("FFMPEG_RAW", "完整日志:\n" + output);

                File audioFile = new File(outputPath);
                writeLog("输出文件大小:" + audioFile.length() + "B");

                String fileName = "audio_" + timestamp + ".m4a";
                saveToPublicMusic(audioFile, fileName, "audio/mp4");

                writeLog("✅ 已保存到 Music 目录");
                audioFile.delete();

                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudio.setEnabled(true);
                    Toast.makeText(MainActivity.this, "音频提取成功！", Toast.LENGTH_SHORT).show();
                    showSuccessDialog("已保存到 Music 目录/" + fileName);
                });
                cleanup();
            }

            @Override
            public void onFailure(String error) {
                long duration = System.currentTimeMillis() - startTime;
                writeLog("❌ 转换失败，耗时:" + duration + "ms");
                writeLog("错误详情:" + error);
                Log.e(TAG, "❌ 转换失败：" + error);
                Log.d("FFMPEG_RAW", "完整原始日志:\n" + error);
                Log.e("FFMPEG_ERROR", "转换失败\n" + error);

                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudio.setEnabled(true);
                    Toast.makeText(MainActivity.this, "失败：" + error, Toast.LENGTH_LONG).show();
                    showErrorDialog("FFmpeg 失败", error);
                });

                if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
            }
        });
    }

    private String quotePath(String path) {
        if (path.contains(" ")) {
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
        progressBar.setVisibility(ProgressBar.GONE);
        btnExtractAudio.setEnabled(true);
        if (tempVideoFile != null && tempVideoFile.exists()) {
            tempVideoFile.delete();
            tempVideoFile = null;
        }
    }

    private void showSuccessDialog(String filePath) {
        new AlertDialog.Builder(this)
            .setTitle("✓ 转换成功")
            .setMessage("已保存:\n" + filePath + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null).setCancelable(false).show();
    }

    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage((message != null && !message.isEmpty() ? message : "错误") + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null).setCancelable(false).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
        writeLog("应用结束");
    }
}
