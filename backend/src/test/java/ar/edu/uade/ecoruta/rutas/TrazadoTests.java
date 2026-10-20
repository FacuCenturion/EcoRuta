package ar.edu.uade.ecoruta.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ar.edu.uade.ecoruta.BaseDePrueba;
import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.emulador.EmuladorDeSensores;
import ar.edu.uade.ecoruta.emulador.ReinicioDeLaDemo;
import ar.edu.uade.ecoruta.rutas.ClienteOsrm.Trazado;
import ar.edu.uade.ecoruta.rutas.ServicioDeTrazado.TrazadoDto;

/** El recorrido por las calles se pide una vez, se guarda y después sale de la base. OSRM está simulado. */
@BaseDePrueba
class TrazadoTests {

    @Autowired
    ServicioDeTrazado servicio;

    @Autowired
    ReinicioDeLaDemo reinicio;

    @Autowired
    EmuladorDeSensores emulador;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ClienteOsrm osrm;

    private long rutaId;
    private int paradas;

    @BeforeEach
    void unaRutaRecienCreada() {
        reinicio.reiniciar();
        emulador.avanzarPasos(3);
        rutaId = jdbc.queryForObject("SELECT min(id) FROM ruta", Long.class);
        paradas = jdbc.queryForObject("SELECT count(*) FROM parada WHERE ruta_id = ?", Integer.class, rutaId);
        when(osrm.activo()).thenReturn(true);
    }

    @Test
    void laPrimeraVezSeCalculaYDespuesSaleDeLaBase() throws Exception {
        when(osrm.trazar(anyList())).thenAnswer(pedido -> {
            List<Coordenada> puntos = pedido.getArgument(0);
            // Un tramo recto por cada par de puntos seguidos, como si OSRM devolviera eso
            List<List<Coordenada>> tramos = new java.util.ArrayList<>();
            for (int i = 0; i + 1 < puntos.size(); i++) {
                tramos.add(List.of(puntos.get(i), puntos.get(i + 1)));
            }
            return Optional.of(new Trazado(tramos, 7.25));
        });

        assertThat(servicio.consultar(rutaId).estado()).isEqualTo(ServicioDeTrazado.CALCULANDO);
        TrazadoDto listo = esperarListo();
        assertThat(listo.kmPorCalle()).isEqualTo(7.25);
        // De la base a la primera parada, entre paradas y la vuelta a la base
        assertThat(listo.tramos()).hasSize(paradas + 1);
        assertThat(listo.tramos().get(0).get(0)).isEqualTo(listo.tramos().get(paradas).get(1));

        servicio.consultar(rutaId);
        verify(osrm, times(1)).trazar(anyList());
    }

    @Test
    void siOsrmNoRespondeSeAvisaYNoSeInsisteEnseguida() throws Exception {
        when(osrm.trazar(anyList())).thenReturn(Optional.empty());
        servicio.consultar(rutaId);
        for (int i = 0; i < 50 && !servicio.consultar(rutaId).estado().equals(ServicioDeTrazado.FALLO); i++) {
            Thread.sleep(100);
        }
        assertThat(servicio.consultar(rutaId).estado()).isEqualTo(ServicioDeTrazado.FALLO);
        verify(osrm, times(1)).trazar(anyList());
    }

    @Test
    void sinOsrmConfiguradoLasRutasSeDibujanRectas() {
        when(osrm.activo()).thenReturn(false);
        TrazadoDto respuesta = servicio.consultar(rutaId);
        assertThat(respuesta.estado()).isEqualTo(ServicioDeTrazado.SIN_SERVICIO);
        assertThat(respuesta.tramos()).isEmpty();
    }

    private TrazadoDto esperarListo() throws InterruptedException {
        TrazadoDto respuesta = servicio.consultar(rutaId);
        for (int i = 0; i < 50 && !respuesta.estado().equals(ServicioDeTrazado.LISTO); i++) {
            Thread.sleep(100);
            respuesta = servicio.consultar(rutaId);
        }
        assertThat(respuesta.estado()).isEqualTo(ServicioDeTrazado.LISTO);
        return respuesta;
    }
}
