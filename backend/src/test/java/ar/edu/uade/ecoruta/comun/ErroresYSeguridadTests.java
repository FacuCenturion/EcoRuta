package ar.edu.uade.ecoruta.comun;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.error.EstadoInvalidoException;
import ar.edu.uade.ecoruta.comun.error.ManejadorDeErrores;
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.comun.seguridad.ConfiguracionSeguridad;

/** Comprueba que todos los errores salen con la forma {codigo, mensaje} y el código HTTP de la guía. */
@WebMvcTest(ErroresYSeguridadTests.ControladorDePrueba.class)
@Import({ ConfiguracionSeguridad.class, ManejadorDeErrores.class, ErroresYSeguridadTests.ControladorDePrueba.class })
class ErroresYSeguridadTests {

    @Autowired
    MockMvc mvc;

    @Test
    void sinTokenResponde401() throws Exception {
        mvc.perform(get("/api/v1/prueba/existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void swaggerEsPublico() throws Exception {
        mvc.perform(get("/api/docs")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    }

    @Test
    void recursoInexistenteResponde404() throws Exception {
        mvc.perform(get("/api/v1/prueba/Z9-99").with(user("operador")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CONTENEDOR_NO_EXISTE"))
                .andExpect(jsonPath("$.mensaje").value("No existe el contenedor Z9-99."));
    }

    @Test
    void accionFueraDeEstadoResponde409() throws Exception {
        mvc.perform(post("/api/v1/prueba/decidir").with(user("operador")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("INCIDENTE_YA_DECIDIDO"));
    }

    @Test
    void datosInvalidosResponden400() throws Exception {
        mvc.perform(post("/api/v1/prueba/lectura").with(user("operador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"llenado\": 140}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void jsonRotoResponde400() throws Exception {
        mvc.perform(post("/api/v1/prueba/lectura").with(user("operador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{llenado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    record Lectura(@Min(0) @Max(100) int llenado) {
    }

    @RestController
    static class ControladorDePrueba {

        @GetMapping("/api/v1/prueba/{id}")
        String buscar(@PathVariable String id) {
            if (!id.equals("existe")) {
                throw new RecursoNoEncontradoException("CONTENEDOR_NO_EXISTE", "No existe el contenedor " + id + ".");
            }
            return "ok";
        }

        @PostMapping("/api/v1/prueba/decidir")
        void decidir() {
            throw new EstadoInvalidoException("INCIDENTE_YA_DECIDIDO", "El incidente 12 ya fue confirmado.");
        }

        @PostMapping("/api/v1/prueba/lectura")
        void lectura(@Valid @RequestBody Lectura lectura) {
        }
    }
}
