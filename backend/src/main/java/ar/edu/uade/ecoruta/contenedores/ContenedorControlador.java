package ar.edu.uade.ecoruta.contenedores;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.contenedores.Prioridad.Evaluacion;
import ar.edu.uade.ecoruta.sensores.RepositorioDeLecturas;

@RestController
@RequestMapping("/api/v1/contenedores")
public class ContenedorControlador {

    record LecturaDto(OffsetDateTime medidoAt, int llenado) {
    }

    private final ContenedorRepositorio contenedores;
    private final ServicioDePrioridad prioridad;
    private final RepositorioDeLecturas lecturas;
    private final RelojDelSistema reloj;

    ContenedorControlador(ContenedorRepositorio contenedores, ServicioDePrioridad prioridad,
            RepositorioDeLecturas lecturas, RelojDelSistema reloj) {
        this.contenedores = contenedores;
        this.prioridad = prioridad;
        this.lecturas = lecturas;
        this.reloj = reloj;
    }

    @GetMapping
    List<ContenedorDto> listar(@RequestParam(required = false) Integer zonaId) {
        return aDto(buscarPorZona(zonaId));
    }

    /** Los que hay que ir a buscar, del más lleno al menos lleno. */
    @GetMapping("/prioritarios")
    List<ContenedorDto> prioritarios(@RequestParam(required = false) Integer zonaId) {
        List<Contenedor> lista = buscarPorZona(zonaId);
        Map<String, Evaluacion> evaluaciones = prioridad.evaluar(lista, reloj.ahora());
        Map<String, Long> reportes = prioridad.reportesVecinales();
        return lista.stream()
                .filter(c -> evaluaciones.get(c.getId()).esPrioritario())
                .sorted(Comparator.comparingDouble((Contenedor c) -> evaluaciones.get(c.getId()).lectura()).reversed())
                .map(c -> ContenedorDto.de(c, evaluaciones.get(c.getId()), reportes.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @GetMapping("/{id}")
    ContenedorDto buscar(@PathVariable String id) {
        return aDto(List.of(buscarPorId(id))).get(0);
    }

    /** Lecturas del sensor para dibujar su curva. Por defecto, las últimas 24 horas. */
    @GetMapping("/{id}/lecturas")
    List<LecturaDto> lecturas(@PathVariable String id, @RequestParam(defaultValue = "24") int horas) {
        buscarPorId(id);
        Instant desde = reloj.ahora().minus(Duration.ofHours(Math.clamp(horas, 1, 72)));
        return lecturas.historial(id, desde).stream()
                .map(l -> new LecturaDto(Fechas.enBuenosAires(l.medidoAt()), l.llenado()))
                .toList();
    }

    private List<Contenedor> buscarPorZona(Integer zonaId) {
        return zonaId == null
                ? contenedores.findByActivoTrueOrderById()
                : contenedores.findByZonaIdAndActivoTrueOrderById(zonaId);
    }

    private Contenedor buscarPorId(String id) {
        return contenedores.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException("CONTENEDOR_NO_EXISTE", "No existe el contenedor " + id + "."));
    }

    private List<ContenedorDto> aDto(List<Contenedor> lista) {
        Map<String, Evaluacion> evaluaciones = prioridad.evaluar(lista, reloj.ahora());
        Map<String, Long> reportes = prioridad.reportesVecinales();
        return lista.stream()
                .map(c -> ContenedorDto.de(c, evaluaciones.get(c.getId()), reportes.getOrDefault(c.getId(), 0L)))
                .toList();
    }
}
