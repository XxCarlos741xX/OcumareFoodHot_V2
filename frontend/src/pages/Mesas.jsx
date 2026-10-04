import { useNavigate } from 'react-router-dom'
import { useOperacion } from '../auth/OperacionContext'
import SinTurno from '../components/SinTurno'
import { useCarga } from '../lib/useCarga'

/** Plano de mesas: libres en contorno, ocupadas encendidas. Tocar una mesa abre o crea su pedido. */
export default function Mesas() {
  const { turno } = useOperacion()
  const navegar = useNavigate()
  const { datos: mesas, error } = useCarga('/api/mesas', 15)

  if (turno === null) return <SinTurno />

  const zonas = agrupar(mesas ?? [])
  const ocupadas = (mesas ?? []).filter((m) => m.pedidoAbiertoId).length

  function abrir(m) {
    navegar(m.pedidoAbiertoId ? `/pedido/${m.pedidoAbiertoId}` : `/pedido/nuevo?tipo=MESA&mesa=${m.id}&numero=${m.numero}`)
  }

  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        <h1>Mesas</h1>
        {mesas && <p className="resumen">{ocupadas} de {mesas.length} ocupadas</p>}
      </div>
      <div className="acciones-rapidas">
        <button type="button" className="btn btn-linea" onClick={() => navegar('/pedido/nuevo?tipo=PARA_LLEVAR')}>Para llevar</button>
        <button type="button" className="btn btn-linea" onClick={() => navegar('/pedido/nuevo?tipo=DELIVERY')}>Delivery</button>
      </div>

      {error && <p className="error">{error.message}</p>}
      {mesas && mesas.length === 0 && (
        <p className="vacio-texto">Todavía no hay mesas. El gerente las agrega en Gestión, Salón.</p>
      )}

      {zonas.map(([zona, lista]) => (
        <section key={zona ?? 'sin-zona'} className="zona">
          {zonas.length > 1 && <h2 className="zona-titulo">{zona ?? 'Otras'}</h2>}
          <ul className="plano">
            {lista.map((m) => (
              <li key={m.id}>
                <button type="button" className={`mesa ${m.pedidoAbiertoId ? 'mesa-ocupada' : ''}`} onClick={() => abrir(m)}
                        aria-label={`Mesa ${m.numero}${m.nombre ? `, ${m.nombre}` : ''}, ${m.pedidoAbiertoId ? 'ocupada' : 'libre'}`}>
                  <span className="mesa-numero">{m.numero}</span>
                  <span className="mesa-pie">{m.pedidoAbiertoId ? 'Ocupada' : m.nombre ?? `${m.capacidad} puestos`}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  )
}

/** Agrupa por zona respetando el orden en que el gerente acomodó las mesas. */
export function agrupar(mesas) {
  const mapa = new Map()
  for (const m of mesas) {
    const z = m.zona || null
    if (!mapa.has(z)) mapa.set(z, [])
    mapa.get(z).push(m)
  }
  return [...mapa.entries()]
}
