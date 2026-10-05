import { KG_CO2_POR_KM } from '../config';
import { buscarZona } from '../data/zonas';
import type { Camion, EstadoSimulacion } from '../types';
import { diaDeSemana } from './llenado';
import { motivoDelContenedor } from './prioridad';
import { planificarRuta } from './rutas';

export function kgCo2(km: number): number {
  return km * KG_CO2_POR_KM;
}

export function esAbierto(estado: string): boolean {
  return estado === 'pendiente' || estado === 'confirmado';
}

export function contenedoresPrioritarios(estado: EstadoSimulacion, zonaId?: number) {
  const fecha = new Date(estado.relojMs);
  const hora = fecha.getHours();
  const dia = diaDeSemana(fecha);
  return estado.contenedores
    .filter((c) => zonaId === undefined || c.zonaId === zonaId)
    .map((c) => ({ contenedor: c, motivo: motivoDelContenedor(c, hora, dia) }))
    .filter((x) => x.motivo !== null) as {
    contenedor: EstadoSimulacion['contenedores'][number];
    motivo: NonNullable<ReturnType<typeof motivoDelContenedor>>;
  }[];
}

export function avanceDeRuta(camion: Camion): { hechas: number; total: number } {
  const hechas = camion.paradas.filter((p) => p.estado !== 'pendiente').length;
  return { hechas, total: camion.paradas.length };
}

// Km que se hubieran recorrido visitando todos los contenedores de la zona.
export function kmRutaFija(estado: EstadoSimulacion, zonaId: number): number {
  const puntos = estado.contenedores
    .filter((c) => c.zonaId === zonaId)
    .map((c) => ({ id: c.id, posicion: c.posicion }));
  return planificarRuta(buscarZona(zonaId).base, puntos).km;
}

export function llenadoPromedio(estado: EstadoSimulacion, zonaId?: number): number {
  const lista = estado.contenedores.filter((c) => zonaId === undefined || c.zonaId === zonaId);
  if (lista.length === 0) return 0;
  return lista.reduce((suma, c) => suma + c.nivelReal, 0) / lista.length;
}
