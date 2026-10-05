import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { SimulacionProvider } from '../context/SimulacionContext';
import { useAuth } from '../context/AuthContext';
import { puedeVer } from '../data/usuarios';
import Layout from './Layout';

// Todo lo que está adentro necesita haber iniciado sesión.
// La simulación arranca recién cuando alguien entra.
export function RequiereSesion() {
  const { usuario } = useAuth();
  if (!usuario) return <Navigate to="/login" replace />;
  return (
    <SimulacionProvider>
      <Layout />
    </SimulacionProvider>
  );
}

// Pantallas que solo ven ciertos roles
export function ConAcceso({ ruta, children }: { ruta: string; children: ReactNode }) {
  const { usuario } = useAuth();
  if (!usuario || !puedeVer(usuario.rol, ruta)) return <Navigate to="/" replace />;
  return <>{children}</>;
}
