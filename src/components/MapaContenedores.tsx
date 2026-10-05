import { useEffect, useState } from 'react';
import MapaGoogle from './mapa/MapaGoogle';
import MapaLeaflet from './mapa/MapaLeaflet';
import type { PropsMapa } from './mapa/tipos';

export type { Vista } from './mapa/tipos';

const CLAVE_GOOGLE = (import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? '').trim();

// Elige qué mapa mostrar: Google Maps si hay una clave configurada y
// funciona. Si no, se usa el mapa alternativo para que la página siga andando.
export default function MapaContenedores(props: PropsMapa) {
  const [claveRechazada, setClaveRechazada] = useState(false);

  useEffect(() => {
    if (!CLAVE_GOOGLE) return;
    window.gm_authFailure = () => setClaveRechazada(true);
    return () => {
      delete window.gm_authFailure;
    };
  }, []);

  if (CLAVE_GOOGLE && !claveRechazada) {
    return <MapaGoogle clave={CLAVE_GOOGLE} {...props} />;
  }

  return (
    <>
      <MapaLeaflet {...props} />
      <p className="nota nota--mapa">
        {claveRechazada
          ? 'Google Maps rechazó la clave. Se muestra el mapa alternativo; revisá la clave en .env.local.'
          : 'Mapa alternativo. Para usar Google Maps hay que cargar la clave en .env.local (ver README).'}
      </p>
    </>
  );
}
