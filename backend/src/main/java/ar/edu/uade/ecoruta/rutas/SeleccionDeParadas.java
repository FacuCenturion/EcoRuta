package ar.edu.uade.ecoruta.rutas;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Parametros;

/**
 * Elige por qué contenedores pasa un camión que sale a un sector:
 * primero los prioritarios, del que se pronostica que desborda antes al que desborda después,
 * y después las "paradas de paso", que son los contenedores a medio llenar que quedan cerca de
 * alguna parada. Recogerlos cuesta pocos km y evita tener que mandar otro camión al día siguiente.
 */
public final class SeleccionDeParadas {

    public static final String DE_PASO = "de_paso";

    private SeleccionDeParadas() {
    }

    /**
     * @param lectura          nivel con el que se decide
     * @param motivo           por qué es prioritario, o null si no lo es
     * @param desbordeEstimado cuándo se pronostica que desborda, o null si no es pronto
     */
    public record Opcion(String id, Coordenada posicion, double lectura, String motivo, Instant desbordeEstimado) {
    }

    /** @param urgente se pronostica que desborda antes de que el camión llegue si lo deja para el final */
    public record Eleccion(String id, String motivoVisita, boolean urgente) {
    }

    /**
     * Devuelve vacío si en el sector no hay ningún prioritario: un camión no sale solo por paradas de paso.
     *
     * @param limiteUrgente los que se pronostica que desbordan antes de este momento son urgentes
     */
    public static List<Eleccion> elegir(Instant limiteUrgente, List<Opcion> delSector) {
        Comparator<Opcion> masLlenoPrimero = Comparator.comparingDouble(Opcion::lectura).reversed();
        // El que desborda antes va primero; los que no tienen desborde a la vista, por nivel
        Comparator<Opcion> masUrgentePrimero = Comparator
                .comparing(Opcion::desbordeEstimado, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(masLlenoPrimero);
        List<Opcion> prioritarios = delSector.stream()
                .filter(o -> o.motivo() != null)
                .sorted(masUrgentePrimero)
                .limit(Parametros.MAX_PARADAS_POR_RUTA)
                .toList();
        List<Eleccion> elegidos = new ArrayList<>(prioritarios.stream()
                .map(o -> new Eleccion(o.id(), o.motivo(),
                        o.desbordeEstimado() != null && o.desbordeEstimado().isBefore(limiteUrgente)))
                .toList());
        if (prioritarios.isEmpty()) {
            return elegidos;
        }

        delSector.stream()
                .filter(o -> o.motivo() == null && o.lectura() >= Parametros.NIVEL_PARADA_DE_PASO)
                .filter(o -> prioritarios.stream().anyMatch(p -> PlanificadorDeRutas.distanciaKm(o.posicion(),
                        p.posicion()) <= Parametros.RADIO_PARADA_DE_PASO_KM))
                .sorted(masLlenoPrimero)
                .limit(Parametros.MAX_PARADAS_POR_RUTA - prioritarios.size())
                .forEach(o -> elegidos.add(new Eleccion(o.id(), DE_PASO, false)));
        return elegidos;
    }
}
