package ar.edu.uade.ecoruta.incidentes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidenteRepositorio extends JpaRepository<Incidente, Long> {

    // Los filtros son opcionales: si llegan vacíos no se aplican.
    @Query("""
            SELECT i FROM Incidente i
            LEFT JOIN FETCH i.contenedor c
            LEFT JOIN FETCH i.camion k
            WHERE (:zonaId IS NULL OR c.zonaId = :zonaId OR k.zonaId = :zonaId)
              AND (:sectorId IS NULL OR c.sectorId = :sectorId OR k.sectorId = :sectorId)
              AND (:tipo IS NULL OR i.tipo = :tipo)
              AND (:estado IS NULL OR i.estado = :estado)
            ORDER BY i.creadoAt DESC, i.id DESC
            """)
    List<Incidente> buscar(@Param("zonaId") Integer zonaId, @Param("sectorId") Integer sectorId,
            @Param("tipo") String tipo, @Param("estado") String estado);

    @Query("""
            SELECT i FROM Incidente i LEFT JOIN FETCH i.contenedor LEFT JOIN FETCH i.camion WHERE i.id = :id
            """)
    java.util.Optional<Incidente> conDetalle(@Param("id") long id);

    @Query("""
            SELECT i FROM Incidente i LEFT JOIN FETCH i.contenedor
            WHERE i.estado IN ('pendiente', 'confirmado')
            """)
    List<Incidente> abiertos();

    @Query("""
            SELECT i.contenedor.id, COUNT(i) FROM Incidente i
            WHERE i.tipo = 'reporte_vecinal' AND i.estado IN ('pendiente', 'confirmado')
            GROUP BY i.contenedor.id
            """)
    List<Object[]> contarReportesVecinalesAbiertos();

    /** Cuántos reportes vecinales abiertos tiene cada contenedor (solo aparecen los que tienen alguno). */
    default Map<String, Long> reportesVecinalesAbiertosPorContenedor() {
        return contarReportesVecinalesAbiertos().stream()
                .collect(Collectors.toMap(fila -> (String) fila[0], fila -> (Long) fila[1]));
    }
}
