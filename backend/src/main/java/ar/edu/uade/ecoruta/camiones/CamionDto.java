package ar.edu.uade.ecoruta.camiones;

import java.util.List;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.rutas.Ruta;
import ar.edu.uade.ecoruta.rutas.RutaDto.ParadaDto;

/**
 * Camión con los mismos nombres de campo que el tipo Camion del front. Las paradas y los
 * km planificados son los de su ruta más nueva; vienen vacíos si todavía no tuvo ninguna.
 */
public record CamionDto(String id, int numero, int zonaId, int sectorId, String chofer, String estado, int cargaPct,
        Coordenada posicion, double kmRecorridosHoy, Long rutaId, String estadoRuta, double kmPlanificados,
        List<ParadaDto> paradas) {

    static CamionDto de(Camion c, Ruta ruta) {
        String chofer = c.getChofer() == null ? null : c.getChofer().getNombre();
        return new CamionDto(c.getId(), c.getNumero(), c.getZonaId(), c.getSectorId(), chofer, c.getEstado(), c.getCargaPct(),
                c.posicion(), c.getKmRecorridosHoy().doubleValue(),
                ruta == null ? null : ruta.getId(),
                ruta == null ? null : ruta.getEstado(),
                ruta == null ? 0 : ruta.getKmPlanificados().doubleValue(),
                ruta == null ? List.of() : ruta.getParadas().stream().map(ParadaDto::de).toList());
    }
}
