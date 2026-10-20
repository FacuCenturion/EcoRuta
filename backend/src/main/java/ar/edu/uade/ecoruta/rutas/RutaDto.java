package ar.edu.uade.ecoruta.rutas;

import java.time.OffsetDateTime;
import java.util.List;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.comun.Fechas;

public record RutaDto(long id, String camionId, int camionNumero, int zonaId, int sectorId, String estado, String origen,
        OffsetDateTime creada, OffsetDateTime salidaProgramada, OffsetDateTime salidaReal, double kmPlanificados,
        double kmRutaFija, int duracionEstimadaMin, List<ParadaDto> paradas) {

    /**
     * Parada con los mismos nombres de campo que el tipo Parada del front.
     *
     * @param motivoVisita      por qué pasa el camión: llenado, riesgo, reporte, tiempo o de_paso
     * @param motivo            por qué no se pudo recolectar (solo en las que quedaron no_se_pudo)
     * @param llenado           lo que el sistema cree que tiene ahora (el estimado si el sensor falla)
     * @param tipo              lateral, bilateral o soterrado
     * @param ultimaRecoleccion la última vez que un chofer lo vació; vacía si no hay dato
     */
    public record ParadaDto(long id, String contenedorId, String direccion, Coordenada posicion, int orden, String estado,
            String motivoVisita, OffsetDateTime eta, OffsetDateTime horaReal, String motivo, int llenado, String tipo,
            String barrio, OffsetDateTime ultimaRecoleccion) {

        public static ParadaDto de(Parada p) {
            Contenedor c = p.getContenedor();
            return new ParadaDto(p.getId(), c.getId(), c.getDireccion(), c.posicion(), p.getOrden(),
                    p.getEstado(), p.getMotivoVisita(), Fechas.enBuenosAires(p.getEta()), Fechas.enBuenosAires(p.getHoraReal()),
                    p.getMotivo(), c.getNivelEstimado() != null ? c.getNivelEstimado() : c.getLlenado(), c.getTipo(),
                    c.getBarrio(), c.getUltimaRecoleccionAt());
        }
    }

    public static RutaDto de(Ruta r) {
        return new RutaDto(r.getId(), r.getCamion().getId(), r.getCamion().getNumero(), r.getCamion().getZonaId(),
                r.getCamion().getSectorId(),
                r.getEstado(), r.getOrigen(), Fechas.enBuenosAires(r.getCreadaAt()),
                Fechas.enBuenosAires(r.getSalidaProgramada()), Fechas.enBuenosAires(r.getSalidaReal()),
                r.getKmPlanificados().doubleValue(), r.getKmRutaFija().doubleValue(), r.getDuracionEstimadaMin(),
                r.getParadas().stream().map(ParadaDto::de).toList());
    }
}
