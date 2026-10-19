import { HORAS_SIN_SENAL, HORAS_VALOR_CONGELADO, NIVEL_LLENO, NIVEL_TRAS_RECOLECCION } from '../config';
import type { Contenedor, EstadoSensor, LecturaSensor } from '../types';
import { diaDeSemana, incrementoPorHora } from './llenado';

const MS_POR_HORA = 60 * 60 * 1000;

export const ETIQUETA_ESTADO_SENSOR: Record<EstadoSensor, string> = {
  funcionando: 'Funcionando',
  irregular: 'Lecturas irregulares',
  congelado: 'Valor congelado',
  sin_senal: 'Sin señal',
  trabado: 'Trabado',
};

export const DETALLE_ESTADO_SENSOR: Record<EstadoSensor, string> = {
  funcionando: 'Informa con normalidad',
  irregular: 'Sube y baja de golpe, puede haber alguien revolviendo',
  congelado: `Marca lo mismo hace más de ${HORAS_VALOR_CONGELADO} h`,
  sin_senal: `No manda datos hace más de ${HORAS_SIN_SENAL} h`,
  trabado: 'Lo vaciaron y siguió marcando lo mismo que antes',
};

// Si el sensor marca exactamente el mismo valor durante varias horas,
// probablemente está trabado. Un contenedor siempre se llena un poco, salvo
// que ya esté lleno: en ese caso es normal que no cambie.
export function estaCongelado(historial: LecturaSensor[], relojMs: number): boolean {
  const desde = relojMs - HORAS_VALOR_CONGELADO * MS_POR_HORA;
  // Hace falta tener datos de toda la ventana para poder decir algo
  if (historial.length === 0 || historial[0].ms > desde) return false;

  const ventana = historial.filter((l) => l.ms >= desde);
  if (ventana.length < 2) return false;
  const primero = ventana[0].nivel;
  if (primero >= NIVEL_LLENO) return false;
  return ventana.every((l) => l.nivel === primero);
}

// Clasifica el sensor usando solo sus lecturas. El orden importa:
// si no manda nada, no tiene sentido analizar qué manda.
export function estadoDelSensor(contenedor: Contenedor, relojMs: number): EstadoSensor {
  if (relojMs - contenedor.ultimaLecturaMs > HORAS_SIN_SENAL * MS_POR_HORA) return 'sin_senal';
  if (estaCongelado(contenedor.historial, relojMs)) return 'congelado';
  if (contenedor.anomalia) return 'irregular';
  return 'funcionando';
}

export function sensorConfiable(estado: EstadoSensor): boolean {
  return estado === 'funcionando' || estado === 'irregular';
}

// Última lectura que todavía sirve. Si el sensor quedó congelado, la que
// vale es la primera de la racha: desde ahí dejó de medir.
export function ultimaLecturaValida(contenedor: Contenedor): LecturaSensor | undefined {
  const { historial } = contenedor;
  if (historial.length === 0) return undefined;
  if (contenedor.estadoSensor !== 'congelado') return historial[historial.length - 1];

  let i = historial.length - 1;
  while (i > 0 && historial[i - 1].nivel === historial[i].nivel) i--;
  return historial[i];
}

// Suma lo que se habría llenado el contenedor entre dos momentos, hora por hora,
// con el mismo modelo de llenado que se usa para priorizar.
export function proyectarEntre(nivel: number, velocidad: number, desdeMs: number, hastaMs: number): number {
  let proyectado = nivel;
  let momento = desdeMs;
  while (momento < hastaMs) {
    const tramo = Math.min(MS_POR_HORA, hastaMs - momento);
    const fecha = new Date(momento);
    proyectado += incrementoPorHora(velocidad, fecha.getHours(), diaDeSemana(fecha)) * (tramo / MS_POR_HORA);
    momento += tramo;
  }
  return Math.min(100, proyectado);
}

// Desde dónde se estima: la última lectura buena o, si después un chofer
// confirmó que vació el contenedor, el momento de esa recolección.
export function puntoDePartida(contenedor: Contenedor, relojMs: number): LecturaSensor {
  const lectura = ultimaLecturaValida(contenedor) ?? { ms: relojMs, nivel: contenedor.llenado };
  if (contenedor.ultimaRecoleccionMs > lectura.ms) {
    return { ms: contenedor.ultimaRecoleccionMs, nivel: NIVEL_TRAS_RECOLECCION };
  }
  return lectura;
}

// Nivel estimado de un contenedor con el sensor caído o trabado
export function nivelEstimado(contenedor: Contenedor, relojMs: number): number {
  const partida = puntoDePartida(contenedor, relojMs);
  return Math.round(proyectarEntre(partida.nivel, contenedor.velocidad, partida.ms, relojMs));
}

// Puntos de la estimación para dibujarla en el gráfico, cada media hora
export function curvaEstimada(contenedor: Contenedor, relojMs: number): LecturaSensor[] {
  const partida = puntoDePartida(contenedor, relojMs);
  const paso = MS_POR_HORA / 2;
  const puntos: LecturaSensor[] = [partida];
  for (let ms = partida.ms + paso; ms < relojMs; ms += paso) {
    puntos.push({ ms, nivel: Math.round(proyectarEntre(partida.nivel, contenedor.velocidad, partida.ms, ms)) });
  }
  puntos.push({ ms: relojMs, nivel: nivelEstimado(contenedor, relojMs) });
  return puntos;
}

// Recalcula el estado del sensor y, si hace falta, el nivel estimado.
// Lo llama el simulador después de cada lectura (o de cada lectura que no llegó).
export function actualizarSensor(contenedor: Contenedor, relojMs: number): void {
  contenedor.estadoSensor = estadoDelSensor(contenedor, relojMs);
  contenedor.nivelEstimado = sensorConfiable(contenedor.estadoSensor) ? undefined : nivelEstimado(contenedor, relojMs);
}

export interface ResumenSensores {
  total: number;
  porEstado: Record<EstadoSensor, number>;
  porcentajeOperativo: number; // funcionando o con lecturas irregulares (el sensor anda)
}

export function resumenSensores(contenedores: Contenedor[]): ResumenSensores {
  const porEstado: Record<EstadoSensor, number> = {
    funcionando: 0,
    irregular: 0,
    congelado: 0,
    sin_senal: 0,
    trabado: 0,
  };
  contenedores.forEach((c) => {
    porEstado[c.estadoSensor] += 1;
  });
  const operativos = porEstado.funcionando + porEstado.irregular;
  return {
    total: contenedores.length,
    porEstado,
    porcentajeOperativo: contenedores.length ? Math.round((operativos / contenedores.length) * 100) : 0,
  };
}
