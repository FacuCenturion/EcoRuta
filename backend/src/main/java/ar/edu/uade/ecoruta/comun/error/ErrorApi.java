package ar.edu.uade.ecoruta.comun.error;

/**
 * Forma única de todos los errores de la API.
 * {@code codigo} es estable y el front puede usarlo para decidir qué hacer;
 * {@code mensaje} es para mostrar.
 */
public record ErrorApi(String codigo, String mensaje) {
}
