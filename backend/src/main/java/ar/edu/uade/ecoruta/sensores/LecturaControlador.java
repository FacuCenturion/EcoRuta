package ar.edu.uade.ecoruta.sensores;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Por acá entran las lecturas de los sensores reales. No usa login: se identifica con una clave. */
@RestController
@RequestMapping("/api/v1/lecturas")
public class LecturaControlador {

    private final ServicioDeLecturas servicio;
    private final String claveDeSensores;

    LecturaControlador(ServicioDeLecturas servicio, @Value("${ecoruta.sensores.api-key}") String claveDeSensores) {
        this.servicio = servicio;
        this.claveDeSensores = claveDeSensores;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    ServicioDeLecturas.Resultado recibir(@RequestHeader(name = "X-Api-Key", required = false) String clave,
            @RequestBody @NotEmpty List<@Valid LecturaNueva> lecturas) {
        if (!claveDeSensores.equals(clave)) {
            throw new BadCredentialsException("Falta el header X-Api-Key o la clave es incorrecta.");
        }
        return servicio.registrar(lecturas);
    }
}
