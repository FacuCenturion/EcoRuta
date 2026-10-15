package ar.edu.uade.ecoruta.sensores;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.contenedores.ModeloDeLlenado;
import ar.edu.uade.ecoruta.contenedores.PatronesDeLlenado;
import ar.edu.uade.ecoruta.incidentes.ServicioDeIncidentes;

/**
 * Deduce si cada sensor funciona mirando solo sus lecturas, y estima el nivel de los
 * contenedores cuyo sensor no es confiable. Parte de src/logic/estadoSensor.ts del front y
 * le suma el sensor trabado (no bajó tras vaciarse) y los incidentes automáticos.
 */
@Service
public class ServicioDeEstadoDeSensores {

    public static final String FUNCIONANDO = "funcionando";
    public static final String IRREGULAR = "irregular";
    public static final String CONGELADO = "congelado";
    public static final String SIN_SENAL = "sin_senal";
    public static final String TRABADO = "trabado";

    private final ContenedorRepositorio contenedores;
    private final RepositorioDeLecturas lecturas;
    private final PatronesDeLlenado patrones;
    private final ServicioDeIncidentes incidentes;

    ServicioDeEstadoDeSensores(ContenedorRepositorio contenedores, RepositorioDeLecturas lecturas,
            PatronesDeLlenado patrones, ServicioDeIncidentes incidentes) {
        this.contenedores = contenedores;
        this.lecturas = lecturas;
        this.patrones = patrones;
        this.incidentes = incidentes;
    }

    public static boolean esConfiable(String estado) {
        return estado.equals(FUNCIONANDO) || estado.equals(IRREGULAR);
    }

    @Transactional
    public void actualizarTodos(Instant ahora) {
        Set<String> congelados = lecturas
                .congeladosDesde(ahora.minus(Duration.ofHours(Parametros.HORAS_VALOR_CONGELADO)));
        Set<String> trabados = lecturas.trabadosTrasRecoleccion(ahora.minus(Duration.ofHours(24)));

        for (Contenedor contenedor : contenedores.findByActivoTrueOrderById()) {
            String antes = contenedor.getEstadoSensor();
            String estado = clasificar(contenedor, ahora, congelados.contains(contenedor.getId()),
                    trabados.contains(contenedor.getId()));
            boolean confiable = esConfiable(estado);
            Integer estimado = confiable ? null : estimarNivel(contenedor, estado, ahora);
            contenedor.actualizarSensor(estado, estimado);

            // Cuando un sensor deja de funcionar, el operador se entera por un incidente
            if (esConfiable(antes) && !confiable) {
                incidentes.crearFallaSensor(contenedor, estado, ahora);
            }
            // Con el sensor caído, el desborde también se estima
            if (!confiable && estimado >= Parametros.NIVEL_LLENO && !contenedor.estaDesbordado()) {
                contenedor.marcarDesborde(ahora);
                incidentes.crearDesborde(contenedor, true, ahora);
            }
        }
    }

    // El orden importa: si no manda nada, no tiene sentido analizar qué manda.
    static String clasificar(Contenedor contenedor, Instant ahora, boolean congelado, boolean trabado) {
        if (contenedor.getUltimaLecturaAt() == null) {
            return FUNCIONANDO; // todavía no mandó ninguna lectura: no hay nada que deducir
        }
        Duration sinDatos = Duration.between(contenedor.getUltimaLecturaAt().toInstant(), ahora);
        if (sinDatos.compareTo(Duration.ofHours(Parametros.HORAS_SIN_SENAL)) > 0) {
            return SIN_SENAL;
        }
        if (trabado) {
            return TRABADO;
        }
        if (congelado) {
            return CONGELADO;
        }
        return contenedor.isAnomalia() ? IRREGULAR : FUNCIONANDO;
    }

    // Se parte de la última lectura que todavía sirve y se le suma lo que se habría llenado
    // desde entonces. Si después un chofer lo vació, se parte de esa recolección.
    private int estimarNivel(Contenedor contenedor, String estado, Instant ahora) {
        Instant partida = contenedor.getUltimaLecturaAt().toInstant();
        if (estado.equals(CONGELADO)) {
            Instant inicio = lecturas.inicioDeLaRacha(contenedor.getId(), contenedor.getLlenado());
            partida = inicio != null ? inicio : partida;
        }
        double nivel = contenedor.getLlenado();

        // Un sensor trabado sigue mandando lecturas, pero no sirven: lo que vale es que lo vaciaron
        boolean despuesDeVaciar = contenedor.getUltimaRecoleccionAt() != null
                && (estado.equals(TRABADO) || contenedor.getUltimaRecoleccionAt().toInstant().isAfter(partida));
        if (despuesDeVaciar) {
            partida = contenedor.getUltimaRecoleccionAt().toInstant();
            nivel = Parametros.NIVEL_TRAS_RECOLECCION;
        }
        return (int) Math.round(ModeloDeLlenado.proyectarEntre(nivel, patrones.ritmoDe(contenedor), partida, ahora));
    }
}
