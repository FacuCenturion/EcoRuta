package ar.edu.uade.ecoruta.emulador;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ar.edu.uade.ecoruta.BaseDePrueba;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.contenedores.Contenedor;
import ar.edu.uade.ecoruta.contenedores.ContenedorRepositorio;
import ar.edu.uade.ecoruta.contenedores.PatronesDeLlenado;

/**
 * Cada caso de la simulación, de punta a punta: el emulador provoca algo que el sistema no ve
 * directamente, y la prueba comprueba que el sistema lo deduce solo de las lecturas.
 */
@BaseDePrueba
class CasosDeSimulacionTests {

    @Autowired
    EmuladorDeSensores emulador;

    @Autowired
    ReinicioDeLaDemo reinicio;

    @Autowired
    RelojDelSistema reloj;

    @Autowired
    PatronesDeLlenado patrones;

    @Autowired
    ContenedorRepositorio contenedores;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void demoDeCero() {
        reinicio.reiniciar();
    }

    @Test
    void unSensorCongeladoSeDetectaALasCuatroHorasYSeRecuperaAlArreglarlo() {
        String id = contenedorTranquilo();
        emulador.fallarSensor(id, "congelado", 8);

        // A las 3 h 20 min todavía no hay forma de distinguirlo de uno que no se llena. (No se mira más
        // cerca de las 4 h: si justo antes de trabarse ya marcaba ese valor, la racha empezó antes.)
        emulador.avanzarPasos(40);
        assertThat(estado(id)).isEqualTo("funcionando");

        emulador.avanzarPasos(9); // pasaron 4 h 05 min
        assertThat(estado(id)).isEqualTo("congelado");
        assertThat(jdbc.queryForObject("SELECT nivel_estimado FROM contenedor WHERE id = ?", Integer.class, id))
                .isNotNull();
        assertThat(incidentes(id, "falla_sensor", "pendiente")).isEqualTo(1);

        // Arreglado: en cuanto el valor cambia, se nota que vuelve a medir. Con un contenedor que se
        // llena despacio puede tardar unos minutos en moverse del valor en el que estaba trabado.
        emulador.repararSensor(id);
        emulador.avanzarPasos(6);
        assertThat(estado(id)).isEqualTo("funcionando");
        assertThat(incidentes(id, "falla_sensor", "resuelto")).isEqualTo(1);
    }

    @Test
    void unSensorSinSenalSeDetectaPasadasLasDosHoras() {
        String id = contenedorTranquilo();
        emulador.fallarSensor(id, "sin_senal", 6);

        emulador.avanzarPasos(24); // justo 2 horas sin datos
        assertThat(estado(id)).isEqualTo("funcionando");

        emulador.avanzarPasos(1);
        assertThat(estado(id)).isEqualTo("sin_senal");
        assertThat(incidentes(id, "falla_sensor", "pendiente")).isEqualTo(1);
    }

    @Test
    void unSensorQueNoBajaDespuesDeVaciarseSeDetectaComoTrabado() {
        // El más lleno del sector 1 seguro entra en la primera ruta
        String id = jdbc.queryForObject("""
                SELECT id FROM contenedor WHERE sector_id = 1 AND estado_sensor = 'funcionando'
                ORDER BY llenado DESC LIMIT 1
                """, String.class);
        emulador.fallarSensor(id, "trabado", 0);

        for (int paso = 0; paso < 60 && !recolectado(id); paso++) {
            emulador.avanzarPasos(1);
        }
        assertThat(recolectado(id)).as("el camión pasó por " + id).isTrue();

        emulador.avanzarPasos(3);
        assertThat(estado(id)).isEqualTo("trabado");
        assertThat(incidentes(id, "falla_sensor", "pendiente")).isEqualTo(1);
        // Recién vaciado: el nivel estimado parte de la recolección, no de lo que marca el sensor
        assertThat(jdbc.queryForObject("SELECT nivel_estimado FROM contenedor WHERE id = ?", Integer.class, id))
                .isLessThan(20);
    }

    @Test
    void unContenedorQueLlegaAlTopeQuedaRegistradoComoDesborde() {
        String id = contenedorTranquilo();
        emulador.llenar(id, 100);

        emulador.avanzarPasos(1);

        assertThat(jdbc.queryForObject("SELECT desborde_desde IS NOT NULL FROM contenedor WHERE id = ?", Boolean.class, id))
                .isTrue();
        assertThat(incidentes(id, "desborde", "pendiente")).isEqualTo(1);
    }

    @Test
    void alguienRevolviendoLaBasuraGeneraUnaAlerta() {
        String id = contenedorTranquilo();
        emulador.llenar(id, 50); // las lecturas irregulares se notan en un contenedor a medio llenar
        emulador.revolver(id);

        emulador.avanzarPasos(7);

        assertThat(incidentes(id, "llenado_irregular", "pendiente")).isEqualTo(1);
    }

