package ar.edu.uade.ecoruta.rutas;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.eventos.CanalDeEventos;
import ar.edu.uade.ecoruta.rutas.ClienteOsrm.Trazado;
import ar.edu.uade.ecoruta.zonas.Zona;
import ar.edu.uade.ecoruta.zonas.ZonaRepositorio;

/**
 * El recorrido de cada ruta por las calles. Se calcula la primera vez que alguien mira la ruta,
 * no al crearla: así, con la simulación acelerada, no se le piden a OSRM cientos de rutas que
 * nadie va a ver. Los pedidos salen de a uno y con un segundo de separación (lo que pide el
 * servidor público). Cuando el recorrido está listo, los paneles reciben un aviso.
 */
@Service
public class ServicioDeTrazado {

    public static final String LISTO = "listo";
    public static final String CALCULANDO = "calculando";
    public static final String SIN_SERVICIO = "sin_servicio"; // OSRM no está configurado: se dibujan rectas
    public static final String FALLO = "fallo"; // OSRM no respondió: se vuelve a intentar más tarde

    private static final long MS_ENTRE_PEDIDOS = 1000;
    private static final long MS_ANTES_DE_REINTENTAR = 60_000;

    /** @param tramos uno por tramo de la ruta (base, paradas en orden, base); vacío si todavía no está */
    public record TrazadoDto(long rutaId, String estado, Double kmPorCalle, List<List<Coordenada>> tramos) {
    }

    private final RutaRepositorio rutas;
    private final ZonaRepositorio zonas;
    private final ClienteOsrm osrm;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final CanalDeEventos eventos;

    private final ExecutorService cola = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("trazado-", 0).daemon().factory());
    private final Set<Long> enCurso = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> fallaronEn = new ConcurrentHashMap<>();

    ServicioDeTrazado(RutaRepositorio rutas, ZonaRepositorio zonas, ClienteOsrm osrm, JdbcTemplate jdbc, JsonMapper json,
            CanalDeEventos eventos) {
        this.rutas = rutas;
        this.zonas = zonas;
        this.osrm = osrm;
        this.jdbc = jdbc;
        this.json = json;
        this.eventos = eventos;
    }

    /** El recorrido guardado, o en qué anda. Si no está y se puede, lo manda a calcular. */
    @Transactional(readOnly = true)
    public TrazadoDto consultar(long rutaId) {
        Ruta ruta = rutas.conParadas(rutaId).orElseThrow(
                () -> new RecursoNoEncontradoException("RUTA_NO_EXISTE", "No existe la ruta " + rutaId + "."));
        Map<String, Object> guardado = jdbc.queryForMap("SELECT trazado, km_por_calle FROM ruta WHERE id = ?", rutaId);
        if (guardado.get("trazado") != null) {
            return new TrazadoDto(rutaId, LISTO, ((BigDecimal) guardado.get("km_por_calle")).doubleValue(),
                    leer((String) guardado.get("trazado")));
        }
        if (!osrm.activo()) {
            return new TrazadoDto(rutaId, SIN_SERVICIO, null, List.of());
        }
        Long fallo = fallaronEn.get(rutaId);
        if (fallo != null && System.currentTimeMillis() - fallo < MS_ANTES_DE_REINTENTAR) {
            return new TrazadoDto(rutaId, FALLO, null, List.of());
        }
        if (enCurso.add(rutaId)) {
            List<Coordenada> puntos = puntosDe(ruta);
            cola.submit(() -> calcular(rutaId, puntos));
        }
        return new TrazadoDto(rutaId, CALCULANDO, null, List.of());
    }

    // La base de la zona, las paradas en orden y la vuelta a la base
    private List<Coordenada> puntosDe(Ruta ruta) {
        Zona zona = zonas.findById(ruta.getCamion().getZonaId()).orElseThrow();
        Coordenada base = new Coordenada(zona.getBaseLat(), zona.getBaseLng());
        List<Coordenada> puntos = new ArrayList<>();
        puntos.add(base);
        ruta.getParadas().forEach(p -> puntos.add(p.getContenedor().posicion()));
        puntos.add(base);
        return puntos;
    }

    void calcular(long rutaId, List<Coordenada> puntos) {
        try {
            var trazado = osrm.trazar(puntos);
            if (trazado.isPresent()) {
                guardar(rutaId, trazado.get());
                fallaronEn.remove(rutaId);
                eventos.avisar();
            } else {
                fallaronEn.put(rutaId, System.currentTimeMillis());
            }
            Thread.sleep(MS_ENTRE_PEDIDOS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            enCurso.remove(rutaId);
        }
    }

    private void guardar(long rutaId, Trazado trazado) {
        double[][][] tramos = trazado.tramos().stream()
                .map(tramo -> tramo.stream().map(p -> new double[] { redondear(p.lat()), redondear(p.lng()) })
                        .toArray(double[][]::new))
                .toArray(double[][][]::new);
        jdbc.update("UPDATE ruta SET trazado = ?, km_por_calle = ? WHERE id = ?", json.writeValueAsString(tramos),
                BigDecimal.valueOf(trazado.km()), rutaId);
    }

    private List<List<Coordenada>> leer(String texto) {
        double[][][] tramos = json.readValue(texto, double[][][].class);
        return Arrays.stream(tramos)
                .map(tramo -> Arrays.stream(tramo).map(p -> new Coordenada(p[0], p[1])).toList())
                .toList();
    }

    // Seis decimales son unos 10 cm: más no sirve para dibujar
    private static double redondear(double grados) {
        return Math.round(grados * 1e6) / 1e6;
    }

    /** Lo usa el reinicio de la demo, que vuelve a numerar las rutas desde 1. */
    public void olvidar() {
        fallaronEn.clear();
    }

    @PreDestroy
    void cerrar() {
        cola.shutdownNow();
    }
}
