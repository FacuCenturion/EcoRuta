package ar.edu.uade.ecoruta.rutas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Parametros;

/**
 * Elige por qué contenedores pasa un camión que sale a una zona:
 * primero los prioritarios, del más lleno al menos lleno, y después las "paradas de paso",
 * que son los contenedores a medio llenar que quedan cerca de alguna parada. Recogerlos
 * cuesta pocos km y evita tener que mandar otro camión al día siguiente.
 */
public final class SeleccionDeParadas {

    public static final String DE_PASO = "de_paso";

    private SeleccionDeParadas() {
    }

    /**
     * @param lectura nivel con el que se decide
     * @param motivo  por qué es prioritario, o null si no lo es
     */
    public record Opcion(String id, Coordenada posicion, double lectura, String motivo) {
    }

    public record Eleccion(String id, String motivoVisita) {
    }

    /** Devuelve vacío si en la zona no hay ningún prioritario: un camión no sale solo por paradas de paso. */
    public static List<Eleccion> elegir(List<Opcion> deLaZona) {
        Comparator<Opcion> masLlenoPrimero = Comparator.comparingDouble(Opcion::lectura).reversed();
        List<Opcion> prioritarios = deLaZona.stream()
                .filter(o -> o.motivo() != null)
                .sorted(masLlenoPrimero)
                .limit(Parametros.MAX_PARADAS_POR_RUTA)
                .toList();
        List<Eleccion> elegidos = new ArrayList<>(prioritarios.stream().map(o -> new Eleccion(o.id(), o.motivo())).toList());
        if (prioritarios.isEmpty()) {
            return elegidos;
        }

        deLaZona.stream()
                .filter(o -> o.motivo() == null && o.lectura() >= Parametros.NIVEL_PARADA_DE_PASO)
                .filter(o -> prioritarios.stream().anyMatch(p -> PlanificadorDeRutas.distanciaKm(o.posicion(),
                        p.posicion()) <= Parametros.RADIO_PARADA_DE_PASO_KM))
                .sorted(masLlenoPrimero)
                .limit(Parametros.MAX_PARADAS_POR_RUTA - prioritarios.size())
                .forEach(o -> elegidos.add(new Eleccion(o.id(), DE_PASO)));
        return elegidos;
    }
}
