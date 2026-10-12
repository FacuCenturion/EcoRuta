package ar.edu.uade.ecoruta.incidentes;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidentes")
public class IncidenteControlador {

    private final IncidenteRepositorio incidentes;

    IncidenteControlador(IncidenteRepositorio incidentes) {
        this.incidentes = incidentes;
    }

    @GetMapping
    List<IncidenteDto> listar(@RequestParam(required = false) Integer zonaId,
            @RequestParam(required = false) String tipo, @RequestParam(required = false) String estado) {
        return incidentes.buscar(zonaId, tipo, estado).stream().map(IncidenteDto::de).toList();
    }
}
