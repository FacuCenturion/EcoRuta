package ar.edu.uade.ecoruta.contenedores;

import java.time.OffsetDateTime;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;

/**
 * Contenedor con los mismos nombres de campo que el tipo Contenedor del front.
 *
 * @param nivel          bajo, medio, prioritario o desborde: el color en el mapa
 * @param motivo         llenado, riesgo, reporte o tiempo; vacío si no hace falta ir a buscarlo
 * @param nivelEstimado  solo viene cuando el sensor está sin señal o congelado
 * @param desbordeEstimado a qué hora se pronostica que desborda; vacío si no es en las próximas 48 h
 * @param desbordeDesde    desde cuándo está lleno; vacío si no lo está
 */
public record ContenedorDto(String id, String direccion, String barrio, int zonaId, int sectorId, String tipo,
        int capacidadLitros, Coordenada posicion, double velocidad,
        int llenado, String nivel, String motivo, boolean anomalia, String estadoSensor, Integer nivelEstimado,
        long reportesVecinales, OffsetDateTime ultimaLectura, OffsetDateTime ultimaRecoleccion,
        OffsetDateTime desbordeEstimado, OffsetDateTime desbordeDesde) {

    static ContenedorDto de(Contenedor c, Evaluacion evaluacion, long reportesVecinales) {
        return new ContenedorDto(c.getId(), c.getDireccion(), c.getBarrio(), c.getZonaId(), c.getSectorId(), c.getTipo(),
                c.getCapacidadLitros(), c.posicion(), c.velocidad(),
                c.getLlenado(), evaluacion.nivel(), evaluacion.motivo(), c.isAnomalia(), c.getEstadoSensor(),
                c.getNivelEstimado(), reportesVecinales, Fechas.enBuenosAires(c.getUltimaLecturaAt()),
                Fechas.enBuenosAires(c.getUltimaRecoleccionAt()), Fechas.enBuenosAires(evaluacion.desbordeEstimado()),
                Fechas.enBuenosAires(c.getDesbordeDesde()));
    }
}
