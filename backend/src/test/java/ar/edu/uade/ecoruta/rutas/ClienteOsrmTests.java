package ar.edu.uade.ecoruta.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ar.edu.uade.ecoruta.comun.Coordenada;

/** El cliente de OSRM, con respuestas grabadas: las pruebas no salen a internet. */
class ClienteOsrmTests {

    // Recortada de una respuesta real: base -> contenedor -> base, cada tramo con sus pasos
    private static final String RESPUESTA = """
            {"code":"Ok","routes":[{"distance":3910.7,"legs":[
              {"steps":[{"geometry":{"coordinates":[[-58.37,-34.609],[-58.3705,-34.609],[-58.371,-34.609]]}},
                        {"geometry":{"coordinates":[[-58.371,-34.609],[-58.371,-34.6085],[-58.3755,-34.6037]]}}]},
              {"steps":[{"geometry":{"coordinates":[[-58.3755,-34.6037],[-58.37,-34.609]]}}]}
            ]}],"waypoints":[]}
            """;

    private final List<Coordenada> puntos = List.of(new Coordenada(-34.609, -58.37), new Coordenada(-34.6037, -58.3755),
            new Coordenada(-34.609, -58.37));

    @Test
    void armaUnTramoPorCadaParDePuntosSinRepetirLasUniones() {
        var trazado = trazarCon(withSuccess(RESPUESTA, MediaType.APPLICATION_JSON)).orElseThrow();
        assertThat(trazado.km()).isEqualTo(3.91);
        assertThat(trazado.tramos()).hasSize(2);
        // El punto del medio de la primera cuadra está sobre la recta: se saca; el de la unión entre pasos no se repite
        assertThat(trazado.tramos().get(0)).containsExactly(new Coordenada(-34.609, -58.37),
                new Coordenada(-34.609, -58.371), new Coordenada(-34.6085, -58.371), new Coordenada(-34.6037, -58.3755));
        assertThat(trazado.tramos().get(1).get(0)).isEqualTo(new Coordenada(-34.6037, -58.3755));
    }

    @Test
    void siOsrmFallaNoHayTrazadoYLaRutaSeDibujaRecta() {
        assertThat(trazarCon(withServerError())).isEmpty();
    }

    @Test
    void sinUrlNoSePideNada() {
        ClienteOsrm cliente = new ClienteOsrm(RestClient.builder(), "");
        assertThat(cliente.activo()).isFalse();
        assertThat(cliente.trazar(puntos)).isEmpty();
    }

    @Test
    void unaRespuestaSinRutaNoSeInterpreta() {
        assertThat(ClienteOsrm.interpretar(new ClienteOsrm.Respuesta("NoRoute", List.of()))).isEmpty();
    }

    @Test
    void simplificarConservaLasEsquinas() {
        List<Coordenada> enL = List.of(new Coordenada(-34.6, -58.38), new Coordenada(-34.6, -58.379),
                new Coordenada(-34.6, -58.378), new Coordenada(-34.599, -58.378));
        assertThat(ClienteOsrm.simplificar(enL, 3)).containsExactly(enL.get(0), enL.get(2), enL.get(3));
    }

    private java.util.Optional<ClienteOsrm.Trazado> trazarCon(
            org.springframework.test.web.client.ResponseCreator respuesta) {
        RestClient.Builder constructor = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(
                "https://osrm.prueba/route/v1/driving/-58.370000,-34.609000;-58.375500,-34.603700;-58.370000,-34.609000"
                        + "?overview=false&steps=true&geometries=geojson"))
                .andRespond(respuesta);
        var resultado = new ClienteOsrm(constructor, "https://osrm.prueba").trazar(puntos);
        servidor.verify();
        return resultado;
    }
}