    @Test
    void unEventoMasivoHaceQueElSectorSeLleneMasRapido() {
        // La misma hora del mismo sector, primero sin evento y después con evento. Como la simulación
        // usa una semilla fija, lo único que cambia entre las dos corridas es el evento.
        Instant desde = reloj.ahora();
        emulador.avanzarPasos(12);
        double sinEvento = subidaPromedio(2, desde);

        reinicio.reiniciar();
        emulador.eventoMasivo(2, 4);
        emulador.avanzarPasos(12);
        double conEvento = subidaPromedio(2, desde);

        // El evento duplica la basura; con el ruido y alguna recolección, al menos un 60% más
        assertThat(conEvento).isGreaterThan(sinEvento * 1.6);
    }

    @Test
    void elSistemaAprendeQueLasOficinasSeLlenanAlMediodiaYLasViviendasALaNoche() {
        emulador.avanzarPasos(1); // con las primeras lecturas (24 h de historial) se calcula el patrón
        assertThat(patrones.cantidadConPatron()).isGreaterThan(150);

        // Con un solo día de datos, cada hora de un contenedor tiene una sola observación y el
        // ruido pesa; promediando el barrio se ve el patrón. Con dos semanas se ve en cada uno.
        double oficinasMediodia = ritmoPromedio("San Nicolás", 12);
        double oficinasMadrugada = ritmoPromedio("San Nicolás", 3);
        double viviendasNoche = ritmoPromedio("Constitución", 19);
        double viviendasMediodia = ritmoPromedio("Constitución", 12);

        assertThat(oficinasMediodia).isGreaterThan(oficinasMadrugada * 4);
        assertThat(viviendasNoche).isGreaterThan(viviendasMediodia * 1.4);
    }

    // ---------- ayudas ----------

    private double ritmoPromedio(String barrio, int hora) {
        return contenedores.findByActivoTrueOrderById().stream()
                .filter(c -> c.getBarrio().equals(barrio) && c.getEstadoSensor().equals("funcionando"))
                .mapToDouble(c -> ritmo(c, hora))
                .average().orElseThrow();
    }

    private double ritmo(Contenedor contenedor, int hora) {
        Instant momento = reloj.ahora().atZone(Fechas.BUENOS_AIRES).toLocalDate().atTime(LocalTime.of(hora, 30))
                .atZone(Fechas.BUENOS_AIRES).toInstant();
        return patrones.ritmoDe(contenedor).porHora(momento);
    }

    // Uno con el sensor sano, casi vacío y que se llena despacio: no lo va a buscar ningún camión
    private String contenedorTranquilo() {
        return jdbc.queryForObject("""
                SELECT id FROM contenedor
                WHERE estado_sensor = 'funcionando' AND tipo = 'lateral' AND llenado < 30
                ORDER BY llenado, id LIMIT 1
                """, String.class);
    }

    private String estado(String id) {
        return jdbc.queryForObject("SELECT estado_sensor FROM contenedor WHERE id = ?", String.class, id);
    }

    private int incidentes(String id, String tipo, String estado) {
        return jdbc.queryForObject("SELECT count(*) FROM incidente WHERE contenedor_id = ? AND tipo = ? AND estado = ?",
                Integer.class, id, tipo, estado);
    }

    private boolean recolectado(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM parada WHERE contenedor_id = ? AND estado = 'recolectada')",
                Boolean.class, id));
    }

    // Cuánto subió en promedio cada contenedor del sector: última lectura menos la primera.
    // No se cuentan los que vació un camión en el medio (tuvieron una bajada grande).
    private double subidaPromedio(int sector, Instant desde) {
        Double valor = jdbc.queryForObject("""
                SELECT avg(ultima - primera) FROM (
                    SELECT l.contenedor_id,
                           (array_agg(l.llenado ORDER BY l.medido_at))[1] AS primera,
                           (array_agg(l.llenado ORDER BY l.medido_at DESC))[1] AS ultima,
                           min(l.llenado - l.anterior) AS mayor_bajada
                    FROM (SELECT contenedor_id, llenado, medido_at,
                                 lag(llenado) OVER (PARTITION BY contenedor_id ORDER BY medido_at) AS anterior
                          FROM lectura_sensor WHERE medido_at >= ?) l
                    JOIN contenedor c ON c.id = l.contenedor_id
                    WHERE c.sector_id = ? AND c.estado_sensor = 'funcionando'
                    GROUP BY l.contenedor_id
                ) s
                WHERE mayor_bajada > -5
                """, Double.class, Fechas.paraLaBase(desde), sector);
        return valor == null ? 0 : valor;
    }
}
