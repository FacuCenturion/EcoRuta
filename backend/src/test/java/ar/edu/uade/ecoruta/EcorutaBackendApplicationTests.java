package ar.edu.uade.ecoruta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Las migraciones cargan los datos de ejemplo y la aplicación arranca contra PostgreSQL. */
@BaseDePrueba
class EcorutaBackendApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void lasMigracionesCarganLosDatosDeEjemplo() {
        assertThat(contar("zona")).isEqualTo(7);
        assertThat(contar("contenedor")).isEqualTo(126);
        assertThat(contar("camion")).isEqualTo(7);
        assertThat(contar("usuario")).isEqualTo(9);
    }

    @Test
    void cadaCamionTieneUnChoferAsignado() {
        Integer sinChofer = jdbc.queryForObject(
                "SELECT count(*) FROM camion c LEFT JOIN usuario u ON u.id = c.chofer_id "
                        + "WHERE u.rol IS DISTINCT FROM 'chofer'", Integer.class);
        assertThat(sinChofer).isZero();
    }

    @Test
    void cadaContenedorTieneSuNivelInicial() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM contenedor WHERE llenado_inicial IS NULL", Integer.class))
                .isZero();
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }
}
