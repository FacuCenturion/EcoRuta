package ar.edu.uade.ecoruta.emulador;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * El emulador solo existe si ecoruta.emulador.activo=true. Cuando haya sensores reales
 * se apaga con esa propiedad y el resto del sistema sigue igual, con la hora real.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "ecoruta.emulador.activo", havingValue = "true")
class ConfiguracionDelEmulador {
}
