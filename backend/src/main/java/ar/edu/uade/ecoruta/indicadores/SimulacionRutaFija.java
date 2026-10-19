package ar.edu.uade.ecoruta.indicadores;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.Parametros;

/**
 * Qué habría pasado con un contenedor si se usara la ruta fija: rondas a las 8, 14 y 20 que
 * vacían todos los contenedores, estén como estén. Se calcula con las mismas lecturas del día:
 * se toma cuánto subió el nivel entre lecturas (lo que se llenó, pase lo que pase con los
 * camiones) y se lo vuelve a acumular con las rondas fijas.
 *
 * Una limitación conocida: si en la realidad el contenedor estuvo lleno un rato, ahí no subió,
 * así que la ruta fija puede quedar un poco mejor de lo que sería. Los desbordes de la ruta fija
 * quedan, si acaso, por debajo de los reales.
 */
public final class SimulacionRutaFija {

    private SimulacionRutaFija() {
    }

    public record Lectura(Instant medidoAt, int llenado) {
    }

    /** Lo que pasó con un contenedor en un día bajo la ruta fija. */
    public record Resultado(int visitas, int visitasBajas, int desbordes) {
    }

    /**
     * @param lecturas del contenedor desde las 20 h del día anterior (la última ronda) hasta el final del día,
     *                 de la más vieja a la más nueva
     * @param inicioDelDia las 0 h del día que se resume
     */
    public static Resultado simular(List<Lectura> lecturas, Instant inicioDelDia) {
        double nivel = Parametros.NIVEL_TRAS_RECOLECCION; // la ronda de las 20 h del día anterior lo vació
        int visitas = 0;
        int bajas = 0;
        int desbordes = 0;
        boolean desbordado = false;
        int proximaRonda = 0;
        Instant finDelDia = inicioDelDia.plusSeconds(24 * 3600);

        for (int i = 1; i < lecturas.size(); i++) {
            Lectura anterior = lecturas.get(i - 1);
            Lectura actual = lecturas.get(i);
            // Antes de esta lectura, ¿pasó la ronda?
            while (proximaRonda < Parametros.RONDAS_RUTA_FIJA.length
                    && !actual.medidoAt().isBefore(ronda(inicioDelDia, proximaRonda))) {
                visitas++;
                if (nivel < Parametros.UMBRAL_BAJO) {
                    bajas++;
                }
                nivel = Parametros.NIVEL_TRAS_RECOLECCION;
                desbordado = false;
                proximaRonda++;
            }
            if (!actual.medidoAt().isBefore(finDelDia)) {
                break;
            }
            int delta = actual.llenado() - anterior.llenado();
            // Las bajadas grandes son recolecciones reales o saltos del sensor: no son llenado
            if (delta >= -2) {
                nivel = Math.min(100, nivel + delta);
            }
            if (!desbordado && nivel >= Parametros.NIVEL_LLENO && !actual.medidoAt().isBefore(inicioDelDia)) {
                desbordes++;
                desbordado = true;
            }
        }
        // Las rondas que quedaron después de la última lectura del día también pasan
        while (proximaRonda < Parametros.RONDAS_RUTA_FIJA.length) {
            visitas++;
            if (nivel < Parametros.UMBRAL_BAJO) {
                bajas++;
            }
            nivel = Parametros.NIVEL_TRAS_RECOLECCION;
            proximaRonda++;
        }
        return new Resultado(visitas, bajas, desbordes);
    }

    private static Instant ronda(Instant inicioDelDia, int indice) {
        ZonedDateTime dia = inicioDelDia.atZone(Fechas.BUENOS_AIRES);
        return dia.withHour(Parametros.RONDAS_RUTA_FIJA[indice]).toInstant();
    }
}
