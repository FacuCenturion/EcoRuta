package ar.edu.uade.ecoruta.indicadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import ar.edu.uade.ecoruta.BaseDePrueba;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.emulador.EmuladorDeSensores;
import ar.edu.uade.ecoruta.emulador.ReinicioDeLaDemo;

@BaseDePrueba
@AutoConfigureMockMvc
class IndicadoresTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReinicioDeLaDemo reinicio;

    @Autowired
    EmuladorDeSensores emulador;

    @Autowired
    RelojDelSistema reloj;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void demoDeCero() {
        reinicio.reiniciar();
    }

    @Test
    void alArrancarHayOchoSemanasDeHistorialSimulado() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resumen_diario WHERE origen = 'simulado'", Integer.class))
                .isEqualTo(56 * 4);
        // Con los mismos datos, la ruta fija vacía muchos más contenedores casi vacíos que la dinámica.
        // (Los km y los desbordes dependen de cuántas rondas tenga la ruta fija de la comparación.)
        assertThat(jdbc.queryForObject(
                "SELECT 1.0 * sum(visitas_bajas_fija) / sum(visitas_fija) > 2.0 * sum(visitas_bajas) / sum(visitas)"
                        + " FROM resumen_diario", Boolean.class)).isTrue();
    }

    @Test
    void alPasarLaMedianocheSeResumeElDiaConLaOperacionReal() {
        LocalDate hoy = reloj.ahora().atZone(Fechas.BUENOS_AIRES).toLocalDate();

        emulador.avanzarPasos(85); // de las 17:00 a las 00:05 del día siguiente

        var fila = jdbc.queryForMap("SELECT * FROM resumen_diario WHERE fecha = ? AND sector_id = 1", hoy);
        assertThat(fila.get("origen")).isEqualTo("calculado");
        assertThat(((Number) fila.get("visitas")).intValue()).isPositive();
        // La ruta fija pasa en cada ronda por cada uno de los 55 contenedores del sector
        assertThat(((Number) fila.get("visitas_fija")).intValue())
                .isEqualTo(ar.edu.uade.ecoruta.comun.Parametros.RONDAS_RUTA_FIJA.length * 55);
    }

    @Test
    void elDirectorVeLosIndicadores() throws Exception {
        mvc.perform(get("/api/v1/indicadores").header("Authorization", "Bearer " + token("director", "director123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(31)) // 30 días más hoy
                .andExpect(jsonPath("$.semanas.length()").value(8))
                .andExpect(jsonPath("$.llenadoPorHora.length()").value(24))
                .andExpect(jsonPath("$.mapaCalor.1.length()").value(7))
                .andExpect(jsonPath("$.eficienciaPorSector.1").isNumber())
                .andExpect(jsonPath("$.kmEvitados").isNumber())
                .andExpect(jsonPath("$.co2EvitadoKg").isNumber());
        mvc.perform(get("/api/v1/indicadores?sectorId=3&dias=7")
                        .header("Authorization", "Bearer " + token("director", "director123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectorId").value(3))
                .andExpect(jsonPath("$.dias.length()").value(8));
    }

    @Test
    void elOperadorNoVeLosIndicadores() throws Exception {
        mvc.perform(get("/api/v1/indicadores").header("Authorization", "Bearer " + token("operador", "operador123")))
                .andExpect(status().isForbidden());
    }

    private String token(String usuario, String contrasena) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
