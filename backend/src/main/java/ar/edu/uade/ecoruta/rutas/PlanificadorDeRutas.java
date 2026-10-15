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

    /** Vecino más cercano: desde la base va siempre al contenedor pendiente que le queda más cerca. */
    public static RutaPlanificada planificar(Coordenada base, List<Punto> puntos) {
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

        if (!orden.isEmpty()) {
            km += distanciaKm(actual, base);
        }
        return new RutaPlanificada(orden, km);
    }
}
