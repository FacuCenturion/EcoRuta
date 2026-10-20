package ar.edu.uade.ecoruta.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.uade.ecoruta.BaseDePrueba;

/** El panel abre una conexión y el servidor le avisa cuando algo cambia. */
@BaseDePrueba
@AutoConfigureMockMvc
class EventosTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    CanalDeEventos canal;

    @Test
    void sinTokenNoHayConexion() throws Exception {
        mvc.perform(get("/api/v1/eventos")).andExpect(status().isUnauthorized());
    }

    @Test
    void alConectarseLlegaUnAvisoConLaHoraDelSistema() throws Exception {
        MockHttpServletResponse respuesta = conectar(token("operador", "operador123"));
        assertThat(respuesta.getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(respuesta.getContentAsString()).contains("event:actualizacion").contains("\"reloj\"");
    }

    @Test
    void unCambioHechoDesdeElPanelLlegaComoAviso() throws Exception {
        String token = token("operador", "operador123");
        canal.enviarSiHayCambios(); // lo que haya quedado de otras pruebas, antes de conectarse
        MockHttpServletResponse respuesta = conectar(token);

        mvc.perform(get("/api/v1/contenedores").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        canal.enviarSiHayCambios();
        assertThat(avisos(respuesta)).as("leer no cambia nada").isEqualTo(1);

        mvc.perform(post("/api/v1/simulacion").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"velocidad\":2}"))
                .andExpect(status().isOk());
        canal.enviarSiHayCambios();
        assertThat(avisos(respuesta)).isEqualTo(2);
    }

    @Test
    void variosCambiosSeguidosSeJuntanEnUnSoloAviso() throws Exception {
        String token = token("director", "director123");
        canal.enviarSiHayCambios(); // lo que haya quedado de otras pruebas, antes de conectarse
        MockHttpServletResponse respuesta = conectar(token);
        canal.avisar();
        canal.avisar();
        canal.avisar();
        canal.enviarSiHayCambios();
        canal.enviarSiHayCambios();
        assertThat(avisos(respuesta)).isEqualTo(2);
    }

    private MockHttpServletResponse conectar(String token) throws Exception {
        return mvc.perform(get("/api/v1/eventos").header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
    }

    // El envío programado también puede mandar el aviso: se espera un poco a que llegue
    private static int avisos(MockHttpServletResponse respuesta) throws Exception {
        int cantidad = 0;
        for (int intento = 0; intento < 20; intento++) {
            cantidad = respuesta.getContentAsString().split("event:actualizacion", -1).length - 1;
            Thread.sleep(50);
        }
        return cantidad;
    }

    private String token(String usuario, String contrasena) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
