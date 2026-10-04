// Formato de montos al estilo venezolano: 1.234,50
const fmt = new Intl.NumberFormat('es-VE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export const usd = (n) => (n == null ? '—' : `$${fmt.format(Number(n))}`)
export const bs = (n) => (n == null ? '—' : `Bs ${fmt.format(Number(n))}`)
export const numero = (n) => (n == null ? '—' : fmt.format(Number(n)))

/** Redondeo a 2 decimales igual al del backend (mitad hacia arriba). */
export const redondear = (n) => Math.round((Number(n) + Number.EPSILON) * 100) / 100

/** Convierte un texto escrito por el usuario ("12,5" o "12.5") a número; NaN si no es válido. */
export function leerMonto(texto) {
  if (texto == null) return NaN
  const limpio = String(texto).trim().replace(/\s/g, '')
  if (!limpio) return NaN
  // Si trae coma, la coma es el decimal y los puntos son miles.
  const normal = limpio.includes(',') ? limpio.replace(/\./g, '').replace(',', '.') : limpio
  return /^\d+(\.\d+)?$/.test(normal) ? Number(normal) : NaN
}

export const METODOS = [
  { id: 'EFECTIVO_USD', nombre: 'Efectivo $', moneda: 'USD', referencia: false },
  { id: 'ZELLE', nombre: 'Zelle', moneda: 'USD', referencia: true },
  { id: 'EFECTIVO_BS', nombre: 'Efectivo Bs', moneda: 'BS', referencia: false },
  { id: 'PAGO_MOVIL', nombre: 'Pago móvil', moneda: 'BS', referencia: true },
  { id: 'PUNTO_DE_VENTA', nombre: 'Punto de venta', moneda: 'BS', referencia: false },
  { id: 'TRANSFERENCIA_BS', nombre: 'Transferencia', moneda: 'BS', referencia: true },
]

export const ESTADOS = {
  ABIERTO: 'Tomado',
  EN_PREPARACION: 'En cocina',
  LISTO: 'Listo',
  ENTREGADO: 'Entregado',
  PAGADO: 'Cerrado',
  ANULADO: 'Anulado',
}

export const SIGUIENTE = {
  ABIERTO: { estado: 'EN_PREPARACION', accion: 'Enviar a cocina' },
  EN_PREPARACION: { estado: 'LISTO', accion: 'Marcar listo' },
  LISTO: { estado: 'ENTREGADO', accion: 'Marcar entregado' },
}

export const TIPOS = {
  MESA: 'Mesa',
  PARA_LLEVAR: 'Para llevar',
  DELIVERY: 'Delivery',
  WEB: 'Web',
}
