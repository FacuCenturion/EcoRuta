package ar.edu.uade.ecoruta.comun;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * La hora que usa todo el sistema para decidir (prioridad, sensores caídos, rutas).
 * Mientras no haya sensores reales es una hora simulada que adelanta el emulador;
 * si el emulador está apagado, es la hora real. Se guarda en la base para que
 * la demo siga donde estaba al reiniciar el servidor.
 */
@Component
public class RelojDelSistema {

    private final JdbcTemplate jdbc;
    private volatile Instant simulado;
    private volatile boolean enPausa;
    private volatile int velocidad;

    public RelojDelSistema(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.query("SELECT reloj_at, en_pausa, velocidad FROM estado_sistema WHERE id = 1", fila -> {
            OffsetDateTime guardado = fila.getObject("reloj_at", OffsetDateTime.class);
            simulado = guardado == null ? null : guardado.toInstant();
            enPausa = fila.getBoolean("en_pausa");
            velocidad = fila.getInt("velocidad");
        });
    }

    public Instant ahora() {
        Instant hora = simulado;
        return hora != null ? hora : Instant.now();
    }

    public boolean esSimulado() {
        return simulado != null;
    }

    public void fijar(Instant momento) {
        simulado = momento;
        jdbc.update("UPDATE estado_sistema SET reloj_at = ? WHERE id = 1", Fechas.paraLaBase(momento));
    }

    public boolean enPausa() {
        return enPausa;
    }

    public int velocidad() {
        return velocidad;
    }

    public void configurar(Boolean pausa, Integer nuevaVelocidad) {
        if (pausa != null) {
            enPausa = pausa;
        }
        if (nuevaVelocidad != null) {
            velocidad = nuevaVelocidad;
        }
        jdbc.update("UPDATE estado_sistema SET en_pausa = ?, velocidad = ? WHERE id = 1", enPausa, velocidad);
    }
}
