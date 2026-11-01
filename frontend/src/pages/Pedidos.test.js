import { describe, expect, it } from 'vitest'
import { pasoDesdeLista } from './Pedidos'

const pedido = (tipo, estado) => ({ id: 1, numeroDiario: 1, tipo, estado })

describe('botón de la lista de pedidos', () => {
  it('muestra el siguiente paso de cada estado', () => {
    expect(pasoDesdeLista(pedido('MESA', 'ABIERTO'), false)).toEqual({ estado: 'EN_PREPARACION', accion: 'Enviar a cocina' })
    expect(pasoDesdeLista(pedido('MESA', 'EN_PREPARACION'), false).accion).toBe('Marcar listo')
    expect(pasoDesdeLista(pedido('PARA_LLEVAR', 'LISTO'), false).accion).toBe('Marcar entregado')
  })

  it('no muestra botón cuando ya no hay paso', () => {
    for (const estado of ['ENTREGADO', 'PAGADO', 'ANULADO']) expect(pasoDesdeLista(pedido('MESA', estado), true)).toBeNull()
  })

  it('un pedido web nuevo solo lo acepta la caja', () => {
    expect(pasoDesdeLista(pedido('WEB', 'ABIERTO'), false)).toBeNull()
    expect(pasoDesdeLista(pedido('WEB', 'ABIERTO'), true)).toEqual({ estado: 'EN_PREPARACION', accion: 'Aceptar pedido' })
    expect(pasoDesdeLista(pedido('WEB', 'EN_PREPARACION'), false).accion).toBe('Marcar listo')
  })
})
