package ar.edu.uade.ecoruta.eventos;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events: el panel abre esta conexión una vez y el servidor le avisa cuando hay
 * datos nuevos (evento "actualizacion", con la hora del sistema). Pide el mismo token que el
 * resto de la API, en el header Authorization.
 */
@RestController
@RequestMapping("/api/v1/eventos")
public class EventosControlador {

    private final CanalDeEventos canal;

    EventosControlador(CanalDeEventos canal) {
        this.canal = canal;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter suscribir() {
        return canal.suscribir();
    }
}
