import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { escucharAvisos, pedir } from '../api';
import {
  IconoAlerta,
  IconoBifurcacion,
  IconoBrujula,
  IconoCapas,
  IconoCasa,
  IconoCheck,
  IconoLista,
  IconoManiobra,
  IconoPlay,
  IconoSenal,
  IconoSilencio,
  IconoSonido,
  IconoTriangulo,
} from '../componentes/Iconos';
import Mapa, { type EstiloMapa } from '../componentes/Mapa';
import NoSePudo from '../componentes/NoSePudo';
import { hora, km, MOTIVO_VISITA } from '../formato';
import {
  armarTramos,
  calleDe,
  esPrioritaria,
  flechaDe,
  loQueFalta,
  navegar,
  prioridadDe,
  textoDistancia,
  textoManiobra,
  velocidadMaxima,
  type Tramo,
} from '../navegacion';
import type { Parada, Ruta, Trazado, Turno, UsuarioSesion } from '../tipos';
import { llego, usePosicion, type ModoUbicacion, type Posicion } from '../usePosicion';
import { decir, silenciar } from '../voz';

interface Props {
  usuario: UsuarioSesion;
  salir: () => void;
}

const SEGUNDOS_PARA_DESHACER = 5;
const SEGUNDOS_PARA_ACEPTAR = 8;
const MS_ENTRE_POSICIONES = 5000;
const BASE_POR_DEFECTO = { lat: -34.609, lng: -58.37 };

