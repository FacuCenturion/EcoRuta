package ar.edu.uade.ecoruta.incidentes;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.ecoruta.auth.Usuario;
import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.comun.error.EstadoInvalidoException;
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;

@Service
public class ServicioDeIncidentes {

    // Una alerta de llenado irregular que nadie confirmó se cierra sola pasado este tiempo
    private static final Duration VENCIMIENTO_DE_ALERTA = Duration.ofMinutes(120);

    private final IncidenteRepositorio incidentes;
    private final ContenedorRepositorio contenedores;

    ServicioDeIncidentes(IncidenteRepositorio incidentes, ContenedorRepositorio contenedores) {
        this.incidentes = incidentes;
        this.contenedores = contenedores;
    }

    @Transactional
    public Incidente crearLlenadoIrregular(Contenedor contenedor, Instant ahora) {
        return incidentes.save(new Incidente(Incidente.LLENADO_IRREGULAR, contenedor, null,
                "El nivel sube y baja en pocos minutos: posible persona revolviendo.", "Atención social (108)", null,
                ahora));
    }

    @Transactional
    public Incidente crearNoSePudo(Contenedor contenedor, Camion camion, String motivo, Instant ahora) {
        return incidentes.save(new Incidente(Incidente.NO_SE_PUDO, contenedor, camion,
                "El chofer reportó: " + motivo.toLowerCase() + ".", "Sumar a la próxima ruta", null, ahora));
    }

    @Transactional
    public Incidente crearReporteVecinal(String contenedorId, String comentario, String origen, Instant ahora) {
        Contenedor contenedor = contenedores.findById(contenedorId).orElseThrow(
                () -> new RecursoNoEncontradoException("CONTENEDOR_NO_EXISTE",
                        "No existe el contenedor " + contenedorId + "."));
        String texto = comentario == null || comentario.isBlank() ? "Sin comentario." : comentario.trim();
        return incidentes.save(new Incidente(Incidente.REPORTE_VECINAL, contenedor, null, texto,
                "Enviar cuadrilla de limpieza", origen == null || origen.isBlank() ? "Boti" : origen.trim(), ahora));
    }

    /** El operador confirma o descarta un incidente pendiente. */
    @Transactional
    public Incidente decidir(long incidenteId, String decision, Usuario usuario, Instant ahora) {
        Incidente incidente = incidentes.findById(incidenteId).orElseThrow(
                () -> new RecursoNoEncontradoException("INCIDENTE_NO_EXISTE",
                        "No existe el incidente " + incidenteId + "."));
        if (!incidente.getEstado().equals(Incidente.PENDIENTE)) {
            throw new EstadoInvalidoException("INCIDENTE_YA_DECIDIDO",
                    "El incidente " + incidenteId + " ya está " + incidente.getEstado() + ".");
        }
        incidente.decidir(decision, usuario, ahora);
        return incidente;
    }

    /** Cierra solos los incidentes que ya se resolvieron. Devuelve cuántos cerró. */
    @Transactional
    public int cerrarResueltos(Instant ahora) {
        int cerrados = 0;
        for (Incidente incidente : incidentes.abiertos()) {
            Contenedor contenedor = incidente.getContenedor();
            if (contenedor == null) {
                continue;
            }
            boolean resuelto;
            if (incidente.getTipo().equals(Incidente.LLENADO_IRREGULAR)) {
                // El sensor volvió a la normalidad y, o ya lo vio un operador, o pasó el tiempo de la alerta
                boolean vencida = Duration.between(incidente.getCreadoAt().toInstant(), ahora)
                        .compareTo(VENCIMIENTO_DE_ALERTA) > 0;
                resuelto = !contenedor.isAnomalia()
                        && (incidente.getEstado().equals(Incidente.CONFIRMADO) || vencida);
            } else {
                // Reporte vecinal o "no se pudo": se resuelve cuando un camión lo vacía después
                resuelto = contenedor.getUltimaRecoleccionAt() != null
                        && contenedor.getUltimaRecoleccionAt().isAfter(incidente.getCreadoAt());
            }
            if (resuelto) {
                incidente.resolver();
                cerrados++;
            }
        }
        return cerrados;
    }
}
