import { useCallback, useEffect, useState } from 'react';
import { cuandoVenzaLaSesion, usarToken } from './api';
import Login from './pantallas/Login';
import PantallaTurno from './pantallas/Turno';
import type { UsuarioSesion } from './tipos';

const CLAVE_SESION = 'ecoruta.chofer.sesion';

interface Sesion {
  token: string;
  usuario: UsuarioSesion;
}

// La sesión queda guardada en el celular para no pedir la contraseña cada vez que se abre la app.
// Al cerrar sesión, o si el token vence, se borra.
function leerSesion(): Sesion | null {
  try {
    const guardada = localStorage.getItem(CLAVE_SESION);
    if (!guardada) return null;
    const sesion = JSON.parse(guardada) as Sesion;
    usarToken(sesion.token);
    return sesion;
  } catch {
    return null;
  }
}

export default function App() {
  const [sesion, setSesion] = useState<Sesion | null>(leerSesion);

  const salir = useCallback(() => {
    try {
      localStorage.removeItem(CLAVE_SESION);
    } catch {
      // nada que borrar
    }
    usarToken(null);
    setSesion(null);
  }, []);

  useEffect(() => cuandoVenzaLaSesion(salir), [salir]);

  function entrar(nueva: Sesion) {
    try {
      localStorage.setItem(CLAVE_SESION, JSON.stringify(nueva));
    } catch {
      // si no se puede guardar, la sesión dura hasta que se cierre la app
    }
    usarToken(nueva.token);
    setSesion(nueva);
  }

  if (!sesion) return <Login alEntrar={entrar} />;
  return <PantallaTurno usuario={sesion.usuario} salir={salir} />;
}
