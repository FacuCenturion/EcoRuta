package ar.edu.uade.ecoruta.contenedores;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.incidentes.IncidenteRepositorio;

@RestController
@RequestMapping("/api/v1/contenedores")
public class ContenedorControlador {

    private final ContenedorRepositorio contenedores;
    private final IncidenteRepositorio incidentes;

    ContenedorControlador(ContenedorRepositorio contenedores, IncidenteRepositorio incidentes) {
        this.contenedores = contenedores;
        this.incidentes = incidentes;
    }

    @GetMapping
    List<ContenedorDto> listar(@RequestParam(required = false) Integer zonaId) {
        List<Contenedor> lista = zonaId == null
                ? contenedores.findByActivoTrueOrderById()
                : contenedores.findByZonaIdAndActivoTrueOrderById(zonaId);
        Map<String, Long> reportes = incidentes.reportesVecinalesAbiertosPorContenedor();
        return lista.stream().map(c -> ContenedorDto.de(c, reportes.getOrDefault(c.getId(), 0L))).toList();
    }

    @GetMapping("/{id}")
    ContenedorDto buscar(@PathVariable String id) {
        Contenedor contenedor = contenedores.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException("CONTENEDOR_NO_EXISTE", "No existe el contenedor " + id + "."));
        Map<String, Long> reportes = incidentes.reportesVecinalesAbiertosPorContenedor();
        return ContenedorDto.de(contenedor, reportes.getOrDefault(id, 0L));
    }
}
