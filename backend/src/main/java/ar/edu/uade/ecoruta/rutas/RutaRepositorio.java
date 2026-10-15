package ar.edu.uade.ecoruta.rutas;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RutaRepositorio extends JpaRepository<Ruta, Long> {

    // Todas traen el camión, las paradas y sus contenedores en la misma consulta

    @Query("""
            SELECT DISTINCT r FROM Ruta r
            JOIN FETCH r.camion k LEFT JOIN FETCH r.paradas p LEFT JOIN FETCH p.contenedor
            WHERE r.estado IN ('programada', 'en_curso')
            """)
    List<Ruta> activas();

    @Query("""
            SELECT DISTINCT r FROM Ruta r
            JOIN FETCH r.camion k LEFT JOIN FETCH r.paradas p LEFT JOIN FETCH p.contenedor
            WHERE r.creadaAt >= :desde
              AND (:zonaId IS NULL OR k.zonaId = :zonaId)
              AND (:estado IS NULL OR r.estado = :estado)
            ORDER BY r.creadaAt DESC
            """)
    List<Ruta> buscar(@Param("desde") OffsetDateTime desde, @Param("zonaId") Integer zonaId,
            @Param("estado") String estado);

    @Query("""
            SELECT DISTINCT r FROM Ruta r
            JOIN FETCH r.camion k LEFT JOIN FETCH r.paradas p LEFT JOIN FETCH p.contenedor
            WHERE r.id = :id
            """)
    Optional<Ruta> conParadas(@Param("id") long id);

    /** La ruta más nueva de cada camión: la que muestra el panel en "Rutas y camiones". */
    @Query("""
            SELECT DISTINCT r FROM Ruta r
            JOIN FETCH r.camion k LEFT JOIN FETCH r.paradas p LEFT JOIN FETCH p.contenedor
            WHERE r.id = (SELECT max(r2.id) FROM Ruta r2 WHERE r2.camion = r.camion)
            """)
    List<Ruta> ultimaDeCadaCamion();
}
