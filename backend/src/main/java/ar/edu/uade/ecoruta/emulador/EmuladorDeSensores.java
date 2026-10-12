package ar.edu.uade.ecoruta.emulador;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.camiones.CamionRepositorio;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.contenedores.ModeloDeLlenado;
import ar.edu.uade.ecoruta.contenedores.ServicioDePrioridad;
import ar.edu.uade.ecoruta.incidentes.ServicioDeIncidentes;
import ar.edu.uade.ecoruta.rutas.Parada;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas;
import ar.edu.uade.ecoruta.rutas.Ruta;
import ar.edu.uade.ecoruta.rutas.RutaRepositorio;
import ar.edu.uade.ecoruta.rutas.ServicioDeDespacho;
import ar.edu.uade.ecoruta.rutas.ServicioDeRutas;
import ar.edu.uade.ecoruta.sensores.LecturaNueva;
import ar.edu.uade.ecoruta.sensores.RepositorioDeLecturas;
import ar.edu.uade.ecoruta.sensores.ServicioDeEstadoDeSensores;
import ar.edu.uade.ecoruta.sensores.ServicioDeLecturas;

/**
 * Hace de "mundo real" mientras no haya sensores ni choferes: cada paso adelanta 5 minutos
 * la hora del sistema, llena los contenedores, manda las lecturas de los sensores y mueve
 * los camiones por sus rutas. Reemplaza a src/simulator/simulador.ts del front.
 *
 * Lo importante: el emulador sabe cosas que el sistema no (el nivel real de cada contenedor,
 * qué sensor está roto). Al sistema solo le llega lo que le llegaría de verdad: lecturas con
 * contenedor, nivel y hora, y lo que confirma el chofer. Todo lo demás lo tiene que deducir.
 */
@Component
@ConditionalOnProperty(name = "ecoruta.emulador.activo", havingValue = "true")
public class EmuladorDeSensores {

    private static final Logger log = LoggerFactory.getLogger(EmuladorDeSensores.class);

    static final int MINUTOS_POR_PASO = 5;
    private static final long MS_REALES_POR_PASO = 4000; // a velocidad 1
    private static final LocalTime HORA_INICIAL = LocalTime.of(17, 0); // arranca justo antes del pico
    private static final int PASOS_DE_DESCARGA = 2;
    private static final int PASOS_POR_DIA = 24 * 60 / MINUTOS_POR_PASO;
    private static final Duration HISTORIAL_GUARDADO = Duration.ofDays(3);

    private static final double PROBABILIDAD_NO_SE_PUDO = 0.08;
    private static final double PROBABILIDAD_FALLA_SENSOR = 0.01; // por paso, en algún sensor de la ciudad
    private static final double PROBABILIDAD_REPORTE_VECINAL = 0.04;
    private static final double PROBABILIDAD_REVUELVEN = 0.0005; // por paso y por contenedor

    private static final String SIN_FALLA = "ninguna";

    private static final List<String> MOTIVOS_NO_SE_PUDO = List.of("Auto estacionado", "Obra o calle cortada",
            "Contenedor dañado", "Basura fuera del contenedor", "Sensor marca mal");
    private static final List<String> COMENTARIOS_VECINALES = List.of(
            "Hay bolsas tiradas al lado del contenedor.", "El contenedor está lleno y huele mal.",
            "Se desbordó y no entra más basura.", "Hay residuos desparramados en la vereda.");

    private final RelojDelSistema reloj;
    private final ContenedorRepositorio contenedores;
    private final CamionRepositorio camiones;
    private final RutaRepositorio rutas;
    private final RepositorioDeLecturas lecturas;
    private final ServicioDeLecturas servicioDeLecturas;
    private final ServicioDeEstadoDeSensores estadoDeSensores;
    private final ServicioDeIncidentes incidentes;
    private final ServicioDePrioridad prioridad;
    private final ServicioDeDespacho despacho;
    private final ServicioDeRutas servicioDeRutas;
    private final HistorialInicial historialInicial;
    private final TransactionTemplate transaccion;

