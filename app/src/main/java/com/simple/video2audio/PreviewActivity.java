package com.simple.video2audio;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class PreviewActivity extends AppCompatActivity {
    private VideoView videoView;
    private SeekBar seekBar;
    private int stopPosition;
    private Handler handler = new Handler();
    private Runnable runnable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("视频预览");
        }
        videoView = findViewById(R.id.videoView);
        seekBar = findViewById(R.id.seekBar);
        TextView tv = findViewById(R.id.tvInstruction);
        String path = getIntent().getStringExtra("filepath");
        tv.setText("已保存：" + path);
        videoView.setVideoURI(Uri.parse(path));
        videoView.start();
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            seekBar.setMax(videoView.getDuration());
            handler.postDelayed(runnable = () -> {
                if (videoView != null && videoView.isPlaying()) {
                    seekBar.setProgress(videoView.getCurrentPosition());
                    handler.postDelayed(runnable, 1000);
                }
            }, 1000);
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) videoView.seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
        if (videoView != null) {
            stopPosition = videoView.getCurrentPosition();
            videoView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null && stopPosition > 0) {
            videoView.seekTo(stopPosition);
            videoView.start();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) finish();
        return super.onOptionsItemSelected(item);
    }
}
