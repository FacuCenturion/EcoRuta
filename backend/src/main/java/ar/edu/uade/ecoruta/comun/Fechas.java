package ar.edu.uade.ecoruta.comun;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** La base guarda las fechas en UTC; la API las devuelve con la hora de Buenos Aires. */
public final class Fechas {

    public static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    private Fechas() {
    }

    public static OffsetDateTime enBuenosAires(OffsetDateTime fecha) {
        return fecha == null ? null : fecha.atZoneSameInstant(BUENOS_AIRES).toOffsetDateTime();
    }

    public static OffsetDateTime enBuenosAires(Instant momento) {
        return momento == null ? null : momento.atZone(BUENOS_AIRES).toOffsetDateTime();
    }

    /** Para guardar un momento en una columna TIMESTAMPTZ. */
    public static OffsetDateTime paraLaBase(Instant momento) {
        return momento == null ? null : momento.atOffset(ZoneOffset.UTC);
    }

    /** Las 0 h de Buenos Aires del día al que pertenece ese momento. */
    public static Instant inicioDelDia(Instant momento) {
        return momento.atZone(BUENOS_AIRES).toLocalDate().atStartOfDay(BUENOS_AIRES).toInstant();
    }
}
