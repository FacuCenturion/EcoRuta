import { useCallback, useEffect, useRef, useState } from 'react';
import { escucharAvisos, pedir } from '../api';
import MapaRuta from '../componentes/MapaRuta';
import NoSePudo from '../componentes/NoSePudo';
import { enlaceParaLlegar, hora, km, MOTIVO_VISITA } from '../formato';
import type { Parada, Ruta, Trazado, Turno, UsuarioSesion } from '../tipos';

interface Props {
  usuario: UsuarioSesion;
  salir: () => void;
}

// La pantalla principal. Pide el turno al entrar y lo vuelve a pedir cada vez que el backend
// avisa que algo cambió (por ejemplo, que el sistema le asignó una ruta nueva).
export default function PantallaTurno({ usuario, salir }: Props) {
  const turno = useTurno();
  const { datos, error, conectado, ocupado, accion } = turno;
  const [paradaSinVaciar, setParadaSinVaciar] = useState<Parada | null>(null);
  const rutaNueva = useAvisoDeRutaNueva(datos);
  const trazado = useTrazado(datos?.ruta);

  return (
    <div className="app">
      <header className="cabecera">
        <div>
          <strong>{datos ? `Camión ${datos.camion.numero}` : 'EcoRuta'}</strong>
          <small>
            {datos?.camion.sector ? `Sector ${datos.camion.sectorId}: ${datos.camion.sector}` : usuario.nombre}
          </small>
        </div>
        <div className="cabecera__derecha">
          {datos && <span className="reloj">{hora(datos.reloj)}</span>}
          <span
            className={conectado ? 'conexion conexion--si' : 'conexion conexion--no'}
            title={conectado ? 'Conectado' : 'Sin conexión'}
            aria-label={conectado ? 'Conectado' : 'Sin conexión'}
          />
          <button type="button" className="boton boton--chico" onClick={salir}>
            Salir
          </button>
        </div>
      </header>

      <main className="contenido">
        {!conectado && <p className="aviso">Sin conexión. Reintentando…</p>}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        {!datos ? (
          !error && <p className="nota centrada">Cargando tu turno…</p>
        ) : !datos.manejadoPorApp ? (
          <EmpezarTurno nombre={datos.chofer} ocupado={ocupado} empezar={() => accion('/chofer/turno/empezar')} />
        ) : (
          <TurnoEmpezado
            turno={datos}
            trazado={trazado}
            ocupado={ocupado}
            rutaNueva={rutaNueva.visible}
            cerrarAvisoRutaNueva={rutaNueva.cerrar}
            iniciar={() => accion('/chofer/ruta/iniciar')}
            vaciar={(p) => accion(`/chofer/paradas/${p.id}/recolectar`)}
            noSePudo={setParadaSinVaciar}
            terminarTurno={() => accion('/chofer/turno/terminar')}
          />
        )}
      </main>

      {paradaSinVaciar && (
        <NoSePudo
          parada={paradaSinVaciar}
          cancelar={() => setParadaSinVaciar(null)}
          confirmar={async (motivo) => {
            const bien = await accion(`/chofer/paradas/${paradaSinVaciar.id}/no-se-pudo`, { motivo });
            if (bien) setParadaSinVaciar(null);
          }}
        />
      )}
    </div>
  );
}

function EmpezarTurno({ nombre, ocupado, empezar }: { nombre: string; ocupado: boolean; empezar: () => void }) {
  return (
    <section className="tarjeta">
      <h1>Hola, {nombre.split(' ')[0]}</h1>
      <p>Cuando estés en el camión, empezá el turno. Desde ahí el sistema te manda las rutas a esta app.</p>
      <button type="button" className="boton boton--principal" disabled={ocupado} onClick={empezar}>
        Empezar turno
      </button>
    </section>
  );
}

interface PropsTurno {
  turno: Turno;
  trazado: Trazado | undefined;
  ocupado: boolean;
  rutaNueva: boolean;
  cerrarAvisoRutaNueva: () => void;
  iniciar: () => void;
  vaciar: (parada: Parada) => void;
  noSePudo: (parada: Parada) => void;
  terminarTurno: () => void;
}

