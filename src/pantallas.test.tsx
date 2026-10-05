// @vitest-environment jsdom
// Pruebas rápidas: cada pantalla tiene que poder mostrarse sin errores
// y cada rol tiene que ver solo lo que le corresponde.
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import App from './App';
import { AuthProvider } from './context/AuthContext';
import { puedeVer, validarCredenciales } from './data/usuarios';

(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

class ResizeObserverFalso {
  observe() {}
  unobserve() {}
  disconnect() {}
}
(globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = ResizeObserverFalso;

async function mostrar(ruta: string) {
  const div = document.createElement('div');
  document.body.appendChild(div);
  const raiz = createRoot(div);
  await act(async () => {
    raiz.render(
      <MemoryRouter initialEntries={[ruta]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
  });
  return div;
}

beforeEach(() => {
  sessionStorage.clear();
});

describe('pantallas con sesión de director', () => {
  beforeEach(() => {
    sessionStorage.setItem('ecoruta.sesion', 'director');
  });

  for (const ruta of ['/', '/mapa', '/rutas', '/incidentes', '/reportes', '/indicadores']) {
    it(`se muestra la pantalla ${ruta}`, async () => {
      const div = await mostrar(ruta);
      expect(div.querySelector('h1')?.textContent?.length).toBeGreaterThan(3);
    });
  }
});

describe('login y roles', () => {
  it('sin sesión se pide iniciar sesión', async () => {
    const div = await mostrar('/');
    expect(div.querySelector('h1')?.textContent).toContain('Ingresá');
  });

  it('el operador no ve Indicadores y vuelve al panel', async () => {
    sessionStorage.setItem('ecoruta.sesion', 'operador');
    const div = await mostrar('/indicadores');
    expect(div.querySelector('h1')?.textContent).toBe('Panel general');
    expect(div.textContent).not.toContain('Indicadores');
  });

  it('el director ve el menú completo', async () => {
    sessionStorage.setItem('ecoruta.sesion', 'director');
    const div = await mostrar('/');
    expect(div.textContent).toContain('Indicadores');
  });

  it('valida usuario y contraseña', () => {
    expect(validarCredenciales('director', 'director123')?.rol).toBe('director');
    expect(validarCredenciales(' Operador ', 'operador123')?.rol).toBe('operador');
    expect(validarCredenciales('director', 'otra')).toBeNull();
    expect(validarCredenciales('nadie', 'director123')).toBeNull();
  });

  it('la sesión devuelta no incluye la contraseña', () => {
    expect(validarCredenciales('director', 'director123')).not.toHaveProperty('contrasena');
  });

  it('los permisos por rol son los esperados', () => {
    expect(puedeVer('director', '/indicadores')).toBe(true);
    expect(puedeVer('operador', '/indicadores')).toBe(false);
    expect(puedeVer('operador', '/incidentes')).toBe(true);
  });
});
