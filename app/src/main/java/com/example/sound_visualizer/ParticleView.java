package com.example.sound_visualizer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

public class ParticleView extends View {
    private Paint paint;
    private Random random;
    private Particle[] particles;
    private int particleCount = 150; // Cantidad fija de partículas
    private float centerX, centerY;
    private float baseRadius = 50f; // Radio base del círculo central más pequeño
    private float currentVolume = 0;
    private float currentBass = 0;

    // Clase interna para representar una partícula
    private class Particle {
        float angle;
        float distance;
        float speed; // Velocidad de expansión de la partícula
        float size; // Tamaño base de la partícula
        int color;
        float currentDisplaySize; // Tamaño actual de la partícula para el dibujo
    }

    public ParticleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint = new Paint();
        random = new Random();
        particles = new Particle[particleCount];

        for (int i = 0; i < particleCount; i++) {
            particles[i] = new Particle();
            resetParticle(i);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        // Al cambiar el tamaño, resetea las partículas para que estén en el nuevo centro
        for (int i = 0; i < particleCount; i++) {
            resetParticle(i);
        }
    }

    /**
     * Reinicia una partícula a su estado inicial en el centro.
     * @param index Índice de la partícula a reiniciar.
     */
    private void resetParticle(int index) {
        Particle p = particles[index];
        p.angle = random.nextFloat() * 360f; // Ángulo aleatorio para la dirección
        p.distance = baseRadius + random.nextFloat() * 20f; // Empieza cerca del centro
        p.speed = 1.0f + random.nextFloat() * 2.0f; // Velocidad base de movimiento hacia afuera
        p.size = 3f + random.nextFloat() * 5f; // Tamaño base de la partícula
        p.color = Color.rgb(
                150 + random.nextInt(106), // Colores más vibrantes, menos oscuros
                150 + random.nextInt(106),
                150 + random.nextInt(106));
        p.currentDisplaySize = p.size; // Inicializa el tamaño de visualización
    }

    /**
     * Actualiza los valores de volumen y bajos. Se espera que los valores estén normalizados (0.0 a 1.0).
     * @param volume Nivel de volumen.
     * @param bass Nivel de bajos.
     */
    public void updateVisuals(float volume, float bass) { // Ya acepta float, ¡bien!
        // Aplicar factores de amplificación para una mejor reacción visual
        this.currentVolume = Math.min(1.0f, volume * 2.5f); // Volumen más sensible, clamped a 1.0
        this.currentBass = Math.min(1.0f, bass * 4.0f);     // Bajos más sensibles, clamped a 1.0
        invalidate(); // Solicitar que la vista se redibuje
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (centerX == 0 || centerY == 0) {
            // Si la vista aún no tiene dimensiones, no dibujar para evitar errores.
            return;
        }

        // Dibujar círculo central tipo "speaker"
        // El radio del círculo central reacciona al volumen
        float dynamicBaseRadius = baseRadius * (1f + currentVolume * 0.8f); // El círculo se expande con el volumen
        paint.setColor(Color.argb(50, 255, 255, 255)); // Círculo blanco semitransparente
        canvas.drawCircle(centerX, centerY, dynamicBaseRadius, paint);

        // Actualizar y dibujar partículas
        for (int i = 0; i < particleCount; i++) {
            Particle p = particles[i];

            // Mover partícula hacia afuera desde el centro. La velocidad de movimiento es influenciada por el volumen.
            p.distance += p.speed * (1 + currentVolume * 2f); // Aumenta la velocidad de alejamiento con el volumen

            // Aumentar tamaño de la partícula con los bajos
            p.currentDisplaySize = p.size * (1 + currentBass * 2f); // El tamaño de la partícula reacciona a los bajos

            // Comprobar si la partícula se ha alejado demasiado y reiniciarla
            // Reinicia si está fuera de un radio más grande que el de la visualización
            float maxParticleDistance = Math.min(getWidth(), getHeight()) / 2f * 0.9f; // Límite dentro de la vista
            if (p.distance > maxParticleDistance) {
                resetParticle(i); // Reinicia la partícula si se va muy lejos
            }

            // Calcular posición cartesiana a partir de polar
            float x = centerX + (float) (p.distance * Math.cos(Math.toRadians(p.angle)));
            float y = centerY + (float) (p.distance * Math.sin(Math.toRadians(p.angle)));

            // Dibujar partícula
            paint.setColor(p.color); // Color vibrante de la partícula
            canvas.drawCircle(x, y, p.currentDisplaySize, paint);

            // Opcional: Rotar un poco el ángulo inicial de cada partícula para un efecto de espiral
            p.angle += 0.5f * (1 + currentBass); // Puede rotar más rápido con los bajos
        }

        // Solicita el redibujo continuamente para la animación
        invalidate();
    }
}