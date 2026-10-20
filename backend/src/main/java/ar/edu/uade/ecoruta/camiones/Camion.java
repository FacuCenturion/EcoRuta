package ar.edu.uade.ecoruta.camiones;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Parametros;

@Entity
@Table(name = "camion")
public class Camion {

    public static final String EN_BASE = "en_base";
    public static final String EN_RUTA = "en_ruta";
    public static final String EN_DESCARGA = "en_descarga";
    public static final String DEMORADO = "demorado";

    @Id
    private String id; // por ejemplo C22
    private int numero;

    @Column(name = "zona_id")
    private int zonaId;

    @Column(name = "sector_id")
    private int sectorId;

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

    // Lo maneja un chofer con la app; si no, el emulador hace de chofer
    @Column(name = "manejado_por_app")
    private boolean manejadoPorApp;

    protected Camion() {
    }

    public void salirARuta(Coordenada base) {
        estado = EN_RUTA;
        cargaPct = 0;
        lat = base.lat();
        lng = base.lng();
    }

    /** El camión llegó a una parada: se mueve hasta ahí y suma los km del tramo. */
    public void moverA(Coordenada destino, double km) {
        lat = destino.lat();
        lng = destino.lng();
        kmRecorridosHoy = kmRecorridosHoy.add(BigDecimal.valueOf(km)).setScale(2, RoundingMode.HALF_UP);
    }

    public void cargarContenedor() {
        cargaPct = Math.min(100, cargaPct + Parametros.CARGA_POR_PARADA);
    }

    public void demorar() {
        estado = DEMORADO;
    }

    public void reanudar() {
        estado = EN_RUTA;
    }

    /** Terminó la ruta: vuelve a la base a descargar. */
    public void volverADescargar(Coordenada base, double km) {
        moverA(base, km);
        estado = EN_DESCARGA;
    }

    public void quedarDisponible() {
        estado = EN_BASE;
        cargaPct = 0;
    }

    public void empezarTurnoConLaApp() {
        manejadoPorApp = true;
    }

    public void terminarTurnoConLaApp() {
        manejadoPorApp = false;
    }

    public boolean isManejadoPorApp() {
        return manejadoPorApp;
    }

    public void reiniciarKmDelDia() {
        kmRecorridosHoy = BigDecimal.ZERO.setScale(2);
    }

    public Coordenada posicion() {
        return new Coordenada(lat, lng);
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

    public int getSectorId() {
        return sectorId;
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

    public BigDecimal getKmRecorridosHoy() {
        return kmRecorridosHoy;
    }
}
