package ar.edu.uade.ecoruta.comun.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import ar.edu.uade.ecoruta.auth.ServicioDeTokens;

/**
 * Reglas de acceso de la API. El login, Swagger y el chequeo de salud son públicos;
 * el resto pide un token JWT en el header Authorization. El rol viaja dentro del token.
 */
@Configuration
public class ConfiguracionSeguridad {

    private static final String[] RUTAS_PUBLICAS = {
            "/api/docs", "/api/docs/**", "/api/openapi", "/api/openapi/**", "/api/swagger-ui/**",
            "/actuator/health", "/error",
    };

    @Bean
    SecurityFilterChain filtros(HttpSecurity http, CorsConfigurationSource origenesPermitidos) throws Exception {
        AuthenticationEntryPoint sinToken = (pedido, respuesta, ex) -> escribirError(respuesta,
                HttpServletResponse.SC_UNAUTHORIZED, "NO_AUTENTICADO",
                "Falta el token o venció. Volvé a iniciar sesión.");
        AccessDeniedHandler sinPermiso = (pedido, respuesta, ex) -> escribirError(respuesta,
                HttpServletResponse.SC_FORBIDDEN, "SIN_PERMISO", "Tu rol no tiene permiso para esta acción.");

        return http
                .csrf(csrf -> csrf.disable()) // API sin cookies: el token va en el header
                .cors(cors -> cors.configurationSource(origenesPermitidos))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/me").authenticated()
                        // Los sensores no tienen usuario: el controlador valida el header X-Api-Key
                        .requestMatchers(HttpMethod.POST, "/api/v1/lecturas").permitAll()
                        // Reiniciar la demo borra lo que pasó: solo el director
                        .requestMatchers(HttpMethod.POST, "/api/v1/simulacion/reiniciar").hasRole("DIRECTOR")
                        .requestMatchers("/api/v1/indicadores", "/api/v1/indicadores/**").hasRole("DIRECTOR")
                        // Todo lo demás es del panel. Los endpoints del chofer se agregan
                        // arriba de esta línea cuando existan.
                        .requestMatchers("/api/v1/**").hasAnyRole("DIRECTOR", "OPERADOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(rolDelToken()))
                        .authenticationEntryPoint(sinToken)
                        .accessDeniedHandler(sinPermiso))
                .exceptionHandling(e -> e.authenticationEntryPoint(sinToken).accessDeniedHandler(sinPermiso))
                .build();
    }

    // El claim "rol" del token (director, operador, chofer) pasa a ser ROLE_DIRECTOR, etc.
    private static JwtAuthenticationConverter rolDelToken() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(token -> {
            String rol = token.getClaimAsString(ServicioDeTokens.CLAIM_ROL);
            return rol == null ? List.<GrantedAuthority>of()
                    : List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase()));
        });
        return conversor;
    }

    @Bean
    SecretKey claveDeFirma(@Value("${ecoruta.jwt.secreto}") String secreto) {
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder codificadorDeTokens(SecretKey clave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
    }

    @Bean
    JwtDecoder lectorDeTokens(SecretKey clave) {
        return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
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
