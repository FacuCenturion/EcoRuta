// Cliente de la API del backend. Si VITE_API_URL está vacía, el panel no usa la API
// y sigue con los datos simulados en el navegador (así se mostró en las Sprints 1 y 2).

export const URL_API = (import.meta.env.VITE_API_URL ?? '').trim().replace(/\/$/, '');
export const USA_API = URL_API !== '';

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

// Lo registra AuthContext: si el backend responde 401, la sesión venció y hay que volver al login
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
    throw new ErrorDeApi(
      0,
      'SIN_CONEXION',
      'No se pudo conectar con el servidor. Revisá que el backend esté prendido.',
    );
  }

  if (respuesta.status === 401 && token) alVencerLaSesion();
  if (!respuesta.ok) {
    // Los errores del backend vienen como { codigo, mensaje }
    const error = await respuesta.json().catch(() => null);
    throw new ErrorDeApi(
      respuesta.status,
      error?.codigo ?? 'ERROR',
      error?.mensaje ?? `El servidor respondió ${respuesta.status}.`,
    );
  }
  return respuesta.status === 204 ? (undefined as T) : ((await respuesta.json()) as T);
}

// Trae una imagen protegida (por ejemplo la foto de un incidente) y la abre en otra pestaña.
// Un <img> o un enlace no pueden mandar el token en el header, por eso se pide con fetch.
export async function abrirImagen(ruta: string) {
  const ventana = window.open('', '_blank');
  const respuesta = await fetch(URL_API + ruta, { headers: encabezados() });
  if (!respuesta.ok) {
    ventana?.close();
    throw new ErrorDeApi(respuesta.status, 'SIN_IMAGEN', 'No se pudo abrir la foto.');
  }
  const url = URL.createObjectURL(await respuesta.blob());
  if (ventana) ventana.location.href = url;
}

// Separa los eventos completos que llegaron por SSE y devuelve lo que sobra para la próxima vez.
// Un evento termina con una línea vacía; las líneas que empiezan con ":" son comentarios (latidos).
export function separarEventos(texto: string): { eventos: string[]; resto: string } {
  const normalizado = texto.replace(/\r\n/g, '\n');
  const bloques = normalizado.split('\n\n');
  const resto = bloques.pop() ?? '';
  const eventos = bloques
    .map((bloque) =>
      bloque
        .split('\n')
        .find((linea) => linea.startsWith('event:'))
        ?.slice(6)
        .trim(),
    )
    .filter((nombre): nombre is string => Boolean(nombre));
  return { eventos, resto };
}

// Escucha los avisos del backend (Server-Sent Events). No usa EventSource porque no deja
// mandar el token en un header. Si se corta la conexión, vuelve a intentar sola.
// Devuelve una función para dejar de escuchar.
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
        // se cortó la conexión o se canceló: se reintenta abajo
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
