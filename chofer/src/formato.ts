const decimal = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

export function hora(fecha: string | null | undefined): string {
  if (!fecha) return '—';
  return new Date(fecha).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false });
}

export function km(valor: number): string {
  return `${decimal.format(valor)} km`;
}

export const MOTIVO_VISITA: Record<string, string> = {
  llenado: 'Pasó el 80%',
  riesgo: 'Por desbordar',
  reporte: 'Reclamo de un vecino',
  tiempo: 'Más de 48 h sin vaciar',
  de_paso: 'De paso',
};

// Abre la navegación en el celular (Google Maps o la app de mapas). No necesita clave.
export function enlaceParaLlegar(destino: { lat: number; lng: number }): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${destino.lat},${destino.lng}&travelmode=driving`;
}
