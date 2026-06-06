package com.simple.video2audio;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
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
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private Button btnSelectVideo, btnExtractAudioM4A, btnExtractAudioMP3;
    private ProgressBar progressBar;
    private TextView txtStatus;
    private FFmpeg ffmpeg;
    private Uri selectedVideoUri = null;
    private File tempVideoFile = null;
    private File logFile;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) pickVideo();
                else showErrorDialog("需要权限", "请授权");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        checkAndRequestPermission();
        initLogFile();
        initViews();
        setupListeners();
        loadFFmpeg();
    }

    private void checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageStoragePermission();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) 
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void initLogFile() {
        File logDir = new File(Environment.getExternalStorageDirectory(), "douyinguanjia/Log");
        if (!logDir.exists()) logDir.mkdirs();
        logFile = new File(logDir, "videoEdit.log");
        writeLog("===启动===");
        writeLog("Android " + Build.VERSION.RELEASE + " SDK" + Build.VERSION.SDK_INT);
        writeLog("CPU " + Build.CPU_ABI);
    }

    private void writeLog(String msg) {
        try {
            String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            FileWriter w = new FileWriter(logFile, true);
            w.append("[").append(ts).append("] ").append(msg).append("\n");
            w.flush(); w.close();
        } catch (Exception e) { e.printStackTrace(); }
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

    private void setupListeners() {
        btnSelectVideo.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                requestManageStoragePermission(); return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO);
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        });
        btnExtractAudioM4A.setOnClickListener(v -> extractAudioM4A());
        btnExtractAudioMP3.setOnClickListener(v -> extractAudioMP3());
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            requestManageStoragePermission(); return false;
        }
        return true;
    }

    private void requestManageStoragePermission() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void pickVideo() { videoPickerLauncher.launch("video/*"); }

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedVideoUri = uri;
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    txtStatus.setText("视频已选");
                    try {
                        tempVideoFile = copyToCache(uri);
                        writeLog("缓存:" + (tempVideoFile != null ? tempVideoFile.length() + "B" : "失败"));
                    } catch (Exception e) {
                        writeLog("复制异常:" + e.getMessage());
                        showErrorDialog("错误", e.getMessage());
                    }
                }
            });

    private File copyToCache(Uri uri) throws Exception {
        File f = new File(getCacheDir(), "t_" + System.currentTimeMillis() + ".mp4");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(f)) {
            if (in == null) return null;
            byte[] b = new byte[4096]; int n; long t = 0;
            while ((n = in.read(b)) != -1) { out.write(b, 0, n); t += n; }
            out.flush();
            return f.exists() && f.canRead() ? f : null;
        }
    }

    private void loadFFmpeg() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
        try {
            ffmpeg = FFmpeg.getInstance(this);
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override public void onSuccess() {
                    progressBar.setVisibility(ProgressBar.GONE);
                    txtStatus.setText("就绪");
                    writeLog("✓FFmpeg OK");
                }
                @Override public void onFailure() {
                    progressBar.setVisibility(ProgressBar.GONE);
                    writeLog("✗FFmpeg fail");
                    showErrorDialog("不支持", "设备不支持 FFmpeg");
                }
            });
        } catch (Exception e) {
            progressBar.setVisibility(ProgressBar.GONE);
            writeLog("✗FFmpeg err:" + e.getMessage());
            showErrorDialog("错误", e.getMessage());
        }
    }

    private void extractAudioM4A() {
        if (!hasPermission()) return;
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选视频"); return;
        }

        writeLog("===M4A===");
        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnExtractAudioM4A.setEnabled(false);
        btnExtractAudioMP3.setEnabled(false);

        new Thread(() -> {
            try {
                File out = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                                   "audio_" + System.currentTimeMillis() + ".m4a");

                MediaExtractor ex = new MediaExtractor();
                ex.setDataSource(tempVideoFile.getAbsolutePath());

                int audioIdx = -1;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat f = ex.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    writeLog("track " + i + ":" + mime);
                    if (mime != null && mime.startsWith("audio/")) {
                        audioIdx = i; break;
                    }
                }

                if (audioIdx < 0) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(ProgressBar.GONE);
                        btnExtractAudioM4A.setEnabled(true);
                        btnExtractAudioMP3.setEnabled(true);
                        showErrorDialog("失败", "无音频轨道");
                    });
                    return;
                }

                ex.selectTrack(audioIdx);
                MediaFormat fmt = ex.getTrackFormat(audioIdx);

                MediaMuxer mux = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int muxIdx = mux.addTrack(fmt);
                mux.start();

                ByteBuffer buf = ByteBuffer.allocate(4 * 1024 * 1024);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                long total = 0;

                while (true) {
                    int sz = ex.readSampleData(buf, 0);
                    if (sz < 0) break;
                    info.offset = 0; info.size = sz;
                    info.presentationTimeUs = ex.getSampleTime();
                    info.flags = ex.getSampleFlags();
                    mux.writeSampleData(muxIdx, buf, info);
                    total += sz;
                    ex.advance();
                }

                mux.stop(); mux.release(); ex.release();

                writeLog("✓M4A " + out + " " + out.length() + "B");
                if (tempVideoFile.exists()) tempVideoFile.delete();

                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    showSuccessDialog(out.getAbsolutePath());
                });
            } catch (Exception e) {
                writeLog("✗M4A err:" + e.getMessage());
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    showErrorDialog("失败", e.getMessage());
                });
            }
        }).start();
    }

    private void extractAudioMP3() {
        if (!hasPermission()) return;
        if (tempVideoFile == null || !tempVideoFile.exists()) {
            showErrorDialog("错误", "请先选视频"); return;
        }

        writeLog("===MP3===");
        writeLog("src:" + tempVideoFile.getAbsolutePath() + " " + tempVideoFile.length() + "B");

        progressBar.setVisibility(ProgressBar.VISIBLE);
        btnExtractAudioM4A.setEnabled(false);
        btnExtractAudioMP3.setEnabled(false);

        File outDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File out = new File(outDir, "audio_" + System.currentTimeMillis() + ".mp3");
        String srcPath = tempVideoFile.getAbsolutePath().replace(" ", "\\ ");
        String dstPath = out.getAbsolutePath().replace(" ", "\\ ");
        writeLog("dst:" + dstPath);

        String[] cmd = {"-y", "-i", srcPath, "-vn", "-ar", "44100", "-ac", "2", "-acodec", "libmp3lame", "-b:a", "192k", dstPath};
        writeLog("cmd:" + String.join(" ", cmd));

        try {
            ffmpeg.execute(cmd, new ExecuteBinaryResponseHandler() {
                @Override public void onSuccess(String s) {
                    writeLog("✓MP3 OK:" + out + " " + out.length() + "B");
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    if (tempVideoFile.exists()) tempVideoFile.delete();
                    showSuccessDialog(out.getAbsolutePath());
                }
                @Override public void onFailure(String s) {
                    writeLog("✗MP3 fail:" + s);
                    progressBar.setVisibility(ProgressBar.GONE);
                    btnExtractAudioM4A.setEnabled(true);
                    btnExtractAudioMP3.setEnabled(true);
                    if (tempVideoFile.exists()) tempVideoFile.delete();
                    showErrorDialog("失败", s == null || s.isEmpty() ? "FFmpeg 失败" : s);
                }
                @Override public void onProgress(String s) { txtStatus.setText("..."); }
                @Override public void onStart() { writeLog("start"); txtStatus.setText("提取中..."); }
                @Override public void onFinish() { writeLog("finish"); }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            writeLog("✗running");
            progressBar.setVisibility(ProgressBar.GONE);
            btnExtractAudioM4A.setEnabled(true);
            btnExtractAudioMP3.setEnabled(true);
            showErrorDialog("错误", "执行中");
        }
    }

    private void showSuccessDialog(String p) {
        new AlertDialog.Builder(this).setTitle("✓")
            .setMessage("已保存:\n" + p + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null).setCancelable(false).show();
    }

    private void showErrorDialog(String t, String m) {
        new AlertDialog.Builder(this).setTitle(t)
            .setMessage((m == null || m.isEmpty() ? "错误" : m) + "\n\n日志:\n" + logFile.getAbsolutePath())
            .setPositiveButton("确定", null).setCancelable(false).show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (tempVideoFile != null && tempVideoFile.exists()) tempVideoFile.delete();
        writeLog("end");
    }
}