// La pantalla principal. Pide el turno al entrar y lo vuelve a pedir cada vez que el backend
// avisa que algo cambió. Según el momento muestra el inicio de jornada, la navegación, la
// llegada al contenedor o el cierre de la ruta.
export default function PantallaTurno({ usuario, salir }: Props) {
  const { datos, error, conectado, ocupado, accion } = useTurno();
  const preferencias = usePreferencias();
  const ruta = datos?.ruta ?? null;
  const trazado = useTrazado(ruta);
  const [pendiente, setPendiente] = useState<Parada | null>(null); // "recolectado" esperando el deshacer
  const temporizador = useRef<ReturnType<typeof setTimeout>>();
  const [sinVaciar, setSinVaciar] = useState<Parada | null>(null);
  const [lista, setLista] = useState(false);
  const [volviendo, setVolviendo] = useState(false);
  const [resumenesVistos, setResumenesVistos] = useState<number[]>([]);
  const [seguir, setSeguir] = useState(true);
  const [verTodo, setVerTodo] = useState(0);

  const base = datos?.base ?? BASE_POR_DEFECTO;
  const tramos = useMemo(() => (ruta ? armarTramos(ruta, trazado, base) : []), [ruta, trazado, base]);
  const cerradasEnElServidor = ruta ? ruta.paradas.filter((p) => p.estado !== 'pendiente').length : 0;
  const esperandoDeshacer = pendiente && ruta?.paradas.find((p) => p.id === pendiente.id)?.estado === 'pendiente';
  const cerradas = cerradasEnElServidor + (esperandoDeshacer ? 1 : 0);
  const proxima = ruta?.paradas.find((p) => p.estado === 'pendiente' && p.id !== pendiente?.id) ?? null;

  const enCurso = ruta?.estado === 'en_curso' && datos?.manejadoPorApp;
  const tramoActual: Tramo | undefined = enCurso ? tramos[cerradas] : volviendo ? tramos[tramos.length - 1] : undefined;
  const posicion = usePosicion(
    preferencias.modo,
    tramoActual,
    Boolean(tramoActual) && !sinVaciar,
    datos?.camion.posicion,
    preferencias.acelerar,
  );
  const llegada = Boolean(tramoActual) && llego(tramoActual, posicion.punto);

  // Confirma de verdad la recolección que estaba esperando el deshacer
  const confirmarPendiente = useCallback(() => {
    clearTimeout(temporizador.current);
    setPendiente((actual) => {
      if (actual) accion(`/chofer/paradas/${actual.id}/recolectar`);
      return null;
    });
  }, [accion]);

  function marcarRecolectado(parada: Parada) {
    confirmarPendiente();
    setPendiente(parada);
    temporizador.current = setTimeout(confirmarPendiente, SEGUNDOS_PARA_DESHACER * 1000);
    decir(`Parada ${parada.orden} recolectada`);
  }

  function deshacer() {
    clearTimeout(temporizador.current);
    setPendiente(null);
  }

  // Si la app se va a segundo plano, lo que estaba esperando el deshacer se manda igual
  useEffect(() => {
    const alOcultar = () => document.hidden && confirmarPendiente();
    document.addEventListener('visibilitychange', alOcultar);
    return () => document.removeEventListener('visibilitychange', alOcultar);
  }, [confirmarPendiente]);

  useEnviarPosicion(Boolean(enCurso), posicion);
  useIndicacionesEnVoz(tramoActual, posicion, proxima, llegada, volviendo);

  // Al llegar a la base después de terminar la ruta, se cierra el resumen
  useEffect(() => {
    if (volviendo && llegada && ruta) {
      setVolviendo(false);
      setResumenesVistos((vistos) => [...vistos, ruta.id]);
    }
  }, [volviendo, llegada, ruta]);

  if (!datos) {
    return (
      <Pantalla>
        <Encabezado>
          <h1>EcoRuta</h1>
          <p>{usuario.nombre}</p>
        </Encabezado>
        <Hoja>
          {error ? (
            <p className="error" role="alert">
              {error}
            </p>
          ) : (
            <p className="tenue">Cargando tu turno…</p>
          )}
        </Hoja>
      </Pantalla>
    );
  }

  const sector = `Sector ${datos.camion.sectorId}, residuos generales`;
  const aviso = !conectado ? 'Sin conexión. Reintentando…' : error;

  if (!datos.manejadoPorApp) {
    return (
      <Pantalla>
        <Encabezado>
          <h1>Hola, {datos.chofer.split(' ')[0]}</h1>
          <p>
            Camión {datos.camion.numero} · {sector}
          </p>
        </Encabezado>
        <Hoja>
          <Aviso texto={aviso} />
          <p className="tenue">
            Cuando estés en el camión, empezá el turno. Desde ahí el sistema te manda las rutas a esta app.
          </p>
          <button
            type="button"
            className="boton boton--principal"
            disabled={ocupado}
            onClick={() => accion('/chofer/turno/empezar')}
          >
            <IconoPlay /> Empezar turno
          </button>
          <button type="button" className="boton boton--borde" onClick={salir}>
            Cerrar sesión
          </button>
        </Hoja>
      </Pantalla>
    );
  }

  const listaDeParadas = lista && ruta && (
    <ListaParadas
      ruta={ruta}
      cerradas={cerradas}
      preferencias={preferencias}
      cerrar={() => setLista(false)}
      terminarTurno={() => {
        setLista(false);
        accion('/chofer/turno/terminar');
      }}
      salir={salir}
    />
  );

  // ---------- Ruta terminada ----------
  if (ruta && ruta.estado === 'terminada' && !resumenesVistos.includes(ruta.id)) {
    const navegando = volviendo ? navegar(tramos[tramos.length - 1], posicion.punto) : null;
    return (
      <Pantalla>
        {navegando ? (
          <EncabezadoManiobra
            metros={navegando.metrosAManiobra}
            flecha={flechaDe(navegando.proxima)}
            calle={navegando.proxima?.calle || 'Base'}
            destino="Base"
          />
        ) : (
          <Encabezado
            icono={<IconoCheck tamano={36} />}
            lateral={{ titulo: 'Cierre', valor: hora(ultimaHora(ruta) ?? datos.reloj) }}
          >
            <h1>Ruta terminada</h1>
            <p>{sector}</p>
          </Encabezado>
        )}
        <Mapa
          tramos={tramos}
          paradas={ruta.paradas}
          cerradas={volviendo ? tramos.length - 1 : tramos.length}
          base={base}
          posicion={volviendo ? posicion : undefined}
          seguir={seguir}
          alSoltar={() => setSeguir(false)}
          estilo={preferencias.estilo}
          verTodo={verTodo + ruta.id}
          margenAbajo={volviendo ? 180 : 430}
        />
        {!volviendo && (
          <ResumenDeRuta
            ruta={ruta}
            trazado={trazado}
            volver={() => {
              setVolviendo(true);
              setSeguir(true);
            }}
            verDetalle={() => setLista(true)}
          />
        )}
        {listaDeParadas}
      </Pantalla>
    );
  }

  // ---------- Sin ruta ----------
  if (!ruta || ruta.estado === 'terminada') {
    return (
      <Pantalla>
        <Encabezado>
          <h1>{datos.camion.estado === 'en_descarga' ? 'Descargando en la base' : 'Sin ruta por ahora'}</h1>
          <p>
            Camión {datos.camion.numero} · {sector}
          </p>
        </Encabezado>
        <Hoja>
          <Aviso texto={aviso} />
          <p className="tenue">
            Cuando haya contenedores para recolectar, el sistema te asigna una ruta y te avisa acá. No hace falta que
            recargues.
          </p>
          <button
            type="button"
            className="boton boton--borde"
            disabled={ocupado}
            onClick={() => accion('/chofer/turno/terminar')}
          >
            Terminar turno
          </button>
          <button type="button" className="boton boton--borde" onClick={salir}>
            Cerrar sesión
          </button>
        </Hoja>
      </Pantalla>
    );
  }

  // ---------- Inicio de jornada ----------
  if (ruta.estado === 'programada') {
    const falta = loQueFalta(tramos, 0, tramos[0]?.largo ?? 0, ruta.paradas.length);
    const prioritarias = ruta.paradas.filter(esPrioritaria).length;
    return (
      <Pantalla>
        <Encabezado lateral={{ titulo: 'Salida', valor: hora(ruta.salidaProgramada) }}>
          <h1>Tu ruta de hoy</h1>
          <p>{sector}</p>
        </Encabezado>
        <Mapa
          tramos={tramos}
          paradas={ruta.paradas}
          cerradas={0}
          base={base}
          seguir={false}
          alSoltar={() => {}}
          estilo={preferencias.estilo}
          verTodo={ruta.id + (trazado ? 1 : 0)}
          margenAbajo={440}
        />
        <Hoja>
          <Aviso texto={aviso} />
          <div className="datos">
            <Dato
              titulo="Paradas"
              valor={String(ruta.paradas.length)}
              detalle={prioritarias ? `${prioritarias} prioritarias` : undefined}
            />
            <Dato titulo="Recorrido" valor={km(kmDeLaRuta(ruta, trazado, tramos))} />
            <Dato titulo="Fin estimado" valor={hora(sumarMinutos(datos.reloj, falta.minutos))} />
          </div>
          <h2 className="subtitulo">Primeras paradas</h2>
          <ol className="primeras">
            {ruta.paradas.slice(0, 3).map((p, i) => (
              <li key={p.id}>
                <span className={esPrioritaria(p) ? 'numero numero--prioritaria' : 'numero'}>{i + 1}</span>
                <span className="primeras__direccion">{p.direccion}</span>
                <span className={p.llenado >= 80 ? 'porcentaje porcentaje--alto' : 'porcentaje'}>{p.llenado}%</span>
              </li>
            ))}
          </ol>
          <button
            type="button"
            className="boton boton--principal"
            disabled={ocupado}
            onClick={() => {
              decir('Arrancamos. ' + textoManiobra(null, ruta.paradas[0].direccion));
              accion('/chofer/ruta/iniciar');
            }}
          >
            <IconoPlay /> Empezar ruta
          </button>
        </Hoja>
      </Pantalla>
    );
  }

  // ---------- En ruta ----------
  const nav = tramoActual ? navegar(tramoActual, posicion.punto) : null;
  const total = ruta.paradas.length;
  const restantes = loQueFalta(tramos, cerradas, nav?.metrosAParada ?? 0, total - cerradas);
  const siguiente = ruta.paradas.filter((p) => p.estado === 'pendiente' && p.id !== pendiente?.id)[1];
  const propuesta = datos.propuesta;

  return (
    <Pantalla>
      {sinVaciar ? (
        <Encabezado
          icono={<IconoAlerta tamano={36} />}
          variante="alerta"
          lateral={{ titulo: 'Parada', valor: `${cerradas + 1} de ${total}` }}
        >
          <h1>No se pudo</h1>
          <p>{sinVaciar.direccion}</p>
        </Encabezado>
      ) : llegada && proxima ? (
        <Encabezado icono={<IconoCheck tamano={36} />} lateral={{ titulo: 'Parada', valor: `${cerradas + 1} de ${total}` }}>
          <h1>Llegaste</h1>
          <p>{proxima.direccion}</p>
        </Encabezado>
      ) : (
        proxima &&
        nav && (
          <EncabezadoManiobra
            metros={nav.metrosAManiobra}
            flecha={flechaDe(nav.proxima)}
            calle={nav.proxima?.calle || calleDe(proxima.direccion)}
            destino={proxima.direccion}
            llenado={proxima.llenado}
          />
        )
      )}

      <Mapa
        tramos={tramos}
        paradas={ruta.paradas}
        cerradas={cerradas}
        base={base}
        posicion={posicion}
        seguir={seguir}
        alSoltar={() => setSeguir(false)}
        estilo={preferencias.estilo}
        propuesta={propuesta}
        posicionPropuesta={propuesta?.posicion}
        verTodo={verTodo}
        margenAbajo={llegada || sinVaciar || propuesta ? 420 : 180}
      />

      {!llegada && !sinVaciar && !propuesta && (
        <div className="controles">
          <button
            type="button"
            className="redondo controles__brujula"
            aria-label="Seguir al camión"
            onClick={() => setSeguir(true)}
          >
            <IconoBrujula />
          </button>
          <div className="controles__derecha">
            <button
              type="button"
              className="redondo"
              aria-label="Cambiar el mapa"
              onClick={() => preferencias.setEstilo(preferencias.estilo === 'oscuro' ? 'claro' : 'oscuro')}
            >
              <IconoCapas />
            </button>
            <button
              type="button"
              className="redondo"
              aria-label={preferencias.silencio ? 'Activar la voz' : 'Silenciar la voz'}
              aria-pressed={preferencias.silencio}
              onClick={() => preferencias.setSilencio(!preferencias.silencio)}
            >
              {preferencias.silencio ? <IconoSilencio /> : <IconoSonido />}
            </button>
          </div>
          <div className="controles__abajo">
            <div className="velocimetro" aria-label={`${posicion.velocidadKmh} kilómetros por hora`}>
              <strong>{posicion.velocidadKmh}</strong>
              <small>km/h</small>
              <span className="velocimetro__maxima">{velocidadMaxima(nav?.calleActual ?? '')}</span>
            </div>
            {nav?.calleActual && <span className="calle">{nav.calleActual}</span>}
            <button
              type="button"
              className="redondo redondo--grande redondo--alerta"
              aria-label="Reportar un problema en esta parada"
              onClick={() => proxima && setSinVaciar(proxima)}
            >
              <IconoTriangulo tamano={30} />
            </button>
          </div>
        </div>
      )}

      {esperandoDeshacer && pendiente && (
        <div className="aviso-deshacer" role="status">
          <span className="aviso-deshacer__icono">
            <IconoCheck />
          </span>
          <span className="aviso-deshacer__texto">
            <strong>Parada {pendiente.orden} recolectada</strong>
            <small>{pendiente.direccion}</small>
          </span>
          <button type="button" className="boton boton--chico" onClick={deshacer}>
            Deshacer
          </button>
        </div>
      )}

      {sinVaciar ? (
        <NoSePudo
          parada={sinVaciar}
          cancelar={() => setSinVaciar(null)}
          confirmar={async (motivo, nota, foto) => {
            confirmarPendiente();
            const bien = await accion(`/chofer/paradas/${sinVaciar.id}/no-se-pudo`, { motivo, nota, foto });
            if (bien) setSinVaciar(null);
            return bien;
          }}
        />
      ) : propuesta ? (
        <ParadaNueva
          propuesta={propuesta}
          cerradas={cerradas}
          total={total}
          sector={datos.camion.sectorId}
          ocupado={ocupado}
          aceptar={() => accion(`/chofer/propuestas/${propuesta.id}/aceptar`)}
          rechazar={() => accion(`/chofer/propuestas/${propuesta.id}/rechazar`)}
        />
      ) : llegada && proxima ? (
        <Llegada
          parada={proxima}
          siguiente={siguiente}
          metrosASiguiente={tramos[cerradas + 1]?.largo}
          reloj={datos.reloj}
          ocupado={ocupado}
          recolectar={() => marcarRecolectado(proxima)}
          noSePudo={() => setSinVaciar(proxima)}
        />
      ) : (
        <Hoja compacta>
          <Aviso texto={aviso} />
          <Progreso hechas={cerradas} total={total} etiqueta={`Sector ${datos.camion.sectorId}`} />
          <div className="pie">
            <button type="button" className="redondo" aria-label="Ver las paradas" onClick={() => setLista(true)}>
              <IconoLista />
            </button>
            <div className="pie__centro">
              <strong>{hora(sumarMinutos(datos.reloj, restantes.minutos))}</strong>
              <small>
                fin en {restantes.minutos} min · {textoDistancia(restantes.metros)}
              </small>
            </div>
            <button
              type="button"
              className="redondo"
              aria-label="Ver toda la ruta"
              onClick={() => {
                setSeguir(false);
                setVerTodo((n) => n + 1);
              }}
            >
              <IconoBifurcacion />
            </button>
          </div>
        </Hoja>
      )}
      {listaDeParadas}
    </Pantalla>
  );
}

