import type { ReactNode } from 'react';

function Icono({ children }: { children: ReactNode }) {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export const IconoPanel = () => (
  <Icono>
    <rect x="4" y="4" width="6.5" height="6.5" rx="1.2" />
    <rect x="13.5" y="4" width="6.5" height="6.5" rx="1.2" />
    <rect x="4" y="13.5" width="6.5" height="6.5" rx="1.2" />
    <rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.2" />
  </Icono>
);

export const IconoMapa = () => (
  <Icono>
    <path d="M3 6.5l6-2.5 6 2.5 6-2.5v13.5l-6 2.5-6-2.5-6 2.5z" />
    <path d="M9 4v13.5M15 6.5V20" />
  </Icono>
);

export const IconoRutas = () => (
  <Icono>
    <circle cx="6" cy="6" r="2.2" />
    <circle cx="18" cy="18" r="2.2" />
    <path d="M8.2 6H15a3 3 0 010 6H9a3 3 0 000 6h6.8" />
  </Icono>
);

export const IconoIncidentes = () => (
  <Icono>
    <path d="M12 4l9 16H3z" />
    <path d="M12 10v4.5M12 17.5v.1" />
  </Icono>
);

export const IconoReportes = () => (
  <Icono>
    <path d="M4 5h16v11H9l-5 4z" />
  </Icono>
);

export const IconoIndicadores = () => (
  <Icono>
    <path d="M6 20V11M12 20V5M18 20v-6" />
  </Icono>
);

export const IconoCampana = () => (
  <Icono>
    <path d="M6 16V11a6 6 0 0112 0v5l1.5 2h-15z" />
    <path d="M10 20.5a2 2 0 004 0" />
  </Icono>
);

export const IconoSensor = () => (
  <Icono>
    <path d="M5 10a10 10 0 0114 0M8 13.5a6 6 0 018 0" />
    <circle cx="12" cy="17" r="1.2" />
  </Icono>
);
