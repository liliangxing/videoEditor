package com.simple.video2audio;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AudioPreviewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_preview);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("音频预览");
        }
        TextView tv = findViewById(R.id.tvInstruction);
        String path = getIntent().getStringExtra("filepath");
        tv.setText("已保存：" + path);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
