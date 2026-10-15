package ar.edu.uade.ecoruta.sensores;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Lo único que manda un sensor: de qué contenedor es, el nivel de llenado y cuándo lo midió. */
public record LecturaNueva(@NotBlank String contenedorId, @Min(0) @Max(100) int llenado, @NotNull Instant medidoAt) {
}
