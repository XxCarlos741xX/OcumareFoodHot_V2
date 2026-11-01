import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useOperacion } from '../auth/OperacionContext'
import { useAviso } from '../components/Aviso'
import SinTurno from '../components/SinTurno'
import { ESTADOS, SIGUIENTE, TIPOS, bs, usd } from '../lib/dinero'
import { useCarga } from '../lib/useCarga'

/** Paso que se puede dar desde la lista, o null. Un pedido web nuevo solo lo acepta la caja. */
export function pasoDesdeLista(p, esCaja) {
  const s = SIGUIENTE[p.estado]
  if (!s) return null
  if (p.tipo === 'WEB' && p.estado === 'ABIERTO') return esCaja ? { ...s, accion: 'Aceptar pedido' } : null
  return s
}

export default function Pedidos() {
  const { turno } = useOperacion()
  const { esCaja } = useAuth()
  const aviso = useAviso()
  const [soloAbiertos, setSoloAbiertos] = useState(true)
  const [avanzando, setAvanzando] = useState(null)
  const { datos: pedidos, error, recargar } = useCarga(turno ? `/api/pedidos?soloAbiertos=${soloAbiertos}` : null, 15)

  async function avanzar(p, paso) {
    setAvanzando(p.id)
    try {
      const r = await api(`/api/pedidos/${p.id}/estado`, { method: 'PATCH', body: { estado: paso.estado } })
      aviso(`#${p.numeroDiario}: ${r.estado === 'PAGADO' ? 'entregado y cerrado' : ESTADOS[r.estado].toLowerCase()}`)
      await recargar()
    } catch (err) {
      aviso(err.message, 'error')
    } finally {
      setAvanzando(null)
    }
  }

  if (turno === null) return <SinTurno />

  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        <h1>Pedidos del turno</h1>
        <fieldset className="segmentado segmentado-chico">
          <legend className="oculto">Mostrar</legend>
          <label><input type="radio" name="filtro" checked={soloAbiertos} onChange={() => setSoloAbiertos(true)} />En curso</label>
          <label><input type="radio" name="filtro" checked={!soloAbiertos} onChange={() => setSoloAbiertos(false)} />Todos</label>
        </fieldset>
      </div>
      {error && <p className="error">{error.message}</p>}
      {pedidos && pedidos.length === 0 && (
        <p className="vacio-texto">{soloAbiertos ? 'No hay pedidos en curso.' : 'Aún no hay pedidos en este turno.'}</p>
      )}
      <ul className="lista-pedidos">
        {pedidos?.map((p) => {
          const paso = pasoDesdeLista(p, esCaja)
          return (
          <li key={p.id} className={`pedido-tarjeta estado-${p.estado.toLowerCase()}`}>
            <Link to={`/pedido/${p.id}`} className="pedido-fila">
              <span className="pedido-num">#{p.numeroDiario}</span>
              <span className="pedido-que">
                <strong>{p.tipo === 'MESA' ? `Mesa ${p.mesaNumero}` : TIPOS[p.tipo]}{p.entrega && <span className={`etiqueta-entrega entrega-${p.entrega.toLowerCase()}`}>{p.entrega === 'DELIVERY' ? 'Delivery' : 'Retira'}</span>}{p.tipo === 'WEB' && p.estado === 'ABIERTO' && <span className="etiqueta-entrega por-aceptar">Por aceptar</span>}</strong>
                <small>{p.clienteNombre ?? p.mesero ?? ''}</small>
              </span>
              <span className="pedido-estado">{ESTADOS[p.estado]}</span>
              <span className="pedido-total">{usd(p.totalUsd)}<small>{p.totalBs != null ? bs(p.totalBs) : ''}</small></span>
            </Link>
            {paso && (
              <button type="button" className="btn btn-linea pedido-accion" disabled={avanzando !== null}
                      onClick={() => avanzar(p, paso)} aria-label={`${paso.accion}: pedido #${p.numeroDiario}`}>
                {avanzando === p.id ? 'Un momento…' : paso.accion}
              </button>
            )}
          </li>
          )
        })}
      </ul>
    </div>
  )
}
