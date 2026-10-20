// @vitest-environment jsdom
// La app del chofer contra un backend falso que responde como el real.
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { separarEventos } from './api';
import type { Turno } from './tipos';

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
      posicion: { lat: -34.609, lng: -58.37 },
      kmRecorridosHoy: 0,
    },
    manejadoPorApp: true,
    base: { lat: -34.609, lng: -58.37 },
    ruta: null,
    reloj: '2026-10-20T17:30:00-03:00',
    ...cambios,
  };
}

const parada = (id: number, direccion: string, estado: 'pendiente' | 'recolectada' = 'pendiente') => ({
  id,
  contenedorId: `SNI-0${id}`,
  direccion,
  posicion: { lat: -34.6 - id / 1000, lng: -58.375 },
  orden: id,
  estado,
  motivoVisita: id === 2 ? 'de_paso' : 'llenado',
  eta: '2026-10-20T17:45:00-03:00',
  horaReal: null,
  motivo: null,
});

const rutaDePrueba = (estado: 'programada' | 'en_curso') => ({
  id: 9,
  estado,
  creada: '2026-10-20T17:20:00-03:00',
  salidaProgramada: '2026-10-20T17:20:00-03:00',
  kmPlanificados: 4,
  duracionEstimadaMin: 35,
  paradas: [parada(1, 'Florida 450'), parada(2, 'Viamonte 438'), parada(3, 'Paraguay 548')],
});

let actual: Turno;
let canalSse: ReadableStreamDefaultController<Uint8Array> | null = null;
const avisarDesdeElServidor = () =>
  canalSse?.enqueue(new TextEncoder().encode('event:actualizacion\ndata:{}\n\n'));
let pedidos: { url: string; metodo: string; cuerpo?: string }[];
let raiz: Root | null = null;

