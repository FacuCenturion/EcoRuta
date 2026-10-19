/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_GOOGLE_MAPS_API_KEY?: string;
  // Dirección de la API, por ejemplo http://localhost:8080/api/v1. Vacía: datos simulados en el navegador
  readonly VITE_API_URL?: string;
}

interface Window {
  // Google Maps llama a esta función cuando rechaza la clave
  gm_authFailure?: () => void;
}
