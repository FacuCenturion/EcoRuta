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
import ar.edu.uade.ecoruta.incidentes.Incidente;
import ar.edu.uade.ecoruta.incidentes.IncidenteRepositorio;
import ar.edu.uade.ecoruta.rutas.Parada;
import ar.edu.uade.ecoruta.rutas.PropuestaDeParada;
import ar.edu.uade.ecoruta.rutas.PropuestaRepositorio;
import ar.edu.uade.ecoruta.rutas.Ruta;
import ar.edu.uade.ecoruta.rutas.RutaDto;
import ar.edu.uade.ecoruta.rutas.RutaRepositorio;
import ar.edu.uade.ecoruta.rutas.ServicioDeParadasNuevas;
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
    private final PropuestaRepositorio propuestas;
    private final ServicioDeParadasNuevas paradasNuevas;
    private final IncidenteRepositorio incidentes;

    // La app achica la foto antes de mandarla; esto es el tope por si llega una grande
    private static final int TAMANO_MAXIMO_DE_FOTO = 1_400_000;

    ServicioDelChofer(CamionRepositorio camiones, RutaRepositorio rutas, SectorRepositorio sectores, ZonaRepositorio zonas,
            ServicioDeRutas servicioDeRutas, ServicioDeTrazado trazado, RelojDelSistema reloj,
            TransactionTemplate transaccion, ObjectProvider<EmuladorDeSensores> emulador, PropuestaRepositorio propuestas,
            ServicioDeParadasNuevas paradasNuevas, IncidenteRepositorio incidentes) {
        this.propuestas = propuestas;
        this.paradasNuevas = paradasNuevas;
        this.incidentes = incidentes;
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

    /**
     * @param nota lo que escribió o dictó el chofer (opcional)
     * @param foto la foto achicada como data URL de una imagen (opcional)
     */
    public TurnoDto noSePudo(String usuario, long paradaId, String motivo, String nota, String foto) {
        if (motivo == null || motivo.isBlank()) {
            throw new DatosInvalidosException("motivo: contá por qué no se pudo recolectar");
        }
        if (foto != null && (!foto.matches("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$")
                || foto.length() > TAMANO_MAXIMO_DE_FOTO)) {
            throw new DatosInvalidosException("foto: tiene que ser una imagen JPEG, PNG o WebP de menos de 1 MB");
        }
        return transaccion.execute(estado -> {
            Camion camion = conTurnoEmpezado(usuario);
            Ruta ruta = rutaActiva(camion);
            Incidente incidente = servicioDeRutas.noSePudo(ruta, parada(ruta, paradaId), motivo.trim(), nota,
                    reloj.ahora());
            if (foto != null) {
                incidentes.guardarFoto(incidente.getId(), foto);
            }
            return armarTurno(camion);
        });
    }

    /** Dónde está el camión, según el celular del chofer. Solo mientras está en ruta. */
    public void ubicar(String usuario, Coordenada posicion) {
        transaccion.executeWithoutResult(estado -> {
            Camion camion = conTurnoEmpezado(usuario);
            if (camion.getEstado().equals(Camion.EN_RUTA) || camion.getEstado().equals(Camion.DEMORADO)) {
                camion.ubicar(posicion);
            }
        });
    }

    public TurnoDto aceptarParadaNueva(String usuario, long propuestaId) {
        return decidirParadaNueva(usuario, propuestaId, true);
    }

    public TurnoDto rechazarParadaNueva(String usuario, long propuestaId) {
        return decidirParadaNueva(usuario, propuestaId, false);
    }

    private TurnoDto decidirParadaNueva(String usuario, long propuestaId, boolean acepta) {
        return transaccion.execute(estado -> {
            Camion camion = conTurnoEmpezado(usuario);
            PropuestaDeParada propuesta = propuestas.findById(propuestaId)
                    .filter(p -> p.getRuta().getCamion().getId().equals(camion.getId()))
                    .orElseThrow(() -> new RecursoNoEncontradoException("PROPUESTA_NO_EXISTE",
                            "Esa parada nueva no es para tu camión."));
            if (acepta) {
                paradasNuevas.aceptar(propuesta, reloj.ahora());
            } else {
                paradasNuevas.rechazar(propuesta, reloj.ahora());
            }
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
        Ruta ultima = rutas.ultimaDelCamion(camion.getId()).orElse(null);
        RutaDto ruta = ultima == null ? null : RutaDto.de(ultima);
        TurnoDto.PropuestaDto propuesta = ultima == null || !ultima.getEstado().equals(Ruta.EN_CURSO) ? null
                : propuestas.pendienteDe(ultima.getId()).map(p -> new TurnoDto.PropuestaDto(p.getId(),
                        p.getContenedor().getId(), p.getContenedor().getDireccion(), p.getContenedor().posicion(),
                        p.getContenedor().getNivelEstimado() != null ? p.getContenedor().getNivelEstimado()
                                : p.getContenedor().getLlenado(),
                        p.getOrden(), p.getKmExtra().doubleValue(), p.getMinutosExtra(), p.getMotivo()))
                        .orElse(null);
        return new TurnoDto(camion.getChofer().getNombre(),
                new TurnoDto.CamionDelChofer(camion.getId(), camion.getNumero(), camion.getSectorId(), sector,
                        camion.getEstado(), camion.getCargaPct(), camion.posicion(),
                        camion.getKmRecorridosHoy().doubleValue()),
                camion.isManejadoPorApp(), new Coordenada(zona.getBaseLat(), zona.getBaseLng()), ruta, propuesta,
                Fechas.enBuenosAires(reloj.ahora()));
    }
}
