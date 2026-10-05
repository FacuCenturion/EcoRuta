import type { ReactNode } from 'react';

interface Props {
  titulo: string;
  valor: ReactNode;
  detalle?: ReactNode;
  variante?: 'normal' | 'oscura' | 'verde' | 'alerta';
  etiqueta?: string; // sello chico arriba a la derecha, por ejemplo "En vivo"
}

export default function Tarjeta({ titulo, valor, detalle, variante = 'normal', etiqueta }: Props) {
  return (
    <div className={`tarjeta tarjeta--${variante}`}>
      {etiqueta && <span className="tarjeta__sello">{etiqueta}</span>}
      <span className="tarjeta__titulo">{titulo}</span>
      <strong className="tarjeta__valor">{valor}</strong>
      {detalle && <span className="tarjeta__detalle">{detalle}</span>}
    </div>
  );
}
