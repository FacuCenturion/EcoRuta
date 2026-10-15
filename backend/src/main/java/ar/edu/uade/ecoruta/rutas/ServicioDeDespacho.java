package ar.edu.uade.ecoruta.rutas;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.camiones.CamionRepositorio;
import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.comun.error.EstadoInvalidoException;
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;
import ar.edu.uade.ecoruta.contenedores.ServicioDePrioridad;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas.Punto;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas.RutaPlanificada;
import ar.edu.uade.ecoruta.rutas.SeleccionDeParadas.Eleccion;
import ar.edu.uade.ecoruta.rutas.SeleccionDeParadas.Opcion;
import ar.edu.uade.ecoruta.sectores.Sector;
import ar.edu.uade.ecoruta.sectores.SectorRepositorio;
import ar.edu.uade.ecoruta.zonas.Zona;
import ar.edu.uade.ecoruta.zonas.ZonaRepositorio;

/**
 * Despacho a demanda: los camiones no salen en rondas fijas sino cuando hace falta.
 * Por ahora la ruta sale en el momento; elegir el horario de menos tráfico llega en la Sprint 4.
 */
@Service
public class ServicioDeDespacho {

    private final CamionRepositorio camiones;
    private final ContenedorRepositorio contenedores;
    private final ZonaRepositorio zonas;
    private final RutaRepositorio rutas;
    private final ServicioDePrioridad prioridad;
    private final SectorRepositorio sectores;

    ServicioDeDespacho(CamionRepositorio camiones, ContenedorRepositorio contenedores, ZonaRepositorio zonas,
            RutaRepositorio rutas, ServicioDePrioridad prioridad, SectorRepositorio sectores) {
        this.sectores = sectores;
        this.camiones = camiones;
        this.contenedores = contenedores;
        this.zonas = zonas;
        this.rutas = rutas;
        this.prioridad = prioridad;
    }

    /**
     * Revisa cada sector con el camión libre y le arma una ruta si hay al menos 3 contenedores
     * prioritarios o alguno ya desbordado. Devuelve las rutas creadas.
     */
    @Transactional
    public List<Ruta> despacharAutomatico(Instant ahora) {
        Set<String> ocupados = camionesConRutaActiva();
        Map<Integer, Zona> zonaPorId = zonas.findAll().stream().collect(Collectors.toMap(Zona::getId, Function.identity()));

        return camiones.findAllByOrderByZonaId().stream()
                .filter(camion -> camion.getEstado().equals(Camion.EN_BASE) && !ocupados.contains(camion.getId()))
                .map(camion -> {
                    List<Contenedor> deLaZona = contenedores.findBySectorIdAndActivoTrueOrderById(camion.getSectorId());
                    Map<String, Evaluacion> evaluaciones = prioridad.evaluar(deLaZona, ahora);
                    long prioritarios = evaluaciones.values().stream().filter(Evaluacion::esPrioritario).count();
                    boolean hayDesbordado = evaluaciones.values().stream()
                            .anyMatch(e -> e.lectura() >= Parametros.UMBRAL_DESBORDE);
                    if (prioritarios < Parametros.MIN_PARADAS_PARA_SALIR && !hayDesbordado) {
                        return null;
                    }
                    return crear(camion, zonaPorId.get(camion.getZonaId()), deLaZona, evaluaciones, Ruta.AUTOMATICO, ahora);
                })
                .filter(ruta -> ruta != null)
                .toList();
    }

    /** El operador manda ahora el camión de un sector, aunque no se llegue al mínimo de paradas. */
    @Transactional
    public Ruta despacharManual(int sectorId, Instant ahora) {
        Sector sector = sectores.findById(sectorId).orElseThrow(
                () -> new RecursoNoEncontradoException("SECTOR_NO_EXISTE", "No existe el sector " + sectorId + "."));
        Set<String> ocupados = camionesConRutaActiva();
        Camion camion = camiones.findBySectorId(sectorId).stream()
                .filter(c -> c.getEstado().equals(Camion.EN_BASE) && !ocupados.contains(c.getId()))
                .findFirst()
                .orElseThrow(() -> new EstadoInvalidoException("CAMION_OCUPADO",
                        "El camión de " + sector.getNombre() + " ya tiene una ruta o todavía no volvió a la base."));

        List<Contenedor> delSector = contenedores.findBySectorIdAndActivoTrueOrderById(sectorId);
        Map<String, Evaluacion> evaluaciones = prioridad.evaluar(delSector, ahora);
        if (evaluaciones.values().stream().noneMatch(Evaluacion::esPrioritario)) {
            throw new EstadoInvalidoException("SIN_CONTENEDORES_PRIORITARIOS",
                    "En " + sector.getNombre() + " no hay contenedores para recolectar ahora.");
        }
        Zona zona = zonas.findById(sector.getZonaId()).orElseThrow();
        return crear(camion, zona, delSector, evaluaciones, Ruta.MANUAL, ahora);
    }

    private Set<String> camionesConRutaActiva() {
        return rutas.activas().stream().map(r -> r.getCamion().getId()).collect(Collectors.toSet());
    }

    private Ruta crear(Camion camion, Zona zona, List<Contenedor> deLaZona, Map<String, Evaluacion> evaluaciones,
            String origen, Instant ahora) {
        Coordenada base = new Coordenada(zona.getBaseLat(), zona.getBaseLng());
        Map<String, Contenedor> porId = deLaZona.stream().collect(Collectors.toMap(Contenedor::getId, Function.identity()));

        // Prioritarios más las paradas de paso que quedan cerca
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(deLaZona.stream()
                .map(c -> new Opcion(c.getId(), c.posicion(), evaluaciones.get(c.getId()).lectura(),
                        evaluaciones.get(c.getId()).motivo()))
                .toList());
        Map<String, String> motivoVisita = elegidos.stream().collect(Collectors.toMap(Eleccion::id, Eleccion::motivoVisita));

        RutaPlanificada plan = PlanificadorDeRutas.planificar(base,
                elegidos.stream().map(e -> new Punto(e.id(), porId.get(e.id()).posicion())).toList());
        // Lo que se habría recorrido visitando todos los contenedores, para medir el ahorro
        double kmRutaFija = PlanificadorDeRutas
                .planificar(base, deLaZona.stream().map(c -> new Punto(c.getId(), c.posicion())).toList()).km();

        double minutos = plan.km() / Parametros.VELOCIDAD_CAMION_KMH * 60
                + (double) plan.orden().size() * Parametros.MINUTOS_POR_PARADA;
        Ruta ruta = new Ruta(camion, origen, ahora, ahora, plan.km(), kmRutaFija, (int) Math.round(minutos));

        Instant reloj = ahora;
        Coordenada posicion = base;
        for (String id : plan.orden()) {
            Contenedor contenedor = porId.get(id);
            double viajeMin = PlanificadorDeRutas.distanciaKm(posicion, contenedor.posicion())
                    / Parametros.VELOCIDAD_CAMION_KMH * 60;
            reloj = reloj.plus(Duration.ofSeconds(Math.round((viajeMin + Parametros.MINUTOS_POR_PARADA) * 60)));
            posicion = contenedor.posicion();
            ruta.agregarParada(contenedor, reloj, motivoVisita.get(id));
        }
        return rutas.save(ruta);
    }
}
