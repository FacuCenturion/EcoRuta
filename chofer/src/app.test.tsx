// @vitest-environment jsdom
// La app del chofer contra un backend falso que responde como el real.
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { separarEventos } from './api';
import type { Parada, Ruta, Turno } from './tipos';

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

// Node 26 trae un localStorage global vacío que tapa al de jsdom: se usa uno en memoria
const guardado = new Map<string, string>();
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: {
    getItem: (clave: string) => guardado.get(clave) ?? null,
    setItem: (clave: string, valor: string) => guardado.set(clave, String(valor)),
    removeItem: (clave: string) => guardado.delete(clave),
    clear: () => guardado.clear(),
  },
});

const API = 'http://localhost:8080/api/v1';
const BASE = { lat: -34.609, lng: -58.37 };

const parada = (id: number, direccion: string, cambios: Partial<Parada> = {}): Parada => ({
  id,
  contenedorId: `SNI-0${id}`,
  direccion,
  posicion: { lat: -34.6 - id / 1000, lng: -58.375 },
  orden: id,
  estado: 'pendiente',
  motivoVisita: id === 2 ? 'de_paso' : 'llenado',
  eta: '2026-10-20T17:45:00-03:00',
  horaReal: null,
  motivo: null,
  llenado: id === 2 ? 60 : 92,
  tipo: 'lateral',
  barrio: 'San Nicolás',
  ultimaRecoleccion: '2026-10-19T21:40:00-03:00',
  ...cambios,
});

const rutaDePrueba = (
  estado: Ruta['estado'],
  paradas = [parada(1, 'Florida 450'), parada(2, 'Viamonte 438'), parada(3, 'Paraguay 548')],
): Ruta => ({
  id: 9,
  estado,
  creada: '2026-10-20T17:20:00-03:00',
  salidaProgramada: '2026-10-20T17:20:00-03:00',
  salidaReal: estado === 'programada' ? null : '2026-10-20T17:22:00-03:00',
  kmPlanificados: 4,
  duracionEstimadaMin: 35,
  paradas,
});

function turnoDePrueba(cambios: Partial<Turno> = {}): Turno {
  return {
    chofer: 'Lucas Benítez',
    camion: {
      id: 'C22',
      numero: 22,
      sectorId: 1,
      sector: 'Retiro y San Nicolás',
      estado: 'en_base',
      cargaPct: 0,
      posicion: BASE,
      kmRecorridosHoy: 0,
    },
    manejadoPorApp: true,
    base: BASE,
    ruta: null,
    propuesta: null,
    reloj: '2026-10-20T17:30:00-03:00',
    ...cambios,
  };
}

let actual: Turno;
let canalSse: ReadableStreamDefaultController<Uint8Array> | null = null;
let pedidos: { url: string; metodo: string; cuerpo?: string }[];
let raiz: Root | null = null;

const avisarDesdeElServidor = () => canalSse?.enqueue(new TextEncoder().encode('event:actualizacion\ndata:{}\n\n'));

function backendFalso(url: string, init?: RequestInit): Promise<Response> {
  const ruta = url.replace(API, '');
  const metodo = init?.method ?? 'GET';
  pedidos.push({ url: ruta, metodo, cuerpo: init?.body as string | undefined });
  if (ruta === '/eventos') {
    const cuerpo = new ReadableStream<Uint8Array>({ start: (c) => void (canalSse = c) });
    return Promise.resolve(new Response(cuerpo, { headers: { 'Content-Type': 'text/event-stream' } }));
  }
  if (ruta === '/auth/login') {
    const { usuario } = JSON.parse(init!.body as string);
    const rol = usuario === 'operador' ? 'operador' : 'chofer';
    return Promise.resolve(Response.json({ token: 't', usuario: { usuario, nombre: 'X', iniciales: 'X', rol } }));
  }
  if (ruta === '/chofer/motivos') return Promise.resolve(Response.json(['Auto estacionado', 'Obra o calle cortada']));
  if (ruta === '/chofer/posicion') return Promise.resolve(new Response(null, { status: 204 }));
  if (ruta === '/chofer/ruta/trazado')
    return Promise.resolve(
      Response.json({ rutaId: 9, estado: 'sin_servicio', kmPorCalle: null, tramos: [], indicaciones: [] }),
    );
  if (ruta === '/chofer/turno/empezar') actual = { ...actual, manejadoPorApp: true };
  if (ruta === '/chofer/ruta/iniciar') actual = { ...actual, ruta: rutaDePrueba('en_curso') };
  return Promise.resolve(Response.json(actual));
}

