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

/**
 * Deduce si cada sensor funciona mirando solo sus lecturas, y estima el nivel de los
 * contenedores cuyo sensor no es confiable. Es la misma lógica que
 * src/logic/estadoSensor.ts del front.
 */
@Service
public class ServicioDeEstadoDeSensores {

    public static final String FUNCIONANDO = "funcionando";
    public static final String IRREGULAR = "irregular";
    public static final String CONGELADO = "congelado";
    public static final String SIN_SENAL = "sin_senal";

    private final ContenedorRepositorio contenedores;
    private final RepositorioDeLecturas lecturas;

    ServicioDeEstadoDeSensores(ContenedorRepositorio contenedores, RepositorioDeLecturas lecturas) {
        this.contenedores = contenedores;
        this.lecturas = lecturas;
    }

    @Transactional
    public void actualizarTodos(Instant ahora) {
        Set<String> congelados = lecturas
                .congeladosDesde(ahora.minus(Duration.ofHours(Parametros.HORAS_VALOR_CONGELADO)));

        for (Contenedor contenedor : contenedores.findByActivoTrueOrderById()) {
            String estado = clasificar(contenedor, ahora, congelados.contains(contenedor.getId()));
            boolean confiable = estado.equals(FUNCIONANDO) || estado.equals(IRREGULAR);
            contenedor.actualizarSensor(estado, confiable ? null : estimarNivel(contenedor, estado, ahora));
        }
    }

    // El orden importa: si no manda nada, no tiene sentido analizar qué manda.
    static String clasificar(Contenedor contenedor, Instant ahora, boolean congelado) {
        if (contenedor.getUltimaLecturaAt() == null) {
            return FUNCIONANDO; // todavía no mandó ninguna lectura: no hay nada que deducir
        }
        Duration sinDatos = Duration.between(contenedor.getUltimaLecturaAt().toInstant(), ahora);
        if (sinDatos.compareTo(Duration.ofHours(Parametros.HORAS_SIN_SENAL)) > 0) {
            return SIN_SENAL;
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

        if (contenedor.getUltimaRecoleccionAt() != null
                && contenedor.getUltimaRecoleccionAt().toInstant().isAfter(partida)) {
            partida = contenedor.getUltimaRecoleccionAt().toInstant();
            nivel = Parametros.NIVEL_TRAS_RECOLECCION;
        }
        return (int) Math.round(ModeloDeLlenado.proyectarEntre(nivel, contenedor.velocidad(), partida, ahora));
    }
}
