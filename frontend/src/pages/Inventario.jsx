import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useAviso } from '../components/Aviso'
import Hoja from '../components/Hoja'
import { leerMonto, usd } from '../lib/dinero'
import { useCarga } from '../lib/useCarga'

const UNIDAD = { UNIDAD: 'unid.', KG: 'kg', LITRO: 'L' }
const UNIDAD_LARGA = { UNIDAD: 'Unidades', KG: 'Kilos', LITRO: 'Litros' }
const TIPO = { ENTRADA: 'Compra', VENTA: 'Venta', DEVOLUCION: 'Devolución', MERMA: 'Merma', AJUSTE: 'Conteo' }
const fmt3 = new Intl.NumberFormat('es-VE', { maximumFractionDigits: 3 })
const cantidad = (n, u) => `${fmt3.format(Number(n))} ${UNIDAD[u] ?? ''}`.trim()
const fechaHora = (iso) => new Date(iso).toLocaleString('es-VE', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })

/**
 * Inventario de insumos. El cajero ve el stock y registra compras y mermas.
 * El gerente además crea insumos, arma las recetas de cada plato y ajusta por conteo físico.
 * Las ventas se descuentan solas cuando el pedido entra a cocina.
 */
export default function Inventario() {
  const { esGerencia } = useAuth()
  const [vista, setVista] = useState('stock')
  const insumos = useCarga('/api/inventario/insumos', 30)
  const alertas = useCarga('/api/inventario/alertas', 30)
  const recargar = () => { insumos.recargar(); alertas.recargar() }

  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        {esGerencia && <Link to="/gestion" className="volver" aria-label="Volver a gestión">‹</Link>}
        <h1>Inventario</h1>
      </div>
      <fieldset className="segmentado segmentado-chico" aria-label="Vista">
        {[['stock', 'Stock'], ['recetas', 'Recetas'], ['movimientos', 'Movimientos']].map(([v, t]) => (
          <label key={v}><input type="radio" name="vista-inv" checked={vista === v} onChange={() => setVista(v)} />{t}</label>
        ))}
      </fieldset>
      {vista === 'stock' && <Stock insumos={insumos} alertas={alertas.datos} onCambio={recargar} />}
      {vista === 'recetas' && <Recetas insumos={insumos.datos ?? []} onCambio={recargar} />}
      {vista === 'movimientos' && <Movimientos insumos={insumos.datos ?? []} />}
    </div>
  )
}

function Alertas({ alertas }) {
  if (!alertas || (alertas.bajoMinimo.length === 0 && alertas.platosAgotados.length === 0)) return null
  return (
    <div className="alerta-inventario" role="status">
      {alertas.bajoMinimo.length > 0 && (
        <p><strong>Por comprar:</strong> {alertas.bajoMinimo.map((i) => `${i.nombre} (${cantidad(i.stockActual, i.unidad)})`).join(', ')}</p>
      )}
      {alertas.platosAgotados.length > 0 && (
        <p><strong>Agotados por falta de insumos:</strong> {alertas.platosAgotados.join(', ')}. Vuelven solos al registrar la compra.</p>
      )}
    </div>
  )
}

