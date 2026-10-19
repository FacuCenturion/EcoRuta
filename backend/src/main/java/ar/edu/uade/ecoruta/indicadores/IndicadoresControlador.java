package ar.edu.uade.ecoruta.indicadores;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.RelojDelSistema;

/** Solo el director (ver ConfiguracionSeguridad). */
@RestController
@RequestMapping("/api/v1/indicadores")
public class IndicadoresControlador {

    private final ServicioDeIndicadores servicio;
    private final RelojDelSistema reloj;

    IndicadoresControlador(ServicioDeIndicadores servicio, RelojDelSistema reloj) {
        this.servicio = servicio;
        this.reloj = reloj;
    }

    @GetMapping
    public IndicadoresDto indicadores(@RequestParam(required = false) Integer sectorId,
            @RequestParam(defaultValue = "30") int dias) {
        return servicio.calcular(sectorId, Math.clamp(dias, 1, 56), reloj.ahora());
    }
}
