package ar.edu.uade.ecoruta.camiones;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
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

    /** Un camión con su ruta más nueva. */
    @GetMapping("/{id}")
    CamionDto buscar(@PathVariable String id) {
        Camion camion = camiones.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException("CAMION_NO_EXISTE", "No existe el camión " + id + "."));
        return CamionDto.de(camion, rutas.ultimaDelCamion(id).orElse(null));
    }

    @GetMapping
    List<CamionDto> listar(@RequestParam(required = false) Integer zonaId,
            @RequestParam(required = false) Integer sectorId) {
        List<Camion> lista = sectorId != null ? camiones.findBySectorId(sectorId)
                : zonaId != null ? camiones.findByZonaId(zonaId) : camiones.findAllByOrderByZonaId();
        Map<String, Ruta> rutaPorCamion = rutas.ultimaDeCadaCamion().stream()
                .collect(Collectors.toMap(r -> r.getCamion().getId(), Function.identity()));
        return lista.stream().map(c -> CamionDto.de(c, rutaPorCamion.get(c.getId()))).toList();
    }
}