// ---------- Piezas ----------

function Pantalla({ children }: { children: ReactNode }) {
  return <div className="pantalla">{children}</div>;
}

interface PropsEncabezado {
  children: ReactNode;
  icono?: ReactNode;
  variante?: 'alerta';
  lateral?: { titulo: string; valor: string };
}

function Encabezado({ children, icono, variante, lateral }: PropsEncabezado) {
  return (
    <header className="encabezado">
      {icono && (
        <span className={variante === 'alerta' ? 'encabezado__icono encabezado__icono--alerta' : 'encabezado__icono'}>
          {icono}
        </span>
      )}
      <div className="encabezado__texto">{children}</div>
      {lateral && (
        <div className="encabezado__lateral">
          <small>{lateral.titulo}</small>
          <strong>{lateral.valor}</strong>
        </div>
      )}
    </header>
  );
}

interface PropsManiobra {
  metros: number;
  flecha: ReturnType<typeof flechaDe>;
  calle: string;
  destino: string;
  llenado?: number;
}

function EncabezadoManiobra({ metros, flecha, calle, destino, llenado }: PropsManiobra) {
  return (
    <header className="encabezado encabezado--maniobra">
      <span className="encabezado__flecha">
        <IconoManiobra flecha={flecha} />
      </span>
      <div className="encabezado__texto">
        <strong className="encabezado__metros">{textoDistancia(metros)}</strong>
        <span className="encabezado__calle">{calle}</span>
      </div>
      <div className="encabezado__lateral encabezado__lateral--parada">
        <small>Próxima parada</small>
        <strong>{destino}</strong>
        {llenado !== undefined && (
          <span className="llenado">
            <span className="llenado__pildora">{llenado}%</span>
            <span className="llenado__barra">
              <span style={{ width: `${Math.min(100, llenado)}%` }} />
            </span>
          </span>
        )}
      </div>
    </header>
  );
}

