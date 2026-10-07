package ar.edu.uade.ecoruta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Comprueba que las contraseñas de prueba del README coinciden con los hashes
 * guardados en V2__datos_iniciales.sql. No necesita base de datos.
 */
class DatosInicialesTests {

    private static final Pattern USUARIO = Pattern.compile("\\('(\\w+)', '(\\$2a\\$[^']+)'");

    @Test
    void lasContrasenasDePruebaCoincidenConLosHashes() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V2__datos_iniciales.sql"));
        Map<String, String> hashes = new HashMap<>();
        Matcher m = USUARIO.matcher(sql);
        while (m.find()) {
            hashes.put(m.group(1), m.group(2));
        }

        var codificador = new BCryptPasswordEncoder();
        assertThat(hashes).hasSize(9);
        assertThat(codificador.matches("director123", hashes.get("director"))).isTrue();
        assertThat(codificador.matches("operador123", hashes.get("operador"))).isTrue();
        hashes.entrySet().stream()
                .filter(e -> e.getKey().startsWith("chofer"))
                .forEach(e -> assertThat(codificador.matches("chofer123", e.getValue())).as(e.getKey()).isTrue());
    }
}
