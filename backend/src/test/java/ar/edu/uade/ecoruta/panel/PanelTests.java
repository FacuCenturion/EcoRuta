package ar.edu.uade.ecoruta.panel;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.uade.ecoruta.BaseDePrueba;
import ar.edu.uade.ecoruta.emulador.EmuladorDeSensores;
import ar.edu.uade.ecoruta.emulador.ReinicioDeLaDemo;

/** Lo que piden las pantallas del panel, con el operador (el rol con menos permisos que las usa). */
@BaseDePrueba
@AutoConfigureMockMvc
class PanelTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReinicioDeLaDemo reinicio;

    @Autowired
    EmuladorDeSensores emulador;

    @Autowired
    JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void demoAndando() throws Exception {
        reinicio.reiniciar();
        emulador.avanzarPasos(3); // ya salieron los primeros camiones
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"operador\",\"contrasena\":\"operador123\"}"))
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(respuesta, "$.token");
    }

    @Test
    void elResumenTieneTodoLoQueMuestraElPanelGeneral() throws Exception {
        mvc.perform(get("/api/v1/panel/resumen").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.camiones.total").value(5))
                .andExpect(jsonPath("$.planificadosHoy").isNumber())
                .andExpect(jsonPath("$.porcentajeHecho").isNumber())
                .andExpect(jsonPath("$.incidentesPendientes").isNumber())
                .andExpect(jsonPath("$.sensoresMidiendo").value(180 - 9)) // los 9 que arrancan fallando
                .andExpect(jsonPath("$.sensores.trabado").isNumber())
                .andExpect(jsonPath("$.llenadoPorHora.length()").value(24))
                .andExpect(jsonPath("$.horaPico").isNumber());
    }

    @Test
    void laTablaPorSectorTieneElAvanceDeCadaCamion() throws Exception {
        mvc.perform(get("/api/v1/panel/sectores").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].nombre").value("Retiro y San Nicolás"))
                .andExpect(jsonPath("$[0].empresa").value("AESA Aseo y Ecología"))
                .andExpect(jsonPath("$[0].camiones.length()").value(2))
                .andExpect(jsonPath("$[0].camiones[0].paradasTotal").isNumber())
                .andExpect(jsonPath("$[0].eficiencia").isNumber())
                .andExpect(jsonPath("$[3].contenedores").value(40));
    }

    @Test
    void laCurvaDeUnSensorQueFallaTraeElNivelEstimado() throws Exception {
        String conFalla = jdbc.queryForObject(
                "SELECT id FROM contenedor WHERE estado_sensor = 'sin_senal' ORDER BY id LIMIT 1", String.class);
        String sano = jdbc.queryForObject(
                "SELECT id FROM contenedor WHERE estado_sensor = 'funcionando' ORDER BY id LIMIT 1", String.class);

        mvc.perform(get("/api/v1/contenedores/" + conFalla + "/curva").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoSensor").value("sin_senal"))
                .andExpect(jsonPath("$.nivelEstimado").isNumber())
                .andExpect(jsonPath("$.lecturas.length()").isNumber())
                .andExpect(jsonPath("$.estimadas[0].llenado").isNumber());
        mvc.perform(get("/api/v1/contenedores/" + sano + "/curva").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimadas.length()").value(0));
    }

    @Test
    void elDetalleDeUnCamionTraeSuRuta() throws Exception {
        mvc.perform(get("/api/v1/camiones/C22").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectorId").value(1))
                .andExpect(jsonPath("$.chofer").isString())
                .andExpect(jsonPath("$.rutaId").isNumber())
                .andExpect(jsonPath("$.paradas[0].motivoVisita").isString());
        mvc.perform(get("/api/v1/camiones/C99").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("CAMION_NO_EXISTE"));
    }

    @Test
    void elDetalleDeUnIncidente() throws Exception {
        mvc.perform(get("/api/v1/incidentes/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.sectorId").isNumber());
        mvc.perform(get("/api/v1/incidentes/9999").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("INCIDENTE_NO_EXISTE"));
    }
}
