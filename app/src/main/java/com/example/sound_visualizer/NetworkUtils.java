package com.example.sound_visualizer;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

/**
 * Clase de utilidad para operaciones de red, como obtener la dirección IP local.
 */
public class NetworkUtils {

    private static final String TAG = "NetworkUtils";

    /**
     * Obtiene la dirección IP local del dispositivo en la red Wi-Fi.
     * Requiere los permisos ACCESS_WIFI_STATE y ACCESS_NETWORK_STATE en AndroidManifest.xml.
     * @param context El contexto de la aplicación o actividad.
     * @return La dirección IP local como String, o null si no se puede obtener.
     */
    public static String getLocalIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) {
            Log.e(TAG, "WifiManager no disponible.");
            return null;
        }

        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convertir la dirección IP de int a String
        // La dirección IP se devuelve como un entero, donde los bytes están en orden de red (big-endian).
        // Si la arquitectura nativa es little-endian, necesitamos invertir los bytes.
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        try {
            // Convertir el array de bytes a una dirección IP legible
            return InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e(TAG, "No se pudo obtener la dirección IP del host.", ex);
            return null;
        } catch (IllegalArgumentException ex) {
            // Esto puede ocurrir si ipByteArray está vacío o mal formado
            Log.e(TAG, "Argumento inválido al convertir IP: " + ex.getMessage());
            return null;
        }
    }
}