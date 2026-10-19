package ar.edu.uade.ecoruta.indicadores;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.indicadores.SimulacionRutaFija.Lectura;
import ar.edu.uade.ecoruta.indicadores.SimulacionRutaFija.Resultado;

class SimulacionRutaFijaTests {

    private static final Instant INICIO = LocalDate.of(2026, 10, 19).atStartOfDay(Fechas.BUENOS_AIRES).toInstant();

    // Una lectura por hora desde las 20 h del día anterior, subiendo "porHora" puntos cada hora
    private static List<Lectura> llenandose(int nivelInicial, int porHora) {
        List<Lectura> lecturas = new ArrayList<>();
        int nivel = nivelInicial;
        for (int h = -4; h < 24; h++) {
            lecturas.add(new Lectura(INICIO.plusSeconds(h * 3600L), Math.min(100, nivel)));
            nivel += porHora;
        }
        return lecturas;
    }

    @Test
    void unContenedorLentoSeVisitaTresVecesCasiVacio() {
        Resultado resultado = SimulacionRutaFija.simular(llenandose(10, 2), INICIO);

        // A las 8 tiene 4 + 12 h x 2 = 28; a las 14 y a las 20, 16: siempre por debajo de 50
        assertThat(resultado.visitas()).isEqualTo(3);
        assertThat(resultado.visitasBajas()).isEqualTo(3);
        assertThat(resultado.desbordes()).isZero();
    }

    @Test
    void unContenedorRapidoDesbordaAntesDeLaRondaDeLasOcho() {
        Resultado resultado = SimulacionRutaFija.simular(llenandose(0, 10), INICIO);

        // Desde las 20 h suma 10 por hora: a las 6 de la mañana pasa el 98, antes de la ronda de las 8.
        // (Las lecturas reales llegan a 100 y ahí dejan de subir: por eso arranca de 0.)
        assertThat(resultado.desbordes()).isEqualTo(1);
        assertThat(resultado.visitas()).isEqualTo(3);
    }

    @Test
    void lasRecoleccionesRealesNoCortanElLlenadoDeLaRutaFija() {
        // En la realidad lo vaciaron a las 2 (bajó de 50 a 4), pero el llenado siguió igual:
        // con la ruta fija nadie pasa a las 2, así que acumula y desborda antes de las 8.
        List<Lectura> lecturas = new ArrayList<>();
        int nivel = 30;
        for (int h = -4; h < 24; h++) {
            lecturas.add(new Lectura(INICIO.plusSeconds(h * 3600L), nivel));
            nivel = h == 1 ? 4 : Math.min(100, nivel + 12);
        }

        Resultado resultado = SimulacionRutaFija.simular(lecturas, INICIO);

        assertThat(resultado.desbordes()).isEqualTo(1);
    }

    @Test
    void sinLecturasIgualPasanLasTresRondas() {
        Resultado resultado = SimulacionRutaFija.simular(List.of(), INICIO);

        assertThat(resultado.visitas()).isEqualTo(3);
        assertThat(resultado.visitasBajas()).isEqualTo(3);
    }
}
