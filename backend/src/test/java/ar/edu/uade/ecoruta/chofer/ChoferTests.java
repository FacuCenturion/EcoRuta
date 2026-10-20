package ar.edu.uade.ecoruta.chofer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
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

/** El recorrido de un chofer con la app, contra la base y el emulador de verdad. */
@BaseDePrueba
@AutoConfigureMockMvc
class ChoferTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReinicioDeLaDemo reinicio;

    @Autowired
    EmuladorDeSensores emulador;

    @Autowired
    JdbcTemplate jdbc;

    private String chofer;

    @BeforeEach
    void demoDeCero() throws Exception {
        jdbc.update("UPDATE camion SET manejado_por_app = FALSE"); // el reinicio no cierra los turnos
        reinicio.reiniciar();
        chofer = token("chofer22", "chofer123");
    }

    // Las demás pruebas cuentan con que el emulador maneja todos los camiones
    @AfterEach
    void cerrarTurnos() {
        jdbc.update("UPDATE camion SET manejado_por_app = FALSE");
    }

    @Test
    void elChoferVeSuCamionYMientrasNoEmpiezaElTurnoLoManejaElEmulador() throws Exception {
        mvc.perform(get("/api/v1/chofer/turno").header("Authorization", "Bearer " + chofer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chofer").value("Lucas Benítez"))
                .andExpect(jsonPath("$.camion.id").value("C22"))
                .andExpect(jsonPath("$.camion.sector").value("Retiro y San Nicolás"))
                .andExpect(jsonPath("$.manejadoPorApp").value(false));
        mvc.perform(post("/api/v1/chofer/ruta/iniciar").header("Authorization", "Bearer " + chofer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO_SIN_EMPEZAR"));
    }

    @Test
    void soloLosChoferesEntranALaAppYNoVenElPanel() throws Exception {
        mvc.perform(get("/api/v1/chofer/turno").header("Authorization", "Bearer " + token("operador", "operador123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/panel/resumen").header("Authorization", "Bearer " + chofer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/chofer/motivos").header("Authorization", "Bearer " + chofer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Auto estacionado"));
    }

    @Test
    void conElTurnoEmpezadoLaRutaLaHaceElChofer() throws Exception {
        empezarTurno();
        llenarElSector(1);
        emulador.avanzarPasos(3);

        // El sistema le asignó una ruta, pero el emulador no la inició: espera al chofer
        String turno = pedir(get("/api/v1/chofer/turno"));
        assertThat((String) JsonPath.read(turno, "$.ruta.estado")).isEqualTo("programada");
        assertThat((String) JsonPath.read(turno, "$.camion.estado")).isEqualTo("en_base");
        List<Integer> paradas = JsonPath.read(turno, "$.ruta.paradas[*].id");
        assertThat(paradas).hasSizeGreaterThanOrEqualTo(3);
        assertThat((Object) JsonPath.read(turno, "$.ruta.paradas[0].posicion.lat")).isNotNull();

        turno = pedir(post("/api/v1/chofer/ruta/iniciar"));
        assertThat((String) JsonPath.read(turno, "$.ruta.estado")).isEqualTo("en_curso");
        assertThat((String) JsonPath.read(turno, "$.camion.estado")).isEqualTo("en_ruta");

        // Primera parada: la vacía. El sensor informa el nivel nuevo (si no está roto)
        String contenedor = JsonPath.read(turno, "$.ruta.paradas[0].contenedorId");
        turno = pedir(post("/api/v1/chofer/paradas/" + paradas.get(0) + "/recolectar"));
        assertThat((String) JsonPath.read(turno, "$.ruta.paradas[0].estado")).isEqualTo("recolectada");
        assertThat(jdbc.queryForObject("SELECT ultima_recoleccion_at IS NOT NULL FROM contenedor WHERE id = ?",
                Boolean.class, contenedor)).isTrue();
        String sensor = jdbc.queryForObject("SELECT estado_sensor FROM contenedor WHERE id = ?", String.class, contenedor);
        if (sensor.equals("funcionando")) {
            assertThat(jdbc.queryForObject("SELECT llenado FROM contenedor WHERE id = ?", Integer.class, contenedor))
                    .isLessThan(15);
        }

        // Segunda: no pudo. Queda el incidente para el operador
        mvc.perform(post("/api/v1/chofer/paradas/" + paradas.get(1) + "/no-se-pudo")
                        .header("Authorization", "Bearer " + chofer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Auto estacionado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruta.paradas[1].estado").value("no_se_pudo"))
                .andExpect(jsonPath("$.ruta.paradas[1].motivo").value("Auto estacionado"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM incidente WHERE tipo = 'no_se_pudo' AND camion_id = 'C22'",
                Integer.class)).isEqualTo(1);

        // Mientras tanto el emulador no avanza la ruta por su cuenta
        emulador.avanzarPasos(6);
        turno = pedir(get("/api/v1/chofer/turno"));
        List<String> estados = JsonPath.read(turno, "$.ruta.paradas[*].estado");
        assertThat(estados.subList(2, estados.size())).containsOnly("pendiente");

        // Cierra las que quedan: la ruta termina y el camión vuelve a descargar
        for (Integer id : paradas.subList(2, paradas.size())) {
            turno = pedir(post("/api/v1/chofer/paradas/" + id + "/recolectar"));
        }
        assertThat((String) JsonPath.read(turno, "$.ruta.estado")).isEqualTo("terminada");
        assertThat((String) JsonPath.read(turno, "$.camion.estado")).isIn("en_descarga", "demorado");
    }

    @Test
    void noPuedeTocarParadasQueNoSonDeSuRuta() throws Exception {
        empezarTurno();
        llenarElSector(1);
        emulador.avanzarPasos(3);
        pedir(post("/api/v1/chofer/ruta/iniciar"));
        Long ajena = jdbc.queryForObject("""
                SELECT p.id FROM parada p JOIN ruta r ON r.id = p.ruta_id WHERE r.camion_id <> 'C22' LIMIT 1
                """, Long.class);
        mvc.perform(post("/api/v1/chofer/paradas/" + ajena + "/recolectar").header("Authorization", "Bearer " + chofer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PARADA_NO_EXISTE"));
    }

    @Test
    void siElTurnoEstabaAbiertoLaPrimeraRutaDespuesDeReiniciarEsDelChofer() throws Exception {
        empezarTurno();
        reinicio.reiniciar();
        emulador.avanzarPasos(3);
        String turno = pedir(get("/api/v1/chofer/turno"));
        assertThat((Boolean) JsonPath.read(turno, "$.manejadoPorApp")).isTrue();
        assertThat((String) JsonPath.read(turno, "$.ruta.estado")).isEqualTo("programada");
    }

    @Test
    void cadaParadaTraeLoQueMuestraLaApp() throws Exception {
        empezarTurno();
        llenarElSector(1);
        emulador.avanzarPasos(3);
        mvc.perform(get("/api/v1/chofer/turno").header("Authorization", "Bearer " + chofer))
                .andExpect(jsonPath("$.ruta.paradas[0].llenado").isNumber())
                .andExpect(jsonPath("$.ruta.paradas[0].tipo").isString())
                .andExpect(jsonPath("$.ruta.paradas[0].barrio").isString())
                .andExpect(jsonPath("$.propuesta").doesNotExist());
    }

    @Test
    void laPosicionDelCelularMueveElCamionEnElPanel() throws Exception {
        empezarRuta();
        mvc.perform(post("/api/v1/chofer/posicion").header("Authorization", "Bearer " + chofer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lat\":-34.6011,\"lng\":-58.3802}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT lat FROM camion WHERE id = 'C22'", Double.class)).isEqualTo(-34.6011);
    }

    @Test
    void unContenedorQueSeVuelveUrgenteSeLeProponeAlChoferYSiAceptaEntraEnLaRuta() throws Exception {
        empezarRuta();
        int antes = jdbc.queryForObject("SELECT count(*) FROM parada p JOIN ruta r ON r.id = p.ruta_id "
                + "WHERE r.camion_id = 'C22' AND r.estado = 'en_curso'", Integer.class);
        String urgente = contenedorLibreDelSector(1);
        emulador.llenar(urgente, 98);
        emulador.avanzarPasos(1);

        String turno = pedir(get("/api/v1/chofer/turno"));
        assertThat((String) JsonPath.read(turno, "$.propuesta.contenedorId")).isEqualTo(urgente);
        assertThat((Integer) JsonPath.read(turno, "$.propuesta.llenado")).isGreaterThanOrEqualTo(95);
        Integer propuesta = JsonPath.read(turno, "$.propuesta.id");
        int orden = JsonPath.read(turno, "$.propuesta.orden");

        turno = pedir(post("/api/v1/chofer/propuestas/" + propuesta + "/aceptar"));
        List<String> contenedores = JsonPath.read(turno, "$.ruta.paradas[*].contenedorId");
        List<Integer> ordenes = JsonPath.read(turno, "$.ruta.paradas[*].orden");
        assertThat(contenedores).hasSize(antes + 1);
        assertThat(contenedores.get(orden - 1)).isEqualTo(urgente);
        assertThat(ordenes).isSorted().doesNotHaveDuplicates();
        assertThat((Object) JsonPath.read(turno, "$.propuesta")).isNull();
    }

    @Test
    void siElChoferMantieneLaRutaElContenedorNoSeVuelveAProponer() throws Exception {
        empezarRuta();
        String urgente = contenedorLibreDelSector(1);
        emulador.llenar(urgente, 98);
        emulador.avanzarPasos(1);
        Integer propuesta = JsonPath.read(pedir(get("/api/v1/chofer/turno")), "$.propuesta.id");

        pedir(post("/api/v1/chofer/propuestas/" + propuesta + "/rechazar"));
        emulador.avanzarPasos(2);
        String turno = pedir(get("/api/v1/chofer/turno"));
        List<String> contenedores = JsonPath.read(turno, "$.ruta.paradas[*].contenedorId");
        assertThat(contenedores).doesNotContain(urgente);
        if (JsonPath.<Object>read(turno, "$.propuesta") != null) {
            assertThat((String) JsonPath.read(turno, "$.propuesta.contenedorId")).isNotEqualTo(urgente);
        }
    }

    @Test
    void elNoSePudoLlevaLaNotaYLaFotoAlOperador() throws Exception {
        String turno = empezarRuta();
        Integer parada = JsonPath.read(turno, "$.ruta.paradas[0].id");
        String foto = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 });
        mvc.perform(post("/api/v1/chofer/paradas/" + parada + "/no-se-pudo").header("Authorization", "Bearer " + chofer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Auto estacionado\",\"nota\":\"Patente AB123CD\",\"foto\":\"" + foto + "\"}"))
                .andExpect(status().isOk());

        String operador = token("operador", "operador123");
        String incidentes = mvc.perform(get("/api/v1/incidentes?tipo=no_se_pudo").header("Authorization", "Bearer " + operador))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(incidentes, "$[0].descripcion")).contains("Patente AB123CD");
        assertThat((Boolean) JsonPath.read(incidentes, "$[0].tieneFoto")).isTrue();
        Integer id = JsonPath.read(incidentes, "$[0].id");
        byte[] imagen = mvc.perform(get("/api/v1/incidentes/" + id + "/foto").header("Authorization", "Bearer " + operador))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(imagen).containsExactly(1, 2, 3);
    }

    @Test
    void unaFotoQueNoEsImagenSeRechaza() throws Exception {
        String turno = empezarRuta();
        Integer parada = JsonPath.read(turno, "$.ruta.paradas[0].id");
        mvc.perform(post("/api/v1/chofer/paradas/" + parada + "/no-se-pudo").header("Authorization", "Bearer " + chofer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Auto estacionado\",\"foto\":\"data:text/html;base64,PHNjcmlwdD4=\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sinMotivoNoSeAceptaElNoSePudo() throws Exception {
        mvc.perform(post("/api/v1/chofer/paradas/1/no-se-pudo").header("Authorization", "Bearer " + chofer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alTerminarElTurnoElEmuladorVuelveAManejarElCamion() throws Exception {
        empezarTurno();
        llenarElSector(1);
        emulador.avanzarPasos(3);
        pedir(post("/api/v1/chofer/turno/terminar"));
        emulador.avanzarPasos(2);
        String turno = pedir(get("/api/v1/chofer/turno"));
        assertThat((Boolean) JsonPath.read(turno, "$.manejadoPorApp")).isFalse();
        assertThat((String) JsonPath.read(turno, "$.ruta.estado")).isNotEqualTo("programada");
    }

    private String empezarRuta() throws Exception {
        empezarTurno();
        llenarElSector(1);
        emulador.avanzarPasos(3);
        return pedir(post("/api/v1/chofer/ruta/iniciar"));
    }

    // Un contenedor del sector que no está en ninguna ruta activa
    private String contenedorLibreDelSector(int sectorId) {
        return jdbc.queryForObject("""
                SELECT c.id FROM contenedor c WHERE c.sector_id = ? AND c.id NOT IN (
                    SELECT p.contenedor_id FROM parada p JOIN ruta r ON r.id = p.ruta_id
                    WHERE r.estado <> 'terminada' AND p.estado = 'pendiente')
                ORDER BY c.id DESC LIMIT 1
                """, String.class, sectorId);
    }

    private void empezarTurno() throws Exception {
        String turno = pedir(post("/api/v1/chofer/turno/empezar"));
        assertThat((Boolean) JsonPath.read(turno, "$.manejadoPorApp")).isTrue();
    }

    // Llena varios contenedores del sector para que el sistema despache seguro
    private void llenarElSector(int sectorId) {
        jdbc.queryForList("SELECT id FROM contenedor WHERE sector_id = ? ORDER BY id LIMIT 8", String.class, sectorId)
                .forEach(id -> emulador.llenar(id, 97));
    }

    private String pedir(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pedido)
            throws Exception {
        return mvc.perform(pedido.header("Authorization", "Bearer " + chofer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String token(String usuario, String contrasena) throws Exception {
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }
}
