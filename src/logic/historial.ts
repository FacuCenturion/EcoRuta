// Historial de los últimos días generado con el mismo modelo de llenado que
// usa el simulador. Compara dos formas de trabajar sobre los mismos contenedores:
//  - Ruta fija: rondas a las 8, 14 y 20 h visitando todos los contenedores de la zona.
//  - Ruta dinámica: las mismas rondas más un refuerzo a las 17 h, pero en cada una
//    solo se visitan los contenedores que superan el umbral o corren riesgo de
//    desbordar antes de la próxima ronda.
// Son estimaciones del modelo, no mediciones reales.
import { HORAS_DE_RONDA, RONDAS_RUTA_FIJA, UMBRAL_BAJO } from '../config';
import { crearContenedores } from '../data/semilla';
import { ZONAS } from '../data/zonas';
import type { Contenedor } from '../types';
import { crearAleatorio, entre } from '../utils/aleatorio';
import { diaDeSemana, incrementoPorHora } from './llenado';
import { motivoDePrioridad } from './prioridad';
import { planificarRuta } from './rutas';

const DIAS_DE_ARRANQUE = 2; // los primeros días no se cuentan, el modelo todavía se está acomodando
export const DIAS_MOSTRADOS = 30;
export const SEMANAS_MOSTRADAS = 8;
const DIAS_ANALIZADOS = SEMANAS_MOSTRADAS * 7;

export interface DiaHistorial {
  fechaMs: number;
  kmFija: number;
  kmDinamica: number;
  porZona: Record<number, { kmFija: number; kmDinamica: number }>;
}

export interface SemanaHistorial {
  etiqueta: string;
  desbordesFija: number;
  desbordesDinamica: number;
}

export interface Historial {
  dias: DiaHistorial[]; // últimos 30 días
  semanas: SemanaHistorial[]; // últimas 8 semanas
  porcentajeVisitasBajasFija: number; // % de visitas a contenedores con menos de 50%
  porcentajeVisitasBajasDinamica: number;
  kmEvitadosPorZona: Record<number, number>; // últimos 30 días
  eficienciaPorZona: Record<number, number>; // sobre 100, ver generarHistorial
  llenadoPorHora: number[]; // promedio de las 24 horas, todas las zonas
  mapaCalor: Record<number, number[][]>; // [zona][día de la semana][hora] = llenado promedio
}

function crearDia(fechaMs: number): DiaHistorial {
  const porZona: DiaHistorial['porZona'] = {};
  ZONAS.forEach((z) => {
    porZona[z.id] = { kmFija: 0, kmDinamica: 0 };
  });
  return { fechaMs, kmFija: 0, kmDinamica: 0, porZona };
}

