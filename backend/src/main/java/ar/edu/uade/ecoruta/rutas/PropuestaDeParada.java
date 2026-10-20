package ar.edu.uade.ecoruta.rutas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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

import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.Contenedor;

/** Un contenedor que se volvió urgente con el camión en ruta: se le propone al chofer sumarlo. */
@Entity
@Table(name = "parada_propuesta")
public class PropuestaDeParada {

    public static final String PENDIENTE = "pendiente";
    public static final String ACEPTADA = "aceptada";
    public static final String RECHAZADA = "rechazada";
    public static final String VENCIDA = "vencida";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contenedor_id")
    private Contenedor contenedor;
    private int orden; // en qué lugar de la ruta entraría

    @Column(name = "km_extra")
    private BigDecimal kmExtra;

    @Column(name = "minutos_extra")
    private int minutosExtra;
    private String motivo; // llenado o riesgo
    private String estado;

    @Column(name = "creada_at")
    private OffsetDateTime creadaAt;

    @Column(name = "decidida_at")
    private OffsetDateTime decididaAt;

    protected PropuestaDeParada() {
    }

    PropuestaDeParada(Ruta ruta, Contenedor contenedor, int orden, double kmExtra, int minutosExtra, String motivo,
            Instant creada) {
        this.ruta = ruta;
        this.contenedor = contenedor;
        this.orden = orden;
        this.kmExtra = BigDecimal.valueOf(kmExtra).setScale(2, RoundingMode.HALF_UP);
        this.minutosExtra = minutosExtra;
        this.motivo = motivo;
        this.estado = PENDIENTE;
        this.creadaAt = Fechas.paraLaBase(creada);
    }

    void decidir(String decision, Instant momento) {
        estado = decision;
        decididaAt = Fechas.paraLaBase(momento);
    }

    public boolean estaPendiente() {
        return estado.equals(PENDIENTE);
    }

    public Long getId() {
        return id;
    }

    public Ruta getRuta() {
        return ruta;
    }

    public Contenedor getContenedor() {
        return contenedor;
    }

    public int getOrden() {
        return orden;
    }

    public BigDecimal getKmExtra() {
        return kmExtra;
    }

    public int getMinutosExtra() {
        return minutosExtra;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getEstado() {
        return estado;
    }
}
