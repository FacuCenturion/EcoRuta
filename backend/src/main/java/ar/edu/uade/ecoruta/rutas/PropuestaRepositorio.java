package ar.edu.uade.ecoruta.rutas;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropuestaRepositorio extends JpaRepository<PropuestaDeParada, Long> {

    @Query("""
            SELECT p FROM PropuestaDeParada p JOIN FETCH p.contenedor
            WHERE p.ruta.id = :rutaId AND p.estado = 'pendiente'
            """)
    Optional<PropuestaDeParada> pendienteDe(@Param("rutaId") long rutaId);

    @Query("SELECT p.contenedor.id FROM PropuestaDeParada p WHERE p.ruta.id = :rutaId")
    List<String> contenedoresPropuestos(@Param("rutaId") long rutaId);
}
