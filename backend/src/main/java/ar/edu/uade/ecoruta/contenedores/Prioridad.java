package ar.edu.uade.ecoruta.contenedores;

import java.time.Duration;
import java.time.Instant;

import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.contenedores.ModeloDeLlenado.Ritmo;

/**
 * Decide si un contenedor hay que ir a buscarlo y por qué. Parte de la regla de
 * src/logic/prioridad.ts del front y le suma el pronóstico con el ritmo propio de
 * cada contenedor y el tiempo máximo sin recolectar.
 */
public final class Prioridad {

    public static final String MOTIVO_LLENADO = "llenado";
    public static final String MOTIVO_RIESGO = "riesgo";
    public static final String MOTIVO_REPORTE = "reporte";
    public static final String MOTIVO_TIEMPO = "tiempo";

    private Prioridad() {
    }

    /**
     * Resultado para un contenedor.
     *
     * @param lectura          nivel con el que se decide (ver {@link #lecturaConfiable})
     * @param motivo           por qué entra en una ruta, o null si no hace falta ir
     * @param nivel            bajo, medio, prioritario o desborde: el color del mapa
     * @param desbordeEstimado a qué hora se pronostica que desborda; null si no es en las próximas 48 h
     */
    public record Evaluacion(double lectura, String motivo, String nivel, Instant desbordeEstimado) {

        public boolean esPrioritario() {
            return motivo != null;
        }
    }

    /**
     * Nivel con el que se decide. Si el sensor no manda datos o quedó trabado se usa el
     * estimado. Si da lecturas irregulares no nos fiamos de la última: va el promedio
     * de las últimas tres.
     */
    public static double lecturaConfiable(int llenado, Integer nivelEstimado, boolean anomalia,
            Double promedioUltimasTres) {
        if (nivelEstimado != null) {
            return nivelEstimado;
        }
        if (anomalia && promedioUltimasTres != null) {
            return promedioUltimasTres;
        }
        return llenado;
    }

    /**
     * Reglas, en orden:
     * 1. Llenado mayor o igual al 80%.
     * 2. Riesgo: se pronostica que desborda en las próximas 3 horas, aunque hoy esté lejos del 80%.
     * 3. Tiene un reporte vecinal abierto.
     * 4. Tiempo: lleva más de 48 horas sin recolectarse y no está casi vacío.
     */
    public static String motivo(double lectura, Instant desbordeEstimado, Instant ahora, long reportesVecinales,
            Instant ultimaRecoleccion) {
        if (lectura >= Parametros.UMBRAL_PRIORITARIO) {
            return MOTIVO_LLENADO;
        }
        Instant horizonte = ahora.plus(Duration.ofHours(Parametros.HORAS_DE_PROYECCION));
        if (desbordeEstimado != null && !desbordeEstimado.isAfter(horizonte)) {
            return MOTIVO_RIESGO;
        }
        if (reportesVecinales > 0) {
            return MOTIVO_REPORTE;
        }
        boolean muchoTiempo = ultimaRecoleccion != null && Duration.between(ultimaRecoleccion, ahora)
                .compareTo(Duration.ofHours(Parametros.HORAS_MAX_SIN_RECOLECTAR)) > 0;
        return muchoTiempo && lectura >= Parametros.NIVEL_MINIMO_POR_TIEMPO ? MOTIVO_TIEMPO : null;
    }

    public static String clasificarNivel(double llenado) {
        if (llenado >= Parametros.UMBRAL_DESBORDE) {
            return "desborde";
        }
        if (llenado >= Parametros.UMBRAL_PRIORITARIO) {
            return "prioritario";
        }
        return llenado >= Parametros.UMBRAL_BAJO ? "medio" : "bajo";
    }

    /** Un contenedor que no llegó al 80% pero hay que ir a buscarlo también se pinta como prioritario. */
    public static String nivelVisual(double lectura, String motivo) {
        if (lectura >= Parametros.UMBRAL_DESBORDE) {
            return "desborde";
        }
        if (motivo != null) {
            return "prioritario";
        }
        return lectura >= Parametros.UMBRAL_BAJO ? "medio" : "bajo";
    }

    public static Evaluacion evaluar(int llenado, Integer nivelEstimado, boolean anomalia, Double promedioUltimasTres,
            Ritmo ritmo, Instant ahora, long reportesVecinales, Instant ultimaRecoleccion) {
        double lectura = lecturaConfiable(llenado, nivelEstimado, anomalia, promedioUltimasTres);
        Instant desborde = ModeloDeLlenado.momentoDeDesborde(lectura, ritmo, ahora,
                Parametros.HORAS_MAX_DE_PRONOSTICO);
        String motivo = motivo(lectura, desborde, ahora, reportesVecinales, ultimaRecoleccion);
        return new Evaluacion(lectura, motivo, nivelVisual(lectura, motivo), desborde);
    }
}
