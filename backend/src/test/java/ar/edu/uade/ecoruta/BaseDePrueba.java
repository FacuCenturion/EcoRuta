package ar.edu.uade.ecoruta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Prueba de integración contra un PostgreSQL de verdad. Usa Docker (Testcontainers) si está;
 * si no, la base local ecoruta_test (ver backend/README.md). Si no hay ninguna de las dos,
 * la prueba se saltea. La base se borra y se vuelve a migrar al arrancar.
 *
 * El emulador está prendido pero no avanza solo: las pruebas lo hacen avanzar a mano.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5432/ecoruta_test}",
        "spring.datasource.username=${TEST_DB_USER:ecoruta}",
        "spring.datasource.password=${TEST_DB_PASSWORD:ecoruta}",
        "spring.flyway.clean-disabled=false",
        "ecoruta.emulador.activo=true",
        "ecoruta.emulador.automatico=false",
})
@Import({ TestcontainersConfiguration.class, BaseLimpia.class })
@EnabledIf("ar.edu.uade.ecoruta.DisponibilidadDeBase#hayBase")
public @interface BaseDePrueba {
}
