package ar.edu.uade.ecoruta.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas.Punto;
import ar.edu.uade.ecoruta.rutas.PlanificadorDeRutas.RutaPlanificada;

class PlanificadorDeRutasTests {

    private static final Coordenada BASE = new Coordenada(-34.60, -58.40);

    @Test
    void laDistanciaEntreElObeliscoYPlazaDeMayoEsDeAlrededorDeUnKm() {
        Coordenada obelisco = new Coordenada(-34.6037, -58.3816);
        Coordenada plazaDeMayo = new Coordenada(-34.6083, -58.3712);
        assertThat(PlanificadorDeRutas.distanciaKm(obelisco, plazaDeMayo)).isCloseTo(1.08, within(0.05));
        assertThat(PlanificadorDeRutas.distanciaKm(obelisco, obelisco)).isZero();
    }

    @Test
    void visitaSiempreElPendienteMasCercano() {
        // Tres puntos en línea hacia el este, cargados desordenados
        List<Punto> puntos = List.of(
                new Punto("lejos", new Coordenada(-34.60, -58.37)),
                new Punto("cerca", new Coordenada(-34.60, -58.39)),
                new Punto("medio", new Coordenada(-34.60, -58.38)));

        RutaPlanificada ruta = PlanificadorDeRutas.planificar(BASE, puntos);

        assertThat(ruta.orden()).containsExactly("cerca", "medio", "lejos");
    }

    @Test
    void losUrgentesSeVisitanPrimeroAunqueEstenLejos() {
        Punto cerca = new Punto("cerca", new Coordenada(-34.60, -58.39));
        Punto lejosYUrgente = new Punto("urgente", new Coordenada(-34.60, -58.36));

        RutaPlanificada ruta = PlanificadorDeRutas.planificarConUrgentes(BASE, List.of(lejosYUrgente), List.of(cerca));

        assertThat(ruta.orden()).containsExactly("urgente", "cerca");
        // base -> urgente -> cerca -> base
        double esperado = PlanificadorDeRutas.distanciaKm(BASE, lejosYUrgente.posicion())
                + PlanificadorDeRutas.distanciaKm(lejosYUrgente.posicion(), cerca.posicion())
                + PlanificadorDeRutas.distanciaKm(cerca.posicion(), BASE);
        assertThat(ruta.km()).isCloseTo(esperado, within(0.0001));
    }

    @Test
    void sinUrgentesEsIgualQueElVecinoMasCercano() {
        List<Punto> puntos = List.of(new Punto("a", new Coordenada(-34.60, -58.37)),
                new Punto("b", new Coordenada(-34.60, -58.39)));

        assertThat(PlanificadorDeRutas.planificarConUrgentes(BASE, List.of(), puntos))
                .isEqualTo(PlanificadorDeRutas.planificar(BASE, puntos));
    }

    @Test
    void losKmIncluyenLaVueltaALaBase() {
        Coordenada destino = new Coordenada(-34.60, -58.39);
        RutaPlanificada ruta = PlanificadorDeRutas.planificar(BASE, List.of(new Punto("unico", destino)));

        assertThat(ruta.km()).isCloseTo(2 * PlanificadorDeRutas.distanciaKm(BASE, destino), within(0.0001));
    }

    @Test
    void sinParadasNoHayRecorrido() {
        RutaPlanificada ruta = PlanificadorDeRutas.planificar(BASE, List.of());

        assertThat(ruta.orden()).isEmpty();
        assertThat(ruta.km()).isZero();
    }
}
