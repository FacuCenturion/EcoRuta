// Lo que manda el backend en /api/v1/chofer (ver backend/README.md)

export interface Coordenada {
  lat: number;
  lng: number;
}

export type EstadoParada = 'pendiente' | 'recolectada' | 'no_se_pudo';
export type EstadoRuta = 'programada' | 'en_curso' | 'terminada';
export type EstadoCamion = 'en_base' | 'en_ruta' | 'en_descarga' | 'demorado';

export interface Parada {
  id: number;
  contenedorId: string;
  direccion: string;
  posicion: Coordenada;
  orden: number;
  estado: EstadoParada;
  motivoVisita: string | null; // llenado, riesgo, reporte, tiempo o de_paso
  eta: string | null;
  horaReal: string | null;
  motivo: string | null; // por qué no se pudo
  llenado: number;
  tipo: string; // lateral, bilateral o soterrado
  barrio: string;
  ultimaRecoleccion: string | null;
}

export interface Ruta {
  id: number;
  estado: EstadoRuta;
  creada: string;
  salidaProgramada: string | null;
  salidaReal: string | null;
  kmPlanificados: number;
  duracionEstimadaMin: number;
  paradas: Parada[];
}

export interface Propuesta {
  id: number;
  contenedorId: string;
  direccion: string;
  posicion: Coordenada;
  llenado: number;
  orden: number;
  kmExtra: number;
  minutosExtra: number;
  motivo: string;
}

export interface Turno {
  chofer: string;
  camion: {
    id: string;
    numero: number;
    sectorId: number;
    sector: string | null;
    estado: EstadoCamion;
    cargaPct: number;
    posicion: Coordenada;
    kmRecorridosHoy: number;
  };
  manejadoPorApp: boolean;
  base: Coordenada;
  ruta: Ruta | null;
  propuesta: Propuesta | null;
  reloj: string;
}

// Una maniobra de OSRM: dónde doblar y por qué calle se sigue
export interface Indicacion {
  tipo: string; // depart, turn, new name, continue, roundabout, arrive...
  modificador: string | null; // left, right, slight left, sharp right, straight, uturn
  calle: string;
  salida: number | null;
  lat: number;
  lng: number;
  metros: number;
}

export interface Trazado {
  rutaId: number;
  estado: 'listo' | 'calculando' | 'sin_servicio' | 'fallo';
  kmPorCalle: number | null;
  tramos: Coordenada[][];
  indicaciones: Indicacion[][];
}

export interface UsuarioSesion {
  usuario: string;
  nombre: string;
  iniciales: string;
  rol: string;
}
