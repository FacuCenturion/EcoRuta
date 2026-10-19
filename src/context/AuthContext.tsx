import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { cuandoVenzaLaSesion, ErrorDeApi, pedir, usarToken, USA_API } from '../api/cliente';
import { buscarUsuario, validarCredenciales, type Rol, type UsuarioSesion } from '../data/usuarios';

const CLAVE_SESION = 'ecoruta.sesion';

// Devuelve null si entró, o el mensaje para mostrar si no
type ResultadoLogin = string | null;

interface ValorAuth {
  usuario: UsuarioSesion | null;
  iniciarSesion: (usuario: string, contrasena: string) => Promise<ResultadoLogin>;
  cerrarSesion: () => void;
}

const Contexto = createContext<ValorAuth | null>(null);

const CREDENCIALES_INVALIDAS = 'Usuario o contraseña incorrectos. Revisá los datos e intentá de nuevo.';

interface RespuestaLogin {
  token: string;
  usuario: Omit<UsuarioSesion, 'rol'> & { rol: Rol | 'chofer' };
}

// La sesión se guarda en sessionStorage: sobrevive a recargar la página
// pero se pierde al cerrar la pestaña. Sin backend se guarda el nombre de usuario;
// con backend, el token y los datos que devolvió el login.
function leerSesion(): UsuarioSesion | null {
  try {
    const guardado = sessionStorage.getItem(CLAVE_SESION);
    if (!guardado) return null;
    if (!USA_API) return buscarUsuario(guardado);
    const { token, usuario } = JSON.parse(guardado) as { token: string; usuario: UsuarioSesion };
    usarToken(token);
    return usuario;
  } catch {
    return null;
  }
}

function guardarSesion(valor: string | null) {
  try {
    if (valor === null) sessionStorage.removeItem(CLAVE_SESION);
    else sessionStorage.setItem(CLAVE_SESION, valor);
  } catch {
    // si el navegador no deja guardar, la sesión dura hasta que se recargue
  }
}

async function loginConBackend(
  nombre: string,
  contrasena: string,
): Promise<{ token: string; usuario: UsuarioSesion } | string> {
  try {
    const { token, usuario } = await pedir<RespuestaLogin>('/auth/login', {
      metodo: 'POST',
      cuerpo: { usuario: nombre, contrasena },
    });
    if (usuario.rol === 'chofer') return 'Los choferes entran desde la app del chofer, no desde el panel.';
    return { token, usuario: { ...usuario, rol: usuario.rol } };
  } catch (error) {
    if (error instanceof ErrorDeApi && error.estado === 401) return CREDENCIALES_INVALIDAS;
    return (error as Error).message;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioSesion | null>(leerSesion);

  const cerrarSesion = useCallback(() => {
    guardarSesion(null);
    usarToken(null);
    setUsuario(null);
  }, []);

  // Si el token vence mientras se usa el panel, se vuelve al login
  useEffect(() => cuandoVenzaLaSesion(cerrarSesion), [cerrarSesion]);

  const iniciarSesion = useCallback(async (nombre: string, contrasena: string): Promise<ResultadoLogin> => {
    if (!USA_API) {
      const encontrado = validarCredenciales(nombre, contrasena);
      if (!encontrado) return CREDENCIALES_INVALIDAS;
      guardarSesion(encontrado.usuario);
      setUsuario(encontrado);
      return null;
    }

    const resultado = await loginConBackend(nombre, contrasena);
    if (typeof resultado === 'string') return resultado;
    usarToken(resultado.token);
    guardarSesion(JSON.stringify(resultado));
    setUsuario(resultado.usuario);
    return null;
  }, []);

  const valor = useMemo(() => ({ usuario, iniciarSesion, cerrarSesion }), [usuario, iniciarSesion, cerrarSesion]);
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useAuth(): ValorAuth {
  const valor = useContext(Contexto);
  if (!valor) throw new Error('useAuth se usa dentro de AuthProvider');
  return valor;
}
