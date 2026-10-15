package ar.edu.uade.ecoruta.rutas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.contenedores.Contenedor;

@Entity
@Table(name = "ruta")
public class Ruta {

    public static final String PROGRAMADA = "programada";
    public static final String EN_CURSO = "en_curso";
    public static final String TERMINADA = "terminada";

    public static final String AUTOMATICO = "automatico";
    public static final String MANUAL = "manual";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camion_id")
    private Camion camion;

    @Column(name = "creada_at")
    private OffsetDateTime creadaAt;

    @Column(name = "salida_programada")
    private OffsetDateTime salidaProgramada;

    @Column(name = "salida_real")
    private OffsetDateTime salidaReal;
    private String origen; // automatico o manual

    @Column(name = "km_planificados")
    private BigDecimal kmPlanificados;

    @Column(name = "km_ruta_fija")
    private BigDecimal kmRutaFija; // km si se visitaran todos los contenedores de la zona

    @Column(name = "duracion_estimada_min")
    private int duracionEstimadaMin;
    private String estado; // programada, en_curso o terminada

    @OneToMany(mappedBy = "ruta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden")
    private List<Parada> paradas = new ArrayList<>();

    protected Ruta() {
    }

    Ruta(Camion camion, String origen, Instant creada, Instant salida, double kmPlanificados, double kmRutaFija,
            int duracionEstimadaMin) {
        this.camion = camion;
        this.origen = origen;
        this.creadaAt = Fechas.paraLaBase(creada);
        this.salidaProgramada = Fechas.paraLaBase(salida);
        this.kmPlanificados = BigDecimal.valueOf(kmPlanificados).setScale(2, RoundingMode.HALF_UP);
        this.kmRutaFija = BigDecimal.valueOf(kmRutaFija).setScale(2, RoundingMode.HALF_UP);
        this.duracionEstimadaMin = duracionEstimadaMin;
        this.estado = PROGRAMADA;
    }

    void agregarParada(Contenedor contenedor, Instant eta, String motivoVisita) {
        paradas.add(new Parada(this, contenedor, paradas.size() + 1, eta, motivoVisita));
    }

    void iniciar(Instant momento) {
        estado = EN_CURSO;
        salidaReal = Fechas.paraLaBase(momento);
    }

    void terminar() {
        estado = TERMINADA;
    }

    public Optional<Parada> proximaParada() {
        return paradas.stream().filter(Parada::estaPendiente).findFirst();
    }

    public Long getId() {
        return id;
    }

    public Camion getCamion() {
        return camion;
    }

    public OffsetDateTime getCreadaAt() {
        return creadaAt;
    }

    public OffsetDateTime getSalidaProgramada() {
        return salidaProgramada;
    }

    public OffsetDateTime getSalidaReal() {
        return salidaReal;
    }

    public String getOrigen() {
        return origen;
    }

    public BigDecimal getKmPlanificados() {
        return kmPlanificados;
    }

    public BigDecimal getKmRutaFija() {
        return kmRutaFija;
    }

    public int getDuracionEstimadaMin() {
        return duracionEstimadaMin;
    }

    public String getEstado() {
        return estado;
    }

    public List<Parada> getParadas() {
        return paradas;
    }
}
