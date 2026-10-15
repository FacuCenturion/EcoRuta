package ar.edu.uade.ecoruta.comun.error;

/** El pedido está bien formado pero le falta un dato o tiene uno que no corresponde. Responde 400. */
public class DatosInvalidosException extends RuntimeException {

    public DatosInvalidosException(String mensaje) {
        super(mensaje);
    }
}
