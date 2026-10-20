package ar.edu.uade.ecoruta.camiones;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CamionRepositorio extends JpaRepository<Camion, String> {

    @Override
    @EntityGraph(attributePaths = "chofer")
    java.util.Optional<Camion> findById(String id);

    // El chofer se trae en la misma consulta para no hacer una por camión. Dentro de cada
    // sector, por número de camión: así el orden no depende de cómo guarda las filas la base
    @EntityGraph(attributePaths = "chofer")
    List<Camion> findAllByOrderBySectorIdAscNumeroAsc();

    /** Igual que {@link #findAllByOrderBySectorIdAscNumeroAsc()}: se mantiene el nombre que ya usa el resto del código. */
    default List<Camion> findAllByOrderByZonaId() {
        return findAllByOrderBySectorIdAscNumeroAsc();
    }

    @EntityGraph(attributePaths = "chofer")
    List<Camion> findByZonaId(int zonaId);

    @EntityGraph(attributePaths = "chofer")
    List<Camion> findBySectorId(int sectorId);

    /** El camión asignado a un chofer, por su nombre de usuario. */
    @EntityGraph(attributePaths = "chofer")
    java.util.Optional<Camion> findByChoferUsuario(String usuario);
}
