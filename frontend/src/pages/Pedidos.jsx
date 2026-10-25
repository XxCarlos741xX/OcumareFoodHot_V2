import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useOperacion } from '../auth/OperacionContext'
import SinTurno from '../components/SinTurno'
import { ESTADOS, TIPOS, bs, usd } from '../lib/dinero'
import { useCarga } from '../lib/useCarga'

export default function Pedidos() {
  const { turno } = useOperacion()
  const [soloAbiertos, setSoloAbiertos] = useState(true)
  const { datos: pedidos, error } = useCarga(turno ? `/api/pedidos?soloAbiertos=${soloAbiertos}` : null, 15)

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
        {pedidos?.map((p) => (
          <li key={p.id}>
            <Link to={`/pedido/${p.id}`} className={`pedido-fila estado-${p.estado.toLowerCase()}`}>
              <span className="pedido-num">#{p.numeroDiario}</span>
              <span className="pedido-que">
                <strong>{p.tipo === 'MESA' ? `Mesa ${p.mesaNumero}` : TIPOS[p.tipo]}{p.entrega && <span className={`etiqueta-entrega entrega-${p.entrega.toLowerCase()}`}>{p.entrega === 'DELIVERY' ? 'Delivery' : 'Retira'}</span>}{p.tipo === 'WEB' && p.estado === 'ABIERTO' && <span className="etiqueta-entrega por-aceptar">Por aceptar</span>}</strong>
                <small>{p.clienteNombre ?? p.mesero ?? ''}</small>
              </span>
              <span className="pedido-estado">{ESTADOS[p.estado]}</span>
              <span className="pedido-total">{usd(p.totalUsd)}<small>{p.totalBs != null ? bs(p.totalBs) : ''}</small></span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
