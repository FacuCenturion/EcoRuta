package ar.edu.uade.ecoruta.rutas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.rutas.SeleccionDeParadas.Eleccion;
import ar.edu.uade.ecoruta.rutas.SeleccionDeParadas.Opcion;

class SeleccionDeParadasTests {

    private static final Instant AHORA = Instant.parse("2026-10-19T15:00:00Z");
    private static final Instant LIMITE = AHORA.plusSeconds(90 * 60);

    // 0,001 grados de longitud en Buenos Aires son unos 92 metros
    private static Coordenada aMetrosAlEste(double metros) {
        return new Coordenada(-34.60, -58.40 + metros / 92_000);
    }

    @Test
    void sumaElContenedorAMedioLlenarQueQuedaCerca() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(LIMITE, List.of(
                new Opcion("lleno", aMetrosAlEste(0), 88, "llenado", null),
                new Opcion("vecino", aMetrosAlEste(150), 60, null, null)));

        assertThat(elegidos).containsExactly(new Eleccion("lleno", "llenado", false), new Eleccion("vecino", "de_paso", false));
    }

    @Test
    void noSeDesviaPorUnoQueQuedaLejos() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(LIMITE, List.of(
                new Opcion("lleno", aMetrosAlEste(0), 88, "llenado", null),
                new Opcion("lejos", aMetrosAlEste(800), 70, null, null)));

        assertThat(elegidos).extracting(Eleccion::id).containsExactly("lleno");
    }

    @Test
    void noPasaPorUnoCercanoQueEstaCasiVacio() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(LIMITE, List.of(
                new Opcion("lleno", aMetrosAlEste(0), 88, "llenado", null),
                new Opcion("vacio", aMetrosAlEste(100), 30, null, null)));

        assertThat(elegidos).extracting(Eleccion::id).containsExactly("lleno");
    }

    @Test
    void unCamionNoSaleSoloPorParadasDePaso() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(LIMITE, List.of(
                new Opcion("a", aMetrosAlEste(0), 70, null, null),
                new Opcion("b", aMetrosAlEste(100), 65, null, null)));

        assertThat(elegidos).isEmpty();
    }

    @Test
    void losPrioritariosVanPrimeroYConservanSuMotivo() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(LIMITE, List.of(
                new Opcion("porTiempo", aMetrosAlEste(0), 45, "tiempo", null),
                new Opcion("dePaso", aMetrosAlEste(100), 75, null, null),
                new Opcion("porLlenado", aMetrosAlEste(200), 90, "llenado", null)));

        assertThat(elegidos).containsExactly(new Eleccion("porLlenado", "llenado", false),
                new Eleccion("porTiempo", "tiempo", false), new Eleccion("dePaso", "de_paso", false));
    }

    @Test
    void entraPrimeroElQueSePronosticaQueDesbordaAntesAunqueHoyEsteMenosLleno() {
        List<Opcion> zona = new ArrayList<>();
        // 14 contenedores al 85% que desbordan dentro de 6 horas
        for (int i = 0; i < 14; i++) {
            zona.add(new Opcion("lleno" + i, aMetrosAlEste(i * 10), 85, "llenado", AHORA.plusSeconds(6 * 3600)));
        }
        // Uno de oficinas al 70% que al mediodía desborda en 40 minutos
        zona.add(new Opcion("oficina", aMetrosAlEste(500), 70, "riesgo", AHORA.plusSeconds(40 * 60)));

        List<Eleccion> elegidos = SeleccionDeParadas.elegir(LIMITE, zona);

        assertThat(elegidos).hasSize(14);
        assertThat(elegidos.get(0)).isEqualTo(new Eleccion("oficina", "riesgo", true));
        assertThat(elegidos.subList(1, 14)).noneMatch(Eleccion::urgente);
    }

    @Test
    void lasParadasDePasoNoSuperanElMaximoDeLaRuta() {
        List<Opcion> zona = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            zona.add(new Opcion("lleno" + i, aMetrosAlEste(i * 10), 85, "llenado", null));
        }
        for (int i = 0; i < 6; i++) {
            zona.add(new Opcion("paso" + i, aMetrosAlEste(i * 10 + 5), 55 + i, null, null));
        }

        List<Eleccion> elegidos = SeleccionDeParadas.elegir(LIMITE, zona);

        // 12 prioritarios y solo las 2 paradas de paso más llenas, hasta el máximo de 14
        assertThat(elegidos).hasSize(14);
        assertThat(elegidos.subList(12, 14)).extracting(Eleccion::id).containsExactly("paso5", "paso4");
    }
}
