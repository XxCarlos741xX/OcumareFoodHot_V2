import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useOperacion } from '../auth/OperacionContext'
import { useAviso } from '../components/Aviso'
import { ConteoCaja, TablaMeseros, TablaMetodos, Totales } from '../components/Corte'
import Hoja from '../components/Hoja'
import SinTurno from '../components/SinTurno'
import { bs, leerMonto, usd } from '../lib/dinero'
import { useCarga } from '../lib/useCarga'

const fechaHora = (iso) => (iso ? new Date(iso).toLocaleString('es-VE', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '')
const fechaLarga = (f) => {
  const t = new Date(`${f}T12:00`).toLocaleDateString('es-VE', { weekday: 'long', day: 'numeric', month: 'long' })
  return t.charAt(0).toUpperCase() + t.slice(1)
}
const nombreTurno = (t) => `Turno de ${t.tipo === 'DIA' ? 'día' : 'noche'}`

/** /caja: corte en vivo del turno abierto, botón para cerrarlo e historial. */
export default function Caja() {
  const { turno } = useOperacion()
  if (turno === undefined) return <div className="pagina"><p className="cargando">Cargando…</p></div>
  return (
    <div className="pagina">
      {turno ? <CorteTurno id={turno.id} vivo /> : <SinTurno />}
      <Historial excluir={turno?.id} />
    </div>
  )
}

/** /caja/turno/:id: corte de un turno pasado. */
export function CajaTurno() {
  const { id } = useParams()
  const navegar = useNavigate()
  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        <button type="button" className="volver" onClick={() => navegar('/caja')} aria-label="Volver a caja">‹</button>
      </div>
      <CorteTurno id={Number(id)} />
    </div>
  )
}

function CorteTurno({ id, vivo }) {
  const { datos: corte, setDatos, error } = useCarga(`/api/turnos/${id}/corte`, vivo ? 30 : 0)
  const [cerrar, setCerrar] = useState(false)

  if (error) return <p className="error">{error.message}</p>
  if (!corte) return <p className="cargando">Cargando el corte…</p>
  const t = corte.turno
  const cerrado = t.estado === 'CERRADO'

  return (
    <>
      <div className="pagina-cabeza">
        <h1>{nombreTurno(t)}</h1>
        <span className={`chip ${cerrado ? '' : 'chip-vivo'}`}>{cerrado ? 'Cerrado' : 'Abierto'}</span>
      </div>
      <p className="nota subtitulo">
        {fechaLarga(t.fechaOperativa)}. Lo abrió {t.abiertoPor} ({fechaHora(t.abiertoEn)})
        {cerrado ? ` y lo cerró ${t.cerradoPor} (${fechaHora(t.cerradoEn)}).` : '.'}
      </p>

      <Totales t={corte.totales} />
      <ConteoCaja c={corte.conteoCaja} turnoId={t.id} cerrado={cerrado} onActualizado={setDatos} />
      <TablaMetodos filas={corte.porMetodo} />
      <TablaMeseros filas={corte.porMesero} />
      {corte.observaciones && <section className="bloque"><h2>Observaciones</h2><p>{corte.observaciones}</p></section>}

      {vivo && !cerrado && (
        <div className="acciones-pedido">
          <button type="button" className="btn btn-oro btn-grande" onClick={() => setCerrar(true)}>Cerrar turno</button>
        </div>
      )}
      {cerrar && <HojaCerrar corte={corte} onCerrar={() => setCerrar(false)} />}
    </>
  )
}

function HojaCerrar({ corte, onCerrar }) {
  const aviso = useAviso()
  const navegar = useNavigate()
  const { refrescar } = useOperacion()
  const [usdTxt, setUsdTxt] = useState('')
  const [bsTxt, setBsTxt] = useState('')
  const [obs, setObs] = useState('')
  const [enviando, setEnviando] = useState(false)
  const c = corte.conteoCaja
  const conConteo = usdTxt.trim() !== '' || bsTxt.trim() !== ''
  const usdN = usdTxt.trim() ? leerMonto(usdTxt) : 0
  const bsN = bsTxt.trim() ? leerMonto(bsTxt) : 0
  const valido = !conConteo || (!Number.isNaN(usdN) && !Number.isNaN(bsN))

  async function cerrarTurno(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      const r = await api('/api/turnos/cerrar', { method: 'POST', body: {
        conteo: conConteo ? { efectivoUsd: usdN, efectivoBs: bsN } : null,
        observaciones: obs.trim() || null,
      } })
      await refrescar()
      aviso('Turno cerrado')
      navegar(`/caja/turno/${r.turno.id}`, { replace: true })
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo={`Cerrar ${nombreTurno(corte.turno).toLowerCase()}`} onCerrar={onCerrar}>
      <form className="form" onSubmit={cerrarTurno}>
        <p>Entraron <strong>{usd(corte.totales.recaudadoUsd)}</strong> y <strong>{bs(corte.totales.recaudadoBs)}</strong>.</p>
        {corte.totales.pendientes > 0 && (
          <p className="nota">Hay {corte.totales.pendientes} {corte.totales.pendientes === 1 ? 'pedido abierto: pasa' : 'pedidos abiertos: pasan'} al siguiente turno y se cobran allá.</p>
        )}
        <fieldset className="grupo">
          <legend>Efectivo contado en caja (opcional)</legend>
          <p className="nota">Debería haber {usd(c.esperadoUsd)} y {bs(c.esperadoBs)}. Si no lo cuentas ahora, lo registras después.</p>
          <div className="fila-2">
            <label>En dólares<input inputMode="decimal" value={usdTxt} onChange={(e) => setUsdTxt(e.target.value)} placeholder="Sin contar" /></label>
            <label>En bolívares<input inputMode="decimal" value={bsTxt} onChange={(e) => setBsTxt(e.target.value)} placeholder="Sin contar" /></label>
          </div>
        </fieldset>
        <label>Observaciones<textarea rows={2} value={obs} onChange={(e) => setObs(e.target.value)} placeholder="Novedades del turno" /></label>
        <button className="btn btn-oro btn-grande" disabled={!valido || enviando}>Cerrar turno</button>
      </form>
    </Hoja>
  )
}

function Historial({ excluir }) {
  const { datos: turnos } = useCarga('/api/turnos')
  const lista = (turnos ?? []).filter((t) => t.id !== excluir)
  const [ver, setVer] = useState(10)
  useEffect(() => setVer(10), [excluir])
  if (!lista.length) return null
  return (
    <section className="bloque">
      <h2>Turnos anteriores</h2>
      <ul className="lista-turnos">
        {lista.slice(0, ver).map((t) => (
          <li key={t.id}>
            <Link to={`/caja/turno/${t.id}`}>
              <span><strong>{fechaLarga(t.fechaOperativa)}</strong> {t.tipo === 'DIA' ? 'día' : 'noche'}</span>
              <span className="num">{usd(t.recaudadoUsd)}</span>
              <span className="num">{bs(t.recaudadoBs)}</span>
            </Link>
          </li>
        ))}
      </ul>
      {lista.length > ver && <button type="button" className="btn btn-texto" onClick={() => setVer(ver + 20)}>Ver más</button>}
    </section>
  )
}
