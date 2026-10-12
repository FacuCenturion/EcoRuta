package ar.edu.uade.ecoruta.camiones;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CamionRepositorio extends JpaRepository<Camion, String> {

    // El chofer se trae en la misma consulta para no hacer una por camión
    @EntityGraph(attributePaths = "chofer")
    List<Camion> findAllByOrderByZonaId();

    @EntityGraph(attributePaths = "chofer")
    List<Camion> findByZonaId(int zonaId);
}