    // Lo que solo conoce el emulador
    private final Map<String, Double> nivelReal = new HashMap<>();
    private final Map<String, String> falla = new HashMap<>(); // ninguna, sin_senal o congelado
    private final Map<String, Integer> revuelvenHasta = new HashMap<>(); // paso hasta el que alguien revuelve
    private final Map<String, Double> minutosDelCamion = new HashMap<>();
    private final Map<String, Integer> pasosDeDescarga = new HashMap<>();
    private final Random azar = new Random(2026);
    private int paso;
    private volatile boolean listo;
    private long proximoPasoMs;

    EmuladorDeSensores(RelojDelSistema reloj, ContenedorRepositorio contenedores, CamionRepositorio camiones,
            RutaRepositorio rutas, RepositorioDeLecturas lecturas, ServicioDeLecturas servicioDeLecturas,
            ServicioDeEstadoDeSensores estadoDeSensores, ServicioDeIncidentes incidentes, ServicioDePrioridad prioridad,
            ServicioDeDespacho despacho, ServicioDeRutas servicioDeRutas, HistorialInicial historialInicial,
            TransactionTemplate transaccion) {
        this.reloj = reloj;
        this.contenedores = contenedores;
        this.camiones = camiones;
        this.rutas = rutas;
        this.lecturas = lecturas;
        this.servicioDeLecturas = servicioDeLecturas;
        this.estadoDeSensores = estadoDeSensores;
        this.incidentes = incidentes;
        this.prioridad = prioridad;
        this.despacho = despacho;
        this.servicioDeRutas = servicioDeRutas;
        this.historialInicial = historialInicial;
        this.transaccion = transaccion;
    }

    @EventListener(ApplicationReadyEvent.class)
    void arrancar() {
        transaccion.executeWithoutResult(estado -> {
            if (!reloj.esSimulado()) {
                reloj.fijar(Instant.now().atZone(Fechas.BUENOS_AIRES).toLocalDate().atTime(HORA_INICIAL)
                        .atZone(Fechas.BUENOS_AIRES).toInstant());
            }
            Map<String, String> fallasIniciales = Map.of();
            if (!lecturas.hayLecturas()) {
                fallasIniciales = historialInicial.cargar(reloj.ahora(), azar);
                // El sensor irregular de la demo sigue así unos pasos más antes de normalizarse
                revuelvenHasta.put(HistorialInicial.SENSOR_IRREGULAR, 6);
                log.info("Emulador: se cargaron 24 h de lecturas de ejemplo");
            }
            for (Contenedor c : contenedores.findByActivoTrueOrderById()) {
                // Al reiniciar no sabemos el nivel real: se parte de lo que el sistema cree
                nivelReal.put(c.getId(), (double) (c.getNivelEstimado() != null ? c.getNivelEstimado() : c.getLlenado()));
                String estadoSensor = c.getEstadoSensor();
                boolean rota = estadoSensor.equals(ServicioDeEstadoDeSensores.SIN_SENAL)
                        || estadoSensor.equals(ServicioDeEstadoDeSensores.CONGELADO);
                falla.put(c.getId(), fallasIniciales.getOrDefault(c.getId(), rota ? estadoSensor : SIN_FALLA));
            }
        });
        listo = true;
        log.info("Emulador de sensores activo. Hora del sistema: {}", Fechas.enBuenosAires(reloj.ahora()));
    }

    // Revisa seguido si ya toca el próximo paso; así la velocidad se puede cambiar en caliente.
    @Scheduled(fixedDelay = 200)
    void latido() {
        if (!listo || reloj.enPausa() || System.currentTimeMillis() < proximoPasoMs) {
            return;
        }
        proximoPasoMs = System.currentTimeMillis() + MS_REALES_POR_PASO / reloj.velocidad();
        try {
            transaccion.executeWithoutResult(estado -> avanzar());
        } catch (RuntimeException ex) {
            log.error("El emulador no pudo completar el paso {}", paso, ex);
        }
    }

