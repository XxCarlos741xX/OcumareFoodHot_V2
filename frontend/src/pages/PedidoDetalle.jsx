import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useOperacion } from '../auth/OperacionContext'
import { useAviso } from '../components/Aviso'
import Hoja from '../components/Hoja'
import { ESTADOS, METODOS, SIGUIENTE, TIPOS, bs, leerMonto, numero, redondear, usd } from '../lib/dinero'
import { useCarga } from '../lib/useCarga'

const PASOS = ['ABIERTO', 'EN_PREPARACION', 'LISTO', 'ENTREGADO']
const EDITABLES = new Set(PASOS)

export default function PedidoDetalle() {
  const { id } = useParams()
  const navegar = useNavigate()
  const aviso = useAviso()
  const { esGerencia } = useAuth()
  const { datos: p, setDatos, error } = useCarga(`/api/pedidos/${id}`, 20)
  const [cobrar, setCobrar] = useState(false)
  const [anular, setAnular] = useState(false)
  const [ocupado, setOcupado] = useState(false)

  if (error) return <div className="pagina"><p className="error">{error.message}</p></div>
  if (!p) return <div className="pagina"><p className="cargando">Cargando pedido…</p></div>

  const editable = EDITABLES.has(p.estado)
  const siguiente = SIGUIENTE[p.estado]
  const saldado = p.saldoPendienteUsd <= 0.05

  async function accion(fn, mensaje) {
    setOcupado(true)
    try {
      const r = await fn()
      setDatos(r)
      if (mensaje) aviso(typeof mensaje === 'function' ? mensaje(r) : mensaje)
    } catch (err) {
      aviso(err.message, 'error')
    } finally {
      setOcupado(false)
    }
  }

  const avanzar = () => accion(
    () => api(`/api/pedidos/${id}/estado`, { method: 'PATCH', body: { estado: siguiente.estado } }),
    (r) => (r.estado === 'PAGADO' ? 'Pedido entregado y cerrado' : `Pedido ${ESTADOS[r.estado].toLowerCase()}`))

  const quitar = (d) => accion(() => api(`/api/pedidos/${id}/items/${d.id}`, { method: 'DELETE' }), `${d.nombre} quitado`)

  return (
    <div className="pagina pagina-detalle">
      <div className="pagina-cabeza">
        <button type="button" className="volver" onClick={() => navegar(-1)} aria-label="Volver">‹</button>
        <h1>{p.tipo === 'MESA' ? `Mesa ${p.mesaNumero}` : TIPOS[p.tipo]} <span className="num-pedido">#{p.numeroDiario}</span></h1>
      </div>

      {p.estado === 'ANULADO' ? (
        <p className="banda banda-anulado">Anulado: {p.motivoAnulacion}</p>
      ) : p.estado === 'PAGADO' ? (
        <p className="banda banda-cerrado">Entregado y pagado</p>
      ) : (
        <ol className="pasos" aria-label="Estado del pedido">
          {PASOS.map((paso, i) => (
            <li key={paso} className={i < PASOS.indexOf(p.estado) ? 'hecho' : i === PASOS.indexOf(p.estado) ? 'actual' : ''}
                aria-current={paso === p.estado ? 'step' : undefined}>{ESTADOS[paso]}</li>
          ))}
        </ol>
      )}

      {(p.clienteNombre || p.clienteTelefono || p.direccionEntrega || p.notas) && (
        <div className="cliente">
          {p.clienteNombre && <p><strong>{p.clienteNombre}</strong></p>}
          {p.clienteTelefono && <p><a href={`tel:${p.clienteTelefono}`}>{p.clienteTelefono}</a></p>}
          {p.direccionEntrega && <p>{p.direccionEntrega}</p>}
          {p.notas && <p className="nota">{p.notas}</p>}
        </div>
      )}

      <ul className="lineas">
        {p.detalles.map((d) => (
          <li key={d.id}>
            <span className="linea-cant">{d.cantidad}</span>
            <span className="linea-nombre">{d.nombre}{d.notas && <small>{d.notas}</small>}</span>
            <span className="linea-monto">{usd(d.subtotalUsd)}</span>
            {editable && p.detalles.length > 1 && (
              <button type="button" className="quitar" disabled={ocupado} onClick={() => quitar(d)} aria-label={`Quitar ${d.nombre}`}>×</button>
            )}
          </li>
        ))}
      </ul>
      {editable && (
        <button type="button" className="btn btn-linea btn-ancho" onClick={() => navegar(`/pedido/${id}/agregar`)}>Agregar productos</button>
      )}

      <dl className="cuenta">
        {p.costoDeliveryUsd > 0 && <><dt>Envío</dt><dd>{usd(p.costoDeliveryUsd)}</dd></>}
        <dt className="cuenta-total">Total</dt>
        <dd className="cuenta-total">{usd(p.totalUsd)}<small>{p.totalBs != null ? bs(p.totalBs) : 'Sin tasa: solo en $'}</small></dd>
        {p.pagos.length > 0 && <><dt>Pagado</dt><dd>{usd(p.pagadoUsd)}</dd></>}
        {p.pagos.length > 0 && !saldado && (
          <><dt>Falta</dt><dd className="falta">{usd(p.saldoPendienteUsd)}<small>{p.saldoPendienteBs != null ? bs(p.saldoPendienteBs) : ''}</small></dd></>
        )}
      </dl>

      {p.pagos.length > 0 && (
        <ul className="pagos">
          {p.pagos.map((pg) => (
            <li key={pg.id}>
              <span>{METODOS.find((m) => m.id === pg.metodo)?.nombre}{pg.referencia && <small>Ref. {pg.referencia}</small>}</span>
              <span>{pg.moneda === 'USD' ? usd(pg.monto) : bs(pg.monto)}</span>
            </li>
          ))}
        </ul>
      )}

      {editable && (
        <div className="acciones-pedido">
          {siguiente && <button type="button" className="btn btn-oro btn-grande" disabled={ocupado} onClick={avanzar}>{siguiente.accion}</button>}
          {!saldado && (
            <button type="button" className={`btn btn-grande ${siguiente ? 'btn-linea' : 'btn-oro'}`} disabled={ocupado} onClick={() => setCobrar(true)}>
              Cobrar {usd(p.saldoPendienteUsd)}
            </button>
          )}
          {esGerencia && p.pagos.length === 0 && (
            <button type="button" className="btn btn-texto btn-peligro" onClick={() => setAnular(true)}>Anular pedido</button>
          )}
        </div>
      )}

      {cobrar && <HojaCobro pedido={p} onCerrar={() => setCobrar(false)}
                            onCobrado={(r) => { setDatos(r); setCobrar(false) }} />}
      {anular && <HojaAnular pedido={p} onCerrar={() => setAnular(false)}
                             onAnulado={(r) => { setDatos(r); setAnular(false) }} />}
    </div>
  )
}

