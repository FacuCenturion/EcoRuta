import { describe, expect, it } from 'vitest';
import {
  aCamion,
  aContenedor,
  aIncidente,
  vistaDeContenedores,
  type CamionApi,
  type ContenedorApi,
} from './adaptadores';
import { textoDeDesborde } from '../logic/prioridad';
import { separarEventos } from './cliente';

const contenedorApi: ContenedorApi = {
  id: 'SNI-05',
  direccion: 'Florida 450',
  barrio: 'San Nicolás',
  zonaId: 1,
  sectorId: 1,
  posicion: { lat: -34.6, lng: -58.375 },
  velocidad: 4.2,
  llenado: 71,
  nivel: 'prioritario',
  motivo: 'riesgo',
  anomalia: false,
  estadoSensor: 'trabado',
  nivelEstimado: 88,
  reportesVecinales: 1,
  ultimaLectura: '2026-10-19T17:05:00-03:00',
  ultimaRecoleccion: null,
  desbordeEstimado: '2026-10-19T19:00:00-03:00',
  desbordeDesde: null,
};

describe('adaptadores de la API', () => {
  it('el contenedor usa el sector para el filtro y conserva lo que calculó el backend', () => {
    const c = aContenedor(contenedorApi);
    expect(c.areaId).toBe(1);
    expect(c.zonaId).toBe(1);
    expect(c.motivo).toBe('riesgo');
    expect(c.nivel).toBe('prioritario');
    expect(c.nivelEstimado).toBe(88);
    expect(c.ultimaLecturaMs).toBe(Date.parse('2026-10-19T20:05:00Z'));
    expect(c.ultimaRecoleccionMs).toBe(0);
  });

  it('un contenedor sin estimación no trae nivelEstimado (el sensor anda)', () => {
    const c = aContenedor({ ...contenedorApi, estadoSensor: 'funcionando', nivelEstimado: null, motivo: null });
    expect(c.nivelEstimado).toBeUndefined();
    expect(c.motivo).toBeNull();
  });

  it('las paradas del camión quedan en el orden de la ruta', () => {
    const camion: CamionApi = {
      id: 'C40',
      numero: 40,
      zonaId: 1,
      sectorId: 1,
      chofer: null,
      estado: 'en_ruta',
      rutaId: 3,
      cargaPct: 20,
      posicion: { lat: -34.6, lng: -58.37 },
      kmRecorridosHoy: 3.2,
      kmPlanificados: 6,
      paradas: [
        {
          id: 2,
          contenedorId: 'B',
          direccion: 'B 1',
          orden: 2,
          estado: 'pendiente',
          motivoVisita: 'de_paso',
          eta: '2026-10-19T18:00:00-03:00',
          horaReal: null,
          motivo: null,
        },
        {
          id: 1,
          contenedorId: 'A',
          direccion: 'A 1',
          orden: 1,
          estado: 'recolectada',
          motivoVisita: 'llenado',
          eta: '2026-10-19T17:40:00-03:00',
          horaReal: '2026-10-19T17:42:00-03:00',
          motivo: null,
        },
      ],
    };
    const c = aCamion(camion);
    expect(c.paradas.map((p) => p.contenedorId)).toEqual(['A', 'B']);
    expect(c.paradas[0].horaRealMs).toBe(Date.parse('2026-10-19T20:42:00Z'));
    expect(c.paradas[1].horaRealMs).toBeUndefined();
    expect(c.chofer).toBe('Sin chofer asignado');
    expect(c.areaId).toBe(1);
    expect(c.rutaId).toBe(3);
  });

  it('el id del incidente pasa a texto y uno sin contenedor no rompe la pantalla', () => {
    const i = aIncidente({
      id: 12,
      tipo: 'no_se_pudo',
      estado: 'pendiente',
      contenedorId: null,
      zonaId: 1,
      sectorId: 3,
      direccion: null,
      descripcion: 'Obra',
      sugerencia: 'Sumar a la próxima ronda',
      creado: '2026-10-19T17:00:00-03:00',
      camionNumero: 14,
      origen: null,
    });
    expect(i.id).toBe('12');
    expect(i.areaId).toBe(3);
    expect(i.direccion).toBe('Sin contenedor asociado');
    expect(i.origen).toBeUndefined();
  });

  it('el pronóstico de desborde se cuenta en palabras', () => {
    const reloj = Date.parse('2026-10-19T17:05:00-03:00');
    const c = aContenedor(contenedorApi);
    expect(c.desbordeEstimadoMs).toBe(Date.parse('2026-10-19T19:00:00-03:00'));
    expect(textoDeDesborde(c, reloj)).toMatch(/^Desborda a las .+ \(estimado\)$/);
    expect(textoDeDesborde({ ...c, desbordeEstimadoMs: reloj + 30 * 3600_000 }, reloj)).toMatch(
      /^Desborda mañana a las/,
    );
    const lleno = aContenedor({ ...contenedorApi, desbordeDesde: '2026-10-19T15:05:00-03:00' });
    expect(textoDeDesborde(lleno, reloj)).toBe('Desbordado hace 2 h');
    expect(textoDeDesborde(aContenedor({ ...contenedorApi, desbordeEstimado: null }), reloj)).toBeNull();
  });

  it('el mapa se encuadra en el centro de los contenedores', () => {
    const a = aContenedor({ ...contenedorApi, posicion: { lat: -34.6, lng: -58.38 } });
    const b = aContenedor({ ...contenedorApi, posicion: { lat: -34.62, lng: -58.36 } });
    const vista = vistaDeContenedores([a, b], 15);
    expect(vista?.lat).toBeCloseTo(-34.61);
    expect(vista?.lng).toBeCloseTo(-58.37);
    expect(vistaDeContenedores([], 15)).toBeNull();
  });
});

describe('avisos del servidor (SSE)', () => {
  it('separa los eventos completos y guarda lo que falta', () => {
    const { eventos, resto } = separarEventos('event:actualizacion\ndata:{"reloj":"x"}\n\n:latido\n\nevent:actua');
    expect(eventos).toEqual(['actualizacion']);
    expect(resto).toBe('event:actua');

    const siguiente = separarEventos(resto + 'lizacion\ndata:{}\n\n');
    expect(siguiente.eventos).toEqual(['actualizacion']);
    expect(siguiente.resto).toBe('');
  });

  it('acepta fines de línea de Windows y el espacio después de los dos puntos', () => {
    expect(separarEventos('event: actualizacion\r\ndata: {}\r\n\r\n').eventos).toEqual(['actualizacion']);
  });

  it('los latidos no cuentan como aviso', () => {
    expect(separarEventos(':latido\n\n').eventos).toEqual([]);
  });
});
