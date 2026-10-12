package ar.edu.uade.ecoruta.sensores;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DetectorDeAnomaliasTests {

    @Test
    void unContenedorQueSeLlenaDeAPocoNoEsIrregular() {
        assertThat(DetectorDeAnomalias.esIrregular(List.of(40, 41, 43, 44, 46, 47))).isFalse();
    }

    @Test
    void lecturasQueSubenYBajanDeGolpeSonIrregulares() {
        assertThat(DetectorDeAnomalias.esIrregular(List.of(52, 61, 49, 62, 48, 60))).isTrue();
    }

    @Test
    void unaRecoleccionEsUnaSolaBajadaYNoCuenta() {
        assertThat(DetectorDeAnomalias.esIrregular(List.of(82, 85, 88, 4, 5, 6))).isFalse();
    }

    @Test
    void variacionesChicasNoCuentanAunqueCambienDeSentido() {
        assertThat(DetectorDeAnomalias.esIrregular(List.of(50, 52, 50, 52, 50, 52))).isFalse();
    }

    @Test
    void hacenFaltaSeisLecturasParaDecidir() {
        assertThat(DetectorDeAnomalias.esIrregular(List.of(52, 61, 49, 62, 48))).isFalse();
    }

    @Test
    void soloSeMiranLasUltimasSeis() {
        // Las irregulares quedaron atrás: las últimas seis suben parejo
        assertThat(DetectorDeAnomalias.esIrregular(List.of(52, 61, 49, 62, 48, 60, 61, 62, 63, 64, 65, 66))).isFalse();
    }
}
