package ar.edu.uade.ecoruta.emulador;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.comun.error.EstadoInvalidoException;

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
    private final ObjectProvider<ReinicioDeLaDemo> reinicio;

    SimulacionControlador(RelojDelSistema reloj, @Value("${ecoruta.emulador.activo}") boolean emuladorActivo,
            ObjectProvider<ReinicioDeLaDemo> reinicio) {
        this.reloj = reloj;
        this.emuladorActivo = emuladorActivo;
        this.reinicio = reinicio;
    }

    @GetMapping
    public EstadoSimulacion estado() {
        return new EstadoSimulacion(Fechas.enBuenosAires(reloj.ahora()), emuladorActivo, reloj.enPausa(),
                reloj.velocidad());
    }

    /** Solo el director: vuelve la demo al punto de partida (borra lecturas, rutas e incidentes). */
    @PostMapping("/reiniciar")
    public EstadoSimulacion reiniciar() {
        ReinicioDeLaDemo servicio = reinicio.getIfAvailable();
        if (servicio == null) {
            throw new EstadoInvalidoException("EMULADOR_APAGADO",
                    "La demo solo se puede reiniciar con el emulador prendido: con datos reales no se borra nada.");
        }
        servicio.reiniciar();
        return estado();
    }

    @PostMapping
    public EstadoSimulacion configurar(@Valid @RequestBody PedidoSimulacion pedido) {
        reloj.configurar(pedido.enPausa(), pedido.velocidad());
        return estado();
    }
}
