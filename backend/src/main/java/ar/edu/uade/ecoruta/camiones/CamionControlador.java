package ar.edu.uade.ecoruta.camiones;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/camiones")
public class CamionControlador {

    private final CamionRepositorio camiones;

    CamionControlador(CamionRepositorio camiones) {
        this.camiones = camiones;
    }

    @GetMapping
    List<CamionDto> listar(@RequestParam(required = false) Integer zonaId) {
        List<Camion> lista = zonaId == null ? camiones.findAllByOrderByZonaId() : camiones.findByZonaId(zonaId);
        return lista.stream().map(CamionDto::de).toList();
    }
}
