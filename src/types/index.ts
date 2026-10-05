export interface Coordenada {
  lat: number;
  lng: number;
}

export interface Zona {
  id: number;
  empresa: string;
  centro: Coordenada;
  base: Coordenada; // playón desde el que salen los camiones
  calles: string[];
}

export type NivelLlenado = 'bajo' | 'medio' | 'prioritario' | 'desborde';

export interface Contenedor {
  id: string;
  direccion: string;
  zonaId: number;
  posicion: Coordenada;
  velocidad: number; // % de llenado por hora en condiciones normales
  nivelReal: number; // lo que realmente hay dentro (el sensor no lo ve directo)
  llenado: number; // última lectura del sensor, en %
  lecturas: number[]; // últimas lecturas, la más nueva al final
  ultimaLecturaMs: number;
  irregularHasta: number; // tick hasta el que se simula una persona revolviendo
  anomalia: boolean; // el sistema detectó lecturas irregulares
  reportesVecinales: number; // reportes abiertos hechos por vecinos
}

export type EstadoCamion = 'en_ruta' | 'en_descarga' | 'en_base' | 'demorado';

export type EstadoParada = 'pendiente' | 'recolectada' | 'no_se_pudo';

export interface Parada {
  contenedorId: string;
  estado: EstadoParada;
  etaMs: number; // hora estimada de llegada
  horaRealMs?: number; // hora en la que se recolectó
  motivo?: string; // por qué no se pudo recolectar
}

export interface Camion {
  id: string;
  numero: number;
  zonaId: number;
  chofer: string;
  estado: EstadoCamion;
  paradas: Parada[];
  kmPlanificados: number; // largo total de la ruta actual, con vuelta a la base
  kmRecorridosHoy: number;
  cargaPct: number;
  posicion: Coordenada;
  minutosDisponibles: number;
  ticksDescarga: number;
}

export type TipoIncidente = 'llenado_irregular' | 'no_se_pudo' | 'reporte_vecinal';
export type EstadoIncidente = 'pendiente' | 'confirmado' | 'descartado' | 'resuelto';

export interface Incidente {
  id: string;
  tipo: TipoIncidente;
  estado: EstadoIncidente;
  contenedorId: string;
  zonaId: number;
  direccion: string;
  descripcion: string;
  sugerencia: string;
  creadoMs: number;
  camionNumero?: number;
  origen?: string; // por ejemplo "Boti" en los reportes vecinales
}

export interface EstadoSimulacion {
  semilla: number;
  tick: number;
  relojMs: number; // hora simulada
  inicioDiaMs: number;
  contenedores: Contenedor[];
  camiones: Camion[];
  incidentes: Incidente[];
  recolectadosHoy: number;
  planificadosHoy: number;
  kmEvitadosHoy: number; // km que se ahorraron frente a visitar todos los contenedores
  contadorIncidentes: number;
}
