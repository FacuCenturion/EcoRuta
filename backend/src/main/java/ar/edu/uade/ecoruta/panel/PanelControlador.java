package ar.edu.uade.ecoruta.panel;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.camiones.CamionRepositorio;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;
import ar.edu.uade.ecoruta.contenedores.ServicioDePrioridad;
import ar.edu.uade.ecoruta.indicadores.ServicioDeIndicadores;
import ar.edu.uade.ecoruta.rutas.Ruta;
import ar.edu.uade.ecoruta.rutas.RutaRepositorio;
import ar.edu.uade.ecoruta.sectores.SectorRepositorio;
import ar.edu.uade.ecoruta.sensores.ServicioDeEstadoDeSensores;
import ar.edu.uade.ecoruta.zonas.Zona;
import ar.edu.uade.ecoruta.zonas.ZonaRepositorio;

/** Lo que muestra el Panel general. "Hoy" es el día de la hora del sistema. Lo ven el director y el operador. */
@RestController
@RequestMapping("/api/v1/panel")
public class PanelControlador {

    private static final int DIAS_PARA_PROMEDIOS = 30;

    /**
     * @param porcentajeHecho      de las paradas planificadas hoy, cuántas ya se recolectaron (%)
     * @param kmEvitadosHoy        km que se ahorraron frente a visitar todos los contenedores en cada salida
     * @param sensoresMidiendo     sensores que funcionan (o dan lecturas irregulares pero mandan datos)
     * @param sensores             cuántos sensores hay en cada estado
     * @param llenadoPorHora       llenado promedio en cada hora del día, últimos 30 días (24 valores)
     * @param horaPico             la hora del día con más llenado
     */
    public record Resumen(OffsetDateTime reloj, long recolectadosHoy, long planificadosHoy, int porcentajeHecho,
            double kmEvitadosHoy, double kmRecorridosHoy, long rutasHoy, long prioritarios, long desbordados,
            long incidentesAbiertos, long incidentesPendientes, Camiones camiones, long sensoresMidiendo,
            Map<String, Long> sensores, double[] llenadoPorHora, int horaPico) {
    }

    public record Camiones(long total, long enRuta, long enBase, long enDescarga) {
    }

    /**
     * Una fila de la tabla por sector del Panel general.
     *
     * @param eficiencia de cada 100 vaciados en los últimos 30 días, cuántos en el momento justo
     */
    public record EstadoSector(int id, String nombre, List<String> barrios, String empresa, long contenedores,
            long prioritarios, long desbordados, long incidentesAbiertos, double llenadoPromedio,
            long sensoresConFalla, Double eficiencia, List<CamionDelSector> camiones) {
    }

    /** @param paradasHechas paradas ya cerradas (recolectadas o no se pudo) de su ruta más nueva */
    public record CamionDelSector(String id, int numero, String estado, int paradasHechas, int paradasTotal) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ContenedorRepositorio contenedores;
    private final CamionRepositorio camiones;
    private final SectorRepositorio sectores;
    private final ZonaRepositorio zonas;
    private final RutaRepositorio rutas;
    private final ServicioDePrioridad prioridad;
    private final ServicioDeIndicadores indicadores;
    private final RelojDelSistema reloj;

