package ar.edu.uade.ecoruta.camiones;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CamionRepositorio extends JpaRepository<Camion, String> {

    @Override
    @EntityGraph(attributePaths = "chofer")
    java.util.Optional<Camion> findById(String id);

    // El chofer se trae en la misma consulta para no hacer una por camión
    @EntityGraph(attributePaths = "chofer")
    List<Camion> findAllByOrderBySectorId();

    /** Igual que {@link #findAllByOrderBySectorId()}: se mantiene el nombre que ya usa el resto del código. */
    default List<Camion> findAllByOrderByZonaId() {
        return findAllByOrderBySectorId();
    }

    @EntityGraph(attributePaths = "chofer")
    List<Camion> findByZonaId(int zonaId);

    @EntityGraph(attributePaths = "chofer")
    List<Camion> findBySectorId(int sectorId);
}
