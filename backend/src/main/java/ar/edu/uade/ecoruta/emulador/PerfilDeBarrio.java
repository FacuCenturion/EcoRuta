package ar.edu.uade.ecoruta.emulador;

import java.time.Instant;
import java.time.ZonedDateTime;

import ar.edu.uade.ecoruta.comun.Fechas;

/**
 * Cómo se genera la basura según el uso del barrio. Lo conoce solo el emulador: el sistema
 * tiene que aprenderlo de las lecturas (ver PatronesDeLlenado). Cada perfil está normalizado
 * para que el promedio de la semana sea 1: cambia cuándo se llena, no cuánto.
 */
enum PerfilDeBarrio {

    /** Oficinas y comercios: pico al mediodía, casi nada de noche y poco el fin de semana. */
    OFICINAS(new double[] {
            0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.6, 1.0, 1.6, 1.8, 2.2, // 0 a 11
            2.4, 2.4, 2.0, 1.6, 1.4, 1.2, 0.9, 0.6, 0.4, 0.3, 0.2, 0.2, // 12 a 23
    }, new double[] { 1, 1, 1, 1, 1, 0.4, 0.25 }),

    /** Bares y restaurantes: pico de noche, más los viernes y sábados. */
    GASTRONOMICO(new double[] {
            2.0, 1.6, 1.0, 0.5, 0.3, 0.2, 0.2, 0.3, 0.4, 0.5, 0.6, 0.8, // 0 a 11
            1.3, 1.4, 1.0, 0.6, 0.6, 0.7, 0.9, 1.2, 1.7, 2.2, 2.5, 2.4, // 12 a 23
    }, new double[] { 0.7, 0.8, 0.8, 0.9, 1.3, 1.5, 1.1 }),

    /** Viviendas: pico de 18 a 21, cuando los vecinos sacan la basura. Es el ritmo fijo del front. */
    RESIDENCIAL(new double[] {
            0.3, 0.3, 0.3, 0.3, 0.3, 0.3, 0.6, 0.8, 1.0, 1.0, 1.0, 1.0, // 0 a 11
            1.1, 1.1, 1.1, 1.2, 1.2, 1.2, 2.0, 2.2, 2.0, 1.5, 0.8, 0.5, // 12 a 23
    }, new double[] { 1, 1, 1, 1, 1.1, 1.2, 1.1 });

    private final double[] porHora;
    private final double[] porDia; // lunes = 0
    private final double normalizacion;

    PerfilDeBarrio(double[] porHora, double[] porDia) {
        this.porHora = porHora;
        this.porDia = porDia;
        double suma = 0;
        for (double dia : porDia) {
            for (double hora : porHora) {
                suma += dia * hora;
            }
        }
        this.normalizacion = suma / (7 * 24);
    }

    /** Cuánto más (o menos) que el promedio se genera en ese momento. */
    double factor(Instant momento) {
        ZonedDateTime local = momento.atZone(Fechas.BUENOS_AIRES);
        return porHora[local.getHour()] * porDia[local.getDayOfWeek().getValue() - 1] / normalizacion;
    }
}