function Stock({ insumos, alertas, onCambio }) {
  const { esGerencia } = useAuth()
  const [mover, setMover] = useState(null)
  const [editar, setEditar] = useState(null)       // insumo, o {} para uno nuevo
  const [ver, setVer] = useState('activos')
  const lista = (insumos.datos ?? []).filter((i) => (ver === 'activos' ? i.activo : !i.activo))

  return (
    <>
      <Alertas alertas={alertas} />
      <div className="acciones-rapidas">
        {esGerencia && <button type="button" className="btn btn-oro" onClick={() => setEditar({})}>Agregar insumo</button>}
        {(insumos.datos ?? []).some((i) => !i.activo) && (
          <button type="button" className="btn btn-linea" onClick={() => setVer(ver === 'activos' ? 'inactivos' : 'activos')}>
            {ver === 'activos' ? 'Ver desactivados' : 'Ver activos'}
          </button>
        )}
      </div>
      {insumos.error && <p className="error">{insumos.error.message}</p>}
      {insumos.datos && insumos.datos.length === 0 && (
        <p className="vacio-texto">
          Todavía no hay insumos. {esGerencia ? 'Agrega lo que compras (carne, pan, queso, refrescos…) y luego arma la receta de cada plato.' : 'El gerente debe agregarlos.'}
        </p>
      )}
      <ul className="gestion-lista">
        {lista.map((i) => (
          <li key={i.id} className={i.activo ? '' : 'apagado'}>
            <span className="gestion-info">
              <strong>{i.nombre} {i.bajo && <span className="chip chip-apagado">{Number(i.stockActual) < 0 ? 'En negativo' : 'Bajo'}</span>}</strong>
              <small>
                {cantidad(i.stockActual, i.unidad)}
                {Number(i.stockMinimo) > 0 && ` · mínimo ${cantidad(i.stockMinimo, i.unidad)}`}
                {i.costoUnitarioUsd != null && ` · ${usd(i.costoUnitarioUsd)} por ${UNIDAD[i.unidad]}`}
                {i.usadoEn > 0 ? ` · en ${i.usadoEn} ${i.usadoEn === 1 ? 'plato' : 'platos'}` : ' · sin receta'}
              </small>
            </span>
            {i.activo && <button type="button" className="btn btn-texto" onClick={() => setMover(i)}>Registrar</button>}
            {esGerencia && <button type="button" className="btn btn-texto" onClick={() => setEditar(i)}>Editar</button>}
          </li>
        ))}
      </ul>
      {mover && <HojaMovimiento insumo={mover} onCerrar={() => setMover(null)} onListo={() => { setMover(null); onCambio() }} />}
      {editar && <HojaInsumo insumo={editar} onCerrar={() => setEditar(null)} onListo={() => { setEditar(null); onCambio() }} />}
    </>
  )
}

function HojaMovimiento({ insumo, onCerrar, onListo }) {
  const { esGerencia } = useAuth()
  const aviso = useAviso()
  const [tipo, setTipo] = useState('ENTRADA')
  const [texto, setTexto] = useState('')
  const [costo, setCosto] = useState(insumo.costoUnitarioUsd != null ? String(insumo.costoUnitarioUsd).replace('.', ',') : '')
  const [nota, setNota] = useState('')
  const [enviando, setEnviando] = useState(false)
  const n = leerMonto(texto)
  const c = costo.trim() ? leerMonto(costo) : null
  const valido = Number.isFinite(n) && (tipo === 'AJUSTE' ? n >= 0 : n > 0) && (c == null || Number.isFinite(c))
    && (tipo !== 'MERMA' || nota.trim())

  const TEXTOS = {
    ENTRADA: ['¿Cuánto llegó?', 'Se suma al stock.'],
    MERMA: ['¿Cuánto se perdió?', 'Se resta del stock. Escribe qué pasó.'],
    AJUSTE: ['¿Cuánto hay contado?', `El stock pasa a ser lo que contaste (hoy dice ${cantidad(insumo.stockActual, insumo.unidad)}).`],
  }

  async function guardar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      const m = await api('/api/inventario/movimientos', { method: 'POST', body: {
        insumoId: insumo.id, tipo, cantidad: n, costoUnitarioUsd: tipo === 'ENTRADA' ? c : null, nota: nota.trim() || null } })
      aviso(`${insumo.nombre}: quedan ${cantidad(m.stockResultante, insumo.unidad)}`)
      onListo()
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo={insumo.nombre} onCerrar={onCerrar}>
      <form className="form" onSubmit={guardar}>
        <p className="nota">Hay {cantidad(insumo.stockActual, insumo.unidad)}.</p>
        <fieldset className="segmentado">
          <legend>¿Qué pasó?</legend>
          <label><input type="radio" name="tipo-mov" checked={tipo === 'ENTRADA'} onChange={() => setTipo('ENTRADA')} />Compra</label>
          <label><input type="radio" name="tipo-mov" checked={tipo === 'MERMA'} onChange={() => setTipo('MERMA')} />Merma</label>
          {esGerencia && <label><input type="radio" name="tipo-mov" checked={tipo === 'AJUSTE'} onChange={() => setTipo('AJUSTE')} />Conteo</label>}
        </fieldset>
        <label>{TEXTOS[tipo][0]} ({UNIDAD_LARGA[insumo.unidad].toLowerCase()})
          <input inputMode="decimal" value={texto} onChange={(e) => setTexto(e.target.value)} placeholder="0" required />
        </label>
        <p className="nota">{TEXTOS[tipo][1]}</p>
        {tipo === 'ENTRADA' && (
          <label>Costo por {UNIDAD[insumo.unidad]} en $ (opcional)
            <input inputMode="decimal" value={costo} onChange={(e) => setCosto(e.target.value)} placeholder="0,00" />
          </label>
        )}
        <label>{tipo === 'MERMA' ? 'Qué pasó' : 'Nota (opcional)'}
          <input value={nota} onChange={(e) => setNota(e.target.value)} maxLength={200}
                 placeholder={tipo === 'MERMA' ? 'Se dañó, se cayó, venció…' : tipo === 'ENTRADA' ? 'Proveedor, factura…' : ''} required={tipo === 'MERMA'} />
        </label>
        <button className="btn btn-oro btn-grande" disabled={!valido || enviando}>Guardar</button>
      </form>
    </Hoja>
  )
}

