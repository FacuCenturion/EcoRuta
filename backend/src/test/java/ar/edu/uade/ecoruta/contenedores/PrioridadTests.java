package ar.edu.uade.ecoruta.contenedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;

class PrioridadTests {

    // Lunes 12/10/2026, hora de Buenos Aires
    private static Instant lunesALas(int hora) {
        return LocalDateTime.of(2026, 10, 12, hora, 0).atZone(Fechas.BUENOS_AIRES).toInstant();
    }

    @Test
    void desdeEl80PorCientoEsPrioritarioPorLlenado() {
        assertThat(Prioridad.motivo(80, 3, lunesALas(10), 0)).isEqualTo("llenado");
        assertThat(Prioridad.motivo(79, 1, lunesALas(3), 0)).isNull();
    }

    @Test
    void unContenedorQueVaADesbordarEnElPicoEsPrioritarioPorRiesgo() {
        // A las 18 con velocidad 5 suma 10, 11 y 10 puntos en las próximas 3 horas: 70 + 31 pasa el 95
        assertThat(Prioridad.motivo(70, 5, lunesALas(18), 0)).isEqualTo("riesgo");
        // El mismo contenedor de madrugada se llena mucho más despacio
        assertThat(Prioridad.motivo(70, 5, lunesALas(2), 0)).isNull();
    }

    @Test
    void unReporteVecinalAbiertoLoHacePrioritarioAunqueEsteCasiVacio() {
        assertThat(Prioridad.motivo(10, 2, lunesALas(10), 1)).isEqualTo("reporte");
    }

    @Test
    void elLlenadoTienePrecedenciaSobreElReporte() {
        assertThat(Prioridad.motivo(90, 2, lunesALas(10), 2)).isEqualTo("llenado");
    }

    @Test
    void conElSensorCaidoSeDecideConElNivelEstimado() {
        // El sensor quedó marcando 30, pero se estima que ya va por 88
        Evaluacion evaluacion = Prioridad.evaluar(30, 88, false, null, 3, lunesALas(10), 0);
        assertThat(evaluacion.lectura()).isEqualTo(88);
        assertThat(evaluacion.motivo()).isEqualTo("llenado");
        assertThat(evaluacion.nivel()).isEqualTo("prioritario");
    }

    @Test
    void conLecturasIrregularesSeUsaElPromedioDeLasUltimasTres() {
        // La última lectura dio 84, pero viene saltando: el promedio es 70 y no alcanza el umbral
        Evaluacion evaluacion = Prioridad.evaluar(84, null, true, 70.0, 1, lunesALas(3), 0);
        assertThat(evaluacion.lectura()).isEqualTo(70);
        assertThat(evaluacion.esPrioritario()).isFalse();
    }

    @Test
    void elColorDelMapaSigueLosUmbrales() {
        assertThat(Prioridad.nivelVisual(96, "llenado")).isEqualTo("desborde");
        assertThat(Prioridad.nivelVisual(85, "llenado")).isEqualTo("prioritario");
        assertThat(Prioridad.nivelVisual(60, "riesgo")).isEqualTo("prioritario"); // en riesgo aunque no llegó al 80
        assertThat(Prioridad.nivelVisual(60, null)).isEqualTo("medio");
        assertThat(Prioridad.nivelVisual(20, null)).isEqualTo("bajo");
    }

    @Test
    void elModeloLlenaMasRapidoEnElPicoYElFinDeSemana() {
        double lunesPico = ModeloDeLlenado.incrementoPorHora(4, lunesALas(19));
        double lunesMadrugada = ModeloDeLlenado.incrementoPorHora(4, lunesALas(3));
        double sabadoPico = ModeloDeLlenado.incrementoPorHora(4, lunesALas(19).plusSeconds(5 * 86400));
        assertThat(lunesPico).isCloseTo(8.8, within(0.001)); // 4 x 2.2
        assertThat(lunesMadrugada).isCloseTo(1.2, within(0.001)); // 4 x 0.3
        assertThat(sabadoPico).isCloseTo(10.56, within(0.001)); // 4 x 2.2 x 1.2
    }

    @Test
    void laProyeccionSumaHoraPorHoraYNoPasaDe100() {
        // De 9 a 11 el factor es 1: dos horas a velocidad 5 suman 10
        assertThat(ModeloDeLlenado.proyectarEntre(40, 5, lunesALas(9), lunesALas(11))).isCloseTo(50, within(0.001));
        // Media hora suma la mitad
        assertThat(ModeloDeLlenado.proyectarEntre(40, 5, lunesALas(9), lunesALas(9).plusSeconds(1800)))
                .isCloseTo(42.5, within(0.001));
        assertThat(ModeloDeLlenado.proyectarEntre(95, 9, lunesALas(18), lunesALas(23))).isEqualTo(100);
    }
}
