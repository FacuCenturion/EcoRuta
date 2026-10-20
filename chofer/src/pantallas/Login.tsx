import { useState, type FormEvent } from 'react';
import { ErrorDeApi, pedir } from '../api';
import type { UsuarioSesion } from '../tipos';

interface Props {
  alEntrar: (sesion: { token: string; usuario: UsuarioSesion }) => void;
}

export default function Login({ alEntrar }: Props) {
  const [usuario, setUsuario] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      const sesion = await pedir<{ token: string; usuario: UsuarioSesion }>('/auth/login', {
        metodo: 'POST',
        cuerpo: { usuario, contrasena },
      });
      if (sesion.usuario.rol !== 'chofer') {
        setError('Esta app es para los choferes. El equipo del Centro de Monitoreo entra desde el panel.');
      } else {
        alEntrar(sesion);
      }
    } catch (e) {
      setError(
        e instanceof ErrorDeApi && e.estado === 401 ? 'Usuario o contraseña incorrectos.' : (e as Error).message,
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="login">
      <div className="marca">
        <span className="marca__logo" aria-hidden="true" />
        <span>EcoRuta</span>
        <small>Chofer</small>
      </div>
      <form className="tarjeta formulario" onSubmit={enviar}>
        <h1>Ingresá para ver tu ruta</h1>
        <label>
          Usuario
          <input
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            value={usuario}
            onChange={(e) => setUsuario(e.target.value)}
            required
          />
        </label>
        <label>
          Contraseña
          <input
            type="password"
            autoComplete="current-password"
            value={contrasena}
            onChange={(e) => setContrasena(e.target.value)}
            required
          />
        </label>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="boton boton--principal" disabled={enviando}>
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
        <p className="nota">Usuarios de prueba: chofer22, chofer31, chofer14, chofer17 o chofer40, contraseña chofer123.</p>
      </form>
    </main>
  );
}
