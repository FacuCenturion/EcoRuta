import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { USUARIOS_DE_PRUEBA } from '../data/usuarios';

const ETIQUETA_ROL = { director: 'Director', operador: 'Operador' } as const;

export default function Login() {
  const { usuario, iniciarSesion } = useAuth();
  const navegar = useNavigate();
  const [nombre, setNombre] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState(false);

  if (usuario) return <Navigate to="/" replace />;

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (iniciarSesion(nombre, contrasena)) {
      navegar('/', { replace: true });
    } else {
      setError(true);
    }
  }

  return (
    <main className="login">
      <section className="login__tarjeta" aria-labelledby="titulo-login">
        <div className="login__marca">
          <span className="lateral__logo" aria-hidden="true" />
          <span>Ecoruta</span>
        </div>
        <h1 id="titulo-login">Ingresá a tu cuenta</h1>
        <p className="nota">Panel de gestión de recolección de residuos de CABA.</p>

        <form className="formulario" onSubmit={enviar}>
          <label>
            Usuario
            <input
              className="campo"
              type="text"
              autoComplete="username"
              value={nombre}
              onChange={(e) => {
                setNombre(e.target.value);
                setError(false);
              }}
              required
            />
          </label>
          <label>
            Contraseña
            <input
              className="campo"
              type="password"
              autoComplete="current-password"
              value={contrasena}
              onChange={(e) => {
                setContrasena(e.target.value);
                setError(false);
              }}
              required
            />
          </label>
          {error && (
            <p className="login__error" role="alert">
              Usuario o contraseña incorrectos. Revisá los datos e intentá de nuevo.
            </p>
          )}
          <button type="submit" className="boton boton--primario">
            Ingresar
          </button>
        </form>

        <div className="login__prueba">
          <strong>Usuarios de prueba</strong>
          <ul>
            {USUARIOS_DE_PRUEBA.map((u) => (
              <li key={u.usuario}>
                <span>
                  {ETIQUETA_ROL[u.rol]}: <code>{u.usuario}</code> / <code>{u.contrasena}</code>
                </span>
                <button
                  type="button"
                  className="boton"
                  onClick={() => {
                    setNombre(u.usuario);
                    setContrasena(u.contrasena);
                    setError(false);
                  }}
                >
                  Completar
                </button>
              </li>
            ))}
          </ul>
        </div>
      </section>
    </main>
  );
}
