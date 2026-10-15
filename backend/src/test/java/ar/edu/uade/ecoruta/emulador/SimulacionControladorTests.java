package ar.edu.uade.ecoruta.emulador;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.uade.ecoruta.BaseDePrueba;

/** El recorrido por la API con usuarios reales de la base: login, permisos y reinicio. */
@BaseDePrueba
@AutoConfigureMockMvc
class SimulacionControladorTests {

    @Autowired
    MockMvc mvc;

    @Test
    void elDirectorPuedeReiniciarLaDemo() throws Exception {
        mvc.perform(post("/api/v1/simulacion/reiniciar").header("Authorization", "Bearer " + token("director", "director123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.enPausa").value(false));
    }

    @Test
    void elOperadorNoPuedeReiniciarLaDemo() throws Exception {
        mvc.perform(post("/api/v1/simulacion/reiniciar").header("Authorization", "Bearer " + token("operador", "operador123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SIN_PERMISO"));
    }

    @Test
    void conElTokenDelLoginSeLeenLosDatosDeLaBase() throws Exception {
        String token = token("operador", "operador123");
        mvc.perform(get("/api/v1/zonas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/v1/contenedores?zonaId=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(180));
        mvc.perform(get("/api/v1/contenedores?sectorId=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(55))
                .andExpect(jsonPath("$[0].barrio").exists())
                .andExpect(jsonPath("$[0].tipo").exists());
        mvc.perform(get("/api/v1/sectores").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].nombre").value("Retiro y San Nicolás"))
                .andExpect(jsonPath("$[0].camionId").value("C22"));
    }

    @Test
    void unChoferNoEntraAlPanel() throws Exception {
        mvc.perform(get("/api/v1/contenedores").header("Authorization", "Bearer " + token("chofer22", "chofer123")))
                .andExpect(status().isForbidden());
    }

    private String token(String usuario, String contrasena) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
