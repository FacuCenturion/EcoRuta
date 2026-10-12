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
    private boolean anomalia; // las últimas lecturas suben y bajan de golpe
    private boolean activo;

    @Column(name = "estado_sensor")
    private String estadoSensor; // funcionando, irregular, congelado o sin_senal

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "nivel_estimado")
    private Integer nivelEstimado; // solo cuando no se puede confiar en el sensor

    @Column(name = "ultima_recoleccion_at")
    private OffsetDateTime ultimaRecoleccionAt;

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
}
