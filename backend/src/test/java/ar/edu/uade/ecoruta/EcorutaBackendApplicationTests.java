package ar.edu.uade.ecoruta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * Levanta la aplicación completa contra un PostgreSQL de verdad (Testcontainers)
 * y comprueba que las migraciones cargan los datos de ejemplo.
 * Necesita Docker: si no está, estas pruebas se saltean.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIf("dockerDisponible")
class EcorutaBackendApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    static boolean dockerDisponible() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

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

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }
}
