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
