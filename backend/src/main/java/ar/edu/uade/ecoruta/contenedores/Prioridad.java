package ar.edu.uade.ecoruta.contenedores;

import java.time.Duration;
import java.time.Instant;

import ar.edu.uade.ecoruta.comun.Parametros;

/**
 * Decide si un contenedor hay que ir a buscarlo y por qué.
 * Es la misma regla que src/logic/prioridad.ts del front.
 */
public final class Prioridad {

    public static final String MOTIVO_LLENADO = "llenado";
    public static final String MOTIVO_RIESGO = "riesgo";
    public static final String MOTIVO_REPORTE = "reporte";

    private Prioridad() {
    }

    /**
     * Resultado para un contenedor.
     *
     * @param lectura nivel con el que se decide (ver {@link #lecturaConfiable})
     * @param motivo  por qué entra en una ruta, o null si no hace falta ir
     * @param nivel   bajo, medio, prioritario o desborde: el color del mapa
     */
    public record Evaluacion(double lectura, String motivo, String nivel) {

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
     * 2. Riesgo de desborde: se proyecta que pasa el 95% en las próximas horas.
     * 3. Tiene un reporte vecinal abierto.
     */
    public static String motivo(double lectura, double velocidad, Instant ahora, long reportesVecinales) {
        if (lectura >= Parametros.UMBRAL_PRIORITARIO) {
            return MOTIVO_LLENADO;
        }
        Instant horizonte = ahora.plus(Duration.ofHours(Parametros.HORAS_DE_PROYECCION));
        if (ModeloDeLlenado.proyectarEntre(lectura, velocidad, ahora, horizonte) >= Parametros.UMBRAL_RIESGO_PROYECTADO) {
            return MOTIVO_RIESGO;
        }
        return reportesVecinales > 0 ? MOTIVO_REPORTE : null;
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

    /** Un contenedor que no llegó al 80% pero corre riesgo de desbordar también se pinta como prioritario. */
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
            double velocidad, Instant ahora, long reportesVecinales) {
        double lectura = lecturaConfiable(llenado, nivelEstimado, anomalia, promedioUltimasTres);
        String motivo = motivo(lectura, velocidad, ahora, reportesVecinales);
        return new Evaluacion(lectura, motivo, nivelVisual(lectura, motivo));
    }
}
