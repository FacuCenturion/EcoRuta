import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { buscarUsuario, validarCredenciales, type UsuarioSesion } from '../data/usuarios';

const CLAVE_SESION = 'ecoruta.sesion';

interface ValorAuth {
  usuario: UsuarioSesion | null;
  iniciarSesion: (usuario: string, contrasena: string) => boolean;
  cerrarSesion: () => void;
}

const Contexto = createContext<ValorAuth | null>(null);

// La sesión se guarda en sessionStorage: sobrevive a recargar la página
// pero se pierde al cerrar la pestaña.
function leerSesion(): UsuarioSesion | null {
  try {
    const guardado = sessionStorage.getItem(CLAVE_SESION);
    return guardado ? buscarUsuario(guardado) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<UsuarioSesion | null>(leerSesion);

  const iniciarSesion = useCallback((nombre: string, contrasena: string) => {
    const encontrado = validarCredenciales(nombre, contrasena);
    if (!encontrado) return false;
    try {
      sessionStorage.setItem(CLAVE_SESION, encontrado.usuario);
    } catch {
      // si el navegador no deja guardar, la sesión dura hasta que se recargue
    }
    setUsuario(encontrado);
    return true;
  }, []);

  const cerrarSesion = useCallback(() => {
    try {
      sessionStorage.removeItem(CLAVE_SESION);
    } catch {
      // nada que borrar
    }
    setUsuario(null);
  }, []);

  const valor = useMemo(() => ({ usuario, iniciarSesion, cerrarSesion }), [usuario, iniciarSesion, cerrarSesion]);
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useAuth(): ValorAuth {
  const valor = useContext(Contexto);
  if (!valor) throw new Error('useAuth se usa dentro de AuthProvider');
  return valor;
}
