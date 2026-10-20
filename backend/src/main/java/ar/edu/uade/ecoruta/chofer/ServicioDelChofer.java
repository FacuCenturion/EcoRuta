package ar.edu.uade.ecoruta.chofer;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ar.edu.uade.ecoruta.camiones.Camion;
import ar.edu.uade.ecoruta.camiones.CamionRepositorio;
import ar.edu.uade.ecoruta.comun.Coordenada;
import ar.edu.uade.ecoruta.comun.Fechas;
import ar.edu.uade.ecoruta.comun.RelojDelSistema;
import ar.edu.uade.ecoruta.comun.error.DatosInvalidosException;
import ar.edu.uade.ecoruta.comun.error.EstadoInvalidoException;
import ar.edu.uade.ecoruta.comun.error.RecursoNoEncontradoException;
import ar.edu.uade.ecoruta.emulador.EmuladorDeSensores;
import ar.edu.uade.ecoruta.rutas.Parada;
import ar.edu.uade.ecoruta.rutas.Ruta;
import ar.edu.uade.ecoruta.rutas.RutaDto;
import ar.edu.uade.ecoruta.rutas.RutaRepositorio;
import ar.edu.uade.ecoruta.rutas.ServicioDeRutas;
import ar.edu.uade.ecoruta.rutas.ServicioDeTrazado;
import ar.edu.uade.ecoruta.rutas.ServicioDeTrazado.TrazadoDto;
import ar.edu.uade.ecoruta.sectores.SectorRepositorio;
import ar.edu.uade.ecoruta.zonas.Zona;
import ar.edu.uade.ecoruta.zonas.ZonaRepositorio;

/**
 * Lo que hace el chofer desde la app. Cada chofer ve y maneja solo el camión que tiene asignado.
 *
 * Mientras el chofer no empieza el turno, el emulador maneja su camión como hasta ahora. Cuando
 * lo empieza, el sistema le sigue asignando rutas, pero iniciarlas y cerrar cada parada pasa a
 * ser cosa del chofer.
 */
@Service
public class ServicioDelChofer {

    private final CamionRepositorio camiones;
    private final RutaRepositorio rutas;
    private final SectorRepositorio sectores;
    private final ZonaRepositorio zonas;
    private final ServicioDeRutas servicioDeRutas;
    private final ServicioDeTrazado trazado;
    private final RelojDelSistema reloj;
    private final TransactionTemplate transaccion;
    private final ObjectProvider<EmuladorDeSensores> emulador;

    ServicioDelChofer(CamionRepositorio camiones, RutaRepositorio rutas, SectorRepositorio sectores, ZonaRepositorio zonas,
            ServicioDeRutas servicioDeRutas, ServicioDeTrazado trazado, RelojDelSistema reloj,
            TransactionTemplate transaccion, ObjectProvider<EmuladorDeSensores> emulador) {
        this.camiones = camiones;
        this.rutas = rutas;
        this.sectores = sectores;
        this.zonas = zonas;
        this.servicioDeRutas = servicioDeRutas;
        this.trazado = trazado;
        this.reloj = reloj;
        this.transaccion = transaccion;
        this.emulador = emulador;
    }

    public TurnoDto turno(String usuario) {
        return transaccion.execute(estado -> armarTurno(suCamion(usuario)));
    }

    public TurnoDto empezarTurno(String usuario) {
        return transaccion.execute(estado -> {
            Camion camion = suCamion(usuario);
            camion.empezarTurnoConLaApp();
            return armarTurno(camion);
        });
    }

    /** Si queda una ruta a medio hacer, la termina el emulador. */
    public TurnoDto terminarTurno(String usuario) {
        return transaccion.execute(estado -> {
            Camion camion = suCamion(usuario);
            camion.terminarTurnoConLaApp();
            return armarTurno(camion);
        });
    }

    public TurnoDto iniciarRuta(String usuario) {
        return transaccion.execute(estado -> {
            Camion camion = conTurnoEmpezado(usuario);
            Ruta ruta = rutaActiva(camion);
            servicioDeRutas.iniciar(ruta, reloj.ahora());
            return armarTurno(camion);
        });
    }