export function generarHistorial(contenedores: Contenedor[], semilla = 7, ahoraMs = Date.now()): Historial {
  const azar = crearAleatorio(semilla);
  const totalDias = DIAS_ANALIZADOS + DIAS_DE_ARRANQUE;

  // Largo de la ruta fija de cada zona (visita a todos los contenedores)
  const kmRutaFija: Record<number, number> = {};
  const contenedoresPorZona: Record<number, Contenedor[]> = {};
  for (const zona of ZONAS) {
    contenedoresPorZona[zona.id] = contenedores.filter((c) => c.zonaId === zona.id);
    kmRutaFija[zona.id] = planificarRuta(
      zona.base,
      contenedoresPorZona[zona.id].map((c) => ({ id: c.id, posicion: c.posicion })),
    ).km;
  }

  // Niveles de cada contenedor bajo cada política
  const nivelFija = new Map<string, number>();
  const nivelDinamica = new Map<string, number>();
  contenedores.forEach((c) => {
    const inicial = entre(azar, 20, 60);
    nivelFija.set(c.id, inicial);
    nivelDinamica.set(c.id, inicial);
  });

  const dias: DiaHistorial[] = [];
  const desbordesPorDia: { fija: number; dinamica: number }[] = [];
  let visitasFija = 0;
  let visitasBajasFija = 0;
  let visitasDinamica = 0;
  let visitasBajasDinamica = 0;
  // Índice de eficiencia: de cada 100 contenedores vaciados, cuántos se vaciaron en el
  // momento justo (con 50% o más de llenado y antes de desbordar).
  const visitasZona: Record<number, number> = {};
  const justasZona: Record<number, number> = {};
  ZONAS.forEach((z) => {
    visitasZona[z.id] = 0;
    justasZona[z.id] = 0;
  });

  const sumaPorHora = new Array<number>(24).fill(0);
  let muestrasPorHora = 0;
  const sumaCalor: Record<number, number[][]> = {};
  const cuentaCalor: Record<number, number[][]> = {};
  ZONAS.forEach((z) => {
    sumaCalor[z.id] = Array.from({ length: 7 }, () => new Array<number>(24).fill(0));
    cuentaCalor[z.id] = Array.from({ length: 7 }, () => new Array<number>(24).fill(0));
  });

  for (let d = 0; d < totalDias; d++) {
    const fecha = new Date(ahoraMs);
    fecha.setHours(0, 0, 0, 0);
    fecha.setDate(fecha.getDate() - (totalDias - d));
    const dia = diaDeSemana(fecha);
    const contar = d >= DIAS_DE_ARRANQUE;
    const ultimos30 = d >= totalDias - DIAS_MOSTRADOS;
    const registro = crearDia(fecha.getTime());
    const desbordadoFija = new Set<string>();
    const desbordadoDinamica = new Set<string>();

    for (let hora = 0; hora < 24; hora++) {
      // Los contenedores se llenan igual bajo las dos políticas
      for (const c of contenedores) {
        const incremento = incrementoPorHora(c.velocidad, hora, dia) * entre(azar, 0.7, 1.3);
        const nf = Math.min(100, (nivelFija.get(c.id) as number) + incremento);
        const nd = Math.min(100, (nivelDinamica.get(c.id) as number) + incremento);
        nivelFija.set(c.id, nf);
        nivelDinamica.set(c.id, nd);
        if (nf >= 100) desbordadoFija.add(c.id);
        if (nd >= 100) desbordadoDinamica.add(c.id);
      }

      for (const zona of ZONAS) {
        const lista = contenedoresPorZona[zona.id];

        if (RONDAS_RUTA_FIJA.includes(hora)) {
          registro.porZona[zona.id].kmFija += kmRutaFija[zona.id];
          if (contar) {
            for (const c of lista) {
              visitasFija++;
              if ((nivelFija.get(c.id) as number) < UMBRAL_BAJO) visitasBajasFija++;
            }
          }
          lista.forEach((c) => nivelFija.set(c.id, 3));
        }

        if (HORAS_DE_RONDA.includes(hora)) {
          const elegidos = lista.filter(
            (c) => motivoDePrioridad(nivelDinamica.get(c.id) as number, c.velocidad, hora, dia, 0) !== null,
          );
          const ruta = planificarRuta(
            zona.base,
            elegidos.map((c) => ({ id: c.id, posicion: c.posicion })),
          );
          registro.porZona[zona.id].kmDinamica += ruta.km;
          if (contar) {
            for (const c of elegidos) {
              const nivel = nivelDinamica.get(c.id) as number;
              visitasDinamica++;
              visitasZona[zona.id]++;
              if (nivel < UMBRAL_BAJO) visitasBajasDinamica++;
              if (nivel >= UMBRAL_BAJO && nivel < 100) justasZona[zona.id]++;
            }
          }
          elegidos.forEach((c) => nivelDinamica.set(c.id, 3));
        }

        // Llenado promedio de la zona a esta hora (para el gráfico y el mapa de calor)
        if (ultimos30) {
          const promedio = lista.reduce((suma, c) => suma + (nivelDinamica.get(c.id) as number), 0) / lista.length;
          sumaCalor[zona.id][dia][hora] += promedio;
          cuentaCalor[zona.id][dia][hora] += 1;
          sumaPorHora[hora] += promedio;
        }
      }
      if (ultimos30) muestrasPorHora += ZONAS.length;
    }

    for (const zona of ZONAS) {
      registro.kmFija += registro.porZona[zona.id].kmFija;
      registro.kmDinamica += registro.porZona[zona.id].kmDinamica;
    }
    if (contar) {
      desbordesPorDia.push({ fija: desbordadoFija.size, dinamica: desbordadoDinamica.size });
      dias.push(registro);
    }
  }

  const ultimosDias = dias.slice(-DIAS_MOSTRADOS);
  const kmEvitadosPorZona: Record<number, number> = {};
  ZONAS.forEach((z) => {
    kmEvitadosPorZona[z.id] = ultimosDias.reduce(
      (suma, dia) => suma + (dia.porZona[z.id].kmFija - dia.porZona[z.id].kmDinamica),
      0,
    );
  });

  const eficienciaPorZona: Record<number, number> = {};
  ZONAS.forEach((z) => {
    eficienciaPorZona[z.id] = visitasZona[z.id] ? (justasZona[z.id] / visitasZona[z.id]) * 100 : 0;
  });

  const semanas: SemanaHistorial[] = [];
  for (let s = 0; s < SEMANAS_MOSTRADAS; s++) {
    const bloque = desbordesPorDia.slice(s * 7, s * 7 + 7);
    semanas.push({
      etiqueta: `S${s + 1}`,
      desbordesFija: bloque.reduce((suma, x) => suma + x.fija, 0),
      desbordesDinamica: bloque.reduce((suma, x) => suma + x.dinamica, 0),
    });
  }

  const mapaCalor: Record<number, number[][]> = {};
  ZONAS.forEach((z) => {
    mapaCalor[z.id] = sumaCalor[z.id].map((fila, dia) =>
      fila.map((suma, hora) => (cuentaCalor[z.id][dia][hora] > 0 ? suma / cuentaCalor[z.id][dia][hora] : 0)),
    );
  });

  return {
    dias: ultimosDias,
    semanas,
    porcentajeVisitasBajasFija: visitasFija ? (visitasBajasFija / visitasFija) * 100 : 0,
    porcentajeVisitasBajasDinamica: visitasDinamica ? (visitasBajasDinamica / visitasDinamica) * 100 : 0,
    kmEvitadosPorZona,
    eficienciaPorZona,
    llenadoPorHora: sumaPorHora.map((suma) => (muestrasPorHora ? (suma * 24) / muestrasPorHora : 0)),
    mapaCalor,
  };
}

let guardado: Historial | null = null;

// El historial se calcula una sola vez y se reutiliza.
export function obtenerHistorial(): Historial {
  if (!guardado) guardado = generarHistorial(crearContenedores(2026));
  return guardado;
}
