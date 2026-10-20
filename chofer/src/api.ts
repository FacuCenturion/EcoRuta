// Cliente de la API. Es el mismo esquema que usa el panel (src/api/cliente.ts en la raíz):
// el token va en el header y los avisos en vivo llegan por Server-Sent Events.

export const URL_API = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1').trim().replace(/\/$/, '');

const ESPERA_PARA_RECONECTAR_MS = 3000;

export class ErrorDeApi extends Error {
  constructor(
    readonly estado: number,
    readonly codigo: string,
    mensaje: string,
  ) {
    super(mensaje);
  }
}

let token: string | null = null;
let alVencerLaSesion: () => void = () => {};

export function usarToken(nuevo: string | null) {
  token = nuevo;
}

export function cuandoVenzaLaSesion(accion: () => void) {
  alVencerLaSesion = accion;
}

function encabezados(extra?: HeadersInit): Headers {
  const h = new Headers(extra);
  if (token) h.set('Authorization', `Bearer ${token}`);
  return h;
}

export async function pedir<T>(ruta: string, opciones: { metodo?: string; cuerpo?: unknown } = {}): Promise<T> {
  let respuesta: Response;
  try {
    respuesta = await fetch(URL_API + ruta, {
      method: opciones.metodo ?? 'GET',
      headers: encabezados(opciones.cuerpo === undefined ? undefined : { 'Content-Type': 'application/json' }),
      body: opciones.cuerpo === undefined ? undefined : JSON.stringify(opciones.cuerpo),
    });
  } catch {
    throw new ErrorDeApi(0, 'SIN_CONEXION', 'Sin conexión con el servidor. Revisá la señal y probá de nuevo.');
  }
  if (respuesta.status === 401 && token) alVencerLaSesion();
  if (!respuesta.ok) {
    const error = await respuesta.json().catch(() => null);
    throw new ErrorDeApi(
      respuesta.status,
      error?.codigo ?? 'ERROR',
      error?.mensaje ?? `El servidor respondió ${respuesta.status}.`,
    );
  }
  return respuesta.status === 204 ? (undefined as T) : ((await respuesta.json()) as T);
}

// Separa los eventos SSE completos y devuelve lo que sobra para la próxima vez
export function separarEventos(texto: string): { eventos: string[]; resto: string } {
  const bloques = texto.replace(/\r\n/g, '\n').split('\n\n');
  const resto = bloques.pop() ?? '';
  const eventos = bloques
    .map((bloque) => bloque.split('\n').find((linea) => linea.startsWith('event:'))?.slice(6).trim())
    .filter((nombre): nombre is string => Boolean(nombre));
  return { eventos, resto };
}

// Escucha los avisos del backend y vuelve a conectarse sola si se corta (pasa mucho en el celular)
export function escucharAvisos(alAvisar: () => void, alCambiarConexion: (conectado: boolean) => void): () => void {
  let terminado = false;
  let controlador: AbortController | null = null;

  async function escuchar() {
    while (!terminado) {
      controlador = new AbortController();
      try {
        const respuesta = await fetch(`${URL_API}/eventos`, {
          headers: encabezados({ Accept: 'text/event-stream' }),
          signal: controlador.signal,
        });
        if (respuesta.status === 401) {
          alVencerLaSesion();
          return;
        }
        if (!respuesta.ok || !respuesta.body) throw new Error(`Respuesta ${respuesta.status}`);
        alCambiarConexion(true);
        const lector = respuesta.body.pipeThrough(new TextDecoderStream()).getReader();
        let pendiente = '';
        for (;;) {
          const { value, done } = await lector.read();
          if (done) break;
          const { eventos, resto } = separarEventos(pendiente + value);
          pendiente = resto;
          if (eventos.includes('actualizacion')) alAvisar();
        }
      } catch {
        // se cortó la conexión o se canceló
      }
      if (terminado) return;
      alCambiarConexion(false);
      await new Promise((listo) => setTimeout(listo, ESPERA_PARA_RECONECTAR_MS));
    }
  }

  escuchar();
  return () => {
    terminado = true;
    controlador?.abort();
  };
}
