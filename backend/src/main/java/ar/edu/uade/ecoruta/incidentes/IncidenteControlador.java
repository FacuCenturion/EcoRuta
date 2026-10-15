package ar.edu.uade.ecoruta.incidentes;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.auth.Usuario;
import ar.edu.uade.ecoruta.auth.UsuarioRepositorio;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;

@RestController
@RequestMapping("/api/v1")
public class IncidenteControlador {

    public record PedidoDecision(
            @NotNull @Pattern(regexp = "confirmado|descartado", message = "tiene que ser confirmado o descartado") String decision) {
    }

    public record PedidoReporteVecinal(@NotBlank String contenedorId, String comentario, String origen) {
    }

    private final IncidenteRepositorio incidentes;
    private final ServicioDeIncidentes servicio;
    private final UsuarioRepositorio usuarios;
    private final RelojDelSistema reloj;

    IncidenteControlador(IncidenteRepositorio incidentes, ServicioDeIncidentes servicio, UsuarioRepositorio usuarios,
            RelojDelSistema reloj) {
        this.incidentes = incidentes;
        this.servicio = servicio;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @GetMapping("/incidentes")
    public List<IncidenteDto> listar(@RequestParam(required = false) Integer zonaId,
            @RequestParam(required = false) Integer sectorId, @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String estado) {
        return incidentes.buscar(zonaId, sectorId, tipo, estado).stream().map(IncidenteDto::de).toList();
    }

    /** El operador confirma o descarta. Queda guardado quién lo decidió y cuándo. */
    @PostMapping("/incidentes/{id}/decision")
    @Transactional
    public IncidenteDto decidir(@PathVariable long id, @Valid @RequestBody PedidoDecision pedido,
            @AuthenticationPrincipal Jwt token) {
        Usuario usuario = usuarios.findByUsuario(token.getSubject())
                .orElseThrow(() -> new BadCredentialsException("El usuario del token ya no existe."));
        return IncidenteDto.de(servicio.decidir(id, pedido.decision(), usuario, reloj.ahora()));
    }

    @GetMapping("/reportes-vecinales")
    public List<IncidenteDto> reportesVecinales(@RequestParam(required = false) Integer zonaId,
            @RequestParam(required = false) Integer sectorId, @RequestParam(required = false) String estado) {
        return incidentes.buscar(zonaId, sectorId, Incidente.REPORTE_VECINAL, estado).stream().map(IncidenteDto::de)
                .toList();
    }

    /** Carga un reporte de un vecino. Hoy lo usa el formulario del panel; más adelante, Boti. */
    @PostMapping("/reportes-vecinales")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public IncidenteDto reportar(@Valid @RequestBody PedidoReporteVecinal pedido) {
        return IncidenteDto.de(servicio.crearReporteVecinal(pedido.contenedorId(), pedido.comentario(),
                pedido.origen(), reloj.ahora()));
    }
}
