package ar.edu.uade.ecoruta;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Cada vez que arranca el contexto de pruebas, la base queda como recién instalada. */
@TestConfiguration(proxyBeanMethods = false)
class BaseLimpia {

    @Bean
    FlywayMigrationStrategy borrarYMigrar() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
