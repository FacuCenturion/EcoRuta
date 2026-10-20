package ar.edu.uade.ecoruta.rutas;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.comun.error.EstadoInvalidoException;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.contenedores.Prioridad;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;
import ar.edu.uade.ecoruta.contenedores.ServicioDePrioridad;
import ar.edu.uade.ecoruta.zonas.ZonaRepositorio;

/**
 * Paradas que aparecen con el camión ya en ruta. Si un contenedor del sector se vuelve urgente
 * (pasó el 95% o desborda antes de una hora y media) y no está en ninguna ruta, se le propone
 * al chofer sumarlo donde menos desvío agrega. El chofer acepta (o no contesta y se acepta
 * solo) o mantiene la ruta: en ese caso el contenedor espera al próximo despacho.
 *
 * Solo para los camiones que maneja un chofer con la app: el emulador no contesta propuestas.
 */
@Service
public class ServicioDeParadasNuevas {

    private final RutaRepositorio rutas;
    private final PropuestaRepositorio propuestas;
    private final ContenedorRepositorio contenedores;
    private final ServicioDePrioridad prioridad;
    private final ZonaRepositorio zonas;
    private final ServicioDeTrazado trazado;

    ServicioDeParadasNuevas(RutaRepositorio rutas, PropuestaRepositorio propuestas, ContenedorRepositorio contenedores,
            ServicioDePrioridad prioridad, ZonaRepositorio zonas, ServicioDeTrazado trazado) {
        this.rutas = rutas;
        this.propuestas = propuestas;
        this.contenedores = contenedores;
        this.prioridad = prioridad;
        this.zonas = zonas;
        this.trazado = trazado;
    }

    /** Revisa las rutas en curso de los choferes con la app. Lo llama el despacho en cada paso. */
    @Transactional
    public List<PropuestaDeParada> proponer(Instant ahora) {
        List<Ruta> activas = rutas.activas();
        Set<String> enAlgunaRuta = new HashSet<>();
        activas.forEach(r -> r.getParadas().stream().filter(Parada::estaPendiente)
                .forEach(p -> enAlgunaRuta.add(p.getContenedor().getId())));

        List<PropuestaDeParada> nuevas = new ArrayList<>();
        for (Ruta ruta : activas) {
            Camion camion = ruta.getCamion();
            if (!camion.isManejadoPorApp() || !ruta.getEstado().equals(Ruta.EN_CURSO)
                    || ruta.proximaParada().isEmpty() || propuestas.pendienteDe(ruta.getId()).isPresent()) {
                continue;
            }
            Set<String> yaPropuestos = new HashSet<>(propuestas.contenedoresPropuestos(ruta.getId()));
            List<Contenedor> delSector = contenedores.findBySectorIdAndActivoTrueOrderById(camion.getSectorId());
            Map<String, Evaluacion> evaluaciones = prioridad.evaluar(delSector, ahora);
            delSector.stream()
                    .filter(c -> !enAlgunaRuta.contains(c.getId()) && !yaPropuestos.contains(c.getId()))
                    .filter(c -> esUrgente(evaluaciones.get(c.getId()), ahora))
                    .max(Comparator.comparingDouble(c -> evaluaciones.get(c.getId()).lectura()))
                    .ifPresent(c -> {
                        PropuestaDeParada propuesta = armar(ruta, c, evaluaciones.get(c.getId()), ahora);
                        nuevas.add(propuestas.save(propuesta));
                        enAlgunaRuta.add(c.getId());
                    });
        }
        return nuevas;
    }

    static boolean esUrgente(Evaluacion e, Instant ahora) {
        return e.lectura() >= Parametros.UMBRAL_DESBORDE || (e.desbordeEstimado() != null
                && !e.desbordeEstimado().isAfter(ahora.plus(Duration.ofMinutes(Parametros.MINUTOS_DE_URGENCIA))));
    }

