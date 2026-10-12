package ar.edu.uade.ecoruta.comun;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** La base guarda las fechas en UTC; la API las devuelve con la hora de Buenos Aires. */
public final class Fechas {

    public static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    private Fechas() {
    }

    public static OffsetDateTime enBuenosAires(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(BUENOS_AIRES).toOffsetDateTime();
    }
}
