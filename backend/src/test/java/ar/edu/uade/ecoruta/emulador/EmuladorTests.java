package ar.edu.uade.ecoruta.emulador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.edu.uade.ecoruta.BaseDePrueba;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;

/** El emulador y el reinicio de la demo, contra la base de verdad. */
@BaseDePrueba
class EmuladorTests {

    @Autowired
    EmuladorDeSensores emulador;

    @Autowired
    ReinicioDeLaDemo reinicio;

    @Autowired
    RelojDelSistema reloj;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void demoDeCero() {
        reinicio.reiniciar();
    }

    @Test
    void alArrancarCargaElHistorialDeEjemplo() {
        // 24 h de lecturas cada 5 minutos para cada sensor, menos lo que no mandaron los que fallan
        assertThat(contar("SELECT count(*) FROM lectura_sensor")).isBetween(40_000, 180 * 289);
        assertThat(contar("SELECT count(*) FROM contenedor WHERE estado_sensor = 'sin_senal'")).isEqualTo(6);
        assertThat(contar("SELECT count(*) FROM contenedor WHERE estado_sensor = 'congelado'")).isEqualTo(3);
        assertThat(contar("SELECT count(*) FROM contenedor WHERE estado_sensor = 'irregular'")).isEqualTo(1);
        // Dos reportes vecinales, una alerta de llenado irregular y una por cada sensor que falla
        assertThat(contar("SELECT count(*) FROM incidente WHERE tipo = 'reporte_vecinal'")).isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM incidente WHERE tipo = 'llenado_irregular'")).isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM incidente WHERE tipo = 'falla_sensor'")).isEqualTo(9);
    }

    @Test
    void cadaPasoAdelantaCincoMinutosYLosSensoresSanosInforman() {
        Instant antes = reloj.ahora();
        int lecturasAntes = contar("SELECT count(*) FROM lectura_sensor");

        emulador.avanzarPasos(1);

        assertThat(Duration.between(antes, reloj.ahora())).isEqualTo(Duration.ofMinutes(5));
        // Mandan todos menos los 6 sin señal (los congelados mandan, repitiendo el valor).
        // Puede sumarse alguna lectura de un contenedor recién vaciado.
        assertThat(contar("SELECT count(*) FROM lectura_sensor") - lecturasAntes).isBetween(170, 190);
    }

    @Test
    void conVariosPasosSaleAlMenosUnCamion() {
        emulador.avanzarPasos(3);

        assertThat(contar("SELECT count(*) FROM ruta")).isPositive();
        assertThat(contar("SELECT count(*) FROM parada WHERE estado = 'recolectada'")).isPositive();
    }

    @Test
    void elReinicioVuelveTodoAlPuntoDePartida() {
        Instant arranque = reloj.ahora();
        emulador.avanzarPasos(6);
        assertThat(reloj.ahora()).isAfter(arranque);

        reinicio.reiniciar();

        assertThat(reloj.ahora()).isEqualTo(arranque);
        assertThat(contar("SELECT count(*) FROM ruta")).isZero();
        assertThat(contar("SELECT min(id) FROM incidente")).isEqualTo(1); // vuelven a numerarse desde 1
        assertThat(contar("SELECT count(*) FROM camion WHERE estado <> 'en_base' OR carga_pct <> 0")).isZero();
    }

    @Test
    void despuesDeReiniciarLaDemoSaleIgual() {
        emulador.avanzarPasos(6);
        String primeraVez = resumenDeLaDemo();

        reinicio.reiniciar();
        emulador.avanzarPasos(6);

        assertThat(resumenDeLaDemo()).isEqualTo(primeraVez);
    }

    // Una "huella" de lo que pasó: niveles de todos los contenedores, rutas y paradas
    private String resumenDeLaDemo() {
        return jdbc.queryForObject("""
                SELECT (SELECT string_agg(id || ':' || llenado, ',' ORDER BY id) FROM contenedor)
                       || '|' || (SELECT count(*) FROM ruta) || '|' || (SELECT count(*) FROM parada)
                       || '|' || (SELECT count(*) FROM incidente)
                """, String.class);
    }

    private int contar(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor == null ? 0 : valor;
    }
}
