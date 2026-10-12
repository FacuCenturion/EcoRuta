package ar.edu.uade.ecoruta.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Arma el token JWT que el front manda en el header Authorization de cada pedido. */
@Service
public class ServicioDeTokens {

    public static final String CLAIM_ROL = "rol";
    private static final Duration DURACION = Duration.ofHours(8);

    private final JwtEncoder codificador;

    ServicioDeTokens(JwtEncoder codificador) {
        this.codificador = codificador;
    }

    public String crear(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet datos = JwtClaimsSet.builder()
                .subject(usuario.getUsuario())
                .claim(CLAIM_ROL, usuario.getRol())
                .issuedAt(ahora)
                .expiresAt(ahora.plus(DURACION))
                .build();
        JwsHeader encabezado = JwsHeader.with(MacAlgorithm.HS256).build();
        return codificador.encode(JwtEncoderParameters.from(encabezado, datos)).getTokenValue();
    }
}
