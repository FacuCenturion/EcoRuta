import type { Camion, Contenedor } from '../types';
import { crearAleatorio, elegir, entre } from '../utils/aleatorio';
import { ZONAS } from './zonas';

const CONTENEDORES_POR_ZONA = 18;
const COLUMNAS = 6;
const SEPARACION_LAT = 0.0011; // aprox. 120 m
const SEPARACION_LNG = 0.0014; // aprox. 125 m

const CHOFERES: Record<number, { numero: number; nombre: string }> = {
  1: { numero: 22, nombre: 'Lucas Benítez' },
  2: { numero: 31, nombre: 'Diego Sosa' },
  3: { numero: 14, nombre: 'Martín Ferreyra' },
  4: { numero: 17, nombre: 'Nicolás Paz' },
  5: { numero: 5, nombre: 'Gabriel Ríos' },
  6: { numero: 26, nombre: 'Hugo Medina' },
  7: { numero: 12, nombre: 'Carlos Luna' },
};

// Crea los contenedores de ejemplo, acomodados como en una grilla de manzanas.
export function crearContenedores(semilla: number): Contenedor[] {
  const azar = crearAleatorio(semilla);
  const lista: Contenedor[] = [];

  for (const zona of ZONAS) {
    for (let i = 0; i < CONTENEDORES_POR_ZONA; i++) {
      const columna = i % COLUMNAS;
      const fila = Math.floor(i / COLUMNAS);
      const posicion = {
        lat: zona.centro.lat + (fila - 1) * SEPARACION_LAT + entre(azar, -0.0002, 0.0002),
        lng: zona.centro.lng + (columna - (COLUMNAS - 1) / 2) * SEPARACION_LNG + entre(azar, -0.0002, 0.0002),
      };
      const calle = elegir(azar, zona.calles);
      const numero = Math.round(entre(azar, 100, 1900) / 50) * 50;

      // Algunos contenedores se llenan bastante más rápido que el resto
      const esCritico = azar() < 0.15;
      const velocidad = esCritico ? entre(azar, 6, 9) : entre(azar, 2.5, 5);

      // Estado inicial: la mayoría a medio llenar y unos pocos casi llenos
      const nivel = azar() < 0.1 ? entre(azar, 90, 99) : entre(azar, 15, 88);

      lista.push({
        id: `Z${zona.id}-${String(i + 1).padStart(2, '0')}`,
        direccion: `${calle} ${numero}`,
        zonaId: zona.id,
        posicion,
        velocidad: Math.round(velocidad * 10) / 10,
        nivelReal: Math.round(nivel),
        llenado: Math.round(nivel),
        lecturas: [Math.round(nivel)],
        ultimaLecturaMs: 0,
        irregularHasta: 0,
        anomalia: false,
        reportesVecinales: 0,
      });
    }
  }
  return lista;
}

// Un camión por zona, todos arrancan en la base.
export function crearCamiones(): Camion[] {
  return ZONAS.map((zona) => {
    const chofer = CHOFERES[zona.id];
    return {
      id: `C${chofer.numero}`,
      numero: chofer.numero,
      zonaId: zona.id,
      chofer: chofer.nombre,
      estado: 'en_base',
      paradas: [],
      kmPlanificados: 0,
      kmRecorridosHoy: 0,
      cargaPct: 0,
      posicion: zona.base,
      minutosDisponibles: 0,
      ticksDescarga: 0,
    };
  });
}
