// Pasa las respuestas del backend a los tipos que ya usan las pantallas. Los DTO del backend
// tienen casi los mismos nombres de campo; lo que cambia son las fechas (ISO en vez de ms)
// y que el filtro del panel usa el sector en vez de la zona.
import type { Vista } from '../components/mapa/tipos';
import type {
  Camion,
  Contenedor,
  Coordenada,
  EstadoCamion,
  EstadoIncidente,
  EstadoParada,
  EstadoSensor,
  Incidente,
  NivelLlenado,
  Parada,
  TipoIncidente,
} from '../types';

// ---------- Lo que manda el backend ----------

export interface ContenedorApi {
  id: string;
  direccion: string;
  barrio: string;
  zonaId: number;
  sectorId: number;
  posicion: Coordenada;
  velocidad: number;
  llenado: number;
  nivel: NivelLlenado;
  motivo: 'llenado' | 'riesgo' | 'reporte' | 'tiempo' | null;
  anomalia: boolean;
  estadoSensor: EstadoSensor;
  nivelEstimado: number | null;
  reportesVecinales: number;
  ultimaLectura: string | null;
  ultimaRecoleccion: string | null;
  desbordeEstimado: string | null;
  desbordeDesde: string | null;
}

export interface ParadaApi {
  id: number;
  contenedorId: string;
  direccion: string;
  orden: number;
  estado: EstadoParada;
  motivoVisita: string | null;
  eta: string | null;
  horaReal: string | null;
  motivo: string | null;
}

export interface CamionApi {
  id: string;
  numero: number;
  zonaId: number;
  sectorId: number;
  chofer: string | null;
  estado: EstadoCamion;
  rutaId: number | null;
  cargaPct: number;
  posicion: Coordenada;
  kmRecorridosHoy: number;
  kmPlanificados: number;
  paradas: ParadaApi[];
}

export interface IncidenteApi {
  id: number;
  tipo: TipoIncidente;
  estado: EstadoIncidente;
  contenedorId: string | null;
  zonaId: number | null;
  sectorId: number | null;
  direccion: string | null;
  descripcion: string;
  sugerencia: string;
  creado: string;
  camionNumero: number | null;
  origen: string | null;
  tieneFoto?: boolean;
}

export interface SimulacionApi {
  reloj: string;
  activa: boolean;
  enPausa: boolean;
  velocidad: number;
}

export interface ResumenApi {
  reloj: string;
  recolectadosHoy: number;
  planificadosHoy: number;
  kmEvitadosHoy: number;
  llenadoPorHora: number[];
  horaPico: number;
}

export interface SectorApi {
  id: number;
  nombre: string;
  barrios: string[];
  empresa: string;
  eficiencia: number | null;
}

export interface CurvaApi {
  lecturas: { medidoAt: string; llenado: number }[];
  estimadas: { medidoAt: string; llenado: number }[];
}

// listo, calculando, sin_servicio (no hay OSRM configurado) o fallo (se reintenta más tarde)
export interface TrazadoApi {
  rutaId: number;
  estado: 'listo' | 'calculando' | 'sin_servicio' | 'fallo';
  kmPorCalle: number | null;
  tramos: Coordenada[][];
}

export interface IndicadoresApi {
  dias: { fecha: string; kmFija: number; kmDinamica: number }[];
  semanas: { etiqueta: string; desbordesFija: number; desbordesDinamica: number }[];
  porcentajeVisitasBajasFija: number;
  porcentajeVisitasBajasDinamica: number;
  eficienciaPorSector: Record<string, number>;
  mapaCalor: Record<string, number[][]>;
  diasSimulados: number;
}

// ---------- Conversiones ----------

export function aMs(fecha: string | null | undefined): number {
  return fecha ? Date.parse(fecha) : 0;
}

export function aContenedor(c: ContenedorApi): Contenedor {
  const ultimaLecturaMs = aMs(c.ultimaLectura);
  return {
    id: c.id,
    direccion: c.direccion,
    barrio: c.barrio,
    zonaId: c.zonaId,
    areaId: c.sectorId,
    posicion: c.posicion,
    velocidad: c.velocidad,
    nivelReal: c.nivelEstimado ?? c.llenado, // el backend no sabe el nivel real: usa lo mejor que tiene
    llenado: c.llenado,
    lecturas: [c.llenado],
    ultimaLecturaMs,
    irregularHasta: 0,
    anomalia: c.anomalia,
    reportesVecinales: c.reportesVecinales,
    historial: ultimaLecturaMs ? [{ ms: ultimaLecturaMs, nivel: c.llenado }] : [], // la curva se pide aparte
    fallaSimulada: 'ninguna',
    estadoSensor: c.estadoSensor,
    nivelEstimado: c.nivelEstimado ?? undefined,
    ultimaRecoleccionMs: aMs(c.ultimaRecoleccion),
    motivo: c.motivo,
    nivel: c.nivel,
    desbordeEstimadoMs: c.desbordeEstimado ? aMs(c.desbordeEstimado) : undefined,
    desbordeDesdeMs: c.desbordeDesde ? aMs(c.desbordeDesde) : undefined,
  };
}

function aParada(p: ParadaApi): Parada {
  return {
    contenedorId: p.contenedorId,
    direccion: p.direccion,
    estado: p.estado,
    motivoVisita: p.motivoVisita ?? undefined,
    etaMs: aMs(p.eta),
    horaRealMs: p.horaReal ? aMs(p.horaReal) : undefined,
    motivo: p.motivo ?? undefined,
  };
}

export function aCamion(c: CamionApi): Camion {
  return {
    id: c.id,
    numero: c.numero,
    zonaId: c.zonaId,
    areaId: c.sectorId,
    rutaId: c.rutaId ?? undefined,
    chofer: c.chofer ?? 'Sin chofer asignado',
    estado: c.estado,
    paradas: [...c.paradas].sort((a, b) => a.orden - b.orden).map(aParada),
    kmPlanificados: c.kmPlanificados,
    kmRecorridosHoy: c.kmRecorridosHoy,
    cargaPct: c.cargaPct,
    posicion: c.posicion,
    minutosDisponibles: 0,
    ticksDescarga: 0,
  };
}

export function aIncidente(i: IncidenteApi): Incidente {
  return {
    id: String(i.id),
    tipo: i.tipo,
    estado: i.estado,
    contenedorId: i.contenedorId ?? '',
    zonaId: i.zonaId ?? 0,
    areaId: i.sectorId ?? 0,
    direccion: i.direccion ?? 'Sin contenedor asociado',
    descripcion: i.descripcion,
    sugerencia: i.sugerencia,
    creadoMs: aMs(i.creado),
    camionNumero: i.camionNumero ?? undefined,
    origen: i.origen ?? undefined,
    tieneFoto: i.tieneFoto ?? false,
  };
}

// Encuadre del mapa para un grupo de contenedores (un sector o toda la zona)
export function vistaDeContenedores(contenedores: Contenedor[], zoom: number): Vista | null {
  if (contenedores.length === 0) return null;
  const lats = contenedores.map((c) => c.posicion.lat);
  const lngs = contenedores.map((c) => c.posicion.lng);
  return {
    lat: (Math.min(...lats) + Math.max(...lats)) / 2,
    lng: (Math.min(...lngs) + Math.max(...lngs)) / 2,
    zoom,
  };
}
