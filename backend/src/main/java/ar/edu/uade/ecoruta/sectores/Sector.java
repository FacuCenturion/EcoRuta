package ar.edu.uade.ecoruta.sectores;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Parte de una zona que trabaja un camión. Los barrios se agrupan por cómo se usan. */
@Entity
@Table(name = "sector")
public class Sector {

    @Id
    private Integer id;

    @Column(name = "zona_id")
    private int zonaId;
    private String nombre;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> barrios;

    protected Sector() {
    }

    public Integer getId() {
        return id;
    }

    public int getZonaId() {
        return zonaId;
    }

    public String getNombre() {
        return nombre;
    }

    public List<String> getBarrios() {
        return barrios;
    }
}