function Hoja({ children, compacta }: { children: ReactNode; compacta?: boolean }) {
  return (
    <section className={compacta ? 'hoja hoja--compacta' : 'hoja'}>
      <span className="hoja__manija" aria-hidden="true" />
      {children}
    </section>
  );
}

function Dato({ titulo, valor, detalle, barra }: { titulo: string; valor: string; detalle?: string; barra?: number }) {
  return (
    <div className="dato">
      <small>{titulo}</small>
      <strong>{valor}</strong>
      {barra !== undefined && (
        <span className="llenado__barra">
          <span style={{ width: `${Math.min(100, barra)}%` }} />
        </span>
      )}
      {detalle && <span className="dato__detalle">{detalle}</span>}
    </div>
  );
}

function Aviso({ texto }: { texto?: string | null }) {
  return texto ? (
    <p className="error" role="alert">
      {texto}
    </p>
  ) : null;
}

interface PropsProgreso {
  hechas: number;
  total: number;
  etiqueta: string;
  pendientes?: number;
}

function Progreso({ hechas, total, etiqueta, pendientes = 0 }: PropsProgreso) {
  return (
    <div className="progreso">
      <div className="progreso__titulo">
        <span>
          {hechas} de {total} recolectados
        </span>
        <span>{pendientes ? `${pendientes} pendiente${pendientes > 1 ? 's' : ''}` : etiqueta}</span>
      </div>
      <div className="progreso__segmentos" aria-hidden="true">
        {Array.from({ length: total }, (_, i) => (
          <span key={i} className={i < hechas ? 'hecho' : i === hechas ? 'actual' : ''} />
        ))}
      </div>
    </div>
  );
}

