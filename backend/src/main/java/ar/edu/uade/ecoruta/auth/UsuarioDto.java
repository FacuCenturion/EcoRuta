package ar.edu.uade.ecoruta.auth;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Datos del usuario que ve el front (el tipo UsuarioSesion). Nunca incluye la contraseña. */
public record UsuarioDto(String usuario, String nombre, String cargo, String iniciales, String rol) {

    static UsuarioDto de(Usuario u) {
        return new UsuarioDto(u.getUsuario(), u.getNombre(), u.getCargo(), iniciales(u.getNombre()), u.getRol());
    }

    // "Roberto Aguirre" -> "RA"
    private static String iniciales(String nombre) {
        return Arrays.stream(nombre.trim().split("\\s+"))
                .limit(2)
                .map(palabra -> palabra.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());
    }
}
