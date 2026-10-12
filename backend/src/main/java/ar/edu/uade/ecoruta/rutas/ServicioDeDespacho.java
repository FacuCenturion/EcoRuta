package ar.edu.uade.ecoruta.rutas;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
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

    ServicioDeDespacho(CamionRepositorio camiones, ContenedorRepositorio contenedores, ZonaRepositorio zonas,
            RutaRepositorio rutas, ServicioDePrioridad prioridad) {
        this.camiones = camiones;
        this.contenedores = contenedores;
        this.zonas = zonas;
        this.rutas = rutas;
        this.prioridad = prioridad;
    }

    /**
     * Revisa cada zona con el camión libre y le arma una ruta si hay al menos 3 contenedores
     * prioritarios o alguno ya desbordado. Devuelve las rutas creadas.
     */
    @Transactional
    public List<Ruta> despacharAutomatico(Instant ahora) {
        Set<String> ocupados = camionesConRutaActiva();
        Map<Integer, Zona> zonaPorId = zonas.findAll().stream().collect(Collectors.toMap(Zona::getId, Function.identity()));

        return camiones.findAllByOrderByZonaId().stream()
                .filter(camion -> camion.getEstado().equals(Camion.EN_BASE) && !ocupados.contains(camion.getId()))
                .map(camion -> {
                    List<Contenedor> deLaZona = contenedores.findByZonaIdAndActivoTrueOrderById(camion.getZonaId());
                    Map<String, Evaluacion> evaluaciones = prioridad.evaluar(deLaZona, ahora);
                    List<Contenedor> candidatos = candidatos(deLaZona, evaluaciones);
                    boolean hayDesbordado = candidatos.stream()
                            .anyMatch(c -> evaluaciones.get(c.getId()).lectura() >= Parametros.UMBRAL_DESBORDE);
                    if (candidatos.size() < Parametros.MIN_PARADAS_PARA_SALIR && !hayDesbordado) {
                        return null;
                    }
                    return crear(camion, zonaPorId.get(camion.getZonaId()), deLaZona, candidatos, Ruta.AUTOMATICO, ahora);
                })
                .filter(ruta -> ruta != null)
                .toList();
    }

    /** El operador manda el camión de una zona ahora, aunque no se llegue al mínimo de paradas. */
    @Transactional
    public Ruta despacharManual(int zonaId, Instant ahora) {
        Zona zona = zonas.findById(zonaId).orElseThrow(
                () -> new RecursoNoEncontradoException("ZONA_NO_EXISTE", "No existe la zona " + zonaId + "."));
        Set<String> ocupados = camionesConRutaActiva();
        Camion camion = camiones.findByZonaId(zonaId).stream()
                .filter(c -> c.getEstado().equals(Camion.EN_BASE) && !ocupados.contains(c.getId()))
                .findFirst()
                .orElseThrow(() -> new EstadoInvalidoException("CAMION_OCUPADO",
                        "El camión de la zona " + zonaId + " ya tiene una ruta o todavía no volvió a la base."));

        List<Contenedor> deLaZona = contenedores.findByZonaIdAndActivoTrueOrderById(zonaId);
        List<Contenedor> candidatos = candidatos(deLaZona, prioridad.evaluar(deLaZona, ahora));
        if (candidatos.isEmpty()) {
            throw new EstadoInvalidoException("SIN_CONTENEDORES_PRIORITARIOS",
                    "En la zona " + zonaId + " no hay contenedores para recolectar ahora.");
        }
        return crear(camion, zona, deLaZona, candidatos, Ruta.MANUAL, ahora);
    }

    private Set<String> camionesConRutaActiva() {
        return rutas.activas().stream().map(r -> r.getCamion().getId()).collect(Collectors.toSet());
    }

    // Los prioritarios de la zona, del más lleno al menos lleno, hasta el máximo de paradas
    private static List<Contenedor> candidatos(List<Contenedor> deLaZona, Map<String, Evaluacion> evaluaciones) {
        return deLaZona.stream()
                .filter(c -> evaluaciones.get(c.getId()).esPrioritario())
                .sorted(Comparator.comparingDouble((Contenedor c) -> evaluaciones.get(c.getId()).lectura()).reversed())
                .limit(Parametros.MAX_PARADAS_POR_RUTA)
                .toList();
    }

    private Ruta crear(Camion camion, Zona zona, List<Contenedor> deLaZona, List<Contenedor> candidatos, String origen,
            Instant ahora) {
        Coordenada base = new Coordenada(zona.getBaseLat(), zona.getBaseLng());
        Map<String, Contenedor> porId = candidatos.stream().collect(Collectors.toMap(Contenedor::getId, Function.identity()));
        RutaPlanificada plan = PlanificadorDeRutas.planificar(base, aPuntos(candidatos));
        // Lo que se habría recorrido visitando todos los contenedores, para medir el ahorro
        double kmRutaFija = PlanificadorDeRutas.planificar(base, aPuntos(deLaZona)).km();

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
            ruta.agregarParada(contenedor, reloj);
        }
        return rutas.save(ruta);
    }

    private static List<Punto> aPuntos(List<Contenedor> lista) {
        return lista.stream().map(c -> new Punto(c.getId(), c.posicion())).toList();
    }
}
