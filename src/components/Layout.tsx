import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { puedeVer } from '../data/usuarios';
import { IconoIncidentes, IconoIndicadores, IconoMapa, IconoPanel, IconoReportes, IconoRutas } from './Iconos';

const ENLACES = [
  { ruta: '/', texto: 'Panel general', icono: <IconoPanel />, fin: true },
  { ruta: '/mapa', texto: 'Mapa en vivo', icono: <IconoMapa /> },
  { ruta: '/rutas', texto: 'Rutas y camiones', icono: <IconoRutas /> },
  { ruta: '/incidentes', texto: 'Incidentes', icono: <IconoIncidentes /> },
  { ruta: '/reportes', texto: 'Reportes vecinales', icono: <IconoReportes /> },
  { ruta: '/indicadores', texto: 'Indicadores', icono: <IconoIndicadores /> },
];

export default function Layout() {
  const { usuario, cerrarSesion } = useAuth();
  const navegar = useNavigate();
  const enlaces = ENLACES.filter((enlace) => !usuario || puedeVer(usuario.rol, enlace.ruta));

  function salir() {
    cerrarSesion();
    navegar('/login', { replace: true });
  }

  return (
    <div className="app">
      <aside className="lateral">
        <div className="lateral__marca">
          <span className="lateral__logo" aria-hidden="true" />
          <span>Ecoruta</span>
        </div>
        <nav className="lateral__menu" aria-label="Secciones">
          {enlaces.map((enlace) => (
            <NavLink
              key={enlace.ruta}
              to={enlace.ruta}
              end={enlace.fin}
              className={({ isActive }) => (isActive ? 'lateral__enlace lateral__enlace--activo' : 'lateral__enlace')}
            >
              {enlace.icono}
              {enlace.texto}
            </NavLink>
          ))}
        </nav>
        {usuario && (
          <div className="lateral__usuario">
            <div className="lateral__persona">
              <span className="avatar">{usuario.iniciales}</span>
              <div>
                <strong>{usuario.nombre}</strong>
                <small>{usuario.cargo}</small>
              </div>
            </div>
            <button type="button" className="lateral__salir" onClick={salir}>
              Cerrar sesión
            </button>
          </div>
        )}
      </aside>
      <main className="contenido">
        <Outlet />
      </main>
    </div>
  );
}
