package ar.edu.uade.ecoruta.rutas;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.error.EstadoInvalidoException;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.incidentes.ServicioDeIncidentes;
import ar.edu.uade.ecoruta.zonas.Zona;
import ar.edu.uade.ecoruta.zonas.ZonaRepositorio;

/**
 * Lo que hace el chofer con su ruta: iniciarla y cerrar cada parada. Hoy lo llama el
 * emulador haciendo de chofer; en la Sprint 4 lo va a llamar la app del chofer.
 */
@Service
public class ServicioDeRutas {

    /** Lo que puede elegir el chofer cuando no pudo vaciar un contenedor. Los usa también el emulador. */
    public static final java.util.List<String> MOTIVOS_NO_SE_PUDO = java.util.List.of("Auto estacionado",
            "Obra o calle cortada", "Contenedor dañado", "Basura fuera del contenedor", "Sensor marca mal");

    private final ZonaRepositorio zonas;
    private final ServicioDeIncidentes incidentes;

    ServicioDeRutas(ZonaRepositorio zonas, ServicioDeIncidentes incidentes) {
        this.zonas = zonas;
        this.incidentes = incidentes;
    }

    @Transactional
    public void iniciar(Ruta ruta, Instant ahora) {
        if (!ruta.getEstado().equals(Ruta.PROGRAMADA)) {
            throw new EstadoInvalidoException("RUTA_YA_INICIADA",
                    "La ruta " + ruta.getId() + " ya está " + ruta.getEstado() + ".");
        }
        ruta.iniciar(ahora);
        ruta.getCamion().salirARuta(base(ruta.getCamion()));
    }

    /** El camión llegó al contenedor y lo vació. */
    @Transactional
    public void recolectar(Ruta ruta, Parada parada, Instant ahora) {
        llegar(ruta, parada);
        Contenedor contenedor = parada.getContenedor();
        // Lo que el sistema cree que tenía: el estimado si el sensor no es confiable
        parada.registrarNivelAlLlegar(contenedor.getNivelEstimado() != null ? contenedor.getNivelEstimado()
                : contenedor.getLlenado());
        parada.cerrar(Parada.RECOLECTADA, null, ahora);
        parada.getContenedor().registrarRecoleccion(ahora);
        ruta.getCamion().cargarContenedor();
        terminarSiCorresponde(ruta);
    }

    /** El camión llegó pero no pudo vaciarlo: queda un incidente para el operador. */
    @Transactional
    public void noSePudo(Ruta ruta, Parada parada, String motivo, Instant ahora) {
        llegar(ruta, parada);
        parada.cerrar(Parada.NO_SE_PUDO, motivo, ahora);
        ruta.getCamion().demorar();
        incidentes.crearNoSePudo(parada.getContenedor(), ruta.getCamion(), motivo, ahora);
        terminarSiCorresponde(ruta);
    }

    private void llegar(Ruta ruta, Parada parada) {
        if (!ruta.getEstado().equals(Ruta.EN_CURSO) || !parada.estaPendiente()) {
            throw new EstadoInvalidoException("PARADA_YA_CERRADA",
                    "La parada " + parada.getId() + " ya se cerró o la ruta no está en curso.");
        }
        Camion camion = ruta.getCamion();
        Contenedor contenedor = parada.getContenedor();
        camion.moverA(contenedor.posicion(), PlanificadorDeRutas.distanciaKm(camion.posicion(), contenedor.posicion()));
    }

    private void terminarSiCorresponde(Ruta ruta) {
        if (ruta.proximaParada().isPresent()) {
            return;
        }
        Camion camion = ruta.getCamion();
        Coordenada base = base(camion);
        ruta.terminar();
        camion.volverADescargar(base, PlanificadorDeRutas.distanciaKm(camion.posicion(), base));
    }

    private Coordenada base(Camion camion) {
        Zona zona = zonas.findById(camion.getZonaId()).orElseThrow();
        return new Coordenada(zona.getBaseLat(), zona.getBaseLng());
    }
}
