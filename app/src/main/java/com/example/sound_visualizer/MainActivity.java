package com.example.sound_visualizer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnMicrophoneMode = findViewById(R.id.btnMicrophoneMode);
        Button btnConcertMode = findViewById(R.id.btnConcertMode);

        btnMicrophoneMode.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, MicrophoneModeActivity.class);
            startActivity(intent);
        });

        btnConcertMode.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ConcertModeActivity.class);
            startActivity(intent);
        });
    }
}