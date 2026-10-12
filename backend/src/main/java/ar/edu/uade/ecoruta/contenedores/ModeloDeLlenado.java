package ar.edu.uade.ecoruta.contenedores;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

import ar.edu.uade.ecoruta.comun.Fechas;

/**
 * Cómo se llena un contenedor a lo largo del día. Se usa para proyectar el riesgo
 * de desborde y para estimar el nivel cuando el sensor falla.
 * Es el mismo modelo que src/logic/llenado.ts del front.
 */
public final class ModeloDeLlenado {

    // Cuánto se llena según la hora del día (1 = ritmo normal).
    // El pico está entre las 18 y las 21, cuando los vecinos sacan la basura.
    private static final double[] FACTOR_HORA = {
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, // 0 a 5
            0.6, 0.8, 1.0, 1.0, 1.0, 1.0, // 6 a 11
            1.1, 1.1, 1.1, 1.2, 1.2, 1.2, // 12 a 17
            2.0, 2.2, 2.0, 1.5, 0.8, 0.5, // 18 a 23
    };

    // Lunes = 0 ... domingo = 6. Los fines de semana se genera un poco más de basura.
    private static final double[] FACTOR_DIA = { 1, 1, 1, 1, 1.1, 1.2, 1.1 };

    private ModeloDeLlenado() {
    }

    /** Cuántos puntos de llenado suma un contenedor durante una hora a ese momento del día. */
    public static double incrementoPorHora(double velocidad, Instant momento) {
        ZonedDateTime local = momento.atZone(Fechas.BUENOS_AIRES);
        int dia = local.getDayOfWeek().getValue() - 1;
        return velocidad * FACTOR_HORA[local.getHour()] * FACTOR_DIA[dia];
    }

    /** Suma lo que se llenaría el contenedor entre dos momentos, hora por hora, con tope en 100. */
    public static double proyectarEntre(double nivel, double velocidad, Instant desde, Instant hasta) {
        double proyectado = nivel;
        Instant momento = desde;
        while (momento.isBefore(hasta)) {
            Duration resto = Duration.between(momento, hasta);
            Duration tramo = resto.compareTo(Duration.ofHours(1)) < 0 ? resto : Duration.ofHours(1);
            proyectado += incrementoPorHora(velocidad, momento) * (tramo.toMillis() / 3_600_000.0);
            momento = momento.plus(tramo);
        }
        return Math.min(100, proyectado);
    }
}
