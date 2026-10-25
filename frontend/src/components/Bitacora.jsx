import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCarga } from '../lib/useCarga'

const hora = (iso) => new Date(iso).toLocaleTimeString('es-VE', { hour: 'numeric', minute: '2-digit' })
const quien = (e) => e.usuario ?? 'Cliente (web)'
const pedido = (e) => (e.tipoPedido === 'MESA' ? `Mesa ${e.mesaNumero} #${e.numeroPedido}` : `#${e.numeroPedido}`)

/** Lista de la bitácora. Con conPedido, cada línea dice de qué pedido es y lleva a él. */
export function ListaEventos({ eventos, conPedido }) {
  if (!eventos?.length) return null
  return (
    <ol className="bitacora">
      {eventos.map((e) => (
        <li key={e.id} className={e.alerta ? 'bitacora-alerta' : ''}>
          <span className="bitacora-hora">{hora(e.creadoEn)}</span>
          <span className="bitacora-texto">
            {conPedido && <Link to={`/pedido/${e.pedidoId}`}>{pedido(e)}</Link>}
            <strong>{quien(e)}</strong> {e.detalle.charAt(0).toLowerCase() + e.detalle.slice(1)}
          </span>
        </li>
      ))}
    </ol>
  )
}

/**
 * Para la caja: lo que conviene revisar del turno (platos quitados después de ir a cocina,
 * anulaciones y reimpresiones). Se puede abrir el historial completo.
 */
export function RevisionTurno({ turnoId, vivo }) {
  const [todo, setTodo] = useState(false)
  const base = `/api/pedidos/bitacora?turnoId=${turnoId}`
  const { datos: alertas } = useCarga(`${base}&soloAlertas=true`, vivo ? 30 : 0)
  const { datos: completo } = useCarga(todo ? base : null)
  return (
    <section className="bloque">
      <h2>Para revisar</h2>
      {alertas && alertas.length === 0 && <p className="nota">Nada raro en este turno: no se quitaron platos de cocina ni hubo anulaciones o reimpresiones.</p>}
      <ListaEventos eventos={alertas} conPedido />
      <details className="desplegable" onToggle={(e) => setTodo(e.currentTarget.open)}>
        <summary><span>Todo lo que pasó en el turno</span>{completo && <span className="resumen-cuadre">{completo.length}</span>}</summary>
        <div className="desplegable-cuerpo">
          {!completo && todo && <p className="cargando">Cargando…</p>}
          <ListaEventos eventos={completo} conPedido />
        </div>
      </details>
    </section>
  )
}
