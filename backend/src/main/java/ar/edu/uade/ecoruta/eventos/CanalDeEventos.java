package ar.edu.uade.ecoruta.eventos;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;

/**
 * Avisa a los paneles abiertos que hay datos nuevos, para que no tengan que preguntar cada
 * tantos segundos. El aviso no lleva los datos: el panel, al recibirlo, vuelve a pedir lo que
 * muestra. Varios cambios seguidos se juntan en un solo aviso por segundo como mucho, así a
 * velocidad 20 el panel no pide todo cinco veces por segundo.
 */
@Component
public class CanalDeEventos {

    static final String ACTUALIZACION = "actualizacion";
    private static final long SIN_VENCIMIENTO = 0L;

    private final List<SseEmitter> suscriptos = new CopyOnWriteArrayList<>();
    private final AtomicBoolean hayCambios = new AtomicBoolean();
    private final RelojDelSistema reloj;

    CanalDeEventos(RelojDelSistema reloj) {
        this.reloj = reloj;
    }

    SseEmitter suscribir() {
        SseEmitter emisor = new SseEmitter(SIN_VENCIMIENTO);
        suscriptos.add(emisor);
        emisor.onCompletion(() -> suscriptos.remove(emisor));
        emisor.onTimeout(() -> suscriptos.remove(emisor));
        emisor.onError(ex -> suscriptos.remove(emisor));
        // El primer aviso confirma la conexión y le da al panel la hora del sistema
        enviar(emisor, SseEmitter.event().name(ACTUALIZACION).data(datos()));
        return emisor;
    }

    /** Hubo un cambio: los paneles se enteran en el próximo envío. */
    public void avisar() {
        hayCambios.set(true);
    }

    int suscriptos() {
        return suscriptos.size();
    }

    @Scheduled(fixedDelay = 1000)
    void enviarSiHayCambios() {
        if (hayCambios.getAndSet(false)) {
            suscriptos.forEach(emisor -> enviar(emisor, SseEmitter.event().name(ACTUALIZACION).data(datos())));
        }
    }

    // Un comentario cada tanto mantiene viva la conexión y descubre los paneles que se cerraron
    @Scheduled(fixedDelay = 20000)
    void latido() {
        suscriptos.forEach(emisor -> enviar(emisor, SseEmitter.event().comment("latido")));
    }

    private Map<String, Object> datos() {
        return Map.of("reloj", Fechas.enBuenosAires(reloj.ahora()));
    }

    private void enviar(SseEmitter emisor, SseEmitter.SseEventBuilder evento) {
        try {
            emisor.send(evento);
        } catch (IOException | IllegalStateException ex) {
            // El panel se cerró o se cortó la conexión
            suscriptos.remove(emisor);
            emisor.completeWithError(ex);
        }
    }
}
