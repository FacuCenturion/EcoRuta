package ar.edu.uade.ecoruta.contenedores;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.Parametros;

/**
 * Cómo se llena un contenedor a lo largo del día. Se usa para pronosticar cuándo va a
 * desbordar y para estimar el nivel cuando el sensor falla.
 *
 * El ritmo de cada contenedor sale de su propio historial ({@link PatronesDeLlenado}).
 * Cuando todavía no hay datos suficientes se usa el ritmo fijo de esta clase, que es el
 * mismo modelo que src/logic/llenado.ts del front.
 */
public final class ModeloDeLlenado {

    /** Cuántos puntos de llenado por hora suma un contenedor en un momento dado. */
    @FunctionalInterface
    public interface Ritmo {
        double porHora(Instant momento);
    }

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

    /** Ritmo fijo: la velocidad del contenedor ajustada por la hora y el día. */
    public static double incrementoPorHora(double velocidad, Instant momento) {
        ZonedDateTime local = momento.atZone(Fechas.BUENOS_AIRES);
        int dia = local.getDayOfWeek().getValue() - 1;
        return velocidad * FACTOR_HORA[local.getHour()] * FACTOR_DIA[dia];
    }

    public static Ritmo ritmoFijo(double velocidad) {
        return momento -> incrementoPorHora(velocidad, momento);
    }

    /** Suma lo que se llenaría el contenedor entre dos momentos, hora por hora, con tope en 100. */
    public static double proyectarEntre(double nivel, Ritmo ritmo, Instant desde, Instant hasta) {
        double proyectado = nivel;
        Instant momento = desde;
        while (momento.isBefore(hasta)) {
            Duration resto = Duration.between(momento, hasta);
            Duration tramo = resto.compareTo(Duration.ofHours(1)) < 0 ? resto : Duration.ofHours(1);
            proyectado += ritmo.porHora(momento) * (tramo.toMillis() / 3_600_000.0);
            momento = momento.plus(tramo);
        }
        return Math.min(100, proyectado);
    }

    /**
     * Pronóstico: a qué hora el contenedor va a llegar al nivel de desborde si sigue
     * llenándose a su ritmo. Devuelve null si no pasa dentro de las próximas horasMax.
     */
    public static Instant momentoDeDesborde(double nivel, Ritmo ritmo, Instant desde, int horasMax) {
        if (nivel >= Parametros.UMBRAL_DESBORDE) {
            return desde;
        }
        double proyectado = nivel;
        Instant momento = desde;
        for (int hora = 0; hora < horasMax; hora++) {
            double incremento = ritmo.porHora(momento);
            if (incremento > 0 && proyectado + incremento >= Parametros.UMBRAL_DESBORDE) {
                // Dentro de esta hora: se reparte en proporción a lo que le falta
                double fraccion = (Parametros.UMBRAL_DESBORDE - proyectado) / incremento;
                return momento.plusSeconds(Math.round(fraccion * 3600));
            }
            proyectado += incremento;
            momento = momento.plus(Duration.ofHours(1));
        }
        return null;
    }
}
