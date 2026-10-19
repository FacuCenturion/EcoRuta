import { useEffect, useState } from 'react';
import type { IndicadoresApi } from '../api/adaptadores';
import { aMs } from '../api/adaptadores';
import { pedir } from '../api/cliente';
import { obtenerHistorial } from '../logic/historial';
import { useSimulacion } from './SimulacionContext';

const DIAS = 30;

// Lo que muestra la pantalla Indicadores, venga del backend o del historial simulado en el navegador.
// "Fija" es la ruta de siempre (rondas a horario visitando todos los contenedores); "dinámica", EcoRuta.
export interface DatosIndicadores {
  dias: { fechaMs: number; kmFija: number; kmDinamica: number }[];
  semanas: { etiqueta: string; desbordesFija: number; desbordesDinamica: number }[];
  porcentajeVisitasBajasFija: number;
  porcentajeVisitasBajasDinamica: number;
  eficienciaPorArea: Record<number, number | null>;
  mapaCalor: Record<number, number[][]>; // [área][día de la semana, lunes = 0][hora]
}

function desdeElHistorialLocal(areaId?: number): DatosIndicadores {
  const historial = obtenerHistorial();
  return {
    dias: historial.dias.map((dia) => {
      const fuente = areaId === undefined ? dia : dia.porZona[areaId];
      return { fechaMs: dia.fechaMs, kmFija: fuente.kmFija, kmDinamica: fuente.kmDinamica };
    }),
    semanas: historial.semanas,
    porcentajeVisitasBajasFija: historial.porcentajeVisitasBajasFija,
    porcentajeVisitasBajasDinamica: historial.porcentajeVisitasBajasDinamica,
    eficienciaPorArea: historial.eficienciaPorZona,
    mapaCalor: historial.mapaCalor,
  };
}

function desdeLaApi(datos: IndicadoresApi): DatosIndicadores {
  return {
    dias: datos.dias.map((d) => ({ fechaMs: aMs(`${d.fecha}T12:00:00`), kmFija: d.kmFija, kmDinamica: d.kmDinamica })),
    semanas: datos.semanas,
    porcentajeVisitasBajasFija: datos.porcentajeVisitasBajasFija,
    porcentajeVisitasBajasDinamica: datos.porcentajeVisitasBajasDinamica,
    eficienciaPorArea: datos.eficienciaPorSector,
    mapaCalor: datos.mapaCalor,
  };
}

// Con backend se pide de nuevo al cambiar el filtro o el día (los indicadores se cierran por día)
export function useIndicadores(areaId?: number): { datos: DatosIndicadores | null; error?: string } {
  const { fuente, estado } = useSimulacion();
  const dia = new Date(estado.relojMs).toDateString();
  const [remoto, setRemoto] = useState<DatosIndicadores>();
  const [error, setError] = useState<string>();

  const clave = `${areaId ?? 'todas'}|${dia}`;
  useEffect(() => {
    if (fuente !== 'api') return;
    let vigente = true;
    const filtro = areaId === undefined ? '' : `&sectorId=${areaId}`;
    pedir<IndicadoresApi>(`/indicadores?dias=${DIAS}${filtro}`)
      .then((respuesta) => {
        if (!vigente) return;
        setRemoto(desdeLaApi(respuesta));
        setError(undefined);
      })
      .catch((e: Error) => vigente && setError(e.message));
    return () => {
      vigente = false;
    };
  }, [fuente, areaId, clave]);

  if (fuente === 'local') return { datos: desdeElHistorialLocal(areaId) };
  // Mientras llegan los datos del filtro nuevo se siguen mostrando los anteriores
  return { datos: remoto ?? null, error };
}