    /** Un paso: pasan 5 minutos en el mundo simulado. */
    void avanzar() {
        Instant antes = reloj.ahora();
        Instant ahora = antes.plus(Duration.ofMinutes(MINUTOS_POR_PASO));
        paso++;
        reloj.fijar(ahora);

        List<Camion> flota = camiones.findAllByOrderByZonaId();
        if (!Fechas.inicioDelDia(antes).equals(Fechas.inicioDelDia(ahora))) {
            flota.forEach(Camion::reiniciarKmDelDia);
        }

        // 1) Los contenedores se llenan y los sensores informan
        List<Contenedor> todos = contenedores.findByActivoTrueOrderById();
        List<LecturaNueva> informadas = new ArrayList<>();
        for (Contenedor c : todos) {
            String id = c.getId();
            double incremento = ModeloDeLlenado.incrementoPorHora(c.velocidad(), ahora) * (MINUTOS_POR_PASO / 60.0)
                    * entre(0.7, 1.3);
            double nivel = Math.min(100, nivelReal.get(id) + incremento);
            nivelReal.put(id, nivel);

            // De vez en cuando alguien revuelve la basura y el sensor marca cualquier cosa
            if (revuelvenHasta.getOrDefault(id, 0) <= paso && nivel > 30 && nivel < 85
                    && azar.nextDouble() < PROBABILIDAD_REVUELVEN) {
                revuelvenHasta.put(id, paso + 8);
            }
            double lectura = nivel;
            if (revuelvenHasta.getOrDefault(id, 0) > paso) {
                lectura += (paso % 2 == 0 ? 1 : -1) * entre(6, 12);
            }

            // Un sensor trabado repite su valor; uno sin señal no manda nada
            String suFalla = falla.get(id);
            if (suFalla.equals(SIN_FALLA)) {
                informadas.add(new LecturaNueva(id, (int) Math.round(Math.clamp(lectura, 0, 100)), ahora));
            } else if (suFalla.equals(ServicioDeEstadoDeSensores.CONGELADO)) {
                informadas.add(new LecturaNueva(id, c.getLlenado(), ahora));
            }
        }
        servicioDeLecturas.registrar(informadas);

        // 2) De vez en cuando un sensor sano deja de funcionar
        if (azar.nextDouble() < PROBABILIDAD_FALLA_SENSOR) {
            List<Contenedor> sanos = todos.stream()
                    .filter(c -> falla.get(c.getId()).equals(SIN_FALLA) && !c.isAnomalia()).toList();
            if (!sanos.isEmpty()) {
                falla.put(elegir(sanos).getId(), azar.nextBoolean() ? ServicioDeEstadoDeSensores.SIN_SENAL
                        : ServicioDeEstadoDeSensores.CONGELADO);
            }
        }

        // 3) A veces entra un reporte de un vecino por Boti
        if (azar.nextDouble() < PROBABILIDAD_REPORTE_VECINAL) {
            Set<String> conReporte = prioridad.reportesVecinales().keySet();
            List<Contenedor> sinReporte = todos.stream().filter(c -> !conReporte.contains(c.getId())).toList();
            if (!sinReporte.isEmpty()) {
                incidentes.crearReporteVecinal(elegir(sinReporte).getId(), elegir(COMENTARIOS_VECINALES), "Boti", ahora);
            }
        }

        // 4) El sistema decide si manda camiones, y los choferes avanzan en sus rutas
        despacho.despacharAutomatico(ahora);
        List<LecturaNueva> trasRecoleccion = new ArrayList<>();
        Map<String, Ruta> rutaPorCamion = new HashMap<>();
        rutas.activas().forEach(r -> rutaPorCamion.put(r.getCamion().getId(), r));
        for (Camion camion : flota) {
            moverCamion(camion, rutaPorCamion.get(camion.getId()), ahora, trasRecoleccion);
        }
        // El sensor de un contenedor recién vaciado informa el nivel nuevo un instante después
        servicioDeLecturas.registrar(trasRecoleccion);

        // 5) Lo que el sistema hace solo con el paso del tiempo
        estadoDeSensores.actualizarTodos(ahora);
        incidentes.cerrarResueltos(ahora);
        if (paso % PASOS_POR_DIA == 0) {
            lecturas.borrarAnterioresA(ahora.minus(HISTORIAL_GUARDADO));
        }
    }

