package ar.edu.uade.ecoruta.contenedores;

import java.time.OffsetDateTime;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Fechas;

/** Contenedor con los mismos nombres de campo que el tipo Contenedor del front. */
public record ContenedorDto(String id, String direccion, int zonaId, Coordenada posicion, double velocidad,
        int llenado, boolean anomalia, long reportesVecinales, OffsetDateTime ultimaLectura) {

    static ContenedorDto de(Contenedor c, long reportesVecinales) {
        return new ContenedorDto(c.getId(), c.getDireccion(), c.getZonaId(), new Coordenada(c.getLat(), c.getLng()),
                c.getVelocidadLlenado().doubleValue(), c.getLlenado(), c.isAnomalia(), reportesVecinales,
                Fechas.enBuenosAires(c.getUltimaLecturaAt()));
    }
}
