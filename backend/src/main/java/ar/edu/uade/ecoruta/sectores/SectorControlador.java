package ar.edu.uade.ecoruta.sectores;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.camiones.CamionRepositorio;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;

@RestController
@RequestMapping("/api/v1/sectores")
public class SectorControlador {

    /** Un sector con su camión y cuántos contenedores tiene. */
    public record SectorDto(int id, int zonaId, String nombre, List<String> barrios, String camionId,
            Integer camionNumero, long contenedores) {
    }

    private final SectorRepositorio sectores;
    private final CamionRepositorio camiones;
    private final ContenedorRepositorio contenedores;

    SectorControlador(SectorRepositorio sectores, CamionRepositorio camiones, ContenedorRepositorio contenedores) {
        this.sectores = sectores;
        this.camiones = camiones;
        this.contenedores = contenedores;
    }

    @GetMapping
    public List<SectorDto> listar() {
        Map<Integer, Camion> camionPorSector = camiones.findAllByOrderByZonaId().stream()
                .collect(Collectors.toMap(Camion::getSectorId, Function.identity(), (a, b) -> a));
        Map<Integer, Long> cantidad = contenedores.findByActivoTrueOrderById().stream()
                .collect(Collectors.groupingBy(Contenedor::getSectorId, Collectors.counting()));
        return sectores.findAll(Sort.by("id")).stream().map(s -> {
            Camion camion = camionPorSector.get(s.getId());
            return new SectorDto(s.getId(), s.getZonaId(), s.getNombre(), s.getBarrios(),
                    camion == null ? null : camion.getId(), camion == null ? null : camion.getNumero(),
                    cantidad.getOrDefault(s.getId(), 0L));
        }).toList();
    }
}
