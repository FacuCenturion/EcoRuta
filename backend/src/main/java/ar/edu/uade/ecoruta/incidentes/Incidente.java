package ar.edu.uade.ecoruta.incidentes;

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

import ar.edu.uade.ecoruta.auth.Usuario;
import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.Contenedor;

@Entity
@Table(name = "incidente")
public class Incidente {

    public static final String LLENADO_IRREGULAR = "llenado_irregular";
    public static final String NO_SE_PUDO = "no_se_pudo";
    public static final String REPORTE_VECINAL = "reporte_vecinal";
    public static final String FALLA_SENSOR = "falla_sensor";
    public static final String DESBORDE = "desborde";

    public static final String PENDIENTE = "pendiente";
    public static final String CONFIRMADO = "confirmado";
    public static final String DESCARTADO = "descartado";
    public static final String RESUELTO = "resuelto";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tipo; // llenado_irregular, no_se_pudo, reporte_vecinal, ruta_no_iniciada, falla_sensor o desborde
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por")
    private Usuario decididoPor;

    @Column(name = "decidido_at")
    private OffsetDateTime decididoAt;

    protected Incidente() {
    }

    Incidente(String tipo, Contenedor contenedor, Camion camion, String descripcion, String sugerencia,
            String origen, Instant creado) {
        this.tipo = tipo;
        this.estado = PENDIENTE;
        this.contenedor = contenedor;
        this.camion = camion;
        this.descripcion = descripcion;
        this.sugerencia = sugerencia;
        this.origen = origen;
        this.creadoAt = Fechas.paraLaBase(creado);
    }

    public boolean estaAbierto() {
        return estado.equals(PENDIENTE) || estado.equals(CONFIRMADO);
    }

    void decidir(String decision, Usuario usuario, Instant momento) {
        estado = decision;
        decididoPor = usuario;
        decididoAt = Fechas.paraLaBase(momento);
    }

    void resolver() {
        estado = RESUELTO;
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

    public OffsetDateTime getDecididoAt() {
        return decididoAt;
    }
}
