package ar.edu.uade.ecoruta.incidentes;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.contenedores.Contenedor;

@Entity
@Table(name = "incidente")
public class Incidente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tipo; // llenado_irregular, no_se_pudo, reporte_vecinal o ruta_no_iniciada
    private String estado; // pendiente, confirmado, descartado o resuelto

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contenedor_id")
    private Contenedor contenedor; // vacío en ruta_no_iniciada

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camion_id")
    private Camion camion;
    private String descripcion;
    private String sugerencia;
    private String origen; // por ejemplo Boti en los reportes vecinales

    @Column(name = "creado_at")
    private OffsetDateTime creadoAt;

    protected Incidente() {
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getEstado() {
        return estado;
    }

    public Contenedor getContenedor() {
        return contenedor;
    }

    public Camion getCamion() {
        return camion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getSugerencia() {
        return sugerencia;
    }

    public String getOrigen() {
        return origen;
    }

    public OffsetDateTime getCreadoAt() {
        return creadoAt;
    }
}