interface PropsLlegada {
  parada: Parada;
  siguiente?: Parada;
  metrosASiguiente?: number;
  reloj: string;
  ocupado: boolean;
  recolectar: () => void;
  noSePudo: () => void;
}

function Llegada({ parada, siguiente, metrosASiguiente, reloj, ocupado, recolectar, noSePudo }: PropsLlegada) {
  return (
    <Hoja>
      <div className="contenedor">
        <span className="contenedor__icono">
          <IconoPapeleraGrande />
        </span>
        <div>
          <h2>Contenedor {parada.tipo} de residuos generales</h2>
          <p className="tenue">
            {parada.barrio} · {parada.contenedorId}
            {parada.motivoVisita && ` · ${MOTIVO_VISITA[parada.motivoVisita] ?? ''}`}
          </p>
        </div>
      </div>
      <div className="datos">
        <Dato titulo="Llenado" valor={`${parada.llenado}%`} barra={parada.llenado} />
        <Dato titulo="Prioridad" valor={prioridadDe(parada)} />
        <Dato
          titulo="Última vez"
          valor={parada.ultimaRecoleccion ? diaRelativo(parada.ultimaRecoleccion, reloj) : '—'}
          detalle={parada.ultimaRecoleccion ? hora(parada.ultimaRecoleccion) : 'sin datos'}
        />
      </div>
      <button type="button" className="boton boton--principal" disabled={ocupado} onClick={recolectar}>
        <IconoCheck /> Marcar como recolectado
      </button>
      <button type="button" className="boton boton--borde" disabled={ocupado} onClick={noSePudo}>
        <span className="texto-ambar">
          <IconoTriangulo tamano={20} />
        </span>
        No se pudo recolectar
      </button>
      <p className="siguiente">
        {siguiente ? (
          <>
            <span>Siguiente: {siguiente.direccion}</span>
            {metrosASiguiente !== undefined && <span>{textoDistancia(metrosASiguiente)}</span>}
          </>
        ) : (
          <span>Es la última parada: después, vuelta a la base</span>
        )}
      </p>
    </Hoja>
  );
}