function TurnoEmpezado(props: PropsTurno) {
  const { turno, trazado, ocupado } = props;
  const ruta = turno.ruta;
  const activa = ruta && ruta.estado !== 'terminada' ? ruta : null;

  if (!activa) {
    return (
      <>
        <section className="tarjeta">
          <h1>{turno.camion.estado === 'en_descarga' ? 'Descargando en la base' : 'Sin ruta por ahora'}</h1>
          <p>
            Cuando haya contenedores para recolectar, el sistema te asigna una ruta y te avisa acá. No hace falta que
            recargues.
          </p>
          {ruta && <ResumenDeRuta ruta={ruta} />}
        </section>
        <button
          type="button"
          className="boton boton--secundario boton--ancho"
          disabled={ocupado}
          onClick={props.terminarTurno}
        >
          Terminar turno
        </button>
      </>
    );
  }

  const proxima = activa.paradas.find((p) => p.estado === 'pendiente');
  const hechas = activa.paradas.filter((p) => p.estado !== 'pendiente').length;

  return (
    <>
      {activa.estado === 'programada' ? (
        <section className={props.rutaNueva ? 'tarjeta tarjeta--destacada' : 'tarjeta'} aria-live="polite">
          {props.rutaNueva && (
            <p className="etiqueta" role="status">
              Ruta nueva
            </p>
          )}
          <h1>Tenés una ruta de {activa.paradas.length} paradas</h1>
          <p>
            {kmDeLaRuta(activa, trazado)}, unos {activa.duracionEstimadaMin} minutos. Sale y vuelve a la base.
          </p>
          <button
            type="button"
            className="boton boton--principal"
            disabled={ocupado}
            onClick={() => {
              props.cerrarAvisoRutaNueva();
              props.iniciar();
            }}
          >
            Iniciar ruta
          </button>
        </section>
      ) : (
        proxima && (
          <ParadaActual
            parada={proxima}
            numero={activa.paradas.indexOf(proxima) + 1}
            total={activa.paradas.length}
            hechas={hechas}
            ocupado={ocupado}
            demorado={turno.camion.estado === 'demorado'}
            vaciar={() => props.vaciar(proxima)}
            noSePudo={() => props.noSePudo(proxima)}
          />
        )
      )}

      <section className="tarjeta tarjeta--mapa" aria-label="Mapa de la ruta">
        <MapaRuta ruta={activa} trazado={trazado} base={turno.base} camion={turno.camion.posicion} />
      </section>

      <section className="tarjeta" aria-labelledby="titulo-paradas">
        <h2 id="titulo-paradas">
          Paradas <small>{hechas} de {activa.paradas.length}</small>
        </h2>
        <ol className="paradas">
          {activa.paradas.map((p, i) => (
            <li key={p.id} className={`parada parada--${p.estado}${p === proxima ? ' parada--proxima' : ''}`}>
              <span className="parada__numero">{p.estado === 'recolectada' ? '✓' : p.estado === 'no_se_pudo' ? '!' : i + 1}</span>
              <span className="parada__texto">
                <strong>{p.direccion}</strong>
                <small>
                  {p.estado === 'no_se_pudo' ? p.motivo : MOTIVO_VISITA[p.motivoVisita ?? ''] ?? ''}
                </small>
              </span>
              <span className="parada__hora">{hora(p.horaReal ?? p.eta)}</span>
            </li>
          ))}
        </ol>
      </section>
    </>
  );
}

interface PropsParada {
  parada: Parada;
  numero: number;
  total: number;
  hechas: number;
  ocupado: boolean;
  demorado: boolean;
  vaciar: () => void;
  noSePudo: () => void;
}

function ParadaActual({ parada, numero, total, hechas, ocupado, demorado, vaciar, noSePudo }: PropsParada) {
  return (
    <section className="tarjeta tarjeta--parada" aria-labelledby="titulo-parada">
      <div className="progreso" aria-label={`${hechas} de ${total} paradas`}>
        <span style={{ width: `${(hechas / total) * 100}%` }} />
      </div>
      <p className="nota">
        Parada {numero} de {total}
        {parada.motivoVisita && ` · ${MOTIVO_VISITA[parada.motivoVisita] ?? parada.motivoVisita}`}
      </p>
      <h1 id="titulo-parada">{parada.direccion}</h1>
      <p className="nota">
        Contenedor {parada.contenedorId} · llegada estimada {hora(parada.eta)}
      </p>
      {demorado && <p className="aviso">Quedó registrado el problema de la parada anterior. Seguí con esta.</p>}
      <a className="boton boton--secundario" href={enlaceParaLlegar(parada.posicion)} target="_blank" rel="noreferrer">
        Cómo llegar
      </a>
      <div className="acciones">
        <button type="button" className="boton boton--principal" disabled={ocupado} onClick={vaciar}>
          Vaciado
        </button>
        <button type="button" className="boton boton--alerta" disabled={ocupado} onClick={noSePudo}>
          No se pudo
        </button>
      </div>
    </section>
  );
}

function ResumenDeRuta({ ruta }: { ruta: Ruta }) {
  const vaciadas = ruta.paradas.filter((p) => p.estado === 'recolectada').length;
  const sinVaciar = ruta.paradas.filter((p) => p.estado === 'no_se_pudo').length;
  return (
    <p className="nota">
      Última ruta: {vaciadas} de {ruta.paradas.length} contenedores vaciados
      {sinVaciar > 0 && `, ${sinVaciar} sin poder vaciar`}.
    </p>
  );
}

