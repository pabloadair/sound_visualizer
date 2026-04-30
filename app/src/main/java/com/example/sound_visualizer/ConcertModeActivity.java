package com.example.sound_visualizer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class ConcertModeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_concert_mode);

        Button btnHost = findViewById(R.id.btnHost);
        Button btnClient = findViewById(R.id.btnClient);
        Button btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        btnHost.setOnClickListener(v -> {
            Intent intent = new Intent(ConcertModeActivity.this, HostActivity.class);
            startActivity(intent);
        });

        btnClient.setOnClickListener(v -> {
            Intent intent = new Intent(ConcertModeActivity.this, ClientActivity.class);
            startActivity(intent);
        });
    }
}