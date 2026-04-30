package com.example.sound_visualizer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HostActivity extends AppCompatActivity {
    private AudioProcessor audioProcessor;
    private ParticleView particleView;
    private TextView tvIpAddress;
    private ServerSocket serverSocket;
    private volatile boolean isServerRunning = false;
    private ExecutorService serverExecutor;
    private List<Socket> connectedClients = Collections.synchronizedList(new ArrayList<>());

    private static final int RECORD_AUDIO_PERMISSION_CODE = 102;
    private static final int SERVER_PORT = 12345;
    private static final String TAG = "HostActivity";

    private String currentIpAddress; // Para almacenar la IP local

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        particleView = findViewById(R.id.particleView);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        Button btnBack = findViewById(R.id.btnBack);
        Button btnStartStop = findViewById(R.id.btnStartStop);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> onBackPressed());
        }

        if (btnStartStop != null) {
            btnStartStop.setOnClickListener(v -> toggleServerAndRecording());
        }

        audioProcessor = new AudioProcessor(new AudioProcessor.AudioDataListener() {
            @Override
            public void onAudioData(double volume, double bass) {
                runOnUiThread(() -> {
                    if (particleView != null) {
                        particleView.updateVisuals((float) volume, (float) bass);
                    }
                });

                if (isServerRunning) {
                    if (!connectedClients.isEmpty()) {
                        Log.d(TAG, "onAudioData: Intentando enviar datos de audio a " + connectedClients.size() + " clientes.");
                        serverExecutor.execute(() -> {
                            sendAudioDataToClients(volume, bass);
                        });
                    } else {
                        Log.d(TAG, "onAudioData: No hay clientes conectados, no se envían datos.");
                    }
                } else {
                    Log.d(TAG, "onAudioData: Servidor no está corriendo, no se envían datos.");
                }
            }
        });

        // Obtener y mostrar dirección IP al inicio
        currentIpAddress = getLocalIpAddress(); // Almacenar la IP
        updateIpAndStatusDisplay(); // Mostrar la IP y el estado inicial

        // Inicializar el pool de hilos
        serverExecutor = Executors.newCachedThreadPool();
    }

    private void updateIpAndStatusDisplay() {
        if (currentIpAddress == null || currentIpAddress.isEmpty() || "0.0.0.0".equals(currentIpAddress) || "N/A".equals(currentIpAddress)) {
            tvIpAddress.setText(R.string.host_ip_not_available);
            Toast.makeText(this, "Error: No se pudo obtener la IP local. Conéctate a Wi-Fi.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "No se pudo obtener la IP local.");
        } else {
            String statusText;
            if (isServerRunning) {
                if (connectedClients.isEmpty()) {
                    statusText = getString(R.string.host_ip_address, currentIpAddress, SERVER_PORT) + "\n" + getString(R.string.host_running_waiting_clients);
                } else {
                    statusText = getString(R.string.host_ip_address_clients, currentIpAddress, SERVER_PORT, connectedClients.size());
                }
            } else {
                statusText = getString(R.string.host_ip_address, currentIpAddress, SERVER_PORT) + "\n" + getString(R.string.press_start_host);
            }
            tvIpAddress.setText(statusText);
            Log.d(TAG, "IP local del Host: " + currentIpAddress + ":" + SERVER_PORT);
        }
    }

    private void toggleServerAndRecording() {
        if (isServerRunning) {
            stopServerAndRecording();
        } else {
            checkAudioPermissionAndStart();
        }
    }

    private void checkAudioPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Solicitando permiso RECORD_AUDIO.");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_CODE);
        } else {
            Log.d(TAG, "Permiso RECORD_AUDIO ya concedido.");
            startServerAndRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso de grabación de audio concedido.", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Permiso RECORD_AUDIO concedido por el usuario.");
                startServerAndRecording();
            } else {
                Toast.makeText(this, "Permiso de grabación de audio denegado. La visualización y streaming no estarán disponibles.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Permiso RECORD_AUDIO denegado por el usuario.");
                updateIpAndStatusDisplay(); // Actualizar el estado si el permiso es denegado
            }
        }
    }

    private void startServerAndRecording() {
        if (isServerRunning) {
            Log.d(TAG, "Servidor ya está corriendo. No se hace nada.");
            return;
        }

        isServerRunning = true;
        Log.d(TAG, "Iniciando procesamiento de audio (desde startServerAndRecording).");
        audioProcessor.startRecording();

        serverExecutor.execute(this::runServerLoop);

        runOnUiThread(() -> {
            ((Button)findViewById(R.id.btnStartStop)).setText(R.string.stop_host);
            updateIpAndStatusDisplay(); // Actualizar estado a "corriendo, esperando clientes"
        });
        Log.d(TAG, "Host iniciado completamente.");
    }

    private void runServerLoop() {
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            Log.d(TAG, "ServerSocket creado en puerto " + SERVER_PORT + ". Esperando conexiones...");

            while (isServerRunning && !serverSocket.isClosed()) {
                Log.d(TAG, "Bucle del servidor: Esperando próxima conexión de cliente...");
                Socket client = serverSocket.accept();
                Log.d(TAG, "Nuevo cliente conectado desde: " + client.getInetAddress().getHostAddress());

                serverExecutor.execute(() -> handleClient(client));
            }
        } catch (IOException e) {
            if (isServerRunning && (serverSocket == null || !serverSocket.isClosed())) {
                Log.e(TAG, "Error en el bucle del servidor (IOException): " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(HostActivity.this,
                        "Error grave en el servidor: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
            } else {
                Log.d(TAG, "ServerSocket se cerró. Deteniendo el bucle del servidor.");
            }
        } finally {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                    Log.d(TAG, "ServerSocket cerrado en finally de runServerLoop.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error cerrando serverSocket en finally: " + e.getMessage());
            }
            isServerRunning = false;
            runOnUiThread(() -> {
                ((Button)findViewById(R.id.btnStartStop)).setText(R.string.start_host);
                updateIpAndStatusDisplay(); // Actualizar estado a "detenido"
            });
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            connectedClients.add(clientSocket);
            Log.d(TAG, "Cliente " + clientSocket.getInetAddress().getHostAddress() + " añadido a la lista. Total clientes: " + connectedClients.size());
            runOnUiThread(() -> {
                updateIpAndStatusDisplay(); // Actualizar número de clientes
                Toast.makeText(HostActivity.this, "Cliente conectado: " + clientSocket.getInetAddress().getHostAddress(), Toast.LENGTH_SHORT).show();
            });

            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String line;
            while (isServerRunning && (line = reader.readLine()) != null) {
                // No se espera que el cliente envíe datos, esto es solo para mantener la conexión
            }
            Log.d(TAG, "Cliente " + clientSocket.getInetAddress().getHostAddress() + " ha cerrado su stream de entrada (readLine() retornó null o servidor detenido).");
        } catch (IOException e) {
            Log.w(TAG, "Error de lectura o cliente desconectado inesperadamente: " + clientSocket.getInetAddress().getHostAddress() + " - " + e.getMessage());
        } finally {
            // Asegurarse de que el cliente sea removido *solo una vez*
            // y que el socket se cierre.
            if (connectedClients.remove(clientSocket)) {
                Log.d(TAG, "Cliente " + clientSocket.getInetAddress().getHostAddress() + " desconectado y removido. Clientes restantes: " + connectedClients.size());
                runOnUiThread(() -> {
                    updateIpAndStatusDisplay(); // Actualizar número de clientes
                    Toast.makeText(HostActivity.this, "Cliente desconectado: " + clientSocket.getInetAddress().getHostAddress(), Toast.LENGTH_SHORT).show();
                });
            } else {
                Log.d(TAG, "Cliente " + clientSocket.getInetAddress().getHostAddress() + " ya estaba removido o no encontrado.");
            }

            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                    Log.d(TAG, "Socket de cliente " + clientSocket.getInetAddress().getHostAddress() + " cerrado en finally.");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error al cerrar socket del cliente en finally: " + e.getMessage());
            }
        }
    }

    private void sendAudioDataToClients(double volume, double bass) {
        String data = String.format(Locale.US, "%.4f,%.4f\n", volume, bass);
        byte[] dataBytes = data.getBytes();

        List<Socket> clientsToSendTo;
        synchronized (connectedClients) {
            clientsToSendTo = new ArrayList<>(connectedClients);
        }

        if (clientsToSendTo.isEmpty()) {
            return;
        }

        for (Socket client : clientsToSendTo) {
            if (client.isConnected() && !client.isClosed()) {
                try {
                    OutputStream os = client.getOutputStream();
                    os.write(dataBytes);
                    os.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Error al enviar datos al cliente " + client.getInetAddress().getHostAddress() + ": " + e.getMessage() + ". Posiblemente desconectado.");
                }
            } else {
                Log.d(TAG, "sendAudioDataToClients: Cliente " + (client.getInetAddress() != null ? client.getInetAddress().getHostAddress() : "desconocido") + " no está conectado o está cerrado. No se envían datos.");
            }
        }
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.isSiteLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(TAG, "Error al obtener IP: " + ex.getMessage());
        }
        return "N/A";
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Al regresar a la actividad, actualizar la IP si ha cambiado
        String newIp = getLocalIpAddress();
        if (!newIp.equals(currentIpAddress)) {
            currentIpAddress = newIp;
            updateIpAndStatusDisplay();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (audioProcessor != null && !audioProcessor.isRecording() && isServerRunning) {
                Log.d(TAG, "onResume: Reiniciando grabación de audio.");
                audioProcessor.startRecording();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (audioProcessor != null && audioProcessor.isRecording()) {
            Log.d(TAG, "onPause: Deteniendo grabación de audio.");
            audioProcessor.stopRecording();
        }
    }

    private void stopServerAndRecording() {
        Log.d(TAG, "stopServerAndRecording: Iniciando proceso de detención...");
        if (audioProcessor != null) {
            audioProcessor.stopRecording();
            audioProcessor.release();
            Log.d(TAG, "AudioProcessor detenido y liberado.");
        }

        isServerRunning = false;
        Log.d(TAG, "Bandera isServerRunning establecida a false.");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                Log.d(TAG, "ServerSocket cerrado.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error al cerrar ServerSocket en stopServerAndRecording: " + e.getMessage());
        }

        synchronized (connectedClients) {
            for (Socket client : connectedClients) {
                try {
                    if (client != null && !client.isClosed()) {
                        client.close();
                        Log.d(TAG, "Cerrando socket de cliente activo: " + client.getInetAddress().getHostAddress());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error al cerrar socket de cliente activo: " + e.getMessage());
                }
            }
            connectedClients.clear();
            Log.d(TAG, "Lista de clientes conectados limpiada.");
        }

        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            Log.d(TAG, "serverExecutor shutdownNow llamado en stopServerAndRecording.");
        }
        serverExecutor = Executors.newCachedThreadPool();

        runOnUiThread(() -> {
            ((Button)findViewById(R.id.btnStartStop)).setText(R.string.start_host);
            updateIpAndStatusDisplay(); // Actualizar estado a "detenido"
            Toast.makeText(HostActivity.this, "Host detenido.", Toast.LENGTH_SHORT).show();
        });
        Log.d(TAG, "stopServerAndRecording: Proceso de detención finalizado.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Iniciando liberación de recursos...");
        stopServerAndRecording();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Log.d(TAG, "onDestroy: Recursos liberados.");
    }
}