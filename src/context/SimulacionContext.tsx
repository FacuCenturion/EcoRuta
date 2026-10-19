import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  aCamion,
  aContenedor,
  aIncidente,
  aMs,
  vistaDeContenedores,
  type CamionApi,
  type ContenedorApi,
  type CurvaApi,
  type IncidenteApi,
  type ResumenApi,
  type SectorApi,
  type SimulacionApi,
} from '../api/adaptadores';
import { escucharAvisos, pedir, USA_API } from '../api/cliente';
import type { Vista } from '../components/mapa/tipos';
import { MS_POR_TICK } from '../config';
import { buscarZona, ZONAS } from '../data/zonas';
import { obtenerHistorial } from '../logic/historial';
import { agregarReporteVecinal, avanzar, crearEstadoInicial, decidirIncidente } from '../simulator/simulador';
import type { EstadoSimulacion, LecturaSensor, Zona } from '../types';
import { vistaParaZona, zonasParaFiltro } from '../utils/vista';

export type FiltroZona = number | 'todas';

// Lo que se puede elegir en el filtro del panel: las zonas de las empresas (datos simulados
// en el navegador) o los sectores de la zona 1 (datos del backend)
export interface Area {
  id: number;
  nombre: string;
  empresa: string;
}

export interface Estadisticas {
  llenadoPorHora: number[]; // 24 valores
  eficienciaPorArea: Record<number, number | null>;
}

export interface Curva {
  historial: LecturaSensor[];
  estimadas: LecturaSensor[];
}

interface ValorContexto {
  fuente: 'local' | 'api';
  estado: EstadoSimulacion;
  zonaFiltro: FiltroZona;
  cambiarZona: (zona: FiltroZona) => void;
  enPausa: boolean;
  alternarPausa: () => void;
  velocidad: number;
  cambiarVelocidad: (velocidad: number) => void;
  conControles: boolean; // false cuando el backend usa datos reales y no hay simulación que pausar
  decidir: (incidenteId: string, decision: 'confirmado' | 'descartado') => void;
  reportarVecino: (contenedorId: string, comentario: string) => void;
  areas: Area[];
  palabraArea: 'Zona' | 'Sector';
  nombreArea: (id: number) => string;
  areaDestacada: number; // la que se muestra cuando una pantalla necesita una sola
  vistaPara: (filtro: FiltroZona) => Vista;
  contornosPara: (filtro: FiltroZona) => Zona[];
  estadisticas: Estadisticas;
  pedirCurva?: (contenedorId: string) => Promise<Curva>; // solo con backend; si no, la curva sale del contenedor
  aviso?: string; // un error que el operador tiene que ver
}

const Contexto = createContext<ValorContexto | null>(null);

export function SimulacionProvider({ children }: { children: ReactNode }) {
  return USA_API ? <ProveedorConectado>{children}</ProveedorConectado> : <ProveedorLocal>{children}</ProveedorLocal>;
}

// ---------- Datos simulados en el navegador ----------

const AREAS_LOCALES: Area[] = ZONAS.map((z) => ({ id: z.id, nombre: `Zona ${z.id}`, empresa: z.empresa }));

