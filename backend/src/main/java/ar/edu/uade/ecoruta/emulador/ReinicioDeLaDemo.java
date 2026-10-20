package ar.edu.uade.ecoruta.emulador;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.contenedores.PatronesDeLlenado;
import ar.edu.uade.ecoruta.indicadores.ServicioDeIndicadores;
import ar.edu.uade.ecoruta.rutas.ServicioDeTrazado;

/**
 * Vuelve la demo al punto de partida sin recrear la base: borra lo que pasó (lecturas, rutas,
 * incidentes, patrón aprendido), devuelve contenedores y camiones a su estado inicial y vuelve
 * a cargar el historial de ejemplo. Los usuarios, zonas y contenedores no se tocan.
 * Solo existe con el emulador prendido: con datos reales no tiene sentido borrar nada.
 */
@Service
@ConditionalOnProperty(name = "ecoruta.emulador.activo", havingValue = "true")
public class ReinicioDeLaDemo {

    private static final Logger log = LoggerFactory.getLogger(ReinicioDeLaDemo.class);

    private final EmuladorDeSensores emulador;
    private final JdbcTemplate jdbc;
    private final EntityManager entidades;
    private final RelojDelSistema reloj;
    private final PatronesDeLlenado patrones;
    private final ServicioDeIndicadores indicadores;
    private final TransactionTemplate transaccion;
    private final ServicioDeTrazado trazado;

    ReinicioDeLaDemo(EmuladorDeSensores emulador, JdbcTemplate jdbc, EntityManager entidades, RelojDelSistema reloj,
            PatronesDeLlenado patrones, ServicioDeIndicadores indicadores, TransactionTemplate transaccion,
            ServicioDeTrazado trazado) {
        this.trazado = trazado;
        this.indicadores = indicadores;
        this.patrones = patrones;
        this.emulador = emulador;
        this.jdbc = jdbc;
        this.entidades = entidades;
        this.reloj = reloj;
        this.transaccion = transaccion;
    }

    /** Devuelve la hora con la que vuelve a arrancar la demo. */
    public Instant reiniciar() {
        Instant arranque = emulador.conElEmuladorDetenido(() -> transaccion.execute(estado -> {
            // RESTART IDENTITY: los incidentes y las rutas vuelven a numerarse desde 1
            jdbc.execute("TRUNCATE notificacion, parada_propuesta, parada, ruta, incidente, lectura_sensor, patron_llenado, resumen_diario"
                    + " RESTART IDENTITY");
            jdbc.update("""
                    UPDATE contenedor SET llenado = llenado_inicial, ultima_lectura_at = NULL, anomalia = FALSE,
                           estado_sensor = 'funcionando', nivel_estimado = NULL, ultima_recoleccion_at = NULL,
                           desborde_desde = NULL
                    """);
            // Los turnos abiertos en la app del chofer siguen abiertos: así, en la demo, la primera
            // ruta después de reiniciar le llega al chofer como ruta nueva
            jdbc.update("""
                    UPDATE camion k SET estado = 'en_base', carga_pct = 0, km_recorridos_hoy = 0,
                           lat = z.base_lat, lng = z.base_lng
                    FROM zona z WHERE z.id = k.zona_id
                    """);
            jdbc.update("UPDATE estado_sistema SET reloj_at = NULL, en_pausa = FALSE, velocidad = 1 WHERE id = 1");

            entidades.clear(); // que nadie siga usando los datos de antes
            reloj.recargar();
            patrones.olvidar();
            indicadores.olvidar();
            trazado.olvidar(); // las rutas se vuelven a numerar desde 1
            emulador.olvidar();
            emulador.preparar();
            return reloj.ahora();
        }));
        log.info("Demo reiniciada. Hora del sistema: {}", arranque);
        return arranque;
    }
}
