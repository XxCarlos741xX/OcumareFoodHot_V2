import { describe, expect, it } from 'vitest'
import { bs, leerMonto, redondear, usd } from './dinero'

describe('dinero', () => {
  it('formatea al estilo venezolano', () => {
    expect(usd(1234.5)).toBe('$1.234,50')
    expect(bs(490)).toBe('Bs 490,00')
    expect(usd(null)).toBe('—')
  })
  it('lee montos con coma o punto decimal', () => {
    expect(leerMonto('12,5')).toBe(12.5)
    expect(leerMonto('12.5')).toBe(12.5)
    expect(leerMonto('1.234,50')).toBe(1234.5)
    expect(leerMonto('abc')).toBeNaN()
    expect(leerMonto('')).toBeNaN()
  })
  it('redondea a centavos', () => {
    expect(redondear(447.125)).toBe(447.13)
  })
})