    // El emulador hace de chofer: inicia la ruta que le asignaron y va de parada en parada.
    private void moverCamion(Camion camion, Ruta ruta, Instant ahora, List<LecturaNueva> trasRecoleccion) {
        String id = camion.getId();
        switch (camion.getEstado()) {
            case Camion.EN_BASE -> {
                if (ruta != null && ruta.getEstado().equals(Ruta.PROGRAMADA)) {
                    servicioDeRutas.iniciar(ruta, ahora);
                    minutosDelCamion.put(id, 0.0);
                }
            }
            case Camion.EN_DESCARGA -> {
                int quedan = pasosDeDescarga.getOrDefault(id, PASOS_DE_DESCARGA) - 1;
                if (quedan <= 0) {
                    camion.quedarDisponible();
                    pasosDeDescarga.remove(id);
                } else {
                    pasosDeDescarga.put(id, quedan);
                }
            }
            case Camion.DEMORADO -> camion.reanudar();
            case Camion.EN_RUTA -> {
                if (ruta == null) {
                    camion.quedarDisponible(); // quedó en ruta sin ruta (reinicio a mitad de camino)
                    return;
                }
                recorrer(camion, ruta, ahora, trasRecoleccion);
            }
            default -> log.warn("Estado de camión desconocido: {}", camion.getEstado());
        }
    }

    // Gasta el tiempo de este paso yendo de parada en parada
    private void recorrer(Camion camion, Ruta ruta, Instant ahora, List<LecturaNueva> trasRecoleccion) {
        double minutos = minutosDelCamion.getOrDefault(camion.getId(), 0.0) + MINUTOS_POR_PASO;
        while (ruta.proximaParada().isPresent()) {
            Parada parada = ruta.proximaParada().get();
            Contenedor contenedor = parada.getContenedor();
            double costo = PlanificadorDeRutas.distanciaKm(camion.posicion(), contenedor.posicion())
                    / Parametros.VELOCIDAD_CAMION_KMH * 60 + Parametros.MINUTOS_POR_PARADA;
            if (minutos < costo) {
                break;
            }
            minutos -= costo;

            if (azar.nextDouble() < PROBABILIDAD_NO_SE_PUDO) {
                servicioDeRutas.noSePudo(ruta, parada, elegir(MOTIVOS_NO_SE_PUDO), ahora);
                break; // el camión queda demorado un paso
            }
            servicioDeRutas.recolectar(ruta, parada, ahora);
            int nivel = (int) Math.round(entre(2, 6));
            nivelReal.put(contenedor.getId(), (double) nivel);
            revuelvenHasta.remove(contenedor.getId());
            // Un sensor caído o trabado no se entera de que lo vaciaron
            if (falla.get(contenedor.getId()).equals(SIN_FALLA)) {
                trasRecoleccion.add(new LecturaNueva(contenedor.getId(), nivel, ahora.plusSeconds(1)));
            }
        }
        minutosDelCamion.put(camion.getId(), ruta.proximaParada().isPresent() ? minutos : 0.0);
    }

    private double entre(double min, double max) {
        return min + azar.nextDouble() * (max - min);
    }

    private <T> T elegir(List<T> lista) {
        return lista.get(azar.nextInt(lista.size()));
    }
}
