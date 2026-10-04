import { useMemo, useState } from 'react'
import { useOperacion } from '../auth/OperacionContext'
import { bs, redondear, usd } from '../lib/dinero'
import { useCarga } from '../lib/useCarga'
import Contador from './Contador'

/**
 * La carta para armar un pedido a toques: categorías arriba, productos con su contador
 * y una barra fija abajo con el total y el botón para confirmar.
 */
export default function Carta({ etiquetaConfirmar, onConfirmar, enviando }) {
  const { datos: productos, error } = useCarga('/api/productos')
  const { tasa } = useOperacion()
  const [carrito, setCarrito] = useState({})          // { productoId: cantidad }
  const [categoria, setCategoria] = useState(null)

  const categorias = useMemo(() => {
    const vistos = new Map()
    for (const p of productos ?? []) if (!vistos.has(p.categoria)) vistos.set(p.categoria, [])
    for (const p of productos ?? []) vistos.get(p.categoria).push(p)
    return [...vistos.entries()]
  }, [productos])

  const activa = categoria ?? categorias[0]?.[0]
  const lineas = Object.entries(carrito).map(([id, cantidad]) => ({ producto: productos.find((p) => p.id === Number(id)), cantidad }))
  const totalUsd = redondear(lineas.reduce((s, l) => s + l.producto.precioUsd * l.cantidad, 0))
  const unidades = lineas.reduce((s, l) => s + l.cantidad, 0)

  function cambiar(id, cantidad) {
    setCarrito((c) => {
      const n = { ...c }
      if (cantidad > 0) n[id] = cantidad
      else delete n[id]
      return n
    })
  }

  if (error) return <p className="error">{error.message}</p>
  if (!productos) return <p className="cargando">Cargando la carta…</p>

  return (
    <div className="carta">
      <nav className="categorias" aria-label="Categorías">
        {categorias.map(([nombre, lista]) => {
          const enCarrito = lista.reduce((s, p) => s + (carrito[p.id] ?? 0), 0)
          return (
            <button key={nombre} type="button" aria-pressed={nombre === activa}
                    className={nombre === activa ? 'activa' : ''} onClick={() => setCategoria(nombre)}>
              {nombre}{enCarrito > 0 && <span className="burbuja">{enCarrito}</span>}
            </button>
          )
        })}
      </nav>

      <ul className="productos">
        {(categorias.find(([n]) => n === activa)?.[1] ?? []).map((p) => (
          <li key={p.id} className={p.disponible ? '' : 'agotado'}>
            <div className="producto-info">
              <h3>{p.nombre}</h3>
              {p.descripcion && <p>{p.descripcion}</p>}
              <span className="precio">{p.disponible ? usd(p.precioUsd) : 'Agotado'}</span>
            </div>
            <Contador valor={carrito[p.id] ?? 0} onCambiar={(n) => cambiar(p.id, n)} deshabilitado={!p.disponible} etiqueta={p.nombre} />
          </li>
        ))}
      </ul>

      <div className="barra-pedido" data-vacia={unidades === 0}>
        <div className="barra-pedido-total">
          <span>{unidades === 0 ? 'Toca + para agregar' : `${unidades} ${unidades === 1 ? 'producto' : 'productos'}`}</span>
          <strong>{usd(totalUsd)}</strong>
          {tasa && unidades > 0 && <small>{bs(totalUsd * tasa.bsPorUsd)}</small>}
        </div>
        <button type="button" className="btn btn-oro" disabled={unidades === 0 || enviando}
                onClick={() => onConfirmar(lineas.map((l) => ({ productoId: l.producto.id, cantidad: l.cantidad })))}>
          {etiquetaConfirmar}
        </button>
      </div>
    </div>
  )
}