async function mostrar() {
  const { default: App } = await import('./App');
  const div = document.createElement('div');
  document.body.appendChild(div);
  raiz = createRoot(div);
  await act(async () => raiz!.render(<App />));
  await esperar();
  return div;
}

const esperar = (ms = 30) => act(() => new Promise((listo) => setTimeout(listo, ms)));
const boton = (div: HTMLElement, texto: string) =>
  [...div.querySelectorAll('button')].find((b) => b.textContent?.replace(/\s+/g, ' ').trim().startsWith(texto));
const tocar = (div: HTMLElement, texto: string) =>
  act(async () => {
    const b = boton(div, texto);
    if (!b) throw new Error(`No está el botón "${texto}"`);
    b.click();
  });
const posts = () => pedidos.filter((p) => p.metodo === 'POST' && p.url !== '/chofer/posicion');

beforeEach(() => {
  vi.resetModules();
  pedidos = [];
  actual = turnoDePrueba();
  vi.stubGlobal('fetch', vi.fn(backendFalso));
  localStorage.setItem(
    'ecoruta.chofer.sesion',
    JSON.stringify({
      token: 'token-chofer',
      usuario: { usuario: 'chofer22', nombre: 'Lucas', iniciales: 'LB', rol: 'chofer' },
    }),
  );
});

