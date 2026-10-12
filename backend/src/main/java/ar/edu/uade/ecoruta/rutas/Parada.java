package ar.edu.uade.ecoruta.rutas;

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

@Entity
@Table(name = "parada")
public class Parada {

    public static final String PENDIENTE = "pendiente";
    public static final String RECOLECTADA = "recolectada";
    public static final String NO_SE_PUDO = "no_se_pudo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contenedor_id")
    private Contenedor contenedor;
    private int orden;
    private String estado; // pendiente, recolectada o no_se_pudo
    private OffsetDateTime eta; // hora estimada de llegada

    @Column(name = "hora_real")
    private OffsetDateTime horaReal;
    private String motivo; // por qué no se pudo recolectar

    @Column(name = "motivo_visita")
    private String motivoVisita; // llenado, riesgo, reporte, tiempo o de_paso

    protected Parada() {
    }

    Parada(Ruta ruta, Contenedor contenedor, int orden, Instant eta, String motivoVisita) {
        this.motivoVisita = motivoVisita;
        this.ruta = ruta;
        this.contenedor = contenedor;
        this.orden = orden;
        this.estado = PENDIENTE;
        this.eta = Fechas.paraLaBase(eta);
    }

    void cerrar(String nuevoEstado, String porQue, Instant momento) {
        estado = nuevoEstado;
        motivo = porQue;
        horaReal = Fechas.paraLaBase(momento);
    }

    public boolean estaPendiente() {
        return estado.equals(PENDIENTE);
    }

    public Long getId() {
        return id;
    }

    public Contenedor getContenedor() {
        return contenedor;
    }

    public int getOrden() {
        return orden;
    }

    public String getEstado() {
        return estado;
    }

    public OffsetDateTime getEta() {
        return eta;
    }

    public OffsetDateTime getHoraReal() {
        return horaReal;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getMotivoVisita() {
        return motivoVisita;
    }
}