function HojaCobro({ pedido, onCerrar, onCobrado }) {
  const aviso = useAviso()
  const { tasa: tasaVigente } = useOperacion()
  const tasa = pedido.tasaBsPorUsd ?? tasaVigente?.bsPorUsd ?? null
  const [metodo, setMetodo] = useState(METODOS[0])
  const sugerido = (m) => {
    if (m.moneda === 'USD') return String(pedido.saldoPendienteUsd).replace('.', ',')
    return tasa ? String(redondear(pedido.saldoPendienteUsd * tasa)).replace('.', ',') : ''
  }
  const [monto, setMonto] = useState(sugerido(METODOS[0]))
  const [referencia, setReferencia] = useState('')
  const [enviando, setEnviando] = useState(false)
  const valor = leerMonto(monto)
  const sinTasa = metodo.moneda === 'BS' && !tasa
  const equivalente = metodo.moneda === 'USD' ? valor : tasa ? redondear(valor / tasa) : NaN
  const quedaria = redondear(pedido.saldoPendienteUsd - (Number.isNaN(equivalente) ? 0 : equivalente))

  function elegir(m) {
    setMetodo(m)
    setMonto(sugerido(m))
  }

  async function registrar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      const r = await api(`/api/pedidos/${pedido.id}/pagos`, { method: 'POST', body: {
        metodo: metodo.id, monto: valor, referencia: referencia.trim() || null,
      } })
      aviso(r.saldoPendienteUsd <= 0.05 ? 'Cobro registrado. Pedido pagado.' : `Cobro registrado. Falta ${usd(r.saldoPendienteUsd)}`)
      onCobrado(r)
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo={`Cobrar ${usd(pedido.saldoPendienteUsd)}`} onCerrar={onCerrar}>
      <form className="form" onSubmit={registrar}>
        <fieldset className="metodos">
          <legend>Cómo paga</legend>
          {METODOS.map((m) => (
            <label key={m.id} className={metodo.id === m.id ? 'elegido' : ''}>
              <input type="radio" name="metodo" checked={metodo.id === m.id} onChange={() => elegir(m)} />
              <span>{m.nombre}</span>
            </label>
          ))}
        </fieldset>
        {sinTasa ? (
          <p className="error">No hay tasa registrada. Pide al gerente que la cargue para cobrar en bolívares.</p>
        ) : (
          <>
            <label>Monto en {metodo.moneda === 'USD' ? 'dólares' : 'bolívares'}
              <input inputMode="decimal" value={monto} onChange={(e) => setMonto(e.target.value)} />
            </label>
            {metodo.referencia && (
              <label>Referencia<input inputMode="numeric" value={referencia} onChange={(e) => setReferencia(e.target.value)} required /></label>
            )}
            {!Number.isNaN(valor) && valor > 0 && (
              <p className="nota">
                {metodo.moneda === 'BS' && `Equivale a ${usd(equivalente)} con tasa ${numero(tasa)}. `}
                {quedaria > 0.05 ? `Quedan ${usd(quedaria)} por cobrar.` : quedaria < -0.05 ? 'Es más que el saldo: registra solo lo que se cobra.' : 'Con esto queda pagado.'}
              </p>
            )}
          </>
        )}
        <button className="btn btn-oro btn-grande" disabled={sinTasa || !(valor > 0) || enviando || quedaria < -0.05}>
          Registrar cobro
        </button>
      </form>
    </Hoja>
  )
}

function HojaAnular({ pedido, onCerrar, onAnulado }) {
  const aviso = useAviso()
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)
  async function anular(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      const r = await api(`/api/pedidos/${pedido.id}/anular`, { method: 'POST', body: { motivo } })
      aviso(`Pedido #${pedido.numeroDiario} anulado`)
      onAnulado(r)
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }
  return (
    <Hoja titulo={`Anular pedido #${pedido.numeroDiario}`} onCerrar={onCerrar}>
      <form className="form" onSubmit={anular}>
        <label>Motivo<input value={motivo} onChange={(e) => setMotivo(e.target.value)} placeholder="El cliente se fue, pedido repetido…" required /></label>
        <p className="nota">El pedido queda en el historial como anulado y libera la mesa.</p>
        <button className="btn btn-peligro btn-grande" disabled={!motivo.trim() || enviando}>Anular pedido</button>
      </form>
    </Hoja>
  )
}
