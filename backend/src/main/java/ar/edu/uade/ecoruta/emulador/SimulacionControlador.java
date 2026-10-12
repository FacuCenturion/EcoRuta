package ar.edu.uade.ecoruta.emulador;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;

/** La hora del sistema y los controles de la demo (pausar y acelerar) que usa el encabezado del panel. */
@RestController
@RequestMapping("/api/v1/simulacion")
public class SimulacionControlador {

    /**
     * @param activa false cuando el emulador está apagado y el sistema usa la hora real
     */
    public record EstadoSimulacion(OffsetDateTime reloj, boolean activa, boolean enPausa, int velocidad) {
    }

    public record PedidoSimulacion(Boolean enPausa, @Min(1) @Max(20) Integer velocidad) {
    }

    private final RelojDelSistema reloj;
    private final boolean emuladorActivo;

    SimulacionControlador(RelojDelSistema reloj, @Value("${ecoruta.emulador.activo}") boolean emuladorActivo) {
        this.reloj = reloj;
        this.emuladorActivo = emuladorActivo;
    }

    @GetMapping
    public EstadoSimulacion estado() {
        return new EstadoSimulacion(Fechas.enBuenosAires(reloj.ahora()), emuladorActivo, reloj.enPausa(),
                reloj.velocidad());
    }

    @PostMapping
    public EstadoSimulacion configurar(@Valid @RequestBody PedidoSimulacion pedido) {
        reloj.configurar(pedido.enPausa(), pedido.velocidad());
        return estado();
    }
}
