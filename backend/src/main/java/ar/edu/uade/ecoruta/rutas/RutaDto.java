package ar.edu.uade.ecoruta.rutas;

import java.time.OffsetDateTime;
import java.util.List;

import ar.edu.uade.ecoruta.comun.Fechas;

public record RutaDto(long id, String camionId, int camionNumero, int zonaId, String estado, String origen,
        OffsetDateTime creada, OffsetDateTime salidaProgramada, OffsetDateTime salidaReal, double kmPlanificados,
        double kmRutaFija, int duracionEstimadaMin, List<ParadaDto> paradas) {

    /**
     * Parada con los mismos nombres de campo que el tipo Parada del front.
     *
     * @param motivoVisita por qué pasa el camión: llenado, riesgo, reporte, tiempo o de_paso
     * @param motivo       por qué no se pudo recolectar (solo en las que quedaron no_se_pudo)
     */
    public record ParadaDto(long id, String contenedorId, String direccion, int orden, String estado,
            String motivoVisita, OffsetDateTime eta, OffsetDateTime horaReal, String motivo) {

        public static ParadaDto de(Parada p) {
            return new ParadaDto(p.getId(), p.getContenedor().getId(), p.getContenedor().getDireccion(), p.getOrden(),
                    p.getEstado(), p.getMotivoVisita(), Fechas.enBuenosAires(p.getEta()), Fechas.enBuenosAires(p.getHoraReal()),
                    p.getMotivo());
        }
    }

    public static RutaDto de(Ruta r) {
        return new RutaDto(r.getId(), r.getCamion().getId(), r.getCamion().getNumero(), r.getCamion().getZonaId(),
                r.getEstado(), r.getOrigen(), Fechas.enBuenosAires(r.getCreadaAt()),
                Fechas.enBuenosAires(r.getSalidaProgramada()), Fechas.enBuenosAires(r.getSalidaReal()),
                r.getKmPlanificados().doubleValue(), r.getKmRutaFija().doubleValue(), r.getDuracionEstimadaMin(),
                r.getParadas().stream().map(ParadaDto::de).toList());
    }
}
