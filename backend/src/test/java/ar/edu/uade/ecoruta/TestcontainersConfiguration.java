package ar.edu.uade.ecoruta;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Solo se usa si hay Docker. Sin Docker, las pruebas van contra la base local ecoruta_test. */
@TestConfiguration(proxyBeanMethods = false)
@Conditional(TestcontainersConfiguration.HayDocker.class)
class TestcontainersConfiguration {

    static class HayDocker implements Condition {
        @Override
        public boolean matches(ConditionContext contexto, AnnotatedTypeMetadata metadatos) {
            return DisponibilidadDeBase.hayDocker();
        }
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }
}
