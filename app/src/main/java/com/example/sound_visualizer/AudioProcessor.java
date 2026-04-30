package com.example.sound_visualizer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class AudioProcessor {
    private static final String TAG = "AudioProcessor";
    private static final int SAMPLE_RATE = 22050; // 22.05 kHz
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private int bufferSize;
    private volatile boolean isRecording = false; // Usar volatile para asegurar visibilidad entre hilos
    private AudioDataListener listener;

    // Interfaz para el callback
    public interface AudioDataListener {
        void onAudioData(double volume, double bass);
    }

    // Constructor que recibe el listener
    public AudioProcessor(AudioDataListener listener) {
        this.listener = listener;
        // Calcular el tamaño mínimo del buffer
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

        // Si getMinBufferSize devuelve un error, usa un tamaño de buffer predeterminado.
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "getMinBufferSize returned error. Using default buffer size.");
            bufferSize = SAMPLE_RATE / 2; // Ejemplo: 0.5 segundos de audio
        }
        // Asegúrate de que el buffer sea un múltiplo de 2 (para PCM_16BIT)
        bufferSize = (bufferSize / 2) * 2; // Esto asegura que sea par para short[]
        Log.d(TAG, "Initialized with SAMPLE_RATE: " + SAMPLE_RATE + ", Buffer Size: " + bufferSize + " bytes (" + (bufferSize/2) + " samples)"); // <--- LOG DE DEPURACIÓN
    }

    public void startRecording() {
        if (isRecording) { // Ya está grabando, no hacer nada
            Log.d(TAG, "Recording already in progress. No action taken."); // <--- LOG DE DEPURACIÓN
            return;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize);

            // Verificar si la inicialización fue exitosa
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed. State: " + audioRecord.getState()); // <--- LOG DE ERROR
                return; // No se puede iniciar la grabación
            }

            isRecording = true;
            audioRecord.startRecording();
            Log.d(TAG, "Audio recording started successfully."); // <--- LOG DE DEPURACIÓN

            new Thread(() -> {
                short[] audioBuffer = new short[bufferSize / 2]; // Tamaño en shorts, no bytes
                while (isRecording) {
                    int samplesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);

                    if (samplesRead > 0) {
                        processAudioData(audioBuffer, samplesRead);
                    } else if (samplesRead == AudioRecord.ERROR_INVALID_OPERATION || samplesRead == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Error reading audio data in recording thread: " + samplesRead); // <--- LOG DE ERROR
                        stopRecording(); // Detener la grabación en caso de error de lectura
                    }
                }
                // Limpiar recursos cuando el hilo termina
                if (audioRecord != null) {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                    audioRecord.release();
                    audioRecord = null; // Liberar referencia
                    Log.d(TAG, "AudioRecord resources released by recording thread."); // <--- LOG DE DEPURACIÓN
                }

            }, "AudioRecordingThread").start(); // Nombrar el hilo para depuración
        } catch (SecurityException e) {
            Log.e(TAG, "RECORD_AUDIO permission not granted. Cannot start recording: " + e.getMessage()); // <--- LOG DE ERROR
            isRecording = false;
        } catch (Exception e) {
            Log.e(TAG, "Unhandled error starting AudioRecord: " + e.getMessage()); // <--- LOG DE ERROR
            isRecording = false;
        }
    }

    public void stopRecording() {
        if (!isRecording) { // No está grabando, no hacer nada
            Log.d(TAG, "Recording is not active. No need to stop."); // <--- LOG DE DEPURACIÓN
            return;
        }

        isRecording = false; // Establecer a falso para detener el bucle del hilo
        Log.d(TAG, "Stop signal sent to recording thread."); // <--- LOG DE DEPURACIÓN
        // El hilo de grabación se encargará de detener y liberar AudioRecord
    }

    public void release() {
        stopRecording(); // Primero, asegura que el hilo se detenga y libere el AudioRecord
        // No es necesario llamar a stop/release en audioRecord aquí, ya que el hilo de grabación lo hace.
        // Si el hilo aún no ha terminado, esta llamada podría ser redundante o causar IllegalStateException.
        // Se confía en que el hilo de grabación limpiará los recursos al finalizar su bucle.
        Log.d(TAG, "AudioProcessor release method called. Recording should stop and resources free up."); // <--- LOG DE DEPURACIÓN
    }

    // En el método processAudioData, modifica la normalización:
    private void processAudioData(short[] audioData, int samplesRead) {
        // Calculate RMS for volume level
        double sum = 0;
        for (int i = 0; i < samplesRead; i++) {
            sum += audioData[i] * audioData[i];
        }
        double rms = Math.sqrt(sum / samplesRead);

        // Bass detection with simple low-pass filter
        double bassSum = 0;
        int bassSamples = 0;
        for (int i = 4; i < samplesRead; i++) {
            // Difference between samples to emphasize lower frequencies
            double diff = audioData[i] - audioData[i-4];
            bassSum += diff * diff;
            bassSamples++;
        }
        double bassRms = bassSamples > 0 ? Math.sqrt(bassSum / bassSamples) : 0;

        // Dynamic normalization based on recent levels
        double normalizedVolume = Math.min(1.0, rms / 10000.0); // Ajusta este valor según sensibilidad
        double normalizedBass = Math.min(1.0, bassRms / 8000.0); // Ajusta para bajos

        // Apply logarithmic scale for better visual response
        normalizedVolume = Math.log1p(normalizedVolume * 10) / Math.log1p(10);
        normalizedBass = Math.log1p(normalizedBass * 10) / Math.log1p(10);

        Log.d(TAG, "Processed Audio Data: Volume=" + String.format("%.4f", normalizedVolume) + ", Bass=" + String.format("%.4f", normalizedBass) + ", Samples Read=" + samplesRead); // <--- LOG DE DEPURACIÓN CRÍTICO

        // Notify listener
        if (listener != null) {
            listener.onAudioData(normalizedVolume, normalizedBass);
        } else {
            Log.w(TAG, "AudioDataListener is null. Cannot send processed data."); // <--- LOG DE ADVERTENCIA
        }
    }

    public boolean isRecording() {
        return isRecording;
    }
}