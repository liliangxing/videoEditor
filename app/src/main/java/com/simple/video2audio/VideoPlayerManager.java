package com.simple.video2audio;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

public class VideoPlayerManager {
    private static final String TAG = "VideoPlayerManager";
    private Context context;
    private SurfaceView surfaceView;
    private ImageView previewImg;
    private MediaPlayer mediaPlayer;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private OnPlaybackListener listener;
    private String currentVideoPath;
    private boolean isPrepared = false;
    private boolean isPlaying = false;

    public interface OnPlaybackListener {
        void onPrepared(int duration);
        void onProgressUpdate(int currentPosition, int duration);
        void onCompletion();
        void onError(String error);
    }

    public VideoPlayerManager(Context context, SurfaceView surfaceView, ImageView previewImg, OnPlaybackListener listener) {
        this.context = context;
        this.surfaceView = surfaceView;
        this.previewImg = previewImg;
        this.listener = listener;
        initMediaPlayer();
        setupSurfaceView();
        setupProgressHandler();
    }

    private void initMediaPlayer() {
        if (mediaPlayer != null) {
            release();
        }
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setDisplay(surfaceView.getHolder());
        mediaPlayer.setOnPreparedListener(mp -> {
            isPrepared = true;
            if (listener != null) {
                listener.onPrepared(mp.getDuration());
            }
            start();
        });
        mediaPlayer.setOnCompletionListener(mp -> {
            isPlaying = false;
            if (listener != null) listener.onCompletion();
        });
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            isPrepared = false;
            String errorMsg = "MediaPlayer Error: what=" + what + ", extra=" + extra;
            Log.e(TAG, errorMsg);
            if (listener != null) listener.onError(errorMsg);
            return true;
        });
    }

    private void setupSurfaceView() {
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (mediaPlayer != null) {
                    mediaPlayer.setDisplay(holder);
                }
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mediaPlayer != null) {
                    mediaPlayer.setDisplay(null);
                }
            }
        });
    }

    private void setupProgressHandler() {
        progressHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPlaying && listener != null) {
                    int currentPosition = mediaPlayer.getCurrentPosition();
                    int duration = mediaPlayer.getDuration();
                    listener.onProgressUpdate(currentPosition, duration);
                }
                if (progressHandler != null) {
                    progressHandler.postDelayed(this, 500);
                }
            }
        };
    }

    public void loadVideo(String path) {
        if (path == null || path.isEmpty()) {
            listener.onError("视频路径无效");
            return;
        }
        currentVideoPath = path;
        isPrepared = false;
        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            listener.onError("加载视频失败：" + e.getMessage());
        }
    }

    public void start() {
        if (mediaPlayer != null && isPrepared && !isPlaying) {
            mediaPlayer.start();
            isPlaying = true;
            progressHandler.post(progressRunnable);
            if (previewImg != null) previewImg.setVisibility(View.GONE);
        }
    }

    public void pause() {
        if (mediaPlayer != null && isPlaying) {
            mediaPlayer.pause();
            isPlaying = false;
            progressHandler.removeCallbacks(progressRunnable);
        }
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.seekTo(msec);
        }
    }

    public int getCurrentPosition() {
        return (mediaPlayer != null && isPrepared) ? mediaPlayer.getCurrentPosition() : 0;
    }

    public int getDuration() {
        return (mediaPlayer != null && isPrepared) ? mediaPlayer.getDuration() : 0;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void release() {
        if (progressHandler != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        if (mediaPlayer != null) {
            if (isPlaying) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        isPrepared = false;
        isPlaying = false;
    }

    public boolean isPrepared() {
        return isPrepared;
    }
}
