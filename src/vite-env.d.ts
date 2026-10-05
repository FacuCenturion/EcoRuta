/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_GOOGLE_MAPS_API_KEY?: string;
}

interface Window {
  // Google Maps llama a esta función cuando rechaza la clave
  gm_authFailure?: () => void;
}