    public TurnoDto recolectar(String usuario, long paradaId) {
        Instant ahora = reloj.ahora();
        int[] antesDeVaciar = new int[1];
        String[] contenedorId = new String[1];
        TurnoDto turno = transaccion.execute(estado -> {
            Camion camion = conTurnoEmpezado(usuario);
            Ruta ruta = rutaActiva(camion);
            Parada parada = parada(ruta, paradaId);
            contenedorId[0] = parada.getContenedor().getId();
            antesDeVaciar[0] = parada.getContenedor().getLlenado();
            servicioDeRutas.recolectar(ruta, parada, ahora);
            return armarTurno(camion);
        });
        // Después de guardar: el mundo simulado se entera de que el contenedor quedó vacío
        emulador.ifAvailable(e -> e.vaciadoPorElChofer(contenedorId[0], antesDeVaciar[0], ahora));
        return turno;
    }

    public TurnoDto noSePudo(String usuario, long paradaId, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new DatosInvalidosException("motivo: contá por qué no se pudo recolectar");
        }
        return transaccion.execute(estado -> {
            Camion camion = conTurnoEmpezado(usuario);
            Ruta ruta = rutaActiva(camion);
            servicioDeRutas.noSePudo(ruta, parada(ruta, paradaId), motivo.trim(), reloj.ahora());
            return armarTurno(camion);
        });
    }

    /** El recorrido por las calles de su ruta más nueva. */
    public TrazadoDto trazado(String usuario) {
        Long rutaId = transaccion.execute(estado -> rutas.ultimaDelCamion(suCamion(usuario).getId())
                .map(Ruta::getId)
                .orElseThrow(() -> new RecursoNoEncontradoException("SIN_RUTA", "Todavía no tenés ninguna ruta.")));
        return trazado.consultar(rutaId);
    }

    public List<String> motivosNoSePudo() {
        return ServicioDeRutas.MOTIVOS_NO_SE_PUDO;
    }

    private Camion suCamion(String usuario) {
        return camiones.findByChoferUsuario(usuario).orElseThrow(() -> new RecursoNoEncontradoException("SIN_CAMION",
                "No tenés un camión asignado. Consultá con el Centro de Monitoreo."));
    }

    private Camion conTurnoEmpezado(String usuario) {
        Camion camion = suCamion(usuario);
        if (!camion.isManejadoPorApp()) {
            throw new EstadoInvalidoException("TURNO_SIN_EMPEZAR", "Primero empezá el turno en la app.");
        }
        return camion;
    }

    private Ruta rutaActiva(Camion camion) {
        return rutas.ultimaDelCamion(camion.getId())
                .filter(r -> !r.getEstado().equals(Ruta.TERMINADA))
                .orElseThrow(() -> new EstadoInvalidoException("SIN_RUTA_ACTIVA",
                        "No tenés una ruta asignada. Cuando el sistema te asigne una, te avisa la app."));
    }

    private static Parada parada(Ruta ruta, long paradaId) {
        return ruta.getParadas().stream()
                .filter(p -> p.getId() == paradaId)
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("PARADA_NO_EXISTE",
                        "La parada " + paradaId + " no es de tu ruta."));
    }

    private TurnoDto armarTurno(Camion camion) {
        Zona zona = zonas.findById(camion.getZonaId()).orElseThrow();
        String sector = sectores.findById(camion.getSectorId()).map(s -> s.getNombre()).orElse(null);
        RutaDto ruta = rutas.ultimaDelCamion(camion.getId()).map(RutaDto::de).orElse(null);
        return new TurnoDto(camion.getChofer().getNombre(),
                new TurnoDto.CamionDelChofer(camion.getId(), camion.getNumero(), camion.getSectorId(), sector,
                        camion.getEstado(), camion.getCargaPct(), camion.posicion(),
                        camion.getKmRecorridosHoy().doubleValue()),
                camion.isManejadoPorApp(), new Coordenada(zona.getBaseLat(), zona.getBaseLng()), ruta,
                Fechas.enBuenosAires(reloj.ahora()));
    }
}
