package ar.edu.uade.ecoruta.sensores;

import java.util.List;

import ar.edu.uade.ecoruta.comun.Parametros;

/**
 * Un contenedor se llena de a poco, así que las lecturas casi siempre suben. Si alguien
 * revuelve la basura, el sensor marca valores que suben y bajan de golpe.
 * Es la misma regla que src/logic/anomalias.ts del front.
 */
public final class DetectorDeAnomalias {

    private DetectorDeAnomalias() {
    }

    /** @param lecturas de la más vieja a la más nueva */
    public static boolean esIrregular(List<Integer> lecturas) {
        int cantidad = Parametros.LECTURAS_PARA_ANOMALIA;
        if (lecturas.size() < cantidad) {
            return false;
        }
        List<Integer> ventana = lecturas.subList(lecturas.size() - cantidad, lecturas.size());

        // Cuenta cuántas veces cambia el sentido con saltos grandes
        int cambiosDeSentido = 0;
        for (int i = 2; i < ventana.size(); i++) {
            int anterior = ventana.get(i - 1) - ventana.get(i - 2);
            int actual = ventana.get(i) - ventana.get(i - 1);
            boolean saltoGrande = Math.abs(anterior) >= Parametros.SALTO_MINIMO_ANOMALIA
                    && Math.abs(actual) >= Parametros.SALTO_MINIMO_ANOMALIA;
            if (saltoGrande && Integer.signum(anterior) != Integer.signum(actual)) {
                cambiosDeSentido++;
            }
        }
        return cambiosDeSentido >= 3;
    }
}
