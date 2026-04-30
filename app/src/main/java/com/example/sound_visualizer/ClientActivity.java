package com.example.sound_visualizer;

import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientActivity extends AppCompatActivity {
    private ParticleView particleView;
    private Socket clientSocket;
    private TextView tvConnectionStatus;
    private volatile boolean isReceivingData = false;
    private ExecutorService clientExecutor;

    private static final int DEFAULT_SERVER_PORT = 12345; // Puerto por defecto si no se especifica
    private static final String TAG = "ClientActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        particleView = findViewById(R.id.particleView);
        EditText etIpAddress = findViewById(R.id.etIpAddress);
        Button btnConnect = findViewById(R.id.btnConnect);
        Button btnBack = findViewById(R.id.btnBack);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> {
                String input = etIpAddress.getText().toString().trim();
                if (!input.isEmpty()) {
                    parseAndConnect(input);
                } else {
                    Toast.makeText(ClientActivity.this, "Por favor, ingresa una dirección IP.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        clientExecutor = Executors.newSingleThreadExecutor();
    }

    private void parseAndConnect(String input) {
        String ip;
        int port;

        // Intentar parsear IP:Puerto
        int colonIndex = input.lastIndexOf(':');
        if (colonIndex > 0 && colonIndex < input.length() - 1) { // Hay un ':' y no está al principio o al final
            ip = input.substring(0, colonIndex);
            try {
                port = Integer.parseInt(input.substring(colonIndex + 1));
                if (port <= 0 || port > 65535) {
                    Toast.makeText(this, "Puerto inválido. Debe ser entre 1 y 65535.", Toast.LENGTH_LONG).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Formato de puerto inválido. Usa IP o IP:Puerto", Toast.LENGTH_LONG).show();
                return;
            }
        } else {
            // Si no hay puerto especificado, usar el puerto por defecto
            ip = input;
            port = DEFAULT_SERVER_PORT;
        }

        // Validación básica de IP (puedes añadir una Regex más robusta si es necesario)
        if (ip.isEmpty() || !isValidIp(ip)) {
            Toast.makeText(this, "Dirección IP inválida.", Toast.LENGTH_LONG).show();
            return;
        }

        final String finalIp = ip;
        final int finalPort = port;

        tvConnectionStatus.setText(getString(R.string.connecting_to, finalIp + ":" + finalPort));
        connectToServer(finalIp, finalPort);
    }

    // Validación básica de IP (no exhaustiva, pero mejor que nada)
    private boolean isValidIp(String ip) {
        // Simple regex para IPv4 (no valida todos los rangos, solo el formato)
        String IP_REGEX = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
        return ip.matches(IP_REGEX);
    }

    private void connectToServer(String ip, int port) {
        stopReceivingData(); // Detener y cerrar cualquier conexión anterior

        clientExecutor.execute(() -> {
            try {
                Log.d(TAG, "Intentando conectar a " + ip + ":" + port + "...");
                clientSocket = new Socket(ip, port);
                isReceivingData = true;
                Log.d(TAG, "Conectado al servidor: " + ip + ":" + port);
                runOnUiThread(() -> {
                    tvConnectionStatus.setText(getString(R.string.connected_to, ip + ":" + port));
                    Toast.makeText(ClientActivity.this, "Conectado al Host: " + ip + ":" + port, Toast.LENGTH_SHORT).show();
                });

                receiveAudioData();

            } catch (IOException e) {
                Log.e(TAG, "Error al conectar al servidor " + ip + ":" + port + ": " + e.getMessage());
                runOnUiThread(() -> {
                    tvConnectionStatus.setText(R.string.connection_failed);
                    Toast.makeText(ClientActivity.this, "Error de conexión: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                stopReceivingData();
            }
        });
    }

    private void receiveAudioData() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String line;
            Log.d(TAG, "Iniciando bucle de lectura de datos...");
            while (isReceivingData && (line = reader.readLine()) != null) {
                Log.d(TAG, "Línea recibida: '" + line + "'");
                try {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        double volume = Double.parseDouble(parts[0]);
                        double bass = Double.parseDouble(parts[1]);
                        Log.d(TAG, "Datos parseados: Volume=" + String.format("%.2f", volume) + ", Bass=" + String.format("%.2f", bass));

                        final float finalVolume = (float) volume;
                        final float finalBass = (float) bass;
                        runOnUiThread(() -> {
                            if (particleView != null) {
                                particleView.updateVisuals(finalVolume, finalBass);
                            }
                        });
                    } else {
                        Log.w(TAG, "Formato de línea incorrecto (partes: " + parts.length + "): '" + line + "'");
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Error al parsear datos de audio: " + e.getMessage() + " para la línea: '" + line + "'");
                }
            }
            Log.d(TAG, "Bucle de lectura de datos finalizado. isReceivingData: " + isReceivingData + ", Socket cerrado: " + (clientSocket != null ? clientSocket.isClosed() : "null"));
        } catch (IOException e) {
            if (isReceivingData) {
                Log.e(TAG, "Error al recibir datos del servidor (IOException): " + e.getMessage());
                runOnUiThread(() -> {
                    tvConnectionStatus.setText(R.string.disconnected);
                    Toast.makeText(ClientActivity.this, "Desconectado del Host: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } else {
                Log.d(TAG, "Conexión cerrada intencionadamente.");
            }
        } finally {
            stopReceivingData();
        }
    }

    private void stopReceivingData() {
        isReceivingData = false;
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                clientSocket = null;
                Log.d(TAG, "Client socket closed.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error al cerrar clientSocket: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // No auto-reconectar aquí, la conexión es manual por el botón.
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopReceivingData();
        Log.d(TAG, "onPause: stopReceivingData llamado.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clientExecutor != null) {
            clientExecutor.shutdownNow();
            Log.d(TAG, "clientExecutor shutdownNow llamado.");
        }
        stopReceivingData();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d(TAG, "onDestroy: stopReceivingData llamado y FLAG_KEEP_SCREEN_ON limpiado.");
    }
}5489