function kmDeLaRuta(ruta: Ruta, trazado: Trazado | undefined): string {
  if (trazado?.estado === 'listo' && trazado.kmPorCalle !== null) return km(trazado.kmPorCalle);
  return `${km(ruta.kmPlanificados)} en línea recta`;
}

// ---------- Datos ----------

function useTurno() {
  const [datos, setDatos] = useState<Turno>();
  const [error, setError] = useState<string>();
  const [conectado, setConectado] = useState(true);
  const [ocupado, setOcupado] = useState(false);
  const enCurso = useRef(false);
  const otraVez = useRef(false);

  // Si llegan varios avisos juntos se pide una sola vez más al terminar
  const cargar = useCallback(async () => {
    if (enCurso.current) {
      otraVez.current = true;
      return;
    }
    enCurso.current = true;
    try {
      do {
        otraVez.current = false;
        try {
          setDatos(await pedir<Turno>('/chofer/turno'));
          setError(undefined);
        } catch (e) {
          setError((e as Error).message);
        }
      } while (otraVez.current);
    } finally {
      enCurso.current = false;
    }
  }, []);

  useEffect(() => {
    cargar();
    return escucharAvisos(cargar, (ahora) => {
      setConectado(ahora);
      if (ahora) cargar();
    });
  }, [cargar]);

  // Devuelve si salió bien. La respuesta ya trae el turno actualizado.
  const accion = useCallback(async (ruta: string, cuerpo?: unknown): Promise<boolean> => {
    setOcupado(true);
    try {
      setDatos(await pedir<Turno>(ruta, { metodo: 'POST', cuerpo: cuerpo ?? {} }));
      setError(undefined);
      return true;
    } catch (e) {
      setError((e as Error).message);
      return false;
    } finally {
      setOcupado(false);
    }
  }, []);

  return { datos, error, conectado, ocupado, accion };
}

// Cuando aparece una ruta que el chofer todavía no vio: vibra, avisa y, si la app está en
// segundo plano y dio permiso, manda una notificación del sistema.
function useAvisoDeRutaNueva(turno: Turno | undefined) {
  const [visible, setVisible] = useState(false);
  const vista = useRef<number | null | undefined>(undefined);

  useEffect(() => {
    if (!turno) return;
    const ruta = turno.ruta;
    const nueva = ruta && ruta.estado === 'programada' && turno.manejadoPorApp ? ruta.id : null;
    // La primera carga no avisa: la ruta ya estaba antes de abrir la app
    if (vista.current !== undefined && nueva !== null && nueva !== vista.current) {
      setVisible(true);
      avisar(`Ruta nueva: ${ruta!.paradas.length} paradas`);
    }
    if (nueva !== null || vista.current === undefined) vista.current = nueva;
  }, [turno]);

  return { visible, cerrar: () => setVisible(false) };
}

function avisar(texto: string) {
  navigator.vibrate?.([200, 100, 200]);
  if (typeof Notification === 'undefined' || Notification.permission !== 'granted' || !document.hidden) return;
  navigator.serviceWorker?.ready
    .then((registro) => registro.showNotification('EcoRuta', { body: texto, tag: 'ruta-nueva' }))
    .catch(() => new Notification('EcoRuta', { body: texto }));
}

// El recorrido por las calles de la ruta. El backend lo calcula la primera vez: mientras tanto
// el mapa muestra líneas rectas y se vuelve a preguntar con cada cambio de la ruta.
function useTrazado(ruta: Ruta | null | undefined): Trazado | undefined {
  const [trazado, setTrazado] = useState<Trazado>();
  const actual = trazado && ruta && trazado.rutaId === ruta.id ? trazado : undefined;
  const yaEsta = actual?.estado === 'listo' || actual?.estado === 'sin_servicio';
  const clave = ruta ? `${ruta.id}|${ruta.paradas.map((p) => p.estado).join()}` : '';

  useEffect(() => {
    if (!ruta || yaEsta) return;
    let vigente = true;
    const reintento = setTimeout(() => {
      pedir<Trazado>('/chofer/ruta/trazado')
        .then((nuevo) => vigente && setTrazado(nuevo))
        .catch(() => {
          // sin recorrido se dibujan líneas rectas
        });
    }, esperaParaPreguntar(actual));
    return () => {
      vigente = false;
      clearTimeout(reintento);
    };
    // Cada respuesta es un objeto nuevo: mientras no esté listo, se vuelve a preguntar
  }, [clave, yaEsta, actual]);

  return actual;
}

function esperaParaPreguntar(trazado: Trazado | undefined): number {
  if (!trazado) return 0;
  return trazado.estado === 'fallo' ? 15000 : 2000;
}
