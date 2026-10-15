package ar.edu.uade.ecoruta.contenedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.ModeloDeLlenado.Ritmo;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;

class PrioridadTests {

    // Lunes 12/10/2026, hora de Buenos Aires
    private static Instant lunesALas(int hora) {
        return LocalDateTime.of(2026, 10, 12, hora, 0).atZone(Fechas.BUENOS_AIRES).toInstant();
    }

    private static Instant hace(Instant ahora, int horas) {
        return ahora.minus(Duration.ofHours(horas));
    }

    // Evalúa un contenedor con el sensor sano, recolectado hace poco, con el ritmo fijo
    private static Evaluacion evaluar(int llenado, double velocidad, Instant ahora, long reportes) {
        return Prioridad.evaluar(llenado, null, false, null, ModeloDeLlenado.ritmoFijo(velocidad), ahora, reportes,
                hace(ahora, 5));
    }

    @Test
    void desdeEl80PorCientoEsPrioritarioPorLlenado() {
        assertThat(evaluar(80, 3, lunesALas(10), 0).motivo()).isEqualTo("llenado");
        assertThat(evaluar(79, 1, lunesALas(3), 0).motivo()).isNull();
    }

    @Test
    void unContenedorQueVaADesbordarEnElPicoEsPrioritarioPorRiesgo() {
        // A las 18 con velocidad 5 suma 10, 11 y 10 puntos en las próximas 3 horas: 70 + 31 pasa el 95
        Evaluacion enElPico = evaluar(70, 5, lunesALas(18), 0);
        assertThat(enElPico.motivo()).isEqualTo("riesgo");
        assertThat(enElPico.nivel()).isEqualTo("prioritario");
        // El mismo contenedor de madrugada se llena mucho más despacio
        assertThat(evaluar(70, 5, lunesALas(2), 0).motivo()).isNull();
    }

    @Test
    void elPronosticoDiceAQueHoraDesborda() {
        // 70% a las 18 con velocidad 5: suma 10 de 18 a 19 (80), 11 de 19 a 20 (91),
        // y le faltan 4 de los 10 de la hora siguiente: desborda 24 minutos después de las 20
        Evaluacion evaluacion = evaluar(70, 5, lunesALas(18), 0);
        assertThat(evaluacion.desbordeEstimado()).isEqualTo(lunesALas(20).plus(Duration.ofMinutes(24)));
    }

    @Test
    void sinDesbordeALaVistaNoHayPronostico() {
        // Con un ritmo de 0,1% por hora no llega al 95% en 48 horas
        Ritmo casiQuieto = momento -> 0.1;
        assertThat(ModeloDeLlenado.momentoDeDesborde(20, casiQuieto, lunesALas(10), 48)).isNull();
        // Y uno que ya pasó el 95% desborda ahora
        assertThat(ModeloDeLlenado.momentoDeDesborde(96, casiQuieto, lunesALas(10), 48)).isEqualTo(lunesALas(10));
    }

    @Test
    void elRiesgoUsaElRitmoPropioDelContenedor() {
        // Dos contenedores al 60% a las 12. Con el ritmo fijo ninguno corre riesgo, pero uno
        // está en una zona de oficinas y se aprendió que al mediodía se llena a 15% por hora.
        Ritmo oficinas = momento -> 15;
        Instant mediodia = lunesALas(12);
        Evaluacion conPatron = Prioridad.evaluar(60, null, false, null, oficinas, mediodia, 0, hace(mediodia, 5));
        Evaluacion sinPatron = evaluar(60, 3, mediodia, 0);

        assertThat(conPatron.motivo()).isEqualTo("riesgo");
        assertThat(conPatron.desbordeEstimado()).isEqualTo(mediodia.plus(Duration.ofMinutes(140)));
        assertThat(sinPatron.motivo()).isNull();
    }

    @Test
    void unReporteVecinalAbiertoLoHacePrioritarioAunqueEsteCasiVacio() {
        assertThat(evaluar(10, 2, lunesALas(10), 1).motivo()).isEqualTo("reporte");
    }

    @Test
    void elLlenadoTienePrecedenciaSobreElReporte() {
        assertThat(evaluar(90, 2, lunesALas(10), 2).motivo()).isEqualTo("llenado");
    }

    @Test
    void masDe48HorasSinRecolectarLoHacePrioritarioPorTiempo() {
        Instant ahora = lunesALas(3);
        Ritmo lento = ModeloDeLlenado.ritmoFijo(1);
        // Al 45%: no llega al 80 ni corre riesgo, pero hace 49 horas que nadie lo vacía
        assertThat(Prioridad.evaluar(45, null, false, null, lento, ahora, 0, hace(ahora, 49)).motivo())
                .isEqualTo("tiempo");
        // A las 47 horas todavía no
        assertThat(Prioridad.evaluar(45, null, false, null, lento, ahora, 0, hace(ahora, 47)).motivo()).isNull();
    }

