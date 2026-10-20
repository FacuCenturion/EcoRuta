// Íconos de línea, del mismo estilo que el diseño. Heredan el color del texto.
import type { Flecha } from '../navegacion';

type Props = { tamano?: number };

function Svg({ tamano = 24, children }: Props & { children: React.ReactNode }) {
  return (
    <svg
      width={tamano}
      height={tamano}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function IconoManiobra({ flecha, tamano = 64 }: { flecha: Flecha; tamano?: number }) {
  const trazo = { width: tamano, height: tamano, viewBox: '0 0 48 48', fill: 'none', stroke: 'currentColor', strokeWidth: 5, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };
  switch (flecha) {
    case 'izquierda':
      return (<svg {...trazo} aria-label="Doblá a la izquierda"><path d="M30 42V22a6 6 0 0 0-6-6H10" /><path d="M17 8l-8 8 8 8" /></svg>);
    case 'derecha':
      return (<svg {...trazo} aria-label="Doblá a la derecha"><path d="M18 42V22a6 6 0 0 1 6-6h14" /><path d="M31 8l8 8-8 8" /></svg>);
    case 'leve-izquierda':
      return (<svg {...trazo} aria-label="Levemente a la izquierda"><path d="M28 42V26L14 12" /><path d="M13 22V11h11" /></svg>);
    case 'leve-derecha':
      return (<svg {...trazo} aria-label="Levemente a la derecha"><path d="M20 42V26l14-14" /><path d="M35 22V11H24" /></svg>);
    case 'vuelta':
      return (<svg {...trazo} aria-label="Pegá la vuelta"><path d="M16 42V18a8 8 0 0 1 16 0v10" /><path d="M24 22l8 8 8-8" /></svg>);
    case 'rotonda':
      return (<svg {...trazo} aria-label="Rotonda"><circle cx="24" cy="20" r="7" /><path d="M24 42V27" /><path d="M29 15l7-7" /><path d="M30 8h6v6" /></svg>);
    case 'llegada':
      return (<svg {...trazo} aria-label="Llegando a la parada"><path d="M24 44s14-12 14-24a14 14 0 0 0-28 0c0 12 14 24 14 24z" /><circle cx="24" cy="20" r="5" /></svg>);
    default:
      return (<svg {...trazo} aria-label="Seguí derecho"><path d="M24 42V8" /><path d="M15 17l9-9 9 9" /></svg>);
  }
}

export const IconoCheck = (p: Props) => (<Svg {...p}><path d="M5 12.5l4.5 4.5L19 7.5" /></Svg>);
export const IconoAlerta = (p: Props) => (<Svg {...p}><path d="M12 6v8" /><path d="M12 18h.01" /></Svg>);
export const IconoTriangulo = (p: Props) => (<Svg {...p}><path d="M10.3 3.9L2.4 17.6A2 2 0 0 0 4.1 20.6h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" /><path d="M12 9v4" /><path d="M12 17h.01" /></Svg>);
export const IconoPapelera = (p: Props) => (<Svg {...p}><path d="M4 7h16" /><path d="M10 4h4" /><path d="M6 7l1 13h10l1-13" /></Svg>);
export const IconoLista = (p: Props) => (<Svg {...p}><path d="M9 6h11M9 12h11M9 18h11" /><path d="M4.5 6h.01M4.5 12h.01M4.5 18h.01" /></Svg>);
export const IconoBifurcacion = (p: Props) => (<Svg {...p}><path d="M12 21v-8" /><path d="M12 13L6 6" /><path d="M12 13l6-7" /><path d="M6 10V6h4" /><path d="M14 6h4v4" /></Svg>);
export const IconoCapas = (p: Props) => (<Svg {...p}><path d="M12 3l9 5-9 5-9-5 9-5z" /><path d="M3 13l9 5 9-5" /></Svg>);
export const IconoSonido = (p: Props) => (<Svg {...p}><path d="M4 9v6h4l5 4V5L8 9H4z" /><path d="M16.5 8.5a5 5 0 0 1 0 7" /></Svg>);
export const IconoSilencio = (p: Props) => (<Svg {...p}><path d="M4 9v6h4l5 4V5L8 9H4z" /><path d="M3 3l18 18" /></Svg>);
export const IconoCasa = (p: Props) => (<Svg {...p}><path d="M4 11l8-7 8 7" /><path d="M6 10v10h12V10" /></Svg>);
export const IconoPlay = (p: Props) => (<svg width={p.tamano ?? 20} height={p.tamano ?? 20} viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.5v15l12.5-7.5L7 4.5z" fill="currentColor" /></svg>);
export const IconoMas = (p: Props) => (<Svg {...p}><path d="M12 5v14M5 12h14" /></Svg>);
export const IconoCamara = (p: Props) => (<Svg {...p}><path d="M4 8h3l2-3h6l2 3h3v11H4z" /><circle cx="12" cy="13" r="3.5" /></Svg>);
export const IconoMicrofono = (p: Props) => (<Svg {...p}><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5 11a7 7 0 0 0 14 0" /><path d="M12 18v3" /></Svg>);
export const IconoSenal = (p: Props) => (<Svg {...p}><path d="M5 12a10 10 0 0 1 14 0" /><path d="M8.5 15.5a5 5 0 0 1 7 0" /><path d="M12 19h.01" /></Svg>);
export const IconoAuto = (p: Props) => (<Svg {...p}><path d="M5 16V12l2-5h10l2 5v4" /><path d="M3 16h18" /><circle cx="7.5" cy="16.5" r="1.5" /><circle cx="16.5" cy="16.5" r="1.5" /></Svg>);
export const IconoObra = (p: Props) => (<Svg {...p}><path d="M12 3L5 20h14L12 3z" /><path d="M8.5 12h7M7 16h10" /></Svg>);
export const IconoRoto = (p: Props) => (<Svg {...p}><path d="M5 6h14l-1.5 14h-11z" /><path d="M10 9l2 3-2 2 2 3" /></Svg>);
export const IconoBolsas = (p: Props) => (<Svg {...p}><path d="M5 20c0-4 1-7 3-8M19 20c0-4-1-7-3-8" /><path d="M8 12l1-3h2M16 12l-1-3h-2" /><path d="M4 20h16" /></Svg>);
export const IconoSensor = (p: Props) => (<Svg {...p}><path d="M5 12a10 10 0 0 1 14 0" /><path d="M8.5 15.5a5 5 0 0 1 7 0" /><path d="M3 3l18 18" /></Svg>);
export const IconoPuntos = (p: Props) => (<Svg {...p}><path d="M6 12h.01M12 12h.01M18 12h.01" strokeWidth={3} /></Svg>);
export const IconoBrujula = () => (
  <svg width="34" height="34" viewBox="0 0 34 34" aria-hidden="true">
    <path d="M4 17l13-4 13 4-13 4z" fill="#fff9f3" />
    <path d="M17 13l13 4-13 4z" fill="#e0b558" />
  </svg>
);
