package ar.edu.uade.ecoruta.camiones;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.rutas.Ruta;
import ar.edu.uade.ecoruta.rutas.RutaRepositorio;

@RestController
@RequestMapping("/api/v1/camiones")
public class CamionControlador {

    private final CamionRepositorio camiones;
    private final RutaRepositorio rutas;

    CamionControlador(CamionRepositorio camiones, RutaRepositorio rutas) {
        this.camiones = camiones;
        this.rutas = rutas;
    }

    @GetMapping
    List<CamionDto> listar(@RequestParam(required = false) Integer zonaId) {
        List<Camion> lista = zonaId == null ? camiones.findAllByOrderByZonaId() : camiones.findByZonaId(zonaId);
        Map<String, Ruta> rutaPorCamion = rutas.ultimaDeCadaCamion().stream()
                .collect(Collectors.toMap(r -> r.getCamion().getId(), Function.identity()));
        return lista.stream().map(c -> CamionDto.de(c, rutaPorCamion.get(c.getId()))).toList();
    }
}
