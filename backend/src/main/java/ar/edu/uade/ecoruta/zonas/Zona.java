package ar.edu.uade.ecoruta.zonas;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "zona")
public class Zona {

    @Id
    private Integer id;
    private String empresa;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "integer[]")
    private List<Integer> comunas;
    private String color;

    @Column(name = "centro_lat")
    private double centroLat;
    @Column(name = "centro_lng")
    private double centroLng;
    @Column(name = "base_lat")
    private double baseLat;
    @Column(name = "base_lng")
    private double baseLng;

    protected Zona() {
    }

    public Integer getId() {
        return id;
    }

    public String getEmpresa() {
        return empresa;
    }

    public List<Integer> getComunas() {
        return comunas;
    }

    public String getColor() {
        return color;
    }

    public double getCentroLat() {
        return centroLat;
    }

    public double getCentroLng() {
        return centroLng;
    }

    public double getBaseLat() {
        return baseLat;
    }

    public double getBaseLng() {
        return baseLng;
    }
}
