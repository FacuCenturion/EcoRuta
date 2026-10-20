package ar.edu.uade.ecoruta.rutas;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ar.edu.uade.ecoruta.comun.Coordenada;

/**
 * Pide a OSRM (Open Source Routing Machine) el recorrido por las calles entre una lista de puntos.
 * Es gratis y no necesita clave. Por defecto usa el servidor público de demostración, que pide no
 * hacer más de un pedido por segundo; para la demo publicada conviene levantar uno propio y
 * cambiar ecoruta.osrm.url. Con la propiedad vacía no se pide nada y las rutas se dibujan rectas.
 */
@Component
public class ClienteOsrm {

    private static final Logger log = LoggerFactory.getLogger(ClienteOsrm.class);

    // Los puntos que están a menos de esto de la línea entre sus vecinos no cambian el dibujo
    static final double TOLERANCIA_METROS = 3;

    /** @param tramos un tramo por cada par de puntos seguidos que se pidieron */
    public record Trazado(List<List<Coordenada>> tramos, double km) {
    }

    // Lo que responde OSRM, solo con lo que se usa
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(String code, List<RutaOsrm> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RutaOsrm(double distance, List<TramoOsrm> legs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TramoOsrm(List<PasoOsrm> steps) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PasoOsrm(Geometria geometry) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Geometria(List<List<Double>> coordinates) { // [lng, lat], como en GeoJSON
    }

    private final RestClient http;
    private final String url;
    private final boolean activo;

    // Spring Boot 4 no registra un RestClient.Builder sin el módulo spring-boot-restclient: se arma uno
    @Autowired
    ClienteOsrm(ObjectProvider<RestClient.Builder> constructor, @Value("${ecoruta.osrm.url:}") String url) {
        this(constructor.getIfAvailable(RestClient::builder), url);
    }

    ClienteOsrm(RestClient.Builder constructor, String url) {
        this.activo = !url.isBlank();
        this.url = url.replaceAll("/$", "");
        this.http = constructor.build();
    }

    public boolean activo() {
        return activo;
    }

    /** El recorrido por las calles pasando por los puntos en orden. Vacío si OSRM no pudo o no está configurado. */
    public Optional<Trazado> trazar(List<Coordenada> puntos) {
        if (!activo || puntos.size() < 2) {
            return Optional.empty();
        }
        String coordenadas = puntos.stream()
                .map(p -> String.format(java.util.Locale.ROOT, "%.6f,%.6f", p.lng(), p.lat()))
                .collect(Collectors.joining(";"));
        try {
            // La URL se arma a mano: con una plantilla se codificarían las comas y los punto y coma
            Respuesta respuesta = http.get()
                    .uri(URI.create(url + "/route/v1/driving/" + coordenadas
                            + "?overview=false&steps=true&geometries=geojson"))
                    // Sin comprimir: el cliente HTTP de Java no descomprime bien lo que manda el servidor público
                    .header("Accept-Encoding", "identity")
                    .retrieve()
                    .body(Respuesta.class);
            return interpretar(respuesta);
        } catch (RestClientException ex) {
            log.warn("OSRM no respondió: {}", ex.getMostSpecificCause().getMessage());
            return Optional.empty();
        }
    }

    static Optional<Trazado> interpretar(Respuesta respuesta) {
        if (respuesta == null || !"Ok".equals(respuesta.code()) || respuesta.routes() == null
                || respuesta.routes().isEmpty()) {
            return Optional.empty();
        }
        RutaOsrm ruta = respuesta.routes().get(0);
        List<List<Coordenada>> tramos = new ArrayList<>();
        for (TramoOsrm tramo : ruta.legs()) {
            List<Coordenada> puntos = new ArrayList<>();
            for (PasoOsrm paso : tramo.steps()) {
                for (List<Double> c : paso.geometry().coordinates()) {
                    Coordenada punto = new Coordenada(c.get(1), c.get(0));
                    // Cada paso empieza donde terminó el anterior: no se repite el punto
                    if (puntos.isEmpty() || !puntos.get(puntos.size() - 1).equals(punto)) {
                        puntos.add(punto);
                    }
                }
            }
            tramos.add(simplificar(puntos, TOLERANCIA_METROS));
        }
        return Optional.of(new Trazado(tramos, Math.round(ruta.distance() / 10) / 100.0));
    }

    /**
     * Douglas-Peucker: saca los puntos que casi no se apartan de la recta entre sus vecinos.
     * OSRM manda muchos puntos sobre la misma cuadra; así el trazado pesa mucho menos y se ve igual.
     */
    static List<Coordenada> simplificar(List<Coordenada> puntos, double toleranciaMetros) {
        if (puntos.size() <= 2) {
            return puntos;
        }
        boolean[] quedan = new boolean[puntos.size()];
        quedan[0] = true;
        quedan[puntos.size() - 1] = true;
        marcar(puntos, 0, puntos.size() - 1, toleranciaMetros, quedan);
        List<Coordenada> resultado = new ArrayList<>();
        for (int i = 0; i < puntos.size(); i++) {
            if (quedan[i]) {
                resultado.add(puntos.get(i));
            }
        }
        return resultado;
    }

    private static void marcar(List<Coordenada> puntos, int desde, int hasta, double tolerancia, boolean[] quedan) {
        double mayor = 0;
        int indice = -1;
        for (int i = desde + 1; i < hasta; i++) {
            double d = distanciaALaRecta(puntos.get(i), puntos.get(desde), puntos.get(hasta));
            if (d > mayor) {
                mayor = d;
                indice = i;
            }
        }
        if (indice >= 0 && mayor > tolerancia) {
            quedan[indice] = true;
            marcar(puntos, desde, indice, tolerancia, quedan);
            marcar(puntos, indice, hasta, tolerancia, quedan);
        }
    }

    // En metros, aproximando la Tierra como plana: en unas cuadras el error no importa
    private static double distanciaALaRecta(Coordenada p, Coordenada a, Coordenada b) {
        double metrosPorGradoLat = 111_320;
        double metrosPorGradoLng = 111_320 * Math.cos(Math.toRadians(a.lat()));
        double px = (p.lng() - a.lng()) * metrosPorGradoLng, py = (p.lat() - a.lat()) * metrosPorGradoLat;
        double bx = (b.lng() - a.lng()) * metrosPorGradoLng, by = (b.lat() - a.lat()) * metrosPorGradoLat;
        double largo = bx * bx + by * by;
        if (largo == 0) {
            return Math.hypot(px, py);
        }
        double t = Math.clamp((px * bx + py * by) / largo, 0, 1);
        return Math.hypot(px - t * bx, py - t * by);
    }
}
