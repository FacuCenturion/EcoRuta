package ar.edu.uade.ecoruta.contenedores;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contenedor")
public class Contenedor {

    @Id
    private String id; // por ejemplo Z2-04
    private String direccion;

    @Column(name = "zona_id")
    private int zonaId;
    private double lat;
    private double lng;

    @Column(name = "velocidad_llenado")
    private BigDecimal velocidadLlenado; // % por hora en condiciones normales

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int llenado; // última lectura del sensor, en %

    @Column(name = "ultima_lectura_at")
    private OffsetDateTime ultimaLecturaAt;
    private boolean anomalia;
    private boolean activo;

    protected Contenedor() {
    }

    public String getId() {
        return id;
    }

    public String getDireccion() {
        return direccion;
    }

    public int getZonaId() {
        return zonaId;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public BigDecimal getVelocidadLlenado() {
        return velocidadLlenado;
    }

    public int getLlenado() {
        return llenado;
    }

    public OffsetDateTime getUltimaLecturaAt() {
        return ultimaLecturaAt;
    }

    public boolean isAnomalia() {
        return anomalia;
    }

    public boolean isActivo() {
        return activo;
    }
}