    // Dónde agrega menos: entre la posición del camión y la próxima parada, entre dos paradas
    // pendientes o entre la última y la base
    private PropuestaDeParada armar(Ruta ruta, Contenedor nuevo, Evaluacion evaluacion, Instant ahora) {
        Camion camion = ruta.getCamion();
        var zona = zonas.findById(camion.getZonaId()).orElseThrow();
        List<Parada> pendientes = ruta.getParadas().stream().filter(Parada::estaPendiente).toList();
        List<Coordenada> puntos = new ArrayList<>();
        puntos.add(camion.posicion());
        pendientes.forEach(p -> puntos.add(p.getContenedor().posicion()));
        puntos.add(new Coordenada(zona.getBaseLat(), zona.getBaseLng()));

        int mejor = 0;
        double menorDesvio = Double.MAX_VALUE;
        for (int i = 0; i + 1 < puntos.size(); i++) {
            double desvio = PlanificadorDeRutas.distanciaKm(puntos.get(i), nuevo.posicion())
                    + PlanificadorDeRutas.distanciaKm(nuevo.posicion(), puntos.get(i + 1))
                    - PlanificadorDeRutas.distanciaKm(puntos.get(i), puntos.get(i + 1));
            if (desvio < menorDesvio) {
                menorDesvio = desvio;
                mejor = i;
            }
        }
        int cerradas = ruta.getParadas().size() - pendientes.size();
        int orden = cerradas + mejor + 1;
        int minutos = (int) Math.round(menorDesvio / Parametros.VELOCIDAD_CAMION_KMH * 60 + Parametros.MINUTOS_POR_PARADA);
        String motivo = evaluacion.lectura() >= Parametros.UMBRAL_PRIORITARIO ? Prioridad.MOTIVO_LLENADO
                : Prioridad.MOTIVO_RIESGO;
        return new PropuestaDeParada(ruta, nuevo, orden, menorDesvio, minutos, motivo, ahora);
    }

    /** El chofer aceptó (o se acabó el tiempo para contestar): el contenedor entra en la ruta. */
    @Transactional
    public void aceptar(PropuestaDeParada propuesta, Instant ahora) {
        validar(propuesta);
        Ruta ruta = propuesta.getRuta();
        boolean yaEnOtra = rutas.activas().stream()
                .filter(r -> !r.getId().equals(ruta.getId()))
                .flatMap(r -> r.getParadas().stream())
                .anyMatch(p -> p.estaPendiente() && p.getContenedor().getId().equals(propuesta.getContenedor().getId()));
        if (yaEnOtra) {
            propuesta.decidir(PropuestaDeParada.VENCIDA, ahora);
            throw new EstadoInvalidoException("PROPUESTA_VENCIDA", "Ese contenedor ya lo tiene otro camión.");
        }
        // No puede entrar antes de una parada que ya se cerró
        int cerradas = (int) ruta.getParadas().stream().filter(p -> !p.estaPendiente()).count();
        int orden = Math.max(propuesta.getOrden(), cerradas + 1);
        ruta.insertarParada(propuesta.getContenedor(), orden, ahora, propuesta.getMotivo(),
                propuesta.getKmExtra().doubleValue(), propuesta.getMinutosExtra());
        propuesta.decidir(PropuestaDeParada.ACEPTADA, ahora);
        trazado.invalidar(ruta.getId());
    }

    /** El chofer mantiene la ruta: el contenedor espera al próximo despacho. */
    @Transactional
    public void rechazar(PropuestaDeParada propuesta, Instant ahora) {
        validar(propuesta);
        propuesta.decidir(PropuestaDeParada.RECHAZADA, ahora);
    }

    private static void validar(PropuestaDeParada propuesta) {
        if (!propuesta.estaPendiente() || !propuesta.getRuta().getEstado().equals(Ruta.EN_CURSO)) {
            throw new EstadoInvalidoException("PROPUESTA_YA_DECIDIDA", "Esa parada nueva ya no está disponible.");
        }
    }
}