// Guarda el estado del sistema y lo va actualizando como si llegaran
// lecturas nuevas de los sensores. Es lo que se usa sin backend.
function ProveedorLocal({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<EstadoSimulacion>(() => crearEstadoInicial());
  const [zonaFiltro, setZonaFiltro] = useState<FiltroZona>('todas');
  const [enPausa, setEnPausa] = useState(false);
  const [velocidad, setVelocidad] = useState(1);
  const estadisticas = useMemo<Estadisticas>(() => {
    const historial = obtenerHistorial();
    return { llenadoPorHora: historial.llenadoPorHora, eficienciaPorArea: historial.eficienciaPorZona };
  }, []);

  useEffect(() => {
    if (enPausa) return;
    const temporizador = setInterval(() => {
      setEstado((anterior) => avanzar(anterior));
    }, MS_POR_TICK / velocidad);
    return () => clearInterval(temporizador);
  }, [enPausa, velocidad]);

  const decidir = useCallback((incidenteId: string, decision: 'confirmado' | 'descartado') => {
    setEstado((anterior) => decidirIncidente(anterior, incidenteId, decision));
  }, []);

  const reportarVecino = useCallback((contenedorId: string, comentario: string) => {
    setEstado((anterior) => agregarReporteVecinal(anterior, contenedorId, comentario));
  }, []);

  const valor = useMemo<ValorContexto>(
    () => ({
      fuente: 'local',
      estado,
      zonaFiltro,
      cambiarZona: setZonaFiltro,
      enPausa,
      alternarPausa: () => setEnPausa((p) => !p),
      velocidad,
      cambiarVelocidad: setVelocidad,
      conControles: true,
      decidir,
      reportarVecino,
      areas: AREAS_LOCALES,
      palabraArea: 'Zona',
      nombreArea: (id) => `Zona ${id}`,
      areaDestacada: 2,
      vistaPara: vistaParaZona,
      contornosPara: zonasParaFiltro,
      estadisticas,
    }),
    [estado, zonaFiltro, enPausa, velocidad, decidir, reportarVecino, estadisticas],
  );

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

// ---------- Datos del backend ----------

// El backend trabaja solo con la zona 1, dividida en sectores
const ZONA_DEL_SISTEMA = 1;

interface Instantanea {
  estado: EstadoSimulacion;
  simulacion: SimulacionApi;
  areas: Area[];
  estadisticas: Estadisticas;
}

async function pedirInstantanea(): Promise<Instantanea> {
  const [simulacion, contenedores, camiones, incidentes, resumen, sectores] = await Promise.all([
    pedir<SimulacionApi>('/simulacion'),
    pedir<ContenedorApi[]>(`/contenedores?zonaId=${ZONA_DEL_SISTEMA}`),
    pedir<CamionApi[]>(`/camiones?zonaId=${ZONA_DEL_SISTEMA}`),
    pedir<IncidenteApi[]>(`/incidentes?zonaId=${ZONA_DEL_SISTEMA}`),
    pedir<ResumenApi>(`/panel/resumen?zonaId=${ZONA_DEL_SISTEMA}`),
    pedir<SectorApi[]>('/panel/sectores'),
  ]);
  const relojMs = aMs(simulacion.reloj);
  const inicioDia = new Date(relojMs);
  inicioDia.setHours(0, 0, 0, 0);

  return {
    simulacion,
    areas: sectores.map((s) => ({ id: s.id, nombre: s.nombre, empresa: s.empresa })),
    estadisticas: {
      llenadoPorHora: resumen.llenadoPorHora,
      eficienciaPorArea: Object.fromEntries(sectores.map((s) => [s.id, s.eficiencia])),
    },
    estado: {
      semilla: 0,
      tick: 0,
      relojMs,
      inicioDiaMs: inicioDia.getTime(),
      contenedores: contenedores.map(aContenedor),
      camiones: camiones.map(aCamion),
      incidentes: incidentes.map(aIncidente).sort((a, b) => b.creadoMs - a.creadoMs),
      recolectadosHoy: resumen.recolectadosHoy,
      planificadosHoy: resumen.planificadosHoy,
      kmEvitadosHoy: resumen.kmEvitadosHoy,
      contadorIncidentes: incidentes.length,
    },
  };
}

// Pide todo una vez al entrar y después solo cuando el backend avisa que hubo cambios
// (Server-Sent Events). No pregunta cada tantos segundos.
function ProveedorConectado({ children }: { children: ReactNode }) {
  const [datos, setDatos] = useState<Instantanea | null>(null);
  const [zonaFiltro, setZonaFiltro] = useState<FiltroZona>('todas');
  const [aviso, setAviso] = useState<string>();
  const [conectado, setConectado] = useState(true);
  const enCurso = useRef(false);
  const otraVez = useRef(false);
  const montado = useRef(true);

  // Si llegan varios avisos mientras se está pidiendo, se vuelve a pedir una sola vez al terminar
  const actualizar = useCallback(async () => {
    if (enCurso.current) {
      otraVez.current = true;
      return;
    }
    enCurso.current = true;
    try {
      do {
        otraVez.current = false;
        try {
          const nueva = await pedirInstantanea();
          if (!montado.current) return;
          setDatos(nueva);
          setAviso(undefined);
        } catch (error) {
          if (montado.current) setAviso((error as Error).message);
        }
      } while (otraVez.current);
    } finally {
      enCurso.current = false;
    }
  }, []);

  useEffect(() => {
    montado.current = true;
    actualizar();
    const dejarDeEscuchar = escucharAvisos(actualizar, (ahora) => {
      setConectado(ahora);
      if (ahora) actualizar(); // al reconectar puede haberse perdido algún aviso
    });
    return () => {
      montado.current = false;
      dejarDeEscuchar();
    };
  }, [actualizar]);

  // Las acciones cambian algo en el backend; el aviso de vuelta trae los datos nuevos
  const accion = useCallback(
    async (hacer: () => Promise<unknown>) => {
      try {
        await hacer();
        actualizar();
      } catch (error) {
        setAviso((error as Error).message);
      }
    },
    [actualizar],
  );

  const valor = useMemo<ValorContexto | null>(() => {
    if (!datos) return null;
    const { estado, simulacion, areas, estadisticas } = datos;
    const zona = buscarZona(ZONA_DEL_SISTEMA);
    const vistaZona = vistaDeContenedores(estado.contenedores, 14) ?? { ...zona.centro, zoom: 14 };
    return {
      fuente: 'api',
      estado,
      zonaFiltro,
      cambiarZona: setZonaFiltro,
      enPausa: simulacion.enPausa,
      alternarPausa: () =>
        accion(() => pedir('/simulacion', { metodo: 'POST', cuerpo: { enPausa: !simulacion.enPausa } })),
      velocidad: simulacion.velocidad,
      cambiarVelocidad: (v) => accion(() => pedir('/simulacion', { metodo: 'POST', cuerpo: { velocidad: v } })),
      conControles: simulacion.activa,
      decidir: (id, decision) =>
        accion(() => pedir(`/incidentes/${encodeURIComponent(id)}/decision`, { metodo: 'POST', cuerpo: { decision } })),
      reportarVecino: (contenedorId, comentario) =>
        accion(() =>
          pedir('/reportes-vecinales', {
            metodo: 'POST',
            cuerpo: { contenedorId, comentario: comentario.trim() || 'Sin comentario.', origen: 'Boti' },
          }),
        ),
      areas,
      palabraArea: 'Sector',
      nombreArea: (id) => `Sector ${id}`,
      areaDestacada: areas[0]?.id ?? 1,
      vistaPara: (filtro) =>
        filtro === 'todas'
          ? vistaZona
          : (vistaDeContenedores(
              estado.contenedores.filter((c) => c.areaId === filtro),
              15,
            ) ?? vistaZona),
      contornosPara: () => [zona],
      estadisticas,
      pedirCurva: async (contenedorId) => {
        const curva = await pedir<CurvaApi>(`/contenedores/${encodeURIComponent(contenedorId)}/curva`);
        return {
          historial: curva.lecturas.map((l) => ({ ms: aMs(l.medidoAt), nivel: l.llenado })),
          estimadas: curva.estimadas.map((l) => ({ ms: aMs(l.medidoAt), nivel: l.llenado })),
        };
      },
      aviso: aviso ?? (conectado ? undefined : 'Se cortó la conexión con el servidor. Reintentando…'),
    };
  }, [datos, zonaFiltro, accion, aviso, conectado]);

  if (!valor) {
    return (
      <main className="login">
        <section className="login__tarjeta" aria-live="polite">
          <h1>{aviso ? 'No se pudieron cargar los datos' : 'Cargando datos…'}</h1>
          {aviso && (
            <>
              <p className="login__error" role="alert">
                {aviso}
              </p>
              <button type="button" className="boton boton--primario" onClick={actualizar}>
                Reintentar
              </button>
            </>
          )}
        </section>
      </main>
    );
  }
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useSimulacion(): ValorContexto {
  const valor = useContext(Contexto);
  if (!valor) throw new Error('useSimulacion se usa dentro de SimulacionProvider');
  return valor;
}
