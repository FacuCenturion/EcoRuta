package ar.edu.uade.ecoruta.comun.error;

/** El contenedor, camión, ruta o incidente pedido no existe. Responde 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    private final String codigo;

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
