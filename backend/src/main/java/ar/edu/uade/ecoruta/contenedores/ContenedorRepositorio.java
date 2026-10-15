package ar.edu.uade.ecoruta.contenedores;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContenedorRepositorio extends JpaRepository<Contenedor, String> {

    List<Contenedor> findByActivoTrueOrderById();

    List<Contenedor> findByZonaIdAndActivoTrueOrderById(int zonaId);

    List<Contenedor> findBySectorIdAndActivoTrueOrderById(int sectorId);
}
