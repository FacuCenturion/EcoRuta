package ar.edu.uade.ecoruta.chofer;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.Coordenada;

import ar.edu.uade.ecoruta.rutas.ServicioDeTrazado.TrazadoDto;

/**
 * La app del chofer. Solo el rol chofer (ver ConfiguracionSeguridad), y cada uno ve su camión:
 * el usuario sale del token. Las acciones devuelven el turno actualizado.
 */
@RestController
@RequestMapping("/api/v1/chofer")
public class ChoferControlador {

    /**
     * @param nota lo que escribió o dictó el chofer (opcional)
     * @param foto la foto, achicada, como data URL (opcional)
     */
    public record PedidoNoSePudo(@NotBlank String motivo, String nota, String foto) {
    }

    public record PedidoPosicion(@NotNull Double lat, @NotNull Double lng) {
    }

    private final ServicioDelChofer servicio;

    ChoferControlador(ServicioDelChofer servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/turno")
    public TurnoDto turno(@AuthenticationPrincipal Jwt token) {
        return servicio.turno(token.getSubject());
    }

    /** Desde ahora el camión lo maneja el chofer con la app, no el emulador. */
    @PostMapping("/turno/empezar")
    public TurnoDto empezarTurno(@AuthenticationPrincipal Jwt token) {
        return servicio.empezarTurno(token.getSubject());
    }

    @PostMapping("/turno/terminar")
    public TurnoDto terminarTurno(@AuthenticationPrincipal Jwt token) {
        return servicio.terminarTurno(token.getSubject());
    }

    @PostMapping("/ruta/iniciar")
    public TurnoDto iniciarRuta(@AuthenticationPrincipal Jwt token) {
        return servicio.iniciarRuta(token.getSubject());
    }

    @GetMapping("/ruta/trazado")
    public TrazadoDto trazado(@AuthenticationPrincipal Jwt token) {
        return servicio.trazado(token.getSubject());
    }

    @PostMapping("/paradas/{id}/recolectar")
    public TurnoDto recolectar(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return servicio.recolectar(token.getSubject(), id);
    }

    @PostMapping("/paradas/{id}/no-se-pudo")
    public TurnoDto noSePudo(@AuthenticationPrincipal Jwt token, @PathVariable long id,
            @Valid @RequestBody PedidoNoSePudo pedido) {
        return servicio.noSePudo(token.getSubject(), id, pedido.motivo(), pedido.nota(), pedido.foto());
    }

    /** La app manda la posición cada algunos segundos mientras el camión está en ruta. */
    @PostMapping("/posicion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void posicion(@AuthenticationPrincipal Jwt token, @Valid @RequestBody PedidoPosicion pedido) {
        servicio.ubicar(token.getSubject(), new Coordenada(pedido.lat(), pedido.lng()));
    }

    @PostMapping("/propuestas/{id}/aceptar")
    public TurnoDto aceptarParadaNueva(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return servicio.aceptarParadaNueva(token.getSubject(), id);
    }

    @PostMapping("/propuestas/{id}/rechazar")
    public TurnoDto rechazarParadaNueva(@AuthenticationPrincipal Jwt token, @PathVariable long id) {
        return servicio.rechazarParadaNueva(token.getSubject(), id);
    }

    @GetMapping("/motivos")
    public List<String> motivos() {
        return servicio.motivosNoSePudo();
    }
}