afterEach(() => {
  act(() => raiz?.unmount());
  raiz = null;
  document.body.innerHTML = '';
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe('app del chofer', () => {
  it('sin turno empezado ofrece empezarlo', async () => {
    actual = turnoDePrueba({ manejadoPorApp: false });
    const div = await mostrar();
    expect(div.textContent).toContain('Hola, Lucas');
    await tocar(div, 'Empezar turno');
    expect(posts()[0]?.url).toBe('/chofer/turno/empezar');
    expect(div.textContent).toContain('Sin ruta por ahora');
  });

  it('el inicio de jornada muestra la ruta y al empezarla pasa a la navegación', async () => {
    actual = turnoDePrueba({ ruta: rutaDePrueba('programada') });
    const div = await mostrar();
    expect(div.querySelector('h1')?.textContent).toBe('Tu ruta de hoy');
    expect(div.textContent).toContain('Sector 1, residuos generales');
    expect(div.textContent).toContain('2 prioritarias');
    expect(div.textContent).toContain('Florida 450');
    expect(div.textContent).toContain('92%');
    await tocar(div, 'Empezar ruta');
    expect(posts()[0]?.url).toBe('/chofer/ruta/iniciar');
    expect(div.textContent).toContain('Próxima parada');
    expect(div.textContent).toContain('0 de 3 recolectados');
  });

  it('al llegar al contenedor muestra sus datos y "recolectado" se puede deshacer', async () => {
    actual = turnoDePrueba({
      ruta: rutaDePrueba('en_curso'),
      camion: { ...turnoDePrueba().camion, posicion: parada(1, '').posicion },
    });
    const div = await mostrar();
    expect(div.querySelector('h1')?.textContent).toBe('Llegaste');
    expect(div.textContent).toContain('Florida 450');
    expect(div.textContent).toContain('Alta');
    expect(div.textContent).toContain('ayer');
    await tocar(div, 'Marcar como recolectado');
    expect(div.textContent).toContain('Parada 1 recolectada');
    await tocar(div, 'Deshacer');
    await esperar();
    expect(posts()).toHaveLength(0);
    expect(div.querySelector('h1')?.textContent).toBe('Llegaste');
  });

  it('si la app se va a segundo plano, el recolectado se manda sin esperar', async () => {
    actual = turnoDePrueba({
      ruta: rutaDePrueba('en_curso'),
      camion: { ...turnoDePrueba().camion, posicion: parada(1, '').posicion },
    });
    const div = await mostrar();
    await tocar(div, 'Marcar como recolectado');
    Object.defineProperty(document, 'hidden', { configurable: true, value: true });
    await act(async () => document.dispatchEvent(new Event('visibilitychange')));
    Object.defineProperty(document, 'hidden', { configurable: true, value: false });
    await esperar();
    expect(posts()[0]?.url).toBe('/chofer/paradas/1/recolectar');
  });

  it('"no se pudo" pide el motivo y lo manda', async () => {
    actual = turnoDePrueba({
      ruta: rutaDePrueba('en_curso'),
      camion: { ...turnoDePrueba().camion, posicion: parada(1, '').posicion },
    });
    const div = await mostrar();
    await tocar(div, 'No se pudo recolectar');
    await esperar();
    expect(div.querySelector('h1')?.textContent).toBe('No se pudo');
    expect(boton(div, 'Enviar y seguir ruta')!.disabled).toBe(true);
    await tocar(div, 'Obra o calle cortada');
    await tocar(div, 'Enviar y seguir ruta');
    await esperar();
    expect(posts()[0]?.url).toBe('/chofer/paradas/1/no-se-pudo');
    expect(JSON.parse(posts()[0].cuerpo!)).toMatchObject({ motivo: 'Obra o calle cortada' });
  });

  it('una parada nueva prioritaria se puede rechazar', async () => {
    actual = turnoDePrueba({
      ruta: rutaDePrueba('en_curso'),
      propuesta: {
        id: 4,
        contenedorId: 'SNI-09',
        direccion: 'Chile 640',
        posicion: { lat: -34.61, lng: -58.38 },
        llenado: 95,
        orden: 2,
        kmExtra: 1.2,
        minutosExtra: 4,
        motivo: 'llenado',
      },
    });
    const div = await mostrar();
    expect(div.textContent).toContain('Nueva parada prioritaria');
    expect(div.textContent).toContain('El contenedor de Chile 640 llegó al 95%');
    expect(div.textContent).toContain('+4 min');
    expect(div.textContent).toContain('parada 2');
    await tocar(div, 'Mantener ruta anterior');
    expect(posts()[0]?.url).toBe('/chofer/propuestas/4/rechazar');
  });

  it('al terminar la ruta muestra el resumen con los problemas', async () => {
    actual = turnoDePrueba({
      ruta: rutaDePrueba('terminada', [
        parada(1, 'Florida 450', { estado: 'recolectada', horaReal: '2026-10-20T17:40:00-03:00' }),
        parada(2, 'Tacuarí 1340', {
          estado: 'no_se_pudo',
          motivo: 'Auto estacionado',
          horaReal: '2026-10-20T18:06:00-03:00',
        }),
      ]),
    });
    const div = await mostrar();
    expect(div.querySelector('h1')?.textContent).toBe('Ruta terminada');
    expect(div.textContent).toContain('1 de 2 recolectados');
    expect(div.textContent).toContain('44 min');
    expect(div.textContent).toContain('Auto estacionado, quedó para el operador');
    expect(boton(div, 'Volver a la base')).toBeDefined();
  });

  it('cuando el sistema asigna una ruta con la app abierta, aparece sola y el celular vibra', async () => {
    const vibrar = vi.fn();
    Object.defineProperty(navigator, 'vibrate', { configurable: true, value: vibrar });
    const div = await mostrar();
    expect(div.textContent).toContain('Sin ruta por ahora');
    actual = turnoDePrueba({ ruta: rutaDePrueba('programada') });
    await act(async () => avisarDesdeElServidor());
    await esperar();
    expect(div.querySelector('h1')?.textContent).toBe('Tu ruta de hoy');
    expect(vibrar).toHaveBeenCalled();
  });

  it('una ruta que ya estaba al abrir la app no se anuncia', async () => {
    const vibrar = vi.fn();
    Object.defineProperty(navigator, 'vibrate', { configurable: true, value: vibrar });
    actual = turnoDePrueba({ ruta: rutaDePrueba('programada') });
    await mostrar();
    expect(vibrar).not.toHaveBeenCalled();
  });

  it('cada pedido lleva el token del chofer', async () => {
    await mostrar();
    for (const [, init] of vi.mocked(fetch).mock.calls) {
      expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token-chofer');
    }
  });

  it('alguien del panel no puede entrar a la app', async () => {
    localStorage.clear();
    const div = await mostrar();
    const [usuario, contrasena] = [...div.querySelectorAll('input')];
    const escribir = (campo: HTMLInputElement, valor: string) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!.call(campo, valor);
      campo.dispatchEvent(new Event('input', { bubbles: true }));
    };
    await act(async () => {
      escribir(usuario, 'operador');
      escribir(contrasena, 'operador123');
    });
    await tocar(div, 'Ingresar');
    await esperar();
    expect(div.textContent).toContain('Esta app es para los choferes');
  });
});

describe('avisos del servidor', () => {
  it('separa los eventos y guarda lo que falta', () => {
    const { eventos, resto } = separarEventos('event:actualizacion\ndata:{}\n\n:latido\n\nevent:act');
    expect(eventos).toEqual(['actualizacion']);
    expect(resto).toBe('event:act');
  });
});