function IconoPapeleraGrande() {
  return (
    <svg
      width="28"
      height="28"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M4 7h16" />
      <path d="M10 4h4" />
      <path d="M6 7l1 13h10l1-13" />
    </svg>
  );
}

interface PropsParadaNueva {
  propuesta: NonNullable<Turno['propuesta']>;
  cerradas: number;
  total: number;
  sector: number;
  ocupado: boolean;
  aceptar: () => void;
  rechazar: () => void;
}

// Si el chofer no contesta, a los 8 segundos se acepta sola: es lo que el sistema recomienda
function ParadaNueva({ propuesta, cerradas, total, sector, ocupado, aceptar, rechazar }: PropsParadaNueva) {
  const [segundos, setSegundos] = useState(SEGUNDOS_PARA_ACEPTAR);
  const respondida = useRef(false);

  useEffect(() => {
    respondida.current = false;
    setSegundos(SEGUNDOS_PARA_ACEPTAR);
    decir(`Nueva parada prioritaria: ${propuesta.direccion}`);
    navigator.vibrate?.([200, 100, 200]);
    const intervalo = setInterval(() => setSegundos((s) => s - 1), 1000);
    return () => clearInterval(intervalo);
  }, [propuesta.id, propuesta.direccion]);

  useEffect(() => {
    if (segundos <= 0 && !respondida.current) {
      respondida.current = true;
      aceptar();
    }
  }, [segundos, aceptar]);

  return (
    <Hoja>
      <div className="contenedor">
        <span className="contenedor__icono">
          <IconoSenal tamano={28} />
        </span>
        <div>
          <h2>Nueva parada prioritaria</h2>
          <p className="tenue">
            El contenedor de {propuesta.direccion} llegó al {propuesta.llenado}%
          </p>
        </div>
      </div>
      <div className="datos">
        <Dato titulo="Tiempo" valor={`+${propuesta.minutosExtra} min`} />
        <Dato titulo="Distancia" valor={`+${textoDistancia(propuesta.kmExtra * 1000)}`} />
        <Dato titulo="Entra como" valor={`parada ${propuesta.orden}`} />
      </div>
      <Progreso hechas={cerradas} total={total + 1} etiqueta={`Sector ${sector}`} />
      <button
        type="button"
        className="boton boton--principal"
        disabled={ocupado}
        onClick={() => {
          respondida.current = true;
          aceptar();
        }}
      >
        Aceptar nueva ruta <span className="cuenta">{Math.max(0, segundos)} s</span>
      </button>
      <button
        type="button"
        className="boton boton--borde"
        disabled={ocupado}
        onClick={() => {
          respondida.current = true;
          rechazar();
        }}
      >
        Mantener ruta anterior
      </button>
    </Hoja>
  );
}

interface PropsResumen {
  ruta: Ruta;
  trazado?: Trazado;
  volver: () => void;
  verDetalle: () => void;
}

function ResumenDeRuta({ ruta, trazado, volver, verDetalle }: PropsResumen) {
  const vaciadas = ruta.paradas.filter((p) => p.estado === 'recolectada').length;
  const problemas = ruta.paradas.filter((p) => p.estado === 'no_se_pudo');
  const fin = ultimaHora(ruta);
  const minutos = ruta.salidaReal && fin ? Math.round((Date.parse(fin) - Date.parse(ruta.salidaReal)) / 60000) : null;
  return (
    <Hoja>
      <Progreso hechas={vaciadas} total={ruta.paradas.length} etiqueta="" pendientes={problemas.length} />
      <div className="datos">
        <Dato titulo="Duración" valor={minutos === null ? '—' : duracion(minutos)} />
        <Dato titulo="Recorrido" valor={km(trazado?.kmPorCalle ?? ruta.kmPlanificados)} />
        <Dato titulo="Incidentes" valor={String(problemas.length)} />
      </div>
      {problemas.map((p) => (
        <div key={p.id} className="incidente">
          <span className="numero numero--prioritaria">!</span>
          <span>
            <strong>{p.direccion}</strong>
            <small>{p.motivo}, quedó para el operador</small>
          </span>
        </div>
      ))}
      <button type="button" className="boton boton--principal" onClick={volver}>
        <IconoCasa /> Volver a la base
      </button>
      <button type="button" className="boton boton--borde" onClick={verDetalle}>
        Ver detalle de paradas
      </button>
    </Hoja>
  );
}

