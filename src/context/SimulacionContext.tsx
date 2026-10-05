import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { MS_POR_TICK } from '../config';
import { agregarReporteVecinal, avanzar, crearEstadoInicial, decidirIncidente } from '../simulator/simulador';
import type { EstadoSimulacion } from '../types';

export type FiltroZona = number | 'todas';

interface ValorContexto {
  estado: EstadoSimulacion;
  zonaFiltro: FiltroZona;
  cambiarZona: (zona: FiltroZona) => void;
  enPausa: boolean;
  alternarPausa: () => void;
  velocidad: number;
  cambiarVelocidad: (velocidad: number) => void;
  decidir: (incidenteId: string, decision: 'confirmado' | 'descartado') => void;
  reportarVecino: (contenedorId: string, comentario: string) => void;
}

const Contexto = createContext<ValorContexto | null>(null);

// Guarda el estado del sistema y lo va actualizando como si llegaran
// lecturas nuevas de los sensores. Todas las pantallas leen de acá.
// Cuando exista el backend, este es el único lugar que hay que cambiar:
// en vez de simular, se piden los datos a la API.
export function SimulacionProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<EstadoSimulacion>(() => crearEstadoInicial());
  const [zonaFiltro, setZonaFiltro] = useState<FiltroZona>('todas');
  const [enPausa, setEnPausa] = useState(false);
  const [velocidad, setVelocidad] = useState(1);

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
      estado,
      zonaFiltro,
      cambiarZona: setZonaFiltro,
      enPausa,
      alternarPausa: () => setEnPausa((p) => !p),
      velocidad,
      cambiarVelocidad: setVelocidad,
      decidir,
      reportarVecino,
    }),
    [estado, zonaFiltro, enPausa, velocidad, decidir, reportarVecino],
  );

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useSimulacion(): ValorContexto {
  const valor = useContext(Contexto);
  if (!valor) throw new Error('useSimulacion se usa dentro de SimulacionProvider');
  return valor;
}
