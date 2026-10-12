package ar.edu.uade.ecoruta.comun.error;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Convierte las excepciones en respuestas con la forma de {@link ErrorApi}.
 * Los códigos HTTP siguen la tabla de Convenciones de la guía de backend.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorApi> datosInvalidos(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return responder(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorApi> jsonInvalido(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "El cuerpo del pedido no es un JSON válido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorApi> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return responder(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO",
                "El parámetro " + ex.getName() + " tiene un valor inválido.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorApi> sinPermiso(AccessDeniedException ex) {
        return responder(HttpStatus.FORBIDDEN, "SIN_PERMISO", "Tu rol no tiene permiso para esta acción.");
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ErrorApi> noEncontrado(RecursoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, ex.getCodigo(), ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorApi> rutaInexistente(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, "RUTA_INEXISTENTE", "No existe el endpoint pedido.");
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    ResponseEntity<ErrorApi> estadoInvalido(EstadoInvalidoException ex) {
        return responder(HttpStatus.CONFLICT, ex.getCodigo(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> errorInesperado(Exception ex) {
        log.error("Error no controlado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error en el servidor.");
    }

    private static ResponseEntity<ErrorApi> responder(HttpStatus estado, String codigo, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorApi(codigo, mensaje));
    }
}
