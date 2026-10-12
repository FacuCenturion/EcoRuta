package ar.edu.uade.ecoruta.zonas;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/zonas")
public class ZonaControlador {

    private final ZonaRepositorio zonas;

    ZonaControlador(ZonaRepositorio zonas) {
        this.zonas = zonas;
    }

    @GetMapping
    List<ZonaDto> listar() {
        return zonas.findAll(Sort.by("id")).stream().map(ZonaDto::de).toList();
    }
}