interface PropsLista {
  ruta: Ruta;
  cerradas: number;
  preferencias: ReturnType<typeof usePreferencias>;
  cerrar: () => void;
  terminarTurno: () => void;
  salir: () => void;
}

function ListaParadas({ ruta, cerradas, preferencias, cerrar, terminarTurno, salir }: PropsLista) {
  return (
    <div className="fondo" role="presentation" onClick={cerrar}>
      <section
        className="hoja hoja--flotante"
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-lista"
        onClick={(e) => e.stopPropagation()}
      >
        <span className="hoja__manija" aria-hidden="true" />
        <h2 id="titulo-lista">Paradas</h2>
        <ol className="paradas">
          {ruta.paradas.map((p, i) => {
            const estado = i < cerradas && p.estado === 'pendiente' ? 'recolectada' : p.estado;
            return (
              <li key={p.id} className={`parada parada--${estado}`}>
                <span
                  className={
                    estado === 'pendiente' && esPrioritaria(p) ? 'numero numero--prioritaria' : `numero numero--${estado}`
                  }
                >
                  {estado === 'recolectada' ? '✓' : estado === 'no_se_pudo' ? '!' : i + 1}
                </span>
                <span className="parada__texto">
                  <strong>{p.direccion}</strong>
                  <small>{estado === 'no_se_pudo' ? p.motivo : (MOTIVO_VISITA[p.motivoVisita ?? ''] ?? '')}</small>
                </span>
                <span className={p.llenado >= 80 ? 'porcentaje porcentaje--alto' : 'porcentaje'}>{p.llenado}%</span>
              </li>
            );
          })}
        </ol>
        <h2 className="subtitulo">Ubicación del camión</h2>
        <div className="opciones opciones--dos">
          <button
            type="button"
            className={preferencias.modo === 'simulada' ? 'opcion opcion--elegida' : 'opcion'}
            onClick={() => preferencias.setModo('simulada')}
          >
            Simulada (demo)
          </button>
          <button
            type="button"
            className={preferencias.modo === 'gps' ? 'opcion opcion--elegida' : 'opcion'}
            onClick={() => preferencias.setModo('gps')}
          >
            GPS del celular
          </button>
        </div>
        {preferencias.modo === 'simulada' && (
          <div className="opciones opciones--dos">
            {[1, 4].map((v) => (
              <button
                key={v}
                type="button"
                className={preferencias.acelerar === v ? 'opcion opcion--elegida' : 'opcion'}
                onClick={() => preferencias.setAcelerar(v)}
              >
                {v === 1 ? 'Velocidad real' : 'Rápido (x4)'}
              </button>
            ))}
          </div>
        )}
        <button type="button" className="boton boton--borde" onClick={terminarTurno}>
          Terminar turno
        </button>
        <button type="button" className="boton boton--borde" onClick={salir}>
          Cerrar sesión
        </button>
        <button type="button" className="boton boton--principal" onClick={cerrar}>
          Volver a la ruta
        </button>
      </section>
    </div>
  );
}

// ---------- Cuentas ----------

function kmDeLaRuta(ruta: Ruta, trazado: Trazado | undefined, tramos: Tramo[]): number {
  if (trazado?.estado === 'listo' && trazado.kmPorCalle !== null) return trazado.kmPorCalle;
  return tramos.length ? tramos.reduce((suma, t) => suma + t.largo, 0) / 1000 : ruta.kmPlanificados;
}

function sumarMinutos(fecha: string, minutos: number): string {
  return new Date(Date.parse(fecha) + minutos * 60000).toISOString();
}

function ultimaHora(ruta: Ruta): string | null {
  const horas = ruta.paradas.map((p) => p.horaReal).filter((h): h is string => Boolean(h));
  return horas.length ? horas.reduce((a, b) => (Date.parse(a) > Date.parse(b) ? a : b)) : null;
}

function duracion(minutos: number): string {
  return minutos >= 60 ? `${Math.floor(minutos / 60)} h ${String(minutos % 60).padStart(2, '0')}` : `${minutos} min`;
}

function diaRelativo(fecha: string, reloj: string): string {
  const dia = (f: string) => new Date(f).toDateString();
  const ayer = new Date(Date.parse(reloj) - 86_400_000).toDateString();
  if (dia(fecha) === dia(reloj)) return 'hoy';
  if (dia(fecha) === ayer) return 'ayer';
  return new Date(fecha).toLocaleDateString('es-AR', { day: 'numeric', month: 'numeric' });
}

// ---------- Datos y efectos ----------

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

