package ar.edu.uade.ecoruta.sensores;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.uade.ecoruta.comun.Parametros;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.incidentes.ServicioDeIncidentes;

/**
 * Entrada de los datos de sensores. La usan por igual el emulador y, cuando existan,
 * los sensores reales a través de POST /api/v1/lecturas.
 */
@Service
public class ServicioDeLecturas {

    public record Resultado(int recibidas, int anomaliasNuevas) {
    }

    private final ContenedorRepositorio contenedores;
    private final RepositorioDeLecturas lecturas;
    private final ServicioDeEstadoDeSensores estadoDeSensores;
    private final ServicioDeIncidentes incidentes;
    private final RelojDelSistema reloj;

    ServicioDeLecturas(ContenedorRepositorio contenedores, RepositorioDeLecturas lecturas,
            ServicioDeEstadoDeSensores estadoDeSensores, ServicioDeIncidentes incidentes, RelojDelSistema reloj) {
        this.contenedores = contenedores;
        this.lecturas = lecturas;
        this.estadoDeSensores = estadoDeSensores;
        this.incidentes = incidentes;
        this.reloj = reloj;
    }

    @Transactional
    public Resultado registrar(List<LecturaNueva> nuevas) {
        if (nuevas.isEmpty()) {
            return new Resultado(0, 0);
        }
        Set<String> ids = nuevas.stream().map(LecturaNueva::contenedorId).collect(Collectors.toSet());
        Map<String, Contenedor> porId = contenedores.findAllById(ids).stream()
                .collect(Collectors.toMap(Contenedor::getId, Function.identity()));
        ids.stream().filter(id -> !porId.containsKey(id)).findFirst().ifPresent(id -> {
            throw new RecursoNoEncontradoException("CONTENEDOR_NO_EXISTE", "No existe el contenedor " + id + ".");
        });

        lecturas.insertar(nuevas);
        nuevas.forEach(l -> porId.get(l.contenedorId()).registrarLectura(l.llenado(), l.medidoAt()));

        Instant ahora = reloj.ahora();
        int anomaliasNuevas = 0;
        Map<String, List<Integer>> ultimas = lecturas.ultimas(ids, Parametros.LECTURAS_PARA_ANOMALIA);
        for (Contenedor contenedor : porId.values()) {
            boolean irregular = DetectorDeAnomalias.esIrregular(ultimas.getOrDefault(contenedor.getId(), List.of()));
            if (irregular && !contenedor.isAnomalia()) {
                // No dispara ninguna acción sola: un operador la revisa y confirma
                contenedor.marcarAnomalia(true);
                incidentes.crearLlenadoIrregular(contenedor, ahora);
                anomaliasNuevas++;
            } else if (!irregular && contenedor.isAnomalia()) {
                contenedor.marcarAnomalia(false);
            }
        }

        estadoDeSensores.actualizarTodos(ahora);
        return new Resultado(nuevas.size(), anomaliasNuevas);
    }
}
