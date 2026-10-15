package ar.edu.uade.ecoruta;

import java.sql.Connection;
import java.sql.DriverManager;

import org.testcontainers.DockerClientFactory;

/** Decide contra qué base corren las pruebas de integración, o si se saltean. */
public final class DisponibilidadDeBase {

    private DisponibilidadDeBase() {
    }

    public static boolean hayDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static boolean hayBase() {
        if (hayDocker()) {
            return true;
        }
        String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:postgresql://localhost:5432/ecoruta_test");
        DriverManager.setLoginTimeout(2);
        try (Connection conexion = DriverManager.getConnection(url,
                System.getenv().getOrDefault("TEST_DB_USER", "ecoruta"),
                System.getenv().getOrDefault("TEST_DB_PASSWORD", "ecoruta"))) {
            return conexion.isValid(2);
        } catch (Exception ex) {
            return false;
        }
    }
}
