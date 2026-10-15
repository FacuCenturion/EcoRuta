package ar.edu.uade.ecoruta.panel;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;
import ar.edu.uade.ecoruta.contenedores.ServicioDePrioridad;

/** Los números del día que muestra el Panel general. "Hoy" es el día de la hora del sistema. */
@RestController
@RequestMapping("/api/v1/panel")
public class PanelControlador {

    /**
     * @param kmEvitadosHoy km que se ahorraron frente a visitar todos los contenedores en cada salida
     * @param sensores      cuántos sensores hay en cada estado
     */
    public record Resumen(OffsetDateTime reloj, long recolectadosHoy, long planificadosHoy, double kmEvitadosHoy,
            double kmRecorridosHoy, long rutasHoy, long prioritarios, long incidentesAbiertos,
            Map<String, Long> sensores) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ContenedorRepositorio contenedores;
    private final ServicioDePrioridad prioridad;
    private final RelojDelSistema reloj;

    PanelControlador(NamedParameterJdbcTemplate jdbc, ContenedorRepositorio contenedores, ServicioDePrioridad prioridad,
            RelojDelSistema reloj) {
        this.jdbc = jdbc;
        this.contenedores = contenedores;
        this.prioridad = prioridad;
        this.reloj = reloj;
    }

    @GetMapping("/resumen")
    public Resumen resumen(@RequestParam(required = false) Integer zonaId,
            @RequestParam(required = false) Integer sectorId) {
        Instant ahora = reloj.ahora();
        MapSqlParameterSource datos = new MapSqlParameterSource()
                .addValue("desde", Fechas.paraLaBase(Fechas.inicioDelDia(ahora)))
                .addValue("zona", zonaId, java.sql.Types.INTEGER)
                .addValue("sector", sectorId, java.sql.Types.INTEGER);

        Map<String, Object> rutas = jdbc.queryForMap("""
                SELECT count(*) AS rutas,
                       COALESCE(sum(GREATEST(r.km_ruta_fija - r.km_planificados, 0)), 0) AS km_evitados,
                       COALESCE(sum((SELECT count(*) FROM parada p WHERE p.ruta_id = r.id)), 0) AS planificados,
                       COALESCE(sum((SELECT count(*) FROM parada p
                                     WHERE p.ruta_id = r.id AND p.estado = 'recolectada')), 0) AS recolectados
                FROM ruta r JOIN camion k ON k.id = r.camion_id
                WHERE r.creada_at >= :desde AND (:zona::int IS NULL OR k.zona_id = :zona)
                  AND (:sector::int IS NULL OR k.sector_id = :sector)
                """, datos);
        Number kmRecorridos = jdbc.queryForObject(
                "SELECT COALESCE(sum(km_recorridos_hoy), 0) FROM camion WHERE (:zona::int IS NULL OR zona_id = :zona)"
                        + " AND (:sector::int IS NULL OR sector_id = :sector)",
                datos, Number.class);
        Long incidentesAbiertos = jdbc.queryForObject("""
                SELECT count(*) FROM incidente i
                LEFT JOIN contenedor c ON c.id = i.contenedor_id
                LEFT JOIN camion k ON k.id = i.camion_id
                WHERE i.estado IN ('pendiente', 'confirmado')
                  AND (:zona::int IS NULL OR c.zona_id = :zona OR k.zona_id = :zona)
                  AND (:sector::int IS NULL OR c.sector_id = :sector OR k.sector_id = :sector)
                """, datos, Long.class);

        Map<String, Long> sensores = new LinkedHashMap<>();
        for (String estado : new String[] { "funcionando", "irregular", "congelado", "sin_senal" }) {
            sensores.put(estado, 0L);
        }
        jdbc.query("""
                SELECT estado_sensor, count(*) AS cantidad FROM contenedor
                WHERE activo AND (:zona::int IS NULL OR zona_id = :zona)
                  AND (:sector::int IS NULL OR sector_id = :sector) GROUP BY estado_sensor
                """, datos, fila -> {
            sensores.put(fila.getString("estado_sensor"), fila.getLong("cantidad"));
        });

        var lista = sectorId != null ? contenedores.findBySectorIdAndActivoTrueOrderById(sectorId)
                : zonaId != null ? contenedores.findByZonaIdAndActivoTrueOrderById(zonaId)
                : contenedores.findByActivoTrueOrderById();
        long prioritarios = prioridad.evaluar(lista, ahora).values().stream().filter(Evaluacion::esPrioritario).count();

        return new Resumen(Fechas.enBuenosAires(ahora), ((Number) rutas.get("recolectados")).longValue(),
                ((Number) rutas.get("planificados")).longValue(), ((Number) rutas.get("km_evitados")).doubleValue(),
                kmRecorridos.doubleValue(), ((Number) rutas.get("rutas")).longValue(), prioritarios, incidentesAbiertos,
                sensores);
    }
}
