package ar.edu.uade.ecoruta.sensores;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.Parametros;

/**
 * Acceso a lectura_sensor, la tabla que más crece. Usa SQL directo porque casi todas
 * las consultas son sobre muchos contenedores a la vez.
 */
@Repository
public class RepositorioDeLecturas {

    public record Lectura(Instant medidoAt, int llenado) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    RepositorioDeLecturas(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(List<LecturaNueva> lecturas) {
        MapSqlParameterSource[] filas = lecturas.stream()
                .map(l -> new MapSqlParameterSource()
                        .addValue("contenedor", l.contenedorId())
                        .addValue("llenado", l.llenado())
                        .addValue("medido", Fechas.paraLaBase(l.medidoAt())))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(
                "INSERT INTO lectura_sensor (contenedor_id, llenado, medido_at) VALUES (:contenedor, :llenado, :medido)",
                filas);
    }

    public boolean hayLecturas() {
        return Boolean.TRUE.equals(jdbc.getJdbcTemplate()
                .queryForObject("SELECT EXISTS (SELECT 1 FROM lectura_sensor)", Boolean.class));
    }

    /** Las últimas lecturas de cada contenedor, de la más vieja a la más nueva. */
    public Map<String, List<Integer>> ultimas(Collection<String> contenedorIds, int cuantas) {
        Map<String, List<Integer>> resultado = new HashMap<>();
        if (contenedorIds.isEmpty()) {
            return resultado;
        }
        jdbc.query("""
                SELECT c.id, l.llenado
                FROM contenedor c
                CROSS JOIN LATERAL (
                    SELECT llenado, medido_at FROM lectura_sensor
                    WHERE contenedor_id = c.id ORDER BY medido_at DESC LIMIT :cuantas
                ) l
                WHERE c.id IN (:ids)
                ORDER BY c.id, l.medido_at
                """,
                new MapSqlParameterSource().addValue("ids", contenedorIds).addValue("cuantas", cuantas),
                fila -> {
                    resultado.computeIfAbsent(fila.getString("id"), id -> new ArrayList<>()).add(fila.getInt("llenado"));
                });
        return resultado;
    }

    /**
     * Contenedores cuyo sensor marca exactamente lo mismo desde ese momento. Hace falta tener
     * datos de toda la ventana para poder decirlo, y no cuenta si el contenedor está lleno:
     * ahí es normal que no cambie.
     */
    public Set<String> congeladosDesde(Instant desde) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT v.contenedor_id
                FROM (
                    SELECT contenedor_id, count(*) AS cantidad, count(DISTINCT llenado) AS distintos, min(llenado) AS nivel
                    FROM lectura_sensor WHERE medido_at >= :desde GROUP BY contenedor_id
                ) v
                WHERE v.cantidad >= 2 AND v.distintos = 1 AND v.nivel < :lleno
                  AND EXISTS (SELECT 1 FROM lectura_sensor a
                              WHERE a.contenedor_id = v.contenedor_id AND a.medido_at <= :desde)
                """,
                new MapSqlParameterSource().addValue("desde", Fechas.paraLaBase(desde))
                        .addValue("lleno", Parametros.NIVEL_LLENO),
                String.class));
    }

    /** Desde cuándo el sensor repite el valor actual: ahí dejó de medir. */
    public Instant inicioDeLaRacha(String contenedorId, int valorActual) {
        OffsetDateTime inicio = jdbc.queryForObject("""
                SELECT min(medido_at) FROM lectura_sensor
                WHERE contenedor_id = :id
                  AND medido_at > COALESCE((SELECT max(medido_at) FROM lectura_sensor
                                            WHERE contenedor_id = :id AND llenado <> :valor), '-infinity')
                """,
                new MapSqlParameterSource().addValue("id", contenedorId).addValue("valor", valorActual),
                OffsetDateTime.class);
        return inicio == null ? null : inicio.toInstant();
    }

    public List<Lectura> historial(String contenedorId, Instant desde) {
        return jdbc.query(
                "SELECT medido_at, llenado FROM lectura_sensor WHERE contenedor_id = :id AND medido_at >= :desde ORDER BY medido_at",
                new MapSqlParameterSource().addValue("id", contenedorId).addValue("desde", Fechas.paraLaBase(desde)),
                (fila, n) -> new Lectura(fila.getObject("medido_at", OffsetDateTime.class).toInstant(),
                        fila.getInt("llenado")));
    }

    public int borrarAnterioresA(Instant limite) {
        return jdbc.update("DELETE FROM lectura_sensor WHERE medido_at < :limite",
                new MapSqlParameterSource("limite", Fechas.paraLaBase(limite)));
    }
}
