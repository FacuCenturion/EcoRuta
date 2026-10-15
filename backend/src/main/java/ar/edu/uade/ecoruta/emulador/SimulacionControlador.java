package ar.edu.uade.ecoruta.emulador;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.comun.error.DatosInvalidosException;
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

    /**
     * Un caso para mostrar en la demo sin esperar a que pase solo.
     *
     * @param tipo falla_sensor, reparar_sensor, revolver, llenar, evento_masivo o reporte_vecinal
     * @param falla para falla_sensor: sin_senal, congelado o trabado (se traba al próximo vaciado)
     * @param horas cuánto dura la falla o el evento (por defecto 6 y 4)
     * @param nivel para llenar: a qué nivel queda el contenedor
     */
    public record PedidoCaso(@NotBlank String tipo, String contenedorId, Integer sectorId, String falla,
            @Min(1) @Max(48) Integer horas, @Min(0) @Max(100) Integer nivel, String comentario) {
    }

    public record ResultadoCaso(String mensaje, EstadoSimulacion simulacion) {
    }

    private final RelojDelSistema reloj;
    private final boolean emuladorActivo;
    private final ObjectProvider<ReinicioDeLaDemo> reinicio;
    private final ObjectProvider<EmuladorDeSensores> emulador;

    SimulacionControlador(RelojDelSistema reloj, @Value("${ecoruta.emulador.activo}") boolean emuladorActivo,
            ObjectProvider<ReinicioDeLaDemo> reinicio, ObjectProvider<EmuladorDeSensores> emulador) {
        this.emulador = emulador;
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

    /** Dispara un caso de la simulación: romper un sensor, un evento masivo, un reporte vecinal... */
    @PostMapping("/casos")
    public ResultadoCaso disparar(@Valid @RequestBody PedidoCaso pedido) {
        EmuladorDeSensores e = emulador.getIfAvailable();
        if (e == null) {
            throw new EstadoInvalidoException("EMULADOR_APAGADO", "Los casos de demo solo existen con el emulador prendido.");
        }
        String mensaje = switch (pedido.tipo()) {
            case "falla_sensor" -> {
                String falla = pedido.falla() == null ? "congelado" : pedido.falla();
                if (!falla.matches("sin_senal|congelado|trabado")) {
                    throw new DatosInvalidosException("falla: tiene que ser sin_senal, congelado o trabado");
                }
                yield e.fallarSensor(contenedor(pedido), falla, pedido.horas() == null ? 6 : pedido.horas());
            }
            case "reparar_sensor" -> e.repararSensor(contenedor(pedido));
            case "revolver" -> e.revolver(contenedor(pedido));
            case "llenar" -> e.llenar(contenedor(pedido), pedido.nivel() == null ? 100 : pedido.nivel());
            case "evento_masivo" -> {
                if (pedido.sectorId() == null) {
                    throw new DatosInvalidosException("sectorId: hace falta para un evento masivo");
                }
                yield e.eventoMasivo(pedido.sectorId(), pedido.horas() == null ? 4 : pedido.horas());
            }
            case "reporte_vecinal" -> e.reporteVecinal(contenedor(pedido), pedido.comentario());
            default -> throw new DatosInvalidosException("tipo: tiene que ser falla_sensor, reparar_sensor, revolver, "
                    + "llenar, evento_masivo o reporte_vecinal");
        };
        return new ResultadoCaso(mensaje, estado());
    }

    private static String contenedor(PedidoCaso pedido) {
        if (pedido.contenedorId() == null || pedido.contenedorId().isBlank()) {
            throw new DatosInvalidosException("contenedorId: hace falta para un caso de tipo " + pedido.tipo());
        }
        return pedido.contenedorId();
    }

    @PostMapping
    public EstadoSimulacion configurar(@Valid @RequestBody PedidoSimulacion pedido) {
        reloj.configurar(pedido.enPausa(), pedido.velocidad());
        return estado();
    }
}
