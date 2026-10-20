import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import 'leaflet/dist/leaflet.css';
import App from './App';
import './estilos.css';

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

// El service worker permite instalar la app en el celular y abrirla aunque la señal sea mala.
// Solo en la versión publicada: en desarrollo guardaría versiones viejas de los archivos.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // sin service worker la app anda igual, solo que no se puede instalar
    });
  });
}
