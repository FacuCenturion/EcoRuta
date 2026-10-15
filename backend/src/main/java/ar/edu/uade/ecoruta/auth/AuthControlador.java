package ar.edu.uade.ecoruta.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthControlador {

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final ServicioDeTokens tokens;

    AuthControlador(UsuarioRepositorio usuarios, PasswordEncoder codificador, ServicioDeTokens tokens) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.tokens = tokens;
    }

    record PedidoLogin(@NotBlank String usuario, @NotBlank String contrasena) {
    }

    record RespuestaLogin(String token, UsuarioDto usuario) {
    }

    @PostMapping("/login")
    RespuestaLogin iniciarSesion(@Valid @RequestBody PedidoLogin pedido) {
        // El mismo error si no existe el usuario o si la contraseña está mal,
        // para no revelar qué usuarios existen.
        Usuario usuario = usuarios.findByUsuario(pedido.usuario().trim().toLowerCase())
                .filter(u -> codificador.matches(pedido.contrasena(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Usuario o contraseña incorrectos."));
        return new RespuestaLogin(tokens.crear(usuario), UsuarioDto.de(usuario));
    }

    @GetMapping("/me")
    UsuarioDto usuarioActual(@AuthenticationPrincipal Jwt token) {
        return usuarios.findByUsuario(token.getSubject())
                .map(UsuarioDto::de)
                .orElseThrow(() -> new BadCredentialsException("El usuario del token ya no existe."));
    }
}