// El recorrido por las calles de la ruta. El backend lo calcula la primera vez y otra vez si se
// suma una parada: mientras tanto el mapa usa líneas rectas y se vuelve a preguntar.
function useTrazado(ruta: Ruta | null): Trazado | undefined {
  const [trazado, setTrazado] = useState<Trazado>();
  const [intento, setIntento] = useState(0);
  const paradas = ruta ? ruta.paradas.map((p) => p.id).join() : '';
  const coincide = (t?: Trazado) => t && ruta && t.rutaId === ruta.id && t.tramos.length === ruta.paradas.length + 1;
  const actual = coincide(trazado) ? trazado : undefined;
  const yaEsta = Boolean(actual) || (trazado?.rutaId === ruta?.id && trazado?.estado === 'sin_servicio');

  useEffect(() => {
    if (!ruta || yaEsta) return;
    let vigente = true;
    const espera = intento === 0 ? 0 : trazado?.estado === 'fallo' ? 15000 : 2000;
    const reintento = setTimeout(() => {
      pedir<Trazado>('/chofer/ruta/trazado')
        .then((nuevo) => vigente && setTrazado(nuevo))
        .catch(() => {
          // sin recorrido se dibujan líneas rectas
        })
        .finally(() => vigente && setIntento((n) => n + 1));
    }, espera);
    return () => {
      vigente = false;
      clearTimeout(reintento);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ruta?.id, paradas, yaEsta, intento]);

  return actual;
}

// Manda la posición al backend cada algunos segundos, así el panel ve dónde está el camión
function useEnviarPosicion(enRuta: boolean, posicion: Posicion) {
  const ultima = useRef(posicion);
  ultima.current = posicion;
  useEffect(() => {
    if (!enRuta) return;
    const intervalo = setInterval(() => {
      pedir('/chofer/posicion', { metodo: 'POST', cuerpo: ultima.current.punto }).catch(() => {
        // si falla, se manda la próxima
      });
    }, MS_ENTRE_POSICIONES);
    return () => clearInterval(intervalo);
  }, [enRuta]);
}

// Dice cada maniobra dos veces (a 150 m y a 30 m) y avisa al llegar
function useIndicacionesEnVoz(
  tramo: Tramo | undefined,
  posicion: Posicion,
  proxima: Parada | null,
  llegada: boolean,
  volviendo: boolean,
) {
  const dichas = useRef(new Set<string>());
  const nav = tramo ? navegar(tramo, posicion.punto) : null;
  const destino = volviendo ? 'la base' : (proxima?.direccion ?? '');
  const clave = nav?.proxima ? `${nav.proxima.lat},${nav.proxima.lng}` : `parada-${destino}`;
  const metros = nav?.metrosAManiobra ?? Infinity;
  const decenas = Math.round(metros / 10);

  useEffect(() => {
    if (!nav) return;
    for (const umbral of [150, 30]) {
      const id = `${clave}|${umbral}`;
      if (metros <= umbral && metros > 5 && !dichas.current.has(id)) {
        dichas.current.add(id);
        decir(`En ${Math.round(metros / 10) * 10} metros, ${textoManiobra(nav.proxima, destino)}`);
        break;
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clave, decenas]);

  useEffect(() => {
    if (llegada && destino) decir(volviendo ? 'Llegaste a la base' : `Llegaste a ${destino}`);
  }, [llegada, destino, volviendo]);
}

// Lo que elige el chofer y queda guardado en el celular
function leerPreferencia<T>(clave: string, porDefecto: T): T {
  try {
    const guardado = localStorage.getItem(`ecoruta.chofer.${clave}`);
    return guardado === null ? porDefecto : (JSON.parse(guardado) as T);
  } catch {
    return porDefecto;
  }
}

function usePreferencia<T>(clave: string, porDefecto: T): [T, (valor: T) => void] {
  const [valor, setValor] = useState<T>(() => leerPreferencia(clave, porDefecto));
  const guardar = useCallback(
    (nuevo: T) => {
      try {
        localStorage.setItem(`ecoruta.chofer.${clave}`, JSON.stringify(nuevo));
      } catch {
        // queda solo hasta cerrar la app
      }
      setValor(nuevo);
    },
    [clave],
  );
  return [valor, guardar];
}

function usePreferencias() {
  const [modo, setModo] = usePreferencia<ModoUbicacion>('ubicacion', 'simulada');
  const [acelerar, setAcelerar] = usePreferencia('acelerar', 1);
  const [estilo, setEstilo] = usePreferencia<EstiloMapa>('mapa', 'oscuro');
  const [silencio, setSilencio] = usePreferencia('silencio', false);
  useEffect(() => silenciar(silencio), [silencio]);
  return { modo, setModo, acelerar, setAcelerar, estilo, setEstilo, silencio, setSilencio };
}