function HojaInsumo({ insumo, onCerrar, onListo }) {
  const aviso = useAviso()
  const nuevo = !insumo.id
  const txt = (v) => (v == null ? '' : String(v).replace('.', ','))
  const [f, setF] = useState({
    nombre: insumo.nombre ?? '', unidad: insumo.unidad ?? 'UNIDAD', minimo: txt(insumo.stockMinimo),
    costo: txt(insumo.costoUnitarioUsd), inicial: '', activo: insumo.activo ?? true })
  const [enviando, setEnviando] = useState(false)
  const num = (s) => (s.trim() ? leerMonto(s) : null)
  const valido = f.nombre.trim() && [f.minimo, f.costo, f.inicial].every((s) => !s.trim() || Number.isFinite(leerMonto(s)))

  async function guardar(e) {
    e.preventDefault()
    setEnviando(true)
    const body = { nombre: f.nombre.trim(), unidad: f.unidad, stockMinimo: num(f.minimo) ?? 0, costoUnitarioUsd: num(f.costo),
      stockInicial: nuevo ? num(f.inicial) ?? 0 : null, activo: f.activo }
    try {
      await api(nuevo ? '/api/inventario/insumos' : `/api/inventario/insumos/${insumo.id}`, { method: nuevo ? 'POST' : 'PUT', body })
      aviso(nuevo ? `${body.nombre} agregado` : 'Cambios guardados')
      onListo()
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo={nuevo ? 'Agregar insumo' : `Editar ${insumo.nombre}`} onCerrar={onCerrar}>
      <form className="form" onSubmit={guardar}>
        <label>Nombre<input value={f.nombre} onChange={(e) => setF({ ...f, nombre: e.target.value })} maxLength={80} required placeholder="Carne molida, pan de hamburguesa…" /></label>
        <fieldset className="segmentado">
          <legend>Se cuenta en</legend>
          {Object.entries(UNIDAD_LARGA).map(([u, t]) => (
            <label key={u}><input type="radio" name="unidad" checked={f.unidad === u} onChange={() => setF({ ...f, unidad: u })} />{t}</label>
          ))}
        </fieldset>
        {nuevo && <label>Cuánto hay hoy<input inputMode="decimal" value={f.inicial} onChange={(e) => setF({ ...f, inicial: e.target.value })} placeholder="0" /></label>}
        <div className="fila-2">
          <label>Avisar cuando quede<input inputMode="decimal" value={f.minimo} onChange={(e) => setF({ ...f, minimo: e.target.value })} placeholder="0" /></label>
          <label>Costo por {UNIDAD[f.unidad]} ($)<input inputMode="decimal" value={f.costo} onChange={(e) => setF({ ...f, costo: e.target.value })} placeholder="Opcional" /></label>
        </div>
        {!nuevo && (
          <label className="casilla">
            <input type="checkbox" checked={!f.activo} onChange={(e) => setF({ ...f, activo: !e.target.checked })} />
            Desactivar (ya no se usa: deja de descontarse y sale de las recetas)
          </label>
        )}
        {!nuevo && <p className="nota">Para cambiar el stock usa "Registrar" (compra, merma o conteo): así queda en el historial.</p>}
        <button className="btn btn-oro btn-grande" disabled={!valido || enviando}>{nuevo ? 'Agregar' : 'Guardar cambios'}</button>
      </form>
    </Hoja>
  )
}

function Recetas({ insumos, onCambio }) {
  const { esGerencia } = useAuth()
  const { datos: recetas, recargar, error } = useCarga('/api/inventario/recetas')
  const [editar, setEditar] = useState(null)
  const [solo, setSolo] = useState('todas')
  const lista = (recetas ?? []).filter((r) => solo === 'todas' || r.lineas.length === 0)
  const sinReceta = (recetas ?? []).filter((r) => r.lineas.length === 0).length

  return (
    <>
      <p className="nota">
        La receta dice cuánto de cada insumo lleva <strong>un</strong> plato. Cuando el pedido entra a cocina se descuenta solo.
        Los platos sin receta no descuentan nada.
      </p>
      {sinReceta > 0 && (
        <fieldset className="segmentado segmentado-chico" aria-label="Filtro">
          <label><input type="radio" name="filtro-rec" checked={solo === 'todas'} onChange={() => setSolo('todas')} />Todos</label>
          <label><input type="radio" name="filtro-rec" checked={solo === 'sin'} onChange={() => setSolo('sin')} />Sin receta ({sinReceta})</label>
        </fieldset>
      )}
      {error && <p className="error">{error.message}</p>}
      <ul className="gestion-lista">
        {lista.map((r) => (
          <li key={r.productoId}>
            <span className="gestion-info">
              <strong>{r.producto} {r.agotadoPorInventario && <span className="chip chip-apagado">Agotado</span>}</strong>
              <small>
                {r.lineas.length === 0 ? 'Sin receta' : r.lineas.map((l) => `${cantidad(l.cantidad, l.unidad)} ${l.insumo}`).join(', ')}
                {r.costoUsd != null && ` · cuesta ${usd(r.costoUsd)} de ${usd(r.precioUsd)} (${Math.round((r.costoUsd / r.precioUsd) * 100)}%)`}
                {r.alcanzaPara != null && ` · alcanza para ${r.alcanzaPara}`}
              </small>
            </span>
            {esGerencia && <button type="button" className="btn btn-texto" onClick={() => setEditar(r)}>{r.lineas.length ? 'Editar' : 'Armar'}</button>}
          </li>
        ))}
      </ul>
      {editar && (
        <HojaReceta receta={editar} insumos={insumos.filter((i) => i.activo)} onCerrar={() => setEditar(null)}
                    onListo={() => { setEditar(null); recargar(); onCambio() }} />
      )}
    </>
  )
}

function HojaReceta({ receta, insumos, onCerrar, onListo }) {
  const aviso = useAviso()
  const [lineas, setLineas] = useState(receta.lineas.map((l) => ({ insumoId: l.insumoId, texto: String(l.cantidad).replace('.', ',') })))
  const [enviando, setEnviando] = useState(false)
  const porId = useMemo(() => new Map(insumos.map((i) => [i.id, i])), [insumos])
  const usados = new Set(lineas.map((l) => l.insumoId))
  const libres = insumos.filter((i) => !usados.has(i.id))
  const validas = lineas.every((l) => porId.has(l.insumoId) && leerMonto(l.texto) > 0)
  const costo = lineas.reduce((s, l) => {
    const i = porId.get(l.insumoId)
    const n = leerMonto(l.texto)
    return s == null || !i || i.costoUnitarioUsd == null || !(n > 0) ? null : s + n * Number(i.costoUnitarioUsd)
  }, 0)
  const costoPlato = costo == null ? null : Math.round(costo * 100) / 100   // igual que el servidor

  const cambiar = (k, cambio) => setLineas(lineas.map((l, j) => (j === k ? { ...l, ...cambio } : l)))

  async function guardar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      await api(`/api/inventario/recetas/${receta.productoId}`, { method: 'PUT',
        body: { lineas: lineas.map((l) => ({ insumoId: l.insumoId, cantidad: leerMonto(l.texto) })) } })
      aviso(`Receta de ${receta.producto} guardada`)
      onListo()
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo={`Receta: ${receta.producto}`} onCerrar={onCerrar}>
      <form className="form" onSubmit={guardar}>
        <p className="nota">Cuánto lleva <strong>un</strong> plato. Para 150 gramos de carne escribe 0,15 kg.</p>
        {insumos.length === 0 && <p className="vacio-texto">Primero agrega insumos en la pestaña Stock.</p>}
        {lineas.map((l, k) => {
          const i = porId.get(l.insumoId)
          return (
            <div key={k} className="linea-receta">
              <label>Insumo
                <select value={l.insumoId} onChange={(e) => cambiar(k, { insumoId: Number(e.target.value) })}>
                  {[...(i ? [i] : []), ...libres].map((x) => <option key={x.id} value={x.id}>{x.nombre}</option>)}
                </select>
              </label>
              <label>Cantidad ({i ? UNIDAD[i.unidad] : ''})
                <input inputMode="decimal" value={l.texto} onChange={(e) => cambiar(k, { texto: e.target.value })} required />
              </label>
              <button type="button" className="btn btn-texto btn-peligro" aria-label={`Quitar ${i?.nombre ?? 'insumo'}`}
                      onClick={() => setLineas(lineas.filter((_, j) => j !== k))}>Quitar</button>
            </div>
          )
        })}
        {libres.length > 0 && (
          <button type="button" className="btn btn-linea" onClick={() => setLineas([...lineas, { insumoId: libres[0].id, texto: '' }])}>
            Agregar insumo a la receta
          </button>
        )}
        {lineas.length > 0 && costoPlato != null && (
          <p className="nota">Costo del plato: <strong>{usd(costoPlato)}</strong> de {usd(receta.precioUsd)} ({Math.round((costoPlato / receta.precioUsd) * 100)}% del precio).</p>
        )}
        <button className="btn btn-oro btn-grande" disabled={!validas || enviando}>
          {lineas.length ? 'Guardar receta' : 'Guardar sin receta'}
        </button>
      </form>
    </Hoja>
  )
}

