package ar.edu.uade.ecoruta.zonas;

import java.util.List;

import ar.edu.uade.ecoruta.comun.Coordenada;

/**
 * Zona como la espera el front. No lleva los contornos: son pesados y no cambian,
 * así que el front los sigue leyendo de src/data/limites-zonas.json.
 */
public record ZonaDto(int id, String empresa, List<Integer> comunas, String color, Coordenada centro,
        Coordenada base) {

    static ZonaDto de(Zona zona) {
        return new ZonaDto(zona.getId(), zona.getEmpresa(), zona.getComunas(), zona.getColor(),
                new Coordenada(zona.getCentroLat(), zona.getCentroLng()),
                new Coordenada(zona.getBaseLat(), zona.getBaseLng()));
    }
}
