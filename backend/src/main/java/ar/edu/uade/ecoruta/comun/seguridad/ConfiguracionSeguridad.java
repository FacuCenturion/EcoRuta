package ar.edu.uade.ecoruta.comun.seguridad;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Reglas de acceso de la API. Por ahora solo están abiertos Swagger y el chequeo de salud:
 * el resto pide autenticación, que se agrega en el Paso 2 (login con JWT).
 */
@Configuration
public class ConfiguracionSeguridad {

    private static final String[] RUTAS_PUBLICAS = {
            "/api/docs", "/api/docs/**", "/api/openapi", "/api/openapi/**", "/swagger-ui/**",
            "/actuator/health", "/error",
    };

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable()) // API sin cookies: el token va en el header
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((pedido, respuesta, ex) -> escribirError(respuesta,
                                HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO",
                                "Falta el token o venció. Volvé a iniciar sesión."))
                        .accessDeniedHandler((pedido, respuesta, ex) -> escribirError(respuesta,
                                HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO",
                                "Tu rol no tiene permiso para esta acción.")))
                .build();
    }

    @Bean
    CorsConfigurationSource origenesPermitidos(@Value("${ecoruta.cors.origenes}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Api-Key"));
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }

    @Bean
    PasswordEncoder codificadorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }

    // Los filtros de seguridad corren antes que Spring MVC, así que estos dos errores
    // se escriben a mano con la misma forma que ErrorApi. Los textos son fijos y no
    // llevan comillas, por eso no hace falta escaparlos.
    private static void escribirError(HttpServletResponse respuesta, int estado, String codigo, String mensaje)
            throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\"}");
    }
}
