import { describe, expect, it } from 'vitest';
import { crearContenedores } from '../data/semilla';
import { avanzar, crearEstadoInicial, decidirIncidente } from '../simulator/simulador';
import { detectarAnomalia } from './anomalias';
import { generarHistorial } from './historial';
import { clasificarNivel, motivoDePrioridad } from './prioridad';
import { distanciaKm, planificarRuta } from './rutas';

describe('prioridad', () => {
  it('clasifica el nivel de llenado según los umbrales', () => {
    expect(clasificarNivel(30)).toBe('bajo');
    expect(clasificarNivel(65)).toBe('medio');
    expect(clasificarNivel(80)).toBe('prioritario');
    expect(clasificarNivel(97)).toBe('desborde');
  });

  it('marca como prioritario un contenedor que supera el 80%', () => {
    expect(motivoDePrioridad(82, 3, 10, 1, 0)).toBe('llenado');
  });

  it('marca riesgo de desborde si no llega a la próxima ronda', () => {
    // A las 17 la próxima ronda es a las 20 y el pico de llenado está en el medio
    expect(motivoDePrioridad(70, 8, 17, 1, 0)).toBe('riesgo');
  });

  it('no marca un contenedor casi vacío', () => {
    expect(motivoDePrioridad(20, 3, 10, 1, 0)).toBeNull();
  });

  it('un reporte vecinal alcanza para priorizar', () => {
    expect(motivoDePrioridad(30, 3, 10, 1, 1)).toBe('reporte');
  });
});

describe('rutas', () => {
  it('calcula una distancia razonable entre dos puntos', () => {
    const obelisco = { lat: -34.6037, lng: -58.3816 };
    const congreso = { lat: -34.6099, lng: -58.3926 };
    const d = distanciaKm(obelisco, congreso);
    expect(d).toBeGreaterThan(1);
    expect(d).toBeLessThan(1.5);
  });

  it('visita primero el punto más cercano y vuelve a la base', () => {
    const base = { lat: 0, lng: 0 };
    const ruta = planificarRuta(base, [
      { id: 'lejos', posicion: { lat: 0, lng: 0.02 } },
      { id: 'cerca', posicion: { lat: 0, lng: 0.005 } },
    ]);
    expect(ruta.orden).toEqual(['cerca', 'lejos']);
    expect(ruta.km).toBeCloseTo(2 * distanciaKm(base, { lat: 0, lng: 0.02 }), 3);
  });

  it('sin puntos no hay recorrido', () => {
    expect(planificarRuta({ lat: 0, lng: 0 }, [])).toEqual({ orden: [], km: 0 });
  });
});

describe('anomalías', () => {
  it('no detecta nada en un llenado normal', () => {
    expect(detectarAnomalia([20, 21, 22, 23, 25, 26])).toBe(false);
  });

  it('no confunde un vaciado con una anomalía', () => {
    expect(detectarAnomalia([70, 75, 80, 4, 5, 6])).toBe(false);
  });

  it('detecta lecturas que suben y bajan de golpe', () => {
    expect(detectarAnomalia([52, 61, 49, 62, 48, 60])).toBe(true);
  });
});

describe('simulador', () => {
  it('arranca con camiones en ruta y algo para revisar', () => {
    const estado = crearEstadoInicial();
    expect(estado.contenedores).toHaveLength(7 * 18);
    expect(estado.camiones.some((c) => c.estado === 'en_ruta')).toBe(true);
    expect(estado.incidentes.some((i) => i.estado === 'pendiente')).toBe(true);
  });

  it('al avanzar el tiempo los camiones recolectan contenedores', () => {
    let estado = crearEstadoInicial();
    for (let i = 0; i < 40; i++) estado = avanzar(estado);
    expect(estado.recolectadosHoy).toBeGreaterThan(0);
    estado.contenedores.forEach((c) => {
      expect(c.nivelReal).toBeGreaterThanOrEqual(0);
      expect(c.nivelReal).toBeLessThanOrEqual(100);
    });
  });

  it('suma los km evitados del día cuando se arman las rutas', () => {
    const estado = crearEstadoInicial();
    expect(estado.kmEvitadosHoy).toBeGreaterThan(0);
  });

  it('el operador puede confirmar y descartar incidentes', () => {
    const estado = crearEstadoInicial();
    const [primero, segundo] = estado.incidentes.filter((i) => i.estado === 'pendiente');
    const confirmado = decidirIncidente(estado, primero.id, 'confirmado');
    const descartado = decidirIncidente(confirmado, segundo.id, 'descartado');
    expect(descartado.incidentes.find((i) => i.id === primero.id)?.estado).toBe('confirmado');
    expect(descartado.incidentes.find((i) => i.id === segundo.id)?.estado).toBe('descartado');
  });
});

describe('historial', () => {
  const historial = generarHistorial(crearContenedores(2026));

  it('la ruta dinámica recorre menos km que la fija', () => {
    const fija = historial.dias.reduce((s, d) => s + d.kmFija, 0);
    const dinamica = historial.dias.reduce((s, d) => s + d.kmDinamica, 0);
    expect(dinamica).toBeLessThan(fija);
  });

  it('la ruta dinámica tiene menos desbordes que la fija', () => {
    const fija = historial.semanas.reduce((s, x) => s + x.desbordesFija, 0);
    const dinamica = historial.semanas.reduce((s, x) => s + x.desbordesDinamica, 0);
    expect(dinamica).toBeLessThan(fija);
  });

  it('el índice de eficiencia de cada zona está entre 0 y 100', () => {
    Object.values(historial.eficienciaPorZona).forEach((indice) => {
      expect(indice).toBeGreaterThan(0);
      expect(indice).toBeLessThanOrEqual(100);
    });
  });

  it('genera 30 días y 8 semanas', () => {
    expect(historial.dias).toHaveLength(30);
    expect(historial.semanas).toHaveLength(8);
  });
});

describe('indicadores del día', () => {
  it('acumula km evitados al planificar las rutas', () => {
    const estado = crearEstadoInicial();
    expect(estado.kmEvitadosHoy).toBeGreaterThan(0);
  });

  it('el índice de eficiencia de cada zona está entre 0 y 100', () => {
    const historial = generarHistorial(crearContenedores(2026));
    Object.values(historial.eficienciaPorZona).forEach((indice) => {
      expect(indice).toBeGreaterThan(0);
      expect(indice).toBeLessThanOrEqual(100);
    });
  });
});
