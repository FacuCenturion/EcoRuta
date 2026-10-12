package ar.edu.uade.ecoruta.camiones;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ar.edu.uade.ecoruta.auth.Usuario;

@Entity
@Table(name = "camion")
public class Camion {

    @Id
    private String id; // por ejemplo C22
    private int numero;

    @Column(name = "zona_id")
    private int zonaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chofer_id")
    private Usuario chofer;
    private String estado; // en_ruta, en_descarga, en_base o demorado

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "carga_pct")
    private int cargaPct;
    private double lat;
    private double lng;

    @Column(name = "km_recorridos_hoy")
    private BigDecimal kmRecorridosHoy;

    protected Camion() {
    }

    public String getId() {
        return id;
    }

    public int getNumero() {
        return numero;
    }

    public int getZonaId() {
        return zonaId;
    }

    public Usuario getChofer() {
        return chofer;
    }

    public String getEstado() {
        return estado;
    }

    public int getCargaPct() {
        return cargaPct;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public BigDecimal getKmRecorridosHoy() {
        return kmRecorridosHoy;
    }
}
