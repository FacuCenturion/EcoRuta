package ar.edu.uade.ecoruta.chofer;

import java.time.OffsetDateTime;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.rutas.RutaDto;

/**
 * Todo lo que muestra la app del chofer: su camión y su ruta más nueva.
 *
 * @param manejadoPorApp el chofer empezó el turno en la app; si no, el emulador maneja el camión
 * @param base           de dónde sale y adónde vuelve el camión
 * @param ruta           la ruta más nueva del camión (puede estar terminada); vacía si todavía no tuvo
 * @param reloj          la hora del sistema (simulada mientras corre el emulador)
 */
public record TurnoDto(String chofer, CamionDelChofer camion, boolean manejadoPorApp, Coordenada base, RutaDto ruta,
        OffsetDateTime reloj) {

    public record CamionDelChofer(String id, int numero, int sectorId, String sector, String estado, int cargaPct,
            Coordenada posicion, double kmRecorridosHoy) {
    }
}
