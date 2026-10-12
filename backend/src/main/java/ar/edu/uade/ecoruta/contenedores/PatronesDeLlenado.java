package ar.edu.uade.ecoruta.contenedores;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.contenedores.ModeloDeLlenado.Ritmo;

/**
 * Aprende de las lecturas guardadas cómo se llena cada contenedor: su promedio por hora
 * del día y día de la semana. Así el pronóstico distingue, por ejemplo, un contenedor de
 * una zona de bares (se llena de noche) de uno de una zona de oficinas (se llena al mediodía).
 *
 * Para cada momento se usa el dato más específico que tenga observación suficiente:
 * 1. el promedio de ese contenedor en esa hora de ese día de la semana;
 * 2. si no, su promedio en esa hora sin importar el día;
 * 3. si no, el ritmo fijo de {@link ModeloDeLlenado}.
 */
@Component
public class PatronesDeLlenado {

    private static final Logger log = LoggerFactory.getLogger(PatronesDeLlenado.class);

    // Horas observadas que hacen falta para confiar en un promedio
    private static final double HORAS_MINIMAS_POR_DIA = 1.5; // unas dos semanas con ese día
    private static final double HORAS_MINIMAS_POR_HORA = 0.75; // casi un día completo
    private static final Duration CADA_CUANTO_RECALCULAR = Duration.ofHours(24);

    /** Lo aprendido de un contenedor: puntos sumados y horas observadas por día y hora. */
    private static final class Patron {
        private final double[][] delta = new double[7][24];
        private final double[][] horas = new double[7][24];

        Double ritmo(int dia, int hora) {
            if (horas[dia][hora] >= HORAS_MINIMAS_POR_DIA) {
                return delta[dia][hora] / horas[dia][hora];
            }
            double sumaDelta = 0;
            double sumaHoras = 0;
            for (int d = 0; d < 7; d++) {
                sumaDelta += delta[d][hora];
                sumaHoras += horas[d][hora];
            }
            return sumaHoras >= HORAS_MINIMAS_POR_HORA ? sumaDelta / sumaHoras : null;
        }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final EntityManager entidades;
    private volatile Map<String, Patron> porContenedor = Map.of();
    private volatile Instant ultimoCalculo;

    PatronesDeLlenado(NamedParameterJdbcTemplate jdbc, EntityManager entidades) {
        this.jdbc = jdbc;
        this.entidades = entidades;
        cargar();
    }

    /** El ritmo de un contenedor, con lo aprendido donde hay datos y el modelo fijo donde no. */
    public Ritmo ritmoDe(Contenedor contenedor) {
        Ritmo fijo = ModeloDeLlenado.ritmoFijo(contenedor.velocidad());
        Patron patron = porContenedor.get(contenedor.getId());
        if (patron == null) {
            return fijo;
        }
        return momento -> {
            ZonedDateTime local = momento.atZone(Fechas.BUENOS_AIRES);
            Double aprendido = patron.ritmo(local.getDayOfWeek().getValue() - 1, local.getHour());
            return aprendido != null ? aprendido : fijo.porHora(momento);
        };
    }

    /** Cuántos contenedores ya tienen un patrón propio. */
    public int cantidadConPatron() {
        return porContenedor.size();
    }

    /** Se llama cada vez que entran lecturas; recalcula como mucho una vez por día. */
    @Transactional
    public void recalcularSiHaceFalta(Instant ahora) {
        Instant ultimo = ultimoCalculo;
        if (ultimo == null || Duration.between(ultimo, ahora).compareTo(CADA_CUANTO_RECALCULAR) >= 0) {
            recalcular(ahora);
        }
    }

    /**
     * Rearma el patrón con las lecturas de las últimas dos semanas. Mira cuánto subió el
     * nivel entre cada lectura y la anterior, y descarta lo que no es llenado normal:
     * - una bajada (pasó el camión, o el sensor saltó);
     * - una subida imposible de tan rápida (el sensor saltó);
     * - un hueco de más de una hora sin lecturas;
     * - un contenedor que ya está lleno (no puede subir más).
     * Los sensores sin señal o congelados conservan el patrón que tenían: sus lecturas no sirven.
     */
    @Transactional
    public void recalcular(Instant ahora) {
        entidades.flush(); // que la consulta vea el estado de los sensores recién actualizado
        MapSqlParameterSource datos = new MapSqlParameterSource()
                .addValue("desde", Fechas.paraLaBase(ahora.minus(Duration.ofDays(Parametros.DIAS_DE_LECTURAS_PARA_EL_PATRON))))
                .addValue("zona", Fechas.BUENOS_AIRES.getId())
                .addValue("ritmoMaximo", Parametros.RITMO_MAXIMO_CREIBLE)
                .addValue("lleno", Parametros.NIVEL_LLENO);

        jdbc.update("""
                DELETE FROM patron_llenado
                WHERE contenedor_id IN (SELECT id FROM contenedor WHERE estado_sensor IN ('funcionando', 'irregular'))
                """, datos);
        int filas = jdbc.update("""
                INSERT INTO patron_llenado (contenedor_id, dia_semana, hora, suma_delta, suma_horas)
                SELECT contenedor_id, dia, hora, sum(delta), sum(horas)
                FROM (
                    SELECT l.contenedor_id,
                           extract(isodow FROM l.medido_at AT TIME ZONE :zona)::int - 1 AS dia,
                           extract(hour FROM l.medido_at AT TIME ZONE :zona)::int AS hora,
                           l.llenado - lag(l.llenado) OVER w AS delta,
                           extract(epoch FROM l.medido_at - lag(l.medido_at) OVER w) / 3600.0 AS horas,
                           l.llenado
                    FROM lectura_sensor l
                    JOIN contenedor c ON c.id = l.contenedor_id
                    WHERE l.medido_at >= :desde AND c.estado_sensor IN ('funcionando', 'irregular')
                    WINDOW w AS (PARTITION BY l.contenedor_id ORDER BY l.medido_at)
                ) tramos
                WHERE horas > 0 AND horas <= 1 AND delta >= 0 AND delta / horas <= :ritmoMaximo AND llenado < :lleno
                GROUP BY contenedor_id, dia, hora
                """, datos);
        cargar();
        ultimoCalculo = ahora;
        log.info("Patrón de llenado recalculado: {} contenedores, {} franjas horarias", porContenedor.size(), filas);
    }

    private void cargar() {
        Map<String, Patron> nuevos = new HashMap<>();
        jdbc.query("SELECT contenedor_id, dia_semana, hora, suma_delta, suma_horas FROM patron_llenado", fila -> {
            Patron patron = nuevos.computeIfAbsent(fila.getString("contenedor_id"), id -> new Patron());
            patron.delta[fila.getInt("dia_semana")][fila.getInt("hora")] = fila.getDouble("suma_delta");
            patron.horas[fila.getInt("dia_semana")][fila.getInt("hora")] = fila.getDouble("suma_horas");
        });
        porContenedor = nuevos;
    }
}
