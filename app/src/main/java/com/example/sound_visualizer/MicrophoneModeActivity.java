package com.example.sound_visualizer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MicrophoneModeActivity extends AppCompatActivity {
    private AudioRecord audioRecord;
    private volatile boolean isRecording = false; // Usar volatile
    private AudioProcessor audioProcessor; // Instancia de AudioProcessor
    private static final int SAMPLE_RATE = 22050; // Tasa de muestreo de audio
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT);

    private ParticleView particleView;
    private static final int RECORD_AUDIO_PERMISSION_CODE = 101; // Código para identificar la solicitud de permiso

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_microphone_mode); // Nombre correcto del XML

        // Mantener la pantalla encendida
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Corregido: Usar R.id.particleView según tu XML proporcionado
        particleView = findViewById(R.id.particleView);

        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        // Inicializar AudioProcessor con el listener
        audioProcessor = new AudioProcessor(new AudioProcessor.AudioDataListener() {
            @Override
            public void onAudioData(double volume, double bass) {
                // Asegúrate de que las actualizaciones de UI se hagan en el hilo principal
                runOnUiThread(() -> {
                    if (particleView != null) {
                        // ¡CORREGIDO! Castear a float antes de pasar, ya que ParticleView.updateVisuals espera float
                        particleView.updateVisuals((float) volume, (float) bass);
                    }
                });
            }
        });

        // 1. Verificar y solicitar el permiso RECORD_AUDIO
        checkAudioPermission();
    }

    private void checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            // El permiso no ha sido concedido, solicitarlo al usuario
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_CODE);
        } else {
            // El permiso ya fue concedido, iniciar el procesamiento de audio
            startAudioProcessing();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permiso concedido por el usuario
                Toast.makeText(this, "Permiso de grabación de audio concedido.", Toast.LENGTH_SHORT).show();
                startAudioProcessing();
            } else {
                // Permiso denegado por el usuario
                Toast.makeText(this, "Permiso de grabación de audio denegado. La visualización de música no estará disponible.", Toast.LENGTH_LONG).show();
                finish(); // Cierra la actividad si la función principal depende de esto
            }
        }
    }

    private void startAudioProcessing() {
        if (audioProcessor != null) {
            audioProcessor.startRecording();
        } else {
            Log.e("MicrophoneModeActivity", "AudioProcessor no inicializado.");
            Toast.makeText(this, "Error interno: AudioProcessor no está listo.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cuando la actividad vuelve al primer plano, verifica los permisos y si es necesario, inicia la grabación.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Solo si el audioProcessor no está ya grabando
            if (audioProcessor != null && !audioProcessor.isRecording()) {
                audioProcessor.startRecording();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Cuando la actividad no está visible, detén la grabación para liberar recursos y batería
        if (audioProcessor != null && audioProcessor.isRecording()) {
            audioProcessor.stopRecording();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Última oportunidad para liberar recursos
        if (audioProcessor != null) {
            audioProcessor.release(); // Llama al método release en AudioProcessor
        }
        // Restaurar el comportamiento normal de la pantalla al salir
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
}