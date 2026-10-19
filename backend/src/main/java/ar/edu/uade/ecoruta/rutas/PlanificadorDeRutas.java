package ar.edu.uade.ecoruta.rutas;

import java.util.ArrayList;
import java.util.List;

import ar.edu.uade.ecoruta.comun.Coordenada;

/**
 * Ordena las paradas de una ruta. Es el mismo algoritmo que src/logic/rutas.ts del front.
 * Más adelante se puede reemplazar por la API de rutas de Google Maps.
 */
public final class PlanificadorDeRutas {

    private static final double RADIO_TIERRA_KM = 6371;

    private PlanificadorDeRutas() {
    }

    public record Punto(String id, Coordenada posicion) {
    }

    /**
     * @param orden ids de los contenedores en el orden de visita
     * @param km    largo total contando la vuelta a la base
     */
    public record RutaPlanificada(List<String> orden, double km) {
    }

    /** Distancia en línea recta (Haversine). Las calles reales dan un recorrido algo más largo. */
    public static double distanciaKm(Coordenada a, Coordenada b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat())) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * RADIO_TIERRA_KM * Math.asin(Math.sqrt(h));
    }

    /**
     * Primero recorre los urgentes y después el resto, cada tanda por vecino más cercano. Así un
     * contenedor a punto de desbordar no queda para el final solo porque está lejos de la base.
     */
    public static RutaPlanificada planificarConUrgentes(Coordenada base, List<Punto> urgentes, List<Punto> resto) {
        RutaPlanificada primera = recorrer(base, urgentes, false);
        Coordenada desde = primera.orden().isEmpty() ? base
                : urgentes.stream().filter(p -> p.id().equals(primera.orden().getLast())).findFirst().orElseThrow().posicion();
        RutaPlanificada segunda = recorrer(desde, resto, false);

        List<String> orden = new ArrayList<>(primera.orden());
        orden.addAll(segunda.orden());
        Coordenada ultima = orden.isEmpty() ? base
                : java.util.stream.Stream.concat(urgentes.stream(), resto.stream())
                        .filter(p -> p.id().equals(orden.getLast())).findFirst().orElseThrow().posicion();
        double km = primera.km() + segunda.km() + (orden.isEmpty() ? 0 : distanciaKm(ultima, base));
        return new RutaPlanificada(orden, km);
    }

    /** Vecino más cercano: desde la base va siempre al contenedor pendiente que le queda más cerca. */
    public static RutaPlanificada planificar(Coordenada base, List<Punto> puntos) {
        return recorrer(base, puntos, true);
    }

    private static RutaPlanificada recorrer(Coordenada base, List<Punto> puntos, boolean volverALaBase) {
        List<Punto> pendientes = new ArrayList<>(puntos);
        List<String> orden = new ArrayList<>();
        Coordenada actual = base;
        double km = 0;

        while (!pendientes.isEmpty()) {
            Punto mejor = pendientes.get(0);
            double menor = Double.MAX_VALUE;
            for (Punto punto : pendientes) {
                double d = distanciaKm(actual, punto.posicion());
                if (d < menor) {
                    menor = d;
                    mejor = punto;
                }
            }
            pendientes.remove(mejor);
            orden.add(mejor.id());
            km += menor;
            actual = mejor.posicion();
        }

        if (volverALaBase && !orden.isEmpty()) {
            km += distanciaKm(actual, base);
        }
        return new RutaPlanificada(orden, km);
    }
}
