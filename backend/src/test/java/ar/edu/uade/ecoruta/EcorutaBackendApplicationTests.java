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
        assertThat(contar("sector")).isEqualTo(4);
        assertThat(contar("contenedor")).isEqualTo(180);
        assertThat(contar("camion")).isEqualTo(5); // uno por sector y un segundo en el sector 1
        assertThat(contar("usuario")).isEqualTo(7); // director, operador y un chofer por camión
    }

    @Test
    void cadaCamionTieneUnChoferAsignado() {
        Integer sinChofer = jdbc.queryForObject(
                "SELECT count(*) FROM camion c LEFT JOIN usuario u ON u.id = c.chofer_id "
                        + "WHERE u.rol IS DISTINCT FROM 'chofer'", Integer.class);
        assertThat(sinChofer).isZero();
    }

    @Test
    void elSistemaTrabajaSoloConLaZona1YCadaSectorTieneSuCamion() {
        assertThat(contar("contenedor WHERE zona_id <> 1")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT sector_id) FROM camion", Integer.class)).isEqualTo(4);
        // Todos los contenedores reales quedaron dentro de la Comuna 1
        assertThat(contar("contenedor WHERE lat NOT BETWEEN -34.64 AND -34.58 OR lng NOT BETWEEN -58.40 AND -58.35"))
                .isZero();
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