    @Test
    void elTiempoNoAplicaAUnContenedorCasiVacio() {
        Instant ahora = lunesALas(3);
        // Hace 60 horas que no se recolecta, pero está al 10%: no vale la pena mandar un camión
        assertThat(Prioridad.evaluar(10, null, false, null, ModeloDeLlenado.ritmoFijo(1), ahora, 0, hace(ahora, 60))
                .motivo()).isNull();
    }

    @Test
    void sinFechaDeRecoleccionNoSePuedeAplicarLaReglaDelTiempo() {
        assertThat(Prioridad.evaluar(45, null, false, null, ModeloDeLlenado.ritmoFijo(1), lunesALas(3), 0, null)
                .motivo()).isNull();
    }

    @Test
    void conElSensorCaidoSeDecideConElNivelEstimado() {
        // El sensor quedó marcando 30, pero se estima que ya va por 88
        Instant ahora = lunesALas(10);
        Evaluacion evaluacion = Prioridad.evaluar(30, 88, false, null, ModeloDeLlenado.ritmoFijo(3), ahora, 0,
                hace(ahora, 5));
        assertThat(evaluacion.lectura()).isEqualTo(88);
        assertThat(evaluacion.motivo()).isEqualTo("llenado");
        assertThat(evaluacion.nivel()).isEqualTo("prioritario");
    }

    @Test
    void conLecturasIrregularesSeUsaElPromedioDeLasUltimasTres() {
        // La última lectura dio 84, pero viene saltando: el promedio es 70 y no alcanza el umbral
        Instant ahora = lunesALas(3);
        Evaluacion evaluacion = Prioridad.evaluar(84, null, true, 70.0, ModeloDeLlenado.ritmoFijo(1), ahora, 0,
                hace(ahora, 5));
        assertThat(evaluacion.lectura()).isEqualTo(70);
        assertThat(evaluacion.esPrioritario()).isFalse();
    }

    @Test
    void elColorDelMapaSigueLosUmbrales() {
        assertThat(Prioridad.nivelVisual(96, "llenado")).isEqualTo("desborde");
        assertThat(Prioridad.nivelVisual(85, "llenado")).isEqualTo("prioritario");
        assertThat(Prioridad.nivelVisual(60, "riesgo")).isEqualTo("prioritario"); // en riesgo aunque no llegó al 80
        assertThat(Prioridad.nivelVisual(45, "tiempo")).isEqualTo("prioritario");
        assertThat(Prioridad.nivelVisual(60, null)).isEqualTo("medio");
        assertThat(Prioridad.nivelVisual(20, null)).isEqualTo("bajo");
    }

    @Test
    void elRitmoFijoLlenaMasRapidoEnElPicoYElFinDeSemana() {
        double lunesPico = ModeloDeLlenado.incrementoPorHora(4, lunesALas(19));
        double lunesMadrugada = ModeloDeLlenado.incrementoPorHora(4, lunesALas(3));
        double sabadoPico = ModeloDeLlenado.incrementoPorHora(4, lunesALas(19).plusSeconds(5 * 86400));
        assertThat(lunesPico).isCloseTo(8.8, within(0.001)); // 4 x 2.2
        assertThat(lunesMadrugada).isCloseTo(1.2, within(0.001)); // 4 x 0.3
        assertThat(sabadoPico).isCloseTo(10.56, within(0.001)); // 4 x 2.2 x 1.2
    }

    @Test
    void laProyeccionSumaHoraPorHoraYNoPasaDe100() {
        Ritmo cinco = ModeloDeLlenado.ritmoFijo(5);
        // De 9 a 11 el factor es 1: dos horas a velocidad 5 suman 10
        assertThat(ModeloDeLlenado.proyectarEntre(40, cinco, lunesALas(9), lunesALas(11))).isCloseTo(50, within(0.001));
        // Media hora suma la mitad
        assertThat(ModeloDeLlenado.proyectarEntre(40, cinco, lunesALas(9), lunesALas(9).plusSeconds(1800)))
                .isCloseTo(42.5, within(0.001));
        assertThat(ModeloDeLlenado.proyectarEntre(95, ModeloDeLlenado.ritmoFijo(9), lunesALas(18), lunesALas(23)))
                .isEqualTo(100);
    }
}
