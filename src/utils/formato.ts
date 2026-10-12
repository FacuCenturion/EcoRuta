const numero = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 0 });
const decimal = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

export function formatearNumero(valor: number): string {
  return numero.format(valor);
}

export function formatearDecimal(valor: number): string {
  return decimal.format(valor);
}

export function formatearHora(ms: number): string {
  return new Date(ms).toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', hour12: false });
}

export function formatearFechaLarga(ms: number): string {
  const texto = new Date(ms).toLocaleDateString('es-AR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

export function formatearFechaCorta(ms: number): string {
  return new Date(ms).toLocaleDateString('es-AR', { day: 'numeric', month: 'numeric' });
}

// Tiempo transcurrido desde una lectura, en palabras: "hace 25 min", "hace 3 h"
export function formatearHaceCuanto(ms: number, ahoraMs: number): string {
  const minutos = Math.max(0, Math.round((ahoraMs - ms) / 60000));
  if (minutos < 1) return 'recién';
  if (minutos < 60) return `hace ${minutos} min`;
  const horas = Math.floor(minutos / 60);
  if (horas < 24) return `hace ${horas} h`;
  const dias = Math.floor(horas / 24);
  return dias === 1 ? 'hace 1 día' : `hace ${dias} días`;
}
