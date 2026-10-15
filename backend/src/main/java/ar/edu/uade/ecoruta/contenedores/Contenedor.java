package ar.edu.uade.ecoruta.contenedores;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Fechas;

@Entity
@Table(name = "contenedor")
public class Contenedor {

    @Id
    private String id; // por ejemplo SNI-05 (prefijo del barrio)
    private String direccion;

    @Column(name = "zona_id")
    private int zonaId;

    @Column(name = "sector_id")
    private int sectorId;
    private String barrio;
    private String tipo; // lateral, bilateral o soterrado

    @Column(name = "capacidad_litros")
    private int capacidadLitros;
    private double lat;
    private double lng;

    @Column(name = "velocidad_llenado")
    private BigDecimal velocidadLlenado; // % por hora en condiciones normales

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int llenado; // última lectura del sensor, en %

    @Column(name = "ultima_lectura_at")
    private OffsetDateTime ultimaLecturaAt;
    private boolean anomalia; // las últimas lecturas suben y bajan de golpe
    private boolean activo;

    @Column(name = "estado_sensor")
    private String estadoSensor; // funcionando, irregular, congelado, sin_senal o trabado

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "nivel_estimado")
    private Integer nivelEstimado; // solo cuando no se puede confiar en el sensor

    @Column(name = "ultima_recoleccion_at")
    private OffsetDateTime ultimaRecoleccionAt;

    @Column(name = "desborde_desde")
    private OffsetDateTime desbordeDesde; // desde cuándo está lleno; vacío si no lo está

    protected Contenedor() {
    }

    public void registrarLectura(int nivel, Instant medidoAt) {
        // Una lectura que llega tarde queda en el historial pero no pisa a una más nueva
        if (ultimaLecturaAt == null || !medidoAt.isBefore(ultimaLecturaAt.toInstant())) {
            llenado = nivel;
            ultimaLecturaAt = Fechas.paraLaBase(medidoAt);
        }
    }

    public void marcarAnomalia(boolean irregular) {
        anomalia = irregular;
    }

    public void actualizarSensor(String estado, Integer estimado) {
        estadoSensor = estado;
        nivelEstimado = estimado;
    }

    /** Un chofer confirmó que lo vació. */
    public void registrarRecoleccion(Instant momento) {
        ultimaRecoleccionAt = Fechas.paraLaBase(momento);
        anomalia = false;
        desbordeDesde = null;
    }

    public void marcarDesborde(Instant momento) {
        desbordeDesde = Fechas.paraLaBase(momento);
    }

    public boolean estaDesbordado() {
        return desbordeDesde != null;
    }

    /** Si un camión lo vació hace menos de esas horas. */
    public boolean vaciadoHaceMenosDe(int horas, Instant ahora) {
        return ultimaRecoleccionAt != null
                && ultimaRecoleccionAt.toInstant().isAfter(ahora.minus(java.time.Duration.ofHours(horas)));
    }

    public Coordenada posicion() {
        return new Coordenada(lat, lng);
    }

    public double velocidad() {
        return velocidadLlenado.doubleValue();
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

    public int getSectorId() {
        return sectorId;
    }

    public String getBarrio() {
        return barrio;
    }

    public String getTipo() {
        return tipo;
    }

    public int getCapacidadLitros() {
        return capacidadLitros;
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

    public String getEstadoSensor() {
        return estadoSensor;
    }

    public Integer getNivelEstimado() {
        return nivelEstimado;
    }

    public OffsetDateTime getUltimaRecoleccionAt() {
        return ultimaRecoleccionAt;
    }

    public OffsetDateTime getDesbordeDesde() {
        return desbordeDesde;
    }
}
