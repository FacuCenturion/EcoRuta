package ar.edu.uade.ecoruta.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.uade.ecoruta.comun.error.ManejadorDeErrores;
import ar.edu.uade.ecoruta.comun.seguridad.ConfiguracionSeguridad;

/** Login y sesión con el token real, sin base de datos: el repositorio de usuarios está simulado. */
@WebMvcTest(AuthControlador.class)
@Import({ ConfiguracionSeguridad.class, ManejadorDeErrores.class, ServicioDeTokens.class })
class AuthControladorTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    PasswordEncoder codificador;

    @MockitoBean
    UsuarioRepositorio usuarios;

    @BeforeEach
    void cargarUsuario() {
        Usuario operador = mock(Usuario.class);
        when(operador.getUsuario()).thenReturn("operador");
        when(operador.getPasswordHash()).thenReturn(codificador.encode("operador123"));
        when(operador.getNombre()).thenReturn("Lucía Gómez");
        when(operador.getCargo()).thenReturn("Operadora del Centro de Monitoreo");
        when(operador.getRol()).thenReturn("operador");
        when(usuarios.findByUsuario("operador")).thenReturn(Optional.of(operador));
    }

    @Test
    void elLoginDevuelveTokenYDatosSinLaContrasena() throws Exception {
        mvc.perform(login("operador", "operador123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.usuario.nombre").value("Lucía Gómez"))
                .andExpect(jsonPath("$.usuario.iniciales").value("LG"))
                .andExpect(jsonPath("$.usuario.rol").value("operador"))
                .andExpect(jsonPath("$.usuario.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.usuario.contrasena").doesNotExist());
    }

    @Test
    void elUsuarioSeBuscaSinImportarMayusculasNiEspacios() throws Exception {
        mvc.perform(login("  Operador ", "operador123")).andExpect(status().isOk());
    }

    @Test
    void contrasenaIncorrectaResponde401() throws Exception {
        mvc.perform(login("operador", "otra"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void usuarioInexistenteRespondeLoMismoQueContrasenaIncorrecta() throws Exception {
        mvc.perform(login("nadie", "operador123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void elTokenDelLoginSirveParaPedirElUsuarioActual() throws Exception {
        String respuesta = mvc.perform(login("operador", "operador123")).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(respuesta, "$.token");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("operador"));
    }

    @Test
    void unTokenAlteradoResponde401() throws Exception {
        String respuesta = mvc.perform(login("operador", "operador123")).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(respuesta, "$.token");
        String alterado = token.substring(0, token.length() - 3) + "abc";

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + alterado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    private static org.springframework.test.web.servlet.RequestBuilder login(String usuario, String contrasena) {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}");
    }
}