function Movimientos({ insumos }) {
  const [insumo, setInsumo] = useState('')
  const { datos, error } = useCarga(`/api/inventario/movimientos?limite=100${insumo ? `&insumoId=${insumo}` : ''}`, 30)
  return (
    <section className="bloque">
      <label className="form filtro-insumo">Insumo
        <select value={insumo} onChange={(e) => setInsumo(e.target.value)}>
          <option value="">Todos</option>
          {insumos.map((i) => <option key={i.id} value={i.id}>{i.nombre}</option>)}
        </select>
      </label>
      {error && <p className="error">{error.message}</p>}
      {datos && datos.length === 0 && <p className="vacio-texto">Todavía no hay movimientos.</p>}
      {datos && datos.length > 0 && (
        <div className="tabla-scroll">
          <table className="tabla">
            <thead><tr><th>Cuándo</th><th>Insumo</th><th>Qué</th><th>Cantidad</th><th>Queda</th><th>Quién</th><th>Detalle</th></tr></thead>
            <tbody>
              {datos.map((m) => (
                <tr key={m.id}>
                  <td>{fechaHora(m.creadoEn)}</td>
                  <td>{m.insumo}</td>
                  <td>{TIPO[m.tipo]}</td>
                  <td className={Number(m.cantidad) < 0 ? 'mov-resta' : 'mov-suma'}>{Number(m.cantidad) > 0 ? '+' : ''}{cantidad(m.cantidad, m.unidad)}</td>
                  <td>{cantidad(m.stockResultante, m.unidad)}</td>
                  <td>{m.usuario ?? '—'}</td>
                  <td className="mov-nota">
                    {m.pedidoId ? <Link to={`/pedido/${m.pedidoId}`}>{m.nota}</Link> : m.nota}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
