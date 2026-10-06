package ar.edu.uade.ecoruta.comun.error;

/**
 * La acción no corresponde al estado actual, por ejemplo decidir un incidente
 * que ya no está pendiente. Responde 409.
 */
public class EstadoInvalidoException extends RuntimeException {

    private final String codigo;

    public EstadoInvalidoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
