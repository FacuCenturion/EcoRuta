package ar.edu.uade.ecoruta.contenedores;

import java.time.OffsetDateTime;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;

/**
 * Contenedor con los mismos nombres de campo que el tipo Contenedor del front.
 *
 * @param nivel          bajo, medio, prioritario o desborde: el color en el mapa
 * @param motivo         llenado, riesgo o reporte; vacío si no hace falta ir a buscarlo
 * @param nivelEstimado  solo viene cuando el sensor está sin señal o congelado
 */
public record ContenedorDto(String id, String direccion, int zonaId, Coordenada posicion, double velocidad,
        int llenado, String nivel, String motivo, boolean anomalia, String estadoSensor, Integer nivelEstimado,
        long reportesVecinales, OffsetDateTime ultimaLectura, OffsetDateTime ultimaRecoleccion) {

    static ContenedorDto de(Contenedor c, Evaluacion evaluacion, long reportesVecinales) {
        return new ContenedorDto(c.getId(), c.getDireccion(), c.getZonaId(), c.posicion(), c.velocidad(),
                c.getLlenado(), evaluacion.nivel(), evaluacion.motivo(), c.isAnomalia(), c.getEstadoSensor(),
                c.getNivelEstimado(), reportesVecinales, Fechas.enBuenosAires(c.getUltimaLecturaAt()),
                Fechas.enBuenosAires(c.getUltimaRecoleccionAt()));
    }
}
