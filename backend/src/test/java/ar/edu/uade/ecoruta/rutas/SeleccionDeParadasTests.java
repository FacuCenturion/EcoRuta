package ar.edu.uade.ecoruta.rutas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.rutas.SeleccionDeParadas.Eleccion;
import ar.edu.uade.ecoruta.rutas.SeleccionDeParadas.Opcion;

class SeleccionDeParadasTests {

    // 0,001 grados de longitud en Buenos Aires son unos 92 metros
    private static Coordenada aMetrosAlEste(double metros) {
        return new Coordenada(-34.60, -58.40 + metros / 92_000);
    }

    @Test
    void sumaElContenedorAMedioLlenarQueQuedaCerca() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(List.of(
                new Opcion("lleno", aMetrosAlEste(0), 88, "llenado"),
                new Opcion("vecino", aMetrosAlEste(150), 60, null)));

        assertThat(elegidos).containsExactly(new Eleccion("lleno", "llenado"), new Eleccion("vecino", "de_paso"));
    }

    @Test
    void noSeDesviaPorUnoQueQuedaLejos() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(List.of(
                new Opcion("lleno", aMetrosAlEste(0), 88, "llenado"),
                new Opcion("lejos", aMetrosAlEste(800), 70, null)));

        assertThat(elegidos).extracting(Eleccion::id).containsExactly("lleno");
    }

    @Test
    void noPasaPorUnoCercanoQueEstaCasiVacio() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(List.of(
                new Opcion("lleno", aMetrosAlEste(0), 88, "llenado"),
                new Opcion("vacio", aMetrosAlEste(100), 30, null)));

        assertThat(elegidos).extracting(Eleccion::id).containsExactly("lleno");
    }

    @Test
    void unCamionNoSaleSoloPorParadasDePaso() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(List.of(
                new Opcion("a", aMetrosAlEste(0), 70, null),
                new Opcion("b", aMetrosAlEste(100), 65, null)));

        assertThat(elegidos).isEmpty();
    }

    @Test
    void losPrioritariosVanPrimeroYConservanSuMotivo() {
        List<Eleccion> elegidos = SeleccionDeParadas.elegir(List.of(
                new Opcion("porTiempo", aMetrosAlEste(0), 45, "tiempo"),
                new Opcion("dePaso", aMetrosAlEste(100), 75, null),
                new Opcion("porLlenado", aMetrosAlEste(200), 90, "llenado")));

        assertThat(elegidos).containsExactly(new Eleccion("porLlenado", "llenado"), new Eleccion("porTiempo", "tiempo"),
                new Eleccion("dePaso", "de_paso"));
    }

    @Test
    void lasParadasDePasoNoSuperanElMaximoDeLaRuta() {
        List<Opcion> zona = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            zona.add(new Opcion("lleno" + i, aMetrosAlEste(i * 10), 85, "llenado"));
        }
        for (int i = 0; i < 6; i++) {
            zona.add(new Opcion("paso" + i, aMetrosAlEste(i * 10 + 5), 55 + i, null));
        }

        List<Eleccion> elegidos = SeleccionDeParadas.elegir(zona);

        // 12 prioritarios y solo las 2 paradas de paso más llenas, hasta el máximo de 14
        assertThat(elegidos).hasSize(14);
        assertThat(elegidos.subList(12, 14)).extracting(Eleccion::id).containsExactly("paso5", "paso4");
    }
}
