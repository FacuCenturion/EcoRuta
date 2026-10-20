package ar.edu.uade.ecoruta.rutas;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;

@RestController
@RequestMapping("/api/v1/rutas")
public class RutaControlador {

    public record PedidoDespacho(@NotNull Integer sectorId) {
    }

    private final RutaRepositorio rutas;
    private final ServicioDeDespacho despacho;
    private final RelojDelSistema reloj;
    private final ServicioDeTrazado trazado;

    RutaControlador(RutaRepositorio rutas, ServicioDeDespacho despacho, RelojDelSistema reloj,
            ServicioDeTrazado trazado) {
        this.trazado = trazado;
        this.rutas = rutas;
        this.despacho = despacho;
        this.reloj = reloj;
    }

    /** Rutas del día (según la hora del sistema), de la más nueva a la más vieja. */
    @GetMapping
    public List<RutaDto> listar(@RequestParam(required = false) Integer zonaId,
            @RequestParam(required = false) Integer sectorId, @RequestParam(required = false) String estado) {
        var desde = Fechas.paraLaBase(Fechas.inicioDelDia(reloj.ahora()));
        return rutas.buscar(desde, zonaId, sectorId, estado).stream().map(RutaDto::de).toList();
    }

    @GetMapping("/{id}")
    public RutaDto buscar(@PathVariable long id) {
        return rutas.conParadas(id).map(RutaDto::de).orElseThrow(
                () -> new RecursoNoEncontradoException("RUTA_NO_EXISTE", "No existe la ruta " + id + "."));
    }

    /**
     * El recorrido por las calles, un tramo por cada tramo de la ruta. La primera vez responde
     * "calculando" y, cuando está listo, llega un aviso por /eventos.
     */
    @GetMapping("/{id}/trazado")
    public ServicioDeTrazado.TrazadoDto trazado(@PathVariable long id) {
        return trazado.consultar(id);
    }

    /** Despacho manual: el operador manda ahora el camión de un sector. */
    @PostMapping("/planificar")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RutaDto despachar(@Valid @RequestBody PedidoDespacho pedido) {
        return RutaDto.de(despacho.despacharManual(pedido.sectorId(), reloj.ahora()));
    }
}
