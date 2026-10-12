package ar.edu.uade.ecoruta.camiones;

import ar.edu.uade.ecoruta.comun.Coordenada;

/** Camión con los mismos nombres de campo que el tipo Camion del front. Las paradas llegan con las rutas. */
public record CamionDto(String id, int numero, int zonaId, String chofer, String estado, int cargaPct,
        Coordenada posicion, double kmRecorridosHoy) {

    static CamionDto de(Camion c) {
        String chofer = c.getChofer() == null ? null : c.getChofer().getNombre();
        return new CamionDto(c.getId(), c.getNumero(), c.getZonaId(), chofer, c.getEstado(), c.getCargaPct(),
                new Coordenada(c.getLat(), c.getLng()), c.getKmRecorridosHoy().doubleValue());
    }
}
