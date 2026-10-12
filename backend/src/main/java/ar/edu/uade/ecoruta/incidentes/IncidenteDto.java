package ar.edu.uade.ecoruta.incidentes;

import java.time.OffsetDateTime;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.Contenedor;

/** Incidente con los mismos nombres de campo que el tipo Incidente del front. */
public record IncidenteDto(long id, String tipo, String estado, String contenedorId, Integer zonaId,
        String direccion, String descripcion, String sugerencia, OffsetDateTime creado, Integer camionNumero,
        String origen) {

    static IncidenteDto de(Incidente i) {
        Contenedor contenedor = i.getContenedor();
        Camion camion = i.getCamion();
        Integer zonaId = contenedor != null ? Integer.valueOf(contenedor.getZonaId())
                : camion != null ? Integer.valueOf(camion.getZonaId()) : null;
        return new IncidenteDto(i.getId(), i.getTipo(), i.getEstado(),
                contenedor == null ? null : contenedor.getId(), zonaId,
                contenedor == null ? null : contenedor.getDireccion(),
                i.getDescripcion(), i.getSugerencia(), Fechas.enBuenosAires(i.getCreadoAt()),
                camion == null ? null : camion.getNumero(), i.getOrigen());
    }
}