function backendFalso(url: string, init?: RequestInit): Promise<Response> {
  const ruta = url.replace(API, '');
  const metodo = init?.method ?? 'GET';
  pedidos.push({ url: ruta, metodo, cuerpo: init?.body as string | undefined });
  if (ruta === '/eventos') {
    // Queda abierta; la prueba manda avisos con avisarDesdeElServidor()
    const cuerpo = new ReadableStream<Uint8Array>({
      start(c) {
        canalSse = c;
      },
    });
    return Promise.resolve(new Response(cuerpo, { headers: { 'Content-Type': 'text/event-stream' } }));
  }
  if (ruta === '/auth/login') {
    const { usuario } = JSON.parse(init!.body as string);
    const rol = usuario === 'operador' ? 'operador' : 'chofer';
    return Promise.resolve(Response.json({ token: 't', usuario: { usuario, nombre: 'X', iniciales: 'X', rol } }));
  }
  if (ruta === '/chofer/motivos') return Promise.resolve(Response.json(['Auto estacionado', 'Obra o calle cortada']));
  if (ruta === '/chofer/ruta/trazado')
    return Promise.resolve(Response.json({ rutaId: 9, estado: 'sin_servicio', kmPorCalle: null, tramos: [] }));
  if (ruta === '/chofer/turno/empezar') actual = { ...actual, manejadoPorApp: true };
  if (ruta === '/chofer/ruta/iniciar') actual = { ...actual, ruta: rutaDePrueba('en_curso') as Turno['ruta'] };
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

const esperar = () => act(() => new Promise((listo) => setTimeout(listo, 20)));
const boton = (div: HTMLElement, texto: string) =>
  [...div.querySelectorAll('button')].find((b) => b.textContent?.trim() === texto);

beforeEach(() => {
  vi.resetModules();
  pedidos = [];
  actual = turnoDePrueba();
  vi.stubGlobal('fetch', vi.fn(backendFalso));
  localStorage.setItem(
    'ecoruta.chofer.sesion',
    JSON.stringify({ token: 'token-chofer', usuario: { usuario: 'chofer22', nombre: 'Lucas', iniciales: 'LB', rol: 'chofer' } }),
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
    expect(div.textContent).toContain('Camión 22');
    expect(div.textContent).toContain('Hola, Lucas');
    await act(async () => boton(div, 'Empezar turno')!.click());
    expect(pedidos.some((p) => p.url === '/chofer/turno/empezar' && p.metodo === 'POST')).toBe(true);
    expect(div.textContent).toContain('Sin ruta por ahora');
  });

  it('con una ruta asignada se puede iniciar y aparece la primera parada', async () => {
    actual = turnoDePrueba({ ruta: rutaDePrueba('programada') as Turno['ruta'] });
    const div = await mostrar();
    expect(div.textContent).toContain('Tenés una ruta de 3 paradas');
    await act(async () => boton(div, 'Iniciar ruta')!.click());
    expect(div.textContent).toContain('Parada 1 de 3');
    expect(div.querySelector('#titulo-parada')?.textContent).toBe('Florida 450');
    const llegar = [...div.querySelectorAll('a')].find((a) => a.textContent === 'Cómo llegar');
    expect(llegar?.getAttribute('href')).toContain('destination=-34.601,-58.375');
  });

  it('vaciar la parada se lo manda al backend', async () => {
    actual = turnoDePrueba({ ruta: rutaDePrueba('en_curso') as Turno['ruta'] });
    const div = await mostrar();
    await act(async () => boton(div, 'Vaciado')!.click());
    const pedido = pedidos.find((p) => p.metodo === 'POST');
    expect(pedido?.url).toBe('/chofer/paradas/1/recolectar');
  });

  it('"no se pudo" pide el motivo y lo manda', async () => {
    actual = turnoDePrueba({ ruta: rutaDePrueba('en_curso') as Turno['ruta'] });
    const div = await mostrar();
    await act(async () => boton(div, 'No se pudo')!.click());
    await esperar();
    expect(div.querySelector('[role=dialog]')).not.toBeNull();
    expect(boton(div, 'Confirmar')!.disabled).toBe(true);
    await act(async () => boton(div, 'Obra o calle cortada')!.click());
    await act(async () => boton(div, 'Confirmar')!.click());
    await esperar();
    const pedido = pedidos.find((p) => p.metodo === 'POST');
    expect(pedido?.url).toBe('/chofer/paradas/1/no-se-pudo');
    expect(JSON.parse(pedido!.cuerpo!)).toEqual({ motivo: 'Obra o calle cortada' });
    expect(div.querySelector('[role=dialog]')).toBeNull();
  });

  it('cada pedido lleva el token del chofer y se escuchan los avisos', async () => {
    await mostrar();
    for (const [, init] of vi.mocked(fetch).mock.calls) {
      expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token-chofer');
    }
    expect(pedidos.some((p) => p.url === '/eventos')).toBe(true);
  });

  it('cuando el sistema asigna una ruta con la app abierta, avisa que es nueva', async () => {
    const vibrar = vi.fn();
    Object.defineProperty(navigator, 'vibrate', { configurable: true, value: vibrar });
    const div = await mostrar();
    expect(div.textContent).toContain('Sin ruta por ahora');

    actual = turnoDePrueba({ ruta: rutaDePrueba('programada') as Turno['ruta'] });
    await act(async () => avisarDesdeElServidor());
    await esperar();
    expect(div.textContent).toContain('Ruta nueva');
    expect(div.textContent).toContain('Tenés una ruta de 3 paradas');
    expect(vibrar).toHaveBeenCalled();
  });

  it('una ruta que ya estaba al abrir la app no se anuncia como nueva', async () => {
    actual = turnoDePrueba({ ruta: rutaDePrueba('programada') as Turno['ruta'] });
    const div = await mostrar();
    expect(div.textContent).toContain('Tenés una ruta de 3 paradas');
    expect(div.textContent).not.toContain('Ruta nueva');
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
    await act(async () => boton(div, 'Ingresar')!.click());
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
