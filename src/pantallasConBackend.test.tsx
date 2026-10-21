// @vitest-environment jsdom
// Las pantallas conectadas al backend, con un backend falso que responde lo mismo que el real.
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
(globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

const API = 'http://api.prueba/api/v1';
const RELOJ = '2026-10-19T17:30:00-03:00';

const contenedor = (id: string, sectorId: number, llenado: number, extra: object = {}) => ({
  id,
  direccion: `Calle ${id}`,
  barrio: 'San Nicolás',
  zonaId: 1,
  sectorId,
  tipo: 'lateral',
  capacidadLitros: 3200,
  posicion: { lat: -34.6 - sectorId / 100, lng: -58.38 },
  velocidad: 4,
  llenado,
  nivel: llenado >= 80 ? 'prioritario' : 'medio',
  motivo: llenado >= 80 ? 'llenado' : null,
  anomalia: false,
  estadoSensor: 'funcionando',
  nivelEstimado: null,
  reportesVecinales: 0,
  ultimaLectura: RELOJ,
  ultimaRecoleccion: null,
  desbordeEstimado: null,
  desbordeDesde: null,
  ...extra,
});

const RESPUESTAS: Record<string, unknown> = {
  '/simulacion': { reloj: RELOJ, activa: true, enPausa: false, velocidad: 1 },
  '/contenedores?zonaId=1': [
    contenedor('SNI-01', 1, 85, { desbordeEstimado: '2026-10-19T19:00:00-03:00' }),
    contenedor('MON-02', 2, 60, { estadoSensor: 'trabado', nivelEstimado: 74, motivo: 'tiempo' }),
  ],
  '/camiones?zonaId=1': [
    {
      id: 'C22',
      numero: 22,
      zonaId: 1,
      sectorId: 1,
      chofer: 'Ana Pérez',
      estado: 'en_ruta',
      cargaPct: 10,
      posicion: { lat: -34.6, lng: -58.37 },
      kmRecorridosHoy: 2.5,
      rutaId: 3,
      estadoRuta: 'en_curso',
      kmPlanificados: 5,
      paradas: [
        {
          id: 1,
          contenedorId: 'SNI-01',
          direccion: 'Calle SNI-01',
          orden: 1,
          estado: 'pendiente',
          motivoVisita: 'llenado',
          eta: '2026-10-19T17:45:00-03:00',
          horaReal: null,
          motivo: null,
        },
        {
          id: 2,
          contenedorId: 'MON-02',
          direccion: 'Calle MON-02',
          orden: 2,
          estado: 'pendiente',
          motivoVisita: 'de_paso',
          eta: '2026-10-19T17:55:00-03:00',
          horaReal: null,
          motivo: null,
        },
      ],
    },
    {
      id: 'C40',
      numero: 40,
      zonaId: 1,
      sectorId: 1,
      chofer: 'Marcos Villalba',
      estado: 'en_base',
      cargaPct: 0,
      posicion: { lat: -34.609, lng: -58.37 },
      kmRecorridosHoy: 0,
      rutaId: null,
      estadoRuta: null,
      kmPlanificados: 0,
      paradas: [],
    },
  ],
  '/rutas/3/trazado': {
    rutaId: 3,
    estado: 'listo',
    kmPorCalle: 10.91,
    tramos: [
      [
        { lat: -34.609, lng: -58.37 },
        { lat: -34.61, lng: -58.38 },
      ],
      [
        { lat: -34.61, lng: -58.38 },
        { lat: -34.62, lng: -58.38 },
      ],
      [
        { lat: -34.62, lng: -58.38 },
        { lat: -34.609, lng: -58.37 },
      ],
    ],
  },
  '/incidentes?zonaId=1': [
    {
      id: 7,
      tipo: 'falla_sensor',
      estado: 'pendiente',
      contenedorId: 'MON-02',
      zonaId: 1,
      sectorId: 2,
      direccion: 'Calle MON-02',
      descripcion: 'El sensor quedó trabado después de vaciarlo.',
      sugerencia: 'Pedir revisión del sensor',
      creado: '2026-10-19T17:00:00-03:00',
      camionNumero: null,
      origen: null,
      decidido: null,
    },
  ],
  '/panel/resumen?zonaId=1': {
    reloj: RELOJ,
    recolectadosHoy: 12,
    planificadosHoy: 20,
    porcentajeHecho: 60,
    kmEvitadosHoy: 8.4,
    llenadoPorHora: Array.from({ length: 24 }, (_, h) => 20 + h),
    horaPico: 23,
  },
  '/panel/sectores': [
    { id: 1, nombre: 'Retiro y San Nicolás', barrios: ['Retiro', 'San Nicolás'], empresa: 'AESA', eficiencia: 81.2 },
    { id: 2, nombre: 'Monserrat', barrios: ['Monserrat'], empresa: 'AESA', eficiencia: null },
  ],
  '/contenedores/MON-02/curva': {
    contenedorId: 'MON-02',
    estadoSensor: 'trabado',
    nivelEstimado: 74,
    lecturas: [{ medidoAt: '2026-10-19T12:00:00-03:00', llenado: 60 }],
    estimadas: [
      { medidoAt: '2026-10-19T12:00:00-03:00', llenado: 5 },
      { medidoAt: RELOJ, llenado: 74 },
    ],
  },
  '/indicadores?dias=30': {
    dias: [
      { fecha: '2026-10-18', kmFija: 140, kmDinamica: 90, desbordesFija: 0, desbordesDinamica: 1, simulado: true },
    ],
    semanas: [
      { etiqueta: 'S1', desbordesFija: 4, desbordesDinamica: 1 },
      { etiqueta: 'S2', desbordesFija: 5, desbordesDinamica: 1 },
      { etiqueta: 'S3', desbordesFija: 4, desbordesDinamica: 0 },
    ],
    porcentajeVisitasBajasFija: 96,
    porcentajeVisitasBajasDinamica: 5,
    eficienciaPorSector: { 1: 81.2, 2: 77 },
    mapaCalor: { 1: Array.from({ length: 7 }, () => Array(24).fill(50)) },
    diasSimulados: 30,
  },
};

let pedidos: { url: string; metodo: string; cuerpo?: string }[];
let raiz: Root | null = null;

function backendFalso(url: string, init?: RequestInit): Promise<Response> {
  const ruta = url.replace(API, '');
  pedidos.push({ url: ruta, metodo: init?.method ?? 'GET', cuerpo: init?.body as string | undefined });
  if (ruta === '/eventos') {
    // Manda el aviso de conexión y queda abierta, como el servidor real
    const cuerpo = new ReadableStream({
      start(c) {
        c.enqueue(new TextEncoder().encode(`event:actualizacion\ndata:{"reloj":"${RELOJ}"}\n\n`));
      },
    });
    return Promise.resolve(new Response(cuerpo, { headers: { 'Content-Type': 'text/event-stream' } }));
  }
  if (init?.method === 'POST' && ruta === '/simulacion/casos')
    return Promise.resolve(Response.json({ mensaje: 'El contenedor queda al 97%.' }));
  if (init?.method === 'POST') return Promise.resolve(Response.json({}));
  const datos = RESPUESTAS[ruta];
  return Promise.resolve(datos === undefined ? new Response(null, { status: 404 }) : Response.json(datos));
}

async function mostrar(ruta: string) {
  const { default: App } = await import('./App');
  const { AuthProvider } = await import('./context/AuthContext');
  const div = document.createElement('div');
  document.body.appendChild(div);
  raiz = createRoot(div);
  await act(async () => {
    raiz!.render(
      <MemoryRouter initialEntries={[ruta]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
  });
  // Espera a que lleguen las respuestas
  for (let i = 0; i < 20 && div.textContent?.includes('Cargando'); i++) {
    await act(() => new Promise((listo) => setTimeout(listo, 10)));
  }
  return div;
}

beforeEach(() => {
  vi.resetModules();
  vi.stubEnv('VITE_API_URL', API);
  pedidos = [];
  vi.stubGlobal('fetch', vi.fn(backendFalso));
  sessionStorage.setItem(
    'ecoruta.sesion',
    JSON.stringify({
      token: 'token-de-prueba',
      usuario: { usuario: 'director', nombre: 'Roberto Aguirre', cargo: 'Director', iniciales: 'RA', rol: 'director' },
    }),
  );
});

afterEach(() => {
  act(() => raiz?.unmount());
  raiz = null;
  document.body.innerHTML = '';
  sessionStorage.clear();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

describe('panel conectado al backend', () => {
  it('el panel general muestra los sectores y los números del backend', async () => {
    const div = await mostrar('/');
    expect(div.querySelector('h1')?.textContent).toBe('Panel general');
    expect(div.textContent).toContain('Estado por sector');
    expect(div.textContent).toContain('Retiro y San Nicolás');
    expect(div.textContent).toContain('60% de lo planificado');
    expect(div.textContent).toContain('El pico es a las 23 h');
    // La eficiencia que todavía no se puede calcular no se inventa
    expect(div.textContent).toContain('—');
  });

  it('cada pedido lleva el token y se escuchan los avisos del servidor', async () => {
    await mostrar('/');
    const llamadas = vi.mocked(fetch).mock.calls;
    expect(llamadas.length).toBeGreaterThan(0);
    for (const [, init] of llamadas) {
      expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token-de-prueba');
    }
    expect(pedidos.some((p) => p.url === '/eventos')).toBe(true);
  });

  it('confirmar un incidente se lo manda al backend', async () => {
    const div = await mostrar('/incidentes');
    expect(div.textContent).toContain('Falla de sensor');
    const confirmar = [...div.querySelectorAll('button')].find((b) => b.textContent === 'Confirmar');
    await act(async () => confirmar!.click());
    const pedido = pedidos.find((p) => p.metodo === 'POST');
    expect(pedido?.url).toBe('/incidentes/7/decision');
    expect(JSON.parse(pedido!.cuerpo!)).toEqual({ decision: 'confirmado' });
  });

  it('estado de sensores muestra el sensor trabado y pide su curva', async () => {
    const div = await mostrar('/sensores');
    expect(div.textContent).toContain('Trabado');
    await act(() => new Promise((listo) => setTimeout(listo, 20)));
    expect(pedidos.some((p) => p.url === '/contenedores/MON-02/curva')).toBe(true);
  });

  it('los indicadores salen del backend', async () => {
    const div = await mostrar('/indicadores');
    await act(() => new Promise((listo) => setTimeout(listo, 20)));
    expect(pedidos.some((p) => p.url === '/indicadores?dias=30')).toBe(true);
    expect(div.textContent).toContain('con ruta fija eran 96%');
    expect(div.textContent).toContain('Índice de eficiencia por sector');
  });

  it('la ruta se dibuja por las calles y marca las paradas de paso', async () => {
    const div = await mostrar('/rutas');
    await act(() => new Promise((listo) => setTimeout(listo, 20)));
    expect(pedidos.some((p) => p.url === '/rutas/3/trazado')).toBe(true);
    expect(div.textContent).toContain('Por las calles: 10,9 km');
    expect(div.textContent).toContain('De paso');
    expect(div.textContent).toContain('Superó el 80%');
    // El camión 22 está en ruta: no se puede volver a despachar
    const boton = [...div.querySelectorAll('button')].find((b) => b.textContent === 'Despachar ahora');
    expect(boton?.disabled).toBe(true);
  });

  it('despachar un camión en la base se lo pide al backend', async () => {
    const div = await mostrar('/rutas');
    const camion40 = [...div.querySelectorAll('button')].find((b) => b.textContent?.includes('Camión 40'));
    await act(async () => camion40!.click());
    const despachar = [...div.querySelectorAll('button')].find((b) => b.textContent === 'Despachar ahora');
    expect(despachar?.disabled).toBe(false);
    await act(async () => despachar!.click());
    await act(() => new Promise((listo) => setTimeout(listo, 10)));
    const pedido = pedidos.find((p) => p.metodo === 'POST');
    expect(pedido?.url).toBe('/rutas/planificar');
    expect(JSON.parse(pedido!.cuerpo!)).toEqual({ sectorId: 1 });
    expect(div.textContent).toContain('Ruta despachada');
  });

  it('el mapa en vivo muestra el pronóstico de desborde', async () => {
    const div = await mostrar('/mapa');
    expect(div.textContent).toMatch(/Desborda a las .+ \(estimado\)/);
  });

  it('el botón Demo dispara un caso de la simulación', async () => {
    const div = await mostrar('/');
    const demo = [...div.querySelectorAll('button')].find((b) => b.textContent === 'Demo');
    await act(async () => demo!.click());
    expect(div.textContent).toContain('Demo simulada');
    expect(div.textContent).toContain('Reiniciar la demo'); // la sesión de prueba es del director
    const llenar = [...div.querySelectorAll('button')].find((b) => b.textContent === 'Llenar un contenedor');
    await act(async () => llenar!.click());
    const disparar = [...div.querySelectorAll('button')].find((b) => b.textContent === 'Disparar el caso');
    await act(async () => disparar!.click());
    await act(() => new Promise((listo) => setTimeout(listo, 10)));
    const pedido = pedidos.find((p) => p.url === '/simulacion/casos');
    expect(JSON.parse(pedido!.cuerpo!)).toEqual({ tipo: 'llenar', contenedorId: 'MON-02', nivel: 97 });
    expect(div.textContent).toContain('El contenedor queda al 97%.');
    expect(div.textContent).toContain('Ver en Mapa en vivo');
  });

  it('las demás pantallas cargan', async () => {
    for (const ruta of ['/mapa', '/rutas', '/reportes']) {
      const div = await mostrar(ruta);
      expect(div.querySelector('h1')?.textContent?.length).toBeGreaterThan(3);
      act(() => raiz?.unmount());
    }
  });

  it('un chofer no puede entrar al panel', async () => {
    sessionStorage.clear();
    const { AuthProvider, useAuth } = await import('./context/AuthContext');
    vi.mocked(fetch).mockImplementationOnce(() =>
      Promise.resolve(
        Response.json({
          token: 't',
          usuario: { usuario: 'chofer22', nombre: 'X', cargo: 'Chofer', iniciales: 'X', rol: 'chofer' },
        }),
      ),
    );
    let resultado: string | null = 'sin probar';
    function Probar() {
      const { iniciarSesion } = useAuth();
      return (
        <button type="button" onClick={async () => (resultado = await iniciarSesion('chofer22', 'chofer123'))}>
          entrar
        </button>
      );
    }
    const div = document.createElement('div');
    document.body.appendChild(div);
    raiz = createRoot(div);
    await act(async () =>
      raiz!.render(
        <AuthProvider>
          <Probar />
        </AuthProvider>,
      ),
    );
    await act(async () => div.querySelector('button')!.click());
    await act(() => new Promise((listo) => setTimeout(listo, 10)));
    expect(resultado).toContain('app del chofer');
  });
});
