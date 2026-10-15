package ar.edu.uade.ecoruta.emulador;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.contenedores.ModeloDeLlenado;
import ar.edu.uade.ecoruta.incidentes.ServicioDeIncidentes;
import ar.edu.uade.ecoruta.sensores.LecturaNueva;
import ar.edu.uade.ecoruta.sensores.RepositorioDeLecturas;
import ar.edu.uade.ecoruta.sensores.ServicioDeEstadoDeSensores;

/**
 * La primera vez que arranca el emulador, arma hacia atrás 24 horas de lecturas para cada
 * sensor, así las curvas y los estados no arrancan vacíos. Deja algunos sensores con fallas,
 * como pasaría al reactivar sensores que estuvieron mucho tiempo sin mantenimiento.
 */
@Component
@ConditionalOnProperty(name = "ecoruta.emulador.activo", havingValue = "true")
class HistorialInicial {

    private static final int SENSORES_SIN_SENAL = 6;
    private static final int SENSORES_CONGELADOS = 3;
    private static final int PASOS = 24 * 60 / EmuladorDeSensores.MINUTOS_POR_PASO;

    // Contenedores con un papel fijo en el arranque de la demo
    static final String SENSOR_IRREGULAR = "SNI-05";
    private static final Map<String, String> REPORTES_VECINALES = Map.of(
            "MON-03", "Hay bolsas tiradas al lado del contenedor.",
            "STE-07", "El contenedor está lleno y huele mal.");
    private static final List<Integer> LECTURAS_IRREGULARES = List.of(52, 61, 49, 62, 48, 60);

    private final ContenedorRepositorio contenedores;
    private final RepositorioDeLecturas lecturas;
    private final ServicioDeEstadoDeSensores estadoDeSensores;
    private final ServicioDeIncidentes incidentes;

    HistorialInicial(ContenedorRepositorio contenedores, RepositorioDeLecturas lecturas,
            ServicioDeEstadoDeSensores estadoDeSensores, ServicioDeIncidentes incidentes) {
        this.contenedores = contenedores;
        this.lecturas = lecturas;
        this.estadoDeSensores = estadoDeSensores;
        this.incidentes = incidentes;
    }

    private record Punto(Instant momento, int nivel) {
    }

    /** Devuelve qué falla le quedó puesta a cada sensor (solo los que tienen alguna). */
    Map<String, String> cargar(Instant ahora, Random azar) {
        List<Contenedor> todos = contenedores.findByActivoTrueOrderById();
        Map<String, String> fallas = sortearFallas(todos, azar);
        List<LecturaNueva> filas = new ArrayList<>();

        for (Contenedor c : todos) {
            List<Punto> curva = new ArrayList<>();
            Instant ultimaRecoleccion = null;
            double nivel = c.getId().equals(SENSOR_IRREGULAR) ? 55 : c.getLlenado();

            // Se va hacia atrás restando lo que se llenó en cada paso. Si el nivel llega a
            // cero es porque ahí pasó el camión: antes de eso el contenedor estaba lleno.
            for (int i = 0; i < PASOS; i++) {
                Instant momento = ahora.minus(Duration.ofMinutes((long) i * EmuladorDeSensores.MINUTOS_POR_PASO));
                curva.add(new Punto(momento, (int) Math.round(nivel)));
                nivel -= ModeloDeLlenado.incrementoPorHora(c.velocidad(), momento)
                        * (EmuladorDeSensores.MINUTOS_POR_PASO / 60.0) * (0.7 + azar.nextDouble() * 0.6);
                if (nivel < 2) {
                    if (ultimaRecoleccion == null) {
                        ultimaRecoleccion = momento;
                    }
                    nivel = 65 + azar.nextDouble() * 27;
                }
            }
            Collections.reverse(curva);

            if (c.getId().equals(SENSOR_IRREGULAR)) {
                int desde = curva.size() - LECTURAS_IRREGULARES.size();
                for (int i = 0; i < LECTURAS_IRREGULARES.size(); i++) {
                    curva.set(desde + i, new Punto(curva.get(desde + i).momento(), LECTURAS_IRREGULARES.get(i)));
                }
            }
            curva = aplicarFalla(curva, fallas.get(c.getId()), ahora, azar);

            Punto ultima = curva.get(curva.size() - 1);
            c.registrarLectura(ultima.nivel(), ultima.momento());
            // Si en estas 24 h no pasó el camión, se asume que pasó justo antes: desde ahí
            // corre el tiempo máximo sin recolectar.
            c.registrarRecoleccion(ultimaRecoleccion != null ? ultimaRecoleccion : curva.get(0).momento());
            curva.forEach(p -> filas.add(new LecturaNueva(c.getId(), p.nivel(), p.momento())));
        }
        lecturas.insertar(filas);

        // Dos reportes de vecinos y un sensor con lecturas raras, para que el panel no arranque vacío
        for (Contenedor c : todos) {
            if (c.getId().equals(SENSOR_IRREGULAR)) {
                c.marcarAnomalia(true);
                incidentes.crearLlenadoIrregular(c, ahora);
            }
            if (REPORTES_VECINALES.containsKey(c.getId())) {
                incidentes.crearReporteVecinal(c.getId(), REPORTES_VECINALES.get(c.getId()), "Boti", ahora);
            }
        }
        estadoDeSensores.actualizarTodos(ahora);
        return fallas;
    }

    private static Map<String, String> sortearFallas(List<Contenedor> todos, Random azar) {
        Set<String> reservados = Set.of(SENSOR_IRREGULAR, "MON-03", "STE-07");
        List<Contenedor> candidatos = new ArrayList<>(
                todos.stream().filter(c -> !reservados.contains(c.getId())).toList());
        Map<String, String> fallas = new HashMap<>();
        for (int i = 0; i < SENSORES_SIN_SENAL + SENSORES_CONGELADOS && !candidatos.isEmpty(); i++) {
            Contenedor elegido = candidatos.remove(azar.nextInt(candidatos.size()));
            fallas.put(elegido.getId(), i < SENSORES_SIN_SENAL ? ServicioDeEstadoDeSensores.SIN_SENAL
                    : ServicioDeEstadoDeSensores.CONGELADO);
        }
        return fallas;
    }

    // El sensor falló entre 5 y 20 horas atrás: uno sin señal no mandó nada más desde
    // entonces, y uno congelado siguió mandando siempre el mismo valor. Al menos 5 horas para
    // que al arrancar la demo ya se puedan detectar: un congelado recién se reconoce a las 4 h.
    private static List<Punto> aplicarFalla(List<Punto> curva, String falla, Instant ahora, Random azar) {
        if (falla == null) {
            return curva;
        }
        Instant corte = ahora.minus(Duration.ofMinutes(Math.round((5 + azar.nextDouble() * 15) * 60)));
        List<Punto> anteriores = curva.stream().filter(p -> !p.momento().isAfter(corte)).toList();
        if (falla.equals(ServicioDeEstadoDeSensores.SIN_SENAL)) {
            return anteriores;
        }
        int valor = anteriores.get(anteriores.size() - 1).nivel();
        return curva.stream().map(p -> p.momento().isAfter(corte) ? new Punto(p.momento(), valor) : p).toList();
    }
}
