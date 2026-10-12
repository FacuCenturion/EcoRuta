import type { Camion, Contenedor, Coordenada, Zona } from '../../types';

export interface Vista {
  lat: number;
  lng: number;
  zoom: number;
}

// Datos que reciben todos los mapas, sin importar la librería que usen
export interface PropsMapa {
  contenedores: Contenedor[];
  camiones?: Camion[];
  relojMs: number;
  vista: Vista;
  seleccionadoId?: string;
  recorridoHecho?: Coordenada[];
  recorridoPendiente?: Coordenada[];
  zonas?: Zona[]; // zonas que se dibujan con su contorno
  className?: string;
}
