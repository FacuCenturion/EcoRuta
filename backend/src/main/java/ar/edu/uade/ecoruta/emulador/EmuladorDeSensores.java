package ar.edu.uade.ecoruta.emulador;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
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
 * los camiones por sus rutas.
 *
 * Lo importante: el emulador sabe cosas que el sistema no (cuánta basura genera cada
 * contenedor y cuándo, qué sensor está roto). Al sistema solo le llega lo que le llegaría de
 * verdad: lecturas con contenedor, nivel y hora, y lo que confirma el chofer. Todo lo demás lo
 * tiene que deducir. Los casos que cubre están en la guía de backend.
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
    // Se guardan las lecturas que usa el patrón de llenado, más un día de margen
    private static final Duration HISTORIAL_GUARDADO = Duration.ofDays(Parametros.DIAS_DE_LECTURAS_PARA_EL_PATRON + 1);

    // Con qué frecuencia pasa cada cosa, por paso de 5 minutos
    private static final double PROBABILIDAD_NO_SE_PUDO = 0.06; // por parada
    private static final double PROBABILIDAD_FALLA_SENSOR = 0.01; // en algún sensor de la zona: ~3 por día
    private static final double PROBABILIDAD_SENSOR_TRABADO = 0.03; // al vaciar un contenedor
    private static final double PROBABILIDAD_REPORTE_VECINAL = 0.03; // ~9 por día
    private static final double PROBABILIDAD_REPORTE_SIN_MOTIVO = 0.3; // vecino que reporta un contenedor vacío
    private static final double PROBABILIDAD_REVUELVEN = 0.0005; // por contenedor
    private static final double PROBABILIDAD_EVENTO_MASIVO = 0.0015; // en algún sector: ~1 cada 2 días
    private static final double FACTOR_EVENTO_MASIVO = 2; // el doble de basura; con el pico de oficinas ya es mucho
    // Un sensor real nunca marca exactamente lo mismo durante horas: fluctúa un poco
    // (compactación, temperatura). Sin este ruido, un contenedor que casi no se usa parecería congelado.
    static final double RUIDO_DEL_SENSOR = 0.7; // desvío en puntos de llenado

    static final String SIN_FALLA = "ninguna";
    static final String SIN_SENAL = ServicioDeEstadoDeSensores.SIN_SENAL;
    static final String CONGELADO = ServicioDeEstadoDeSensores.CONGELADO;
    static final String TRABADO = ServicioDeEstadoDeSensores.TRABADO;

    private static final List<String> MOTIVOS_NO_SE_PUDO = List.of("Auto estacionado", "Obra o calle cortada",
            "Contenedor dañado", "Basura fuera del contenedor", "Sensor marca mal");
    private static final List<String> COMENTARIOS_VECINALES = List.of(
            "Hay bolsas tiradas al lado del contenedor.", "El contenedor está lleno y huele mal.",
            "Se desbordó y no entra más basura.", "Hay residuos desparramados en la vereda.");
    private static final List<String> COMENTARIOS_SIN_MOTIVO = List.of(
            "El contenedor está roto y no cierra la tapa.", "Pusieron el contenedor muy cerca de la entrada.",
            "Hay olor en la cuadra.");

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
    private final MundoSimulado mundo;
    private final TransactionTemplate transaccion;
    private final boolean automatico;

    // Lo que solo conoce el emulador
    private final Map<String, Double> nivelReal = new HashMap<>();
    private final Map<String, String> falla = new HashMap<>(); // ninguna, sin_senal, congelado o trabado
    private final Map<String, Instant> reparaEn = new HashMap<>(); // cuándo vuelve a funcionar el sensor
    private final Map<String, Integer> valorTrabado = new HashMap<>(); // lo que repite un sensor trabado
    private final Set<String> trabarAlVaciar = new HashSet<>(); // casos de demo: se traba en la próxima recolección
    private final Map<String, Integer> revuelvenHasta = new HashMap<>(); // paso hasta el que alguien revuelve
    private final Map<String, Double> minutosDelCamion = new HashMap<>();
    private final Map<String, Integer> pasosDeDescarga = new HashMap<>();

    // Semilla fija: después de reiniciar, la demo vuelve a salir igual
    private static final long SEMILLA = 2026;
    private Random azar = new Random(SEMILLA);
    private int paso;
    private volatile boolean listo;
    private long proximoPasoMs;
    private final Object turno = new Object(); // un paso, un reinicio o un caso de demo a la vez

    EmuladorDeSensores(RelojDelSistema reloj, ContenedorRepositorio contenedores, CamionRepositorio camiones,
            RutaRepositorio rutas, RepositorioDeLecturas lecturas, ServicioDeLecturas servicioDeLecturas,
            ServicioDeEstadoDeSensores estadoDeSensores, ServicioDeIncidentes incidentes, ServicioDePrioridad prioridad,
            ServicioDeDespacho despacho, ServicioDeRutas servicioDeRutas, HistorialInicial historialInicial,
            MundoSimulado mundo, TransactionTemplate transaccion,
            @Value("${ecoruta.emulador.automatico:true}") boolean automatico) {
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
        this.mundo = mundo;
        this.transaccion = transaccion;
        this.automatico = automatico;
    }

    @EventListener(ApplicationReadyEvent.class)
    void arrancar() {
        synchronized (turno) {
            transaccion.executeWithoutResult(estado -> preparar());
            listo = true;
        }
        log.info("Emulador de sensores activo. Hora del sistema: {}", Fechas.enBuenosAires(reloj.ahora()));
    }

    /**
     * Arma el estado del emulador. Si la base no tiene lecturas, carga el historial de ejemplo;
     * si ya tiene, sigue desde donde quedó. Corre dentro de una transacción.
     */
    void preparar() {
        if (!reloj.esSimulado()) {
            reloj.fijar(Instant.now().atZone(Fechas.BUENOS_AIRES).toLocalDate().atTime(HORA_INICIAL)
                    .atZone(Fechas.BUENOS_AIRES).toInstant());
        }
        Instant ahora = reloj.ahora();
        Map<String, String> fallasIniciales = Map.of();
        if (!lecturas.hayLecturas()) {
            fallasIniciales = historialInicial.cargar(ahora, azar, mundo);
            // El sensor irregular de la demo sigue así unos pasos más antes de normalizarse
            revuelvenHasta.put(HistorialInicial.SENSOR_IRREGULAR, 6);
            log.info("Emulador: se cargaron 24 h de lecturas de ejemplo");
        }
        for (Contenedor c : contenedores.findByActivoTrueOrderById()) {
            // Al reiniciar no sabemos el nivel real: se parte de lo que el sistema cree
            nivelReal.put(c.getId(), (double) (c.getNivelEstimado() != null ? c.getNivelEstimado() : c.getLlenado()));
            String estadoSensor = c.getEstadoSensor();
            boolean rota = estadoSensor.equals(SIN_SENAL) || estadoSensor.equals(CONGELADO) || estadoSensor.equals(TRABADO);
            String suFalla = fallasIniciales.getOrDefault(c.getId(), rota ? estadoSensor : SIN_FALLA);
            falla.put(c.getId(), suFalla);
            if (!suFalla.equals(SIN_FALLA)) {
                // Las fallas del arranque se arreglan en algún momento del día: se ve la recuperación
                reparaEn.put(c.getId(), ahora.plus(Duration.ofMinutes(Math.round(entre(4, 20) * 60))));
                valorTrabado.put(c.getId(), c.getLlenado());
            }
        }
    }

    /** Olvida todo lo que sabía el emulador. Lo usa el reinicio de la demo, que después llama a preparar(). */
    void olvidar() {
        nivelReal.clear();
        falla.clear();
        reparaEn.clear();
        valorTrabado.clear();
        trabarAlVaciar.clear();
        revuelvenHasta.clear();
        minutosDelCamion.clear();
        pasosDeDescarga.clear();
        mundo.olvidarEventos();
        azar = new Random(SEMILLA);
        paso = 0;
        proximoPasoMs = 0;
    }

    /** Para hacer algo con el emulador detenido (reiniciar la demo, disparar un caso). */
    <T> T conElEmuladorDetenido(Supplier<T> accion) {
        synchronized (turno) {
            return accion.get();
        }
    }

    /** Avanza a mano varios pasos, cada uno en su transacción. Lo usan las pruebas. */
    public void avanzarPasos(int cuantos) {
        synchronized (turno) {
            for (int i = 0; i < cuantos; i++) {
                transaccion.executeWithoutResult(estado -> avanzar());
            }
        }
    }

    // Revisa seguido si ya toca el próximo paso; así la velocidad se puede cambiar en caliente.
    @Scheduled(fixedDelay = 200)
    void latido() {
        if (!automatico || !listo || reloj.enPausa() || System.currentTimeMillis() < proximoPasoMs) {
            return;
        }
        synchronized (turno) {
            proximoPasoMs = System.currentTimeMillis() + MS_REALES_POR_PASO / reloj.velocidad();
            try {
                transaccion.executeWithoutResult(estado -> avanzar());
            } catch (RuntimeException ex) {
                log.error("El emulador no pudo completar el paso {}", paso, ex);
            }
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
        List<Contenedor> todos = contenedores.findByActivoTrueOrderById();

        // 1) Lo que pasa en el mundo: eventos masivos y sensores que se rompen o se arreglan
        sortearEventoMasivo(todos, ahora);
        repararSensores(ahora);
        sortearFallaDeSensor(todos, ahora);

        // 2) Los contenedores se llenan y los sensores informan
        List<LecturaNueva> informadas = new ArrayList<>();
        for (Contenedor c : todos) {
            String id = c.getId();
            double incremento = mundo.ritmoPorHora(c, ahora) * (MINUTOS_POR_PASO / 60.0) * entre(0.7, 1.3);
            double nivel = Math.min(100, nivelReal.get(id) + incremento);
            nivelReal.put(id, nivel);

            // De vez en cuando alguien revuelve la basura y el sensor marca cualquier cosa
            if (revuelvenHasta.getOrDefault(id, 0) <= paso && nivel > 30 && nivel < 85
                    && azar.nextDouble() < PROBABILIDAD_REVUELVEN) {
                revuelvenHasta.put(id, paso + 8);
            }
            double lectura = nivel + azar.nextGaussian() * RUIDO_DEL_SENSOR;
            if (revuelvenHasta.getOrDefault(id, 0) > paso) {
                lectura += (paso % 2 == 0 ? 1 : -1) * entre(6, 12);
            }

            // Un sensor congelado repite su último valor, uno trabado repite el de antes de vaciarse
            // y uno sin señal no manda nada
            switch (falla.get(id)) {
                case SIN_FALLA -> informadas.add(new LecturaNueva(id, (int) Math.round(Math.clamp(lectura, 0, 100)), ahora));
                case CONGELADO -> informadas.add(new LecturaNueva(id, c.getLlenado(), ahora));
                case TRABADO -> informadas.add(new LecturaNueva(id, valorTrabado.getOrDefault(id, c.getLlenado()), ahora));
                default -> {
                    // sin señal
                }
            }
        }
        servicioDeLecturas.registrar(informadas);

        // 3) A veces entra un reporte de un vecino por Boti
        if (azar.nextDouble() < PROBABILIDAD_REPORTE_VECINAL) {
            sortearReporteVecinal(todos, ahora);
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

    private void sortearEventoMasivo(List<Contenedor> todos, Instant ahora) {
        if (azar.nextDouble() >= PROBABILIDAD_EVENTO_MASIVO) {
            return;
        }
        List<Integer> sectores = todos.stream().map(Contenedor::getSectorId).distinct().sorted().toList();
        int sector = elegir(sectores);
        if (!mundo.hayEventoEn(sector, ahora)) {
            mundo.iniciarEvento(sector, ahora, (int) Math.round(entre(3, 5)), FACTOR_EVENTO_MASIVO);
        }
    }

    // Las fallas no son para siempre: alguien va y arregla el sensor
    private void repararSensores(Instant ahora) {
        reparaEn.entrySet().removeIf(entrada -> {
            if (entrada.getValue().isAfter(ahora)) {
                return false;
            }
            falla.put(entrada.getKey(), SIN_FALLA);
            valorTrabado.remove(entrada.getKey());
            return true;
        });
    }

    private void sortearFallaDeSensor(List<Contenedor> todos, Instant ahora) {
        if (azar.nextDouble() >= PROBABILIDAD_FALLA_SENSOR) {
            return;
        }
        List<Contenedor> sanos = todos.stream()
                .filter(c -> falla.get(c.getId()).equals(SIN_FALLA) && !c.isAnomalia()).toList();
        if (!sanos.isEmpty()) {
            Contenedor elegido = elegir(sanos);
            romperSensor(elegido, azar.nextBoolean() ? SIN_SENAL : CONGELADO, ahora,
                    (int) Math.round(entre(6, 30)));
        }
    }

    private void romperSensor(Contenedor contenedor, String tipo, Instant ahora, int horas) {
        falla.put(contenedor.getId(), tipo);
        valorTrabado.put(contenedor.getId(), contenedor.getLlenado());
        reparaEn.put(contenedor.getId(), ahora.plus(Duration.ofHours(horas)));
    }

    // La mayoría de los reportes son sobre contenedores que de verdad están llenos; algunos no
    private void sortearReporteVecinal(List<Contenedor> todos, Instant ahora) {
        Set<String> conReporte = prioridad.reportesVecinales().keySet();
        boolean sinMotivo = azar.nextDouble() < PROBABILIDAD_REPORTE_SIN_MOTIVO;
        List<Contenedor> candidatos = todos.stream()
                .filter(c -> !conReporte.contains(c.getId()))
                .filter(c -> sinMotivo || nivelReal.get(c.getId()) >= 70)
                .toList();
        if (!candidatos.isEmpty()) {
            incidentes.crearReporteVecinal(elegir(candidatos).getId(),
                    elegir(sinMotivo ? COMENTARIOS_SIN_MOTIVO : COMENTARIOS_VECINALES), "Boti", ahora);
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
            int antesDeVaciar = contenedor.getLlenado();
            servicioDeRutas.recolectar(ruta, parada, ahora);
            String id = contenedor.getId();
            int nivel = (int) Math.round(entre(2, 6));
            nivelReal.put(id, (double) nivel);
            revuelvenHasta.remove(id);

            // A veces el sensor se traba al vaciar el contenedor y sigue marcando lo de antes
            if (falla.get(id).equals(SIN_FALLA)
                    && (trabarAlVaciar.remove(id) || azar.nextDouble() < PROBABILIDAD_SENSOR_TRABADO)) {
                falla.put(id, TRABADO);
                valorTrabado.put(id, antesDeVaciar);
                reparaEn.put(id, ahora.plus(Duration.ofMinutes(Math.round(entre(4, 12) * 60))));
            }
            // Un sensor sano informa enseguida que lo vaciaron; uno caído o trabado, no
            if (falla.get(id).equals(SIN_FALLA)) {
                trasRecoleccion.add(new LecturaNueva(id, nivel, ahora.plusSeconds(1)));
            }
        }
        minutosDelCamion.put(camion.getId(), ruta.proximaParada().isPresent() ? minutos : 0.0);
    }

    // ---------- Casos que se disparan a pedido para la demo ----------

    /** Rompe el sensor de un contenedor: sin_senal o congelado ahora, trabado en la próxima recolección. */
    public String fallarSensor(String contenedorId, String tipo, int horas) {
        return conCaso(contenedorId, contenedor -> {
            Instant ahora = reloj.ahora();
            if (tipo.equals(TRABADO)) {
                trabarAlVaciar.add(contenedorId);
                return "El sensor de " + contenedorId + " se va a trabar la próxima vez que un camión lo vacíe.";
            }
            romperSensor(contenedor, tipo, ahora, horas);
            return "El sensor de " + contenedorId + " pasó a " + tipo.replace('_', ' ') + " durante " + horas
                    + " h. El sistema lo detecta a las " + (tipo.equals(SIN_SENAL) ? Parametros.HORAS_SIN_SENAL
                            : Parametros.HORAS_VALOR_CONGELADO) + " h.";
        });
    }

    public String repararSensor(String contenedorId) {
        return conCaso(contenedorId, contenedor -> {
            falla.put(contenedorId, SIN_FALLA);
            reparaEn.remove(contenedorId);
            valorTrabado.remove(contenedorId);
            trabarAlVaciar.remove(contenedorId);
            return "El sensor de " + contenedorId + " vuelve a funcionar desde el próximo paso.";
        });
    }

    /** Alguien revuelve la basura: el sensor marca saltos durante 40 minutos. */
    public String revolver(String contenedorId) {
        return conCaso(contenedorId, contenedor -> {
            revuelvenHasta.put(contenedorId, paso + 8);
            return "Alguien revuelve " + contenedorId + ": el sensor va a marcar saltos durante 40 minutos.";
        });
    }

    /** Lleva el nivel real de un contenedor a un valor (por ejemplo, para mostrar un desborde). */
    public String llenar(String contenedorId, int nivel) {
        return conCaso(contenedorId, contenedor -> {
            nivelReal.put(contenedorId, (double) Math.clamp(nivel, 0, 100));
            return "El contenedor " + contenedorId + " queda al " + nivel + "%; el sensor lo informa en el próximo paso.";
        });
    }

    /** Feria, recital o partido: el sector genera más basura durante unas horas. */
    public String eventoMasivo(int sectorId, int horas) {
        return conElEmuladorDetenido(() -> {
            mundo.iniciarEvento(sectorId, reloj.ahora(), horas, FACTOR_EVENTO_MASIVO);
            return "Evento masivo en el sector " + sectorId + ": genera el doble de basura durante " + horas + " h.";
        });
    }

    public String reporteVecinal(String contenedorId, String comentario) {
        return conCaso(contenedorId, contenedor -> {
            incidentes.crearReporteVecinal(contenedorId,
                    comentario == null || comentario.isBlank() ? elegir(COMENTARIOS_VECINALES) : comentario, "Boti",
                    reloj.ahora());
            return "Entró un reporte vecinal sobre " + contenedorId + ".";
        });
    }

    private String conCaso(String contenedorId, java.util.function.Function<Contenedor, String> accion) {
        return conElEmuladorDetenido(() -> transaccion.execute(estado -> {
            Contenedor contenedor = contenedores.findById(contenedorId).orElseThrow(
                    () -> new RecursoNoEncontradoException("CONTENEDOR_NO_EXISTE",
                            "No existe el contenedor " + contenedorId + "."));
            return accion.apply(contenedor);
        }));
    }

    private double entre(double min, double max) {
        return min + azar.nextDouble() * (max - min);
    }

    private <T> T elegir(List<T> lista) {
        return lista.get(azar.nextInt(lista.size()));
    }
}