    PanelControlador(NamedParameterJdbcTemplate jdbc, ContenedorRepositorio contenedores, CamionRepositorio camiones,
            SectorRepositorio sectores, ZonaRepositorio zonas, RutaRepositorio rutas, ServicioDePrioridad prioridad,
            ServicioDeIndicadores indicadores, RelojDelSistema reloj) {
        this.jdbc = jdbc;
        this.contenedores = contenedores;
        this.camiones = camiones;
        this.sectores = sectores;
        this.zonas = zonas;
        this.rutas = rutas;
        this.prioridad = prioridad;
        this.indicadores = indicadores;
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

        Map<String, Object> rutasHoy = jdbc.queryForMap("""
                SELECT count(*) AS rutas,
                       COALESCE(sum(GREATEST(r.km_ruta_fija - r.km_planificados, 0)), 0) AS km_evitados,
                       COALESCE(sum((SELECT count(*) FROM parada p WHERE p.ruta_id = r.id)), 0) AS planificados,
                       COALESCE(sum((SELECT count(*) FROM parada p
                                     WHERE p.ruta_id = r.id AND p.estado = 'recolectada')), 0) AS recolectados
                FROM ruta r JOIN camion k ON k.id = r.camion_id
                WHERE r.creada_at >= :desde AND (:zona::int IS NULL OR k.zona_id = :zona)
                  AND (:sector::int IS NULL OR k.sector_id = :sector)
                """, datos);
        Map<String, Object> incidentes = jdbc.queryForMap("""
                SELECT count(*) AS abiertos, count(*) FILTER (WHERE i.estado = 'pendiente') AS pendientes
                FROM incidente i
                LEFT JOIN contenedor c ON c.id = i.contenedor_id
                LEFT JOIN camion k ON k.id = i.camion_id
                WHERE i.estado IN ('pendiente', 'confirmado')
                  AND (:zona::int IS NULL OR c.zona_id = :zona OR k.zona_id = :zona)
                  AND (:sector::int IS NULL OR c.sector_id = :sector OR k.sector_id = :sector)
                """, datos);

        List<Contenedor> lista = sectorId != null ? contenedores.findBySectorIdAndActivoTrueOrderById(sectorId)
                : zonaId != null ? contenedores.findByZonaIdAndActivoTrueOrderById(zonaId)
                : contenedores.findByActivoTrueOrderById();
        Map<String, Long> sensores = new LinkedHashMap<>();
        for (String estado : new String[] { "funcionando", "irregular", "congelado", "sin_senal", "trabado" }) {
            sensores.put(estado, lista.stream().filter(c -> c.getEstadoSensor().equals(estado)).count());
        }
        long prioritarios = prioridad.evaluar(lista, ahora).values().stream().filter(Evaluacion::esPrioritario).count();

        List<Camion> flota = sectorId != null ? camiones.findBySectorId(sectorId)
                : zonaId != null ? camiones.findByZonaId(zonaId) : camiones.findAllByOrderByZonaId();
        Camiones estadoFlota = new Camiones(flota.size(),
                flota.stream().filter(c -> c.getEstado().equals(Camion.EN_RUTA) || c.getEstado().equals(Camion.DEMORADO)).count(),
                flota.stream().filter(c -> c.getEstado().equals(Camion.EN_BASE)).count(),
                flota.stream().filter(c -> c.getEstado().equals(Camion.EN_DESCARGA)).count());
        double kmRecorridos = flota.stream().mapToDouble(c -> c.getKmRecorridosHoy().doubleValue()).sum();

        long planificados = ((Number) rutasHoy.get("planificados")).longValue();
        long recolectados = ((Number) rutasHoy.get("recolectados")).longValue();
        double[] porHora = indicadores.llenadoPorHora(sectorId, DIAS_PARA_PROMEDIOS, ahora);
        int horaPico = 0;
        for (int h = 1; h < 24; h++) {
            if (porHora[h] > porHora[horaPico]) {
                horaPico = h;
            }
        }

        return new Resumen(Fechas.enBuenosAires(ahora), recolectados, planificados,
                planificados == 0 ? 0 : (int) Math.round(100.0 * recolectados / planificados),
                ((Number) rutasHoy.get("km_evitados")).doubleValue(), Math.round(kmRecorridos * 10) / 10.0,
                ((Number) rutasHoy.get("rutas")).longValue(), prioritarios,
                lista.stream().filter(Contenedor::estaDesbordado).count(),
                ((Number) incidentes.get("abiertos")).longValue(), ((Number) incidentes.get("pendientes")).longValue(),
                estadoFlota, lista.stream().filter(c -> ServicioDeEstadoDeSensores.esConfiable(c.getEstadoSensor())).count(),
                sensores, porHora, horaPico);
    }

    /** La tabla por sector del Panel general: avance de cada camión, prioritarios, incidentes y eficiencia. */
    @GetMapping("/sectores")
    public List<EstadoSector> sectores() {
        Instant ahora = reloj.ahora();
        List<Contenedor> todos = contenedores.findByActivoTrueOrderById();
        Map<String, Evaluacion> evaluaciones = prioridad.evaluar(todos, ahora);
        Map<Integer, Double> eficiencia = indicadores.eficienciaPorSector(DIAS_PARA_PROMEDIOS, ahora);
        Map<String, Ruta> rutaPorCamion = rutas.ultimaDeCadaCamion().stream()
                .collect(Collectors.toMap(r -> r.getCamion().getId(), Function.identity()));
        Map<Integer, Zona> zonaPorId = zonas.findAll().stream().collect(Collectors.toMap(Zona::getId, Function.identity()));
        Map<Integer, Long> abiertosPorSector = new java.util.HashMap<>();
        jdbc.query("""
                SELECT COALESCE(c.sector_id, k.sector_id) AS sector_id, count(*) AS cantidad
                FROM incidente i LEFT JOIN contenedor c ON c.id = i.contenedor_id LEFT JOIN camion k ON k.id = i.camion_id
                WHERE i.estado IN ('pendiente', 'confirmado') GROUP BY 1
                """, new MapSqlParameterSource(), fila -> {
            abiertosPorSector.put(fila.getInt("sector_id"), fila.getLong("cantidad"));
        });

        return sectores.findAll(Sort.by("id")).stream().map(sector -> {
            List<Contenedor> delSector = todos.stream().filter(c -> c.getSectorId() == sector.getId()).toList();
            List<CamionDelSector> suyos = camiones.findBySectorId(sector.getId()).stream()
                    .sorted(java.util.Comparator.comparing(Camion::getNumero))
                    .map(c -> {
                        Ruta ruta = rutaPorCamion.get(c.getId());
                        int total = ruta == null ? 0 : ruta.getParadas().size();
                        int hechas = ruta == null ? 0 : (int) ruta.getParadas().stream().filter(p -> !p.estaPendiente()).count();
                        return new CamionDelSector(c.getId(), c.getNumero(), c.getEstado(), hechas, total);
                    }).toList();
            return new EstadoSector(sector.getId(), sector.getNombre(), sector.getBarrios(),
                    zonaPorId.get(sector.getZonaId()).getEmpresa(), delSector.size(),
                    delSector.stream().filter(c -> evaluaciones.get(c.getId()).esPrioritario()).count(),
                    delSector.stream().filter(Contenedor::estaDesbordado).count(),
                    abiertosPorSector.getOrDefault(sector.getId(), 0L),
                    Math.round(delSector.stream().mapToDouble(c -> evaluaciones.get(c.getId()).lectura()).average().orElse(0) * 10) / 10.0,
                    delSector.stream().filter(c -> !ServicioDeEstadoDeSensores.esConfiable(c.getEstadoSensor())).count(),
                    eficiencia.get(sector.getId()), suyos);
        }).toList();
    }
}
