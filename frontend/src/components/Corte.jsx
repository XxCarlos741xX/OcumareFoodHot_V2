import { useState } from 'react'
import { api } from '../api/client'
import { METODOS, bs, leerMonto, usd } from '../lib/dinero'
import { useAviso } from './Aviso'

const NOMBRE_METODO = Object.fromEntries(METODOS.map((m) => [m.id, m.nombre]))
const monto = (moneda, n) => (moneda === 'USD' ? usd(n) : bs(n))

/** Las cifras grandes del corte: lo que se ve de entrada. */
export function Totales({ t }) {
  return (
    <>
      <div className="cifras">
        <div className="cifra cifra-principal">
          <span>Entró en dólares</span>
          <strong>{usd(t.recaudadoUsd)}</strong>
        </div>
        <div className="cifra cifra-principal">
          <span>Entró en bolívares</span>
          <strong>{bs(t.recaudadoBs)}</strong>
        </div>
        <div className="cifra">
          <span>Todo lo cobrado, en $</span>
          <strong>{usd(t.recaudadoTotalEquivUsd)}</strong>
        </div>
        <div className="cifra">
          <span>Vendido</span>
          <strong>{usd(t.ventaUsd)}</strong>
          <small>{t.pedidos} {t.pedidos === 1 ? 'pedido' : 'pedidos'}, ticket {usd(t.ticketPromedioUsd)}</small>
        </div>
      </div>
      {(t.pendientes > 0 || t.anulados > 0) && (
        <p className="nota cifras-nota">
          {t.pendientes > 0 && `${t.pendientes} ${t.pendientes === 1 ? 'pedido sigue abierto' : 'pedidos siguen abiertos'}. `}
          {t.anulados > 0 && `${t.anulados} ${t.anulados === 1 ? 'anulado' : 'anulados'}.`}
        </p>
      )}
    </>
  )
}

export function TablaMetodos({ filas }) {
  if (!filas?.length) return null
  return (
    <section className="bloque">
      <h2>Por forma de pago</h2>
      <table className="tabla">
        <thead><tr><th>Forma de pago</th><th className="num">Cobros</th><th className="num">Monto</th><th className="num">En $</th></tr></thead>
        <tbody>
          {filas.map((f) => (
            <tr key={f.metodo} className={f.cantidad === 0 ? 'en-cero' : ''}>
              <td>{NOMBRE_METODO[f.metodo]}</td>
              <td className="num">{f.cantidad}</td>
              <td className="num">{monto(f.moneda, f.monto)}</td>
              <td className="num">{usd(f.equivalenteUsd)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

export function TablaMeseros({ filas }) {
  if (!filas?.length) return null
  return (
    <section className="bloque">
      <h2>Por mesero</h2>
      <div className="tabla-scroll">
        <table className="tabla">
          <thead><tr><th>Mesero</th><th className="num">Pedidos</th><th className="num">Vendido</th><th className="num">Cobrado $</th><th className="num">Cobrado Bs</th></tr></thead>
          <tbody>
            {filas.map((f) => (
              <tr key={f.meseroId ?? f.mesero}>
                <td>{f.mesero}</td>
                <td className="num">{f.pedidos}</td>
                <td className="num">{usd(f.ventaUsd)}</td>
                <td className="num">{usd(f.cobradoUsd)}</td>
                <td className="num">{bs(f.cobradoBs)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

const ETIQUETA_CUADRE = { CUADRA: 'Cuadra', SOBRA: 'Sobra', FALTA: 'Falta', SIN_CONTAR: 'Sin contar' }
const SIMBOLO = { CUADRA: '✓', SOBRA: '+', FALTA: '−', SIN_CONTAR: '·' }

function Cuadre({ estado, diferencia, moneda }) {
  return (
    <span className={`cuadre cuadre-${estado.toLowerCase()}`}>
      <span aria-hidden="true">{SIMBOLO[estado]}</span>
      {ETIQUETA_CUADRE[estado]}
      {(estado === 'SOBRA' || estado === 'FALTA') && <> {monto(moneda, Math.abs(diferencia))}</>}
    </span>
  )
}

function resumenCuadre(c) {
  if (c.cuadreUsd === 'SIN_CONTAR') return 'Sin contar'
  if (c.cuadreUsd === 'CUADRA' && c.cuadreBs === 'CUADRA') return 'Cuadra'
  return 'Con diferencia'
}

/**
 * Conteo de caja en un desplegable cerrado por defecto: de entrada solo se ven los totales.
 * Si el turno está cerrado y no se ha contado, permite registrarlo aquí.
 */
export function ConteoCaja({ c, turnoId, cerrado, onActualizado }) {
  const aviso = useAviso()
  const [usdTxt, setUsdTxt] = useState('')
  const [bsTxt, setBsTxt] = useState('')
  const [editando, setEditando] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const contado = c.cuadreUsd !== 'SIN_CONTAR'

  async function guardar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      const r = await api(`/api/turnos/${turnoId}/conteo`, { method: 'PUT', body: { efectivoUsd: leerMonto(usdTxt), efectivoBs: leerMonto(bsTxt) } })
      aviso('Conteo guardado')
      setEditando(false)
      onActualizado(r)
    } catch (err) {
      aviso(err.message, 'error')
    } finally {
      setEnviando(false)
    }
  }

  const valido = !Number.isNaN(leerMonto(usdTxt)) && !Number.isNaN(leerMonto(bsTxt))

  return (
    <details className="desplegable">
      <summary>
        <span>Conteo de caja</span>
        <span className={`resumen-cuadre ${contado ? '' : 'pendiente'}`}>{resumenCuadre(c)}</span>
      </summary>
      <div className="desplegable-cuerpo">
        <p className="nota">Solo efectivo. Pago móvil, punto, Zelle y transferencias van al banco.</p>
        <div className="tabla-scroll">
        <table className="tabla tabla-conteo">
          <thead><tr><th /><th className="num">Dólares</th><th className="num">Bolívares</th></tr></thead>
          <tbody>
            <tr><td>Fondo inicial</td><td className="num">{usd(c.fondoInicialUsd)}</td><td className="num">{bs(c.fondoInicialBs)}</td></tr>
            <tr><td>Cobrado en efectivo</td><td className="num">{usd(c.cobradoEfectivoUsd)}</td><td className="num">{bs(c.cobradoEfectivoBs)}</td></tr>
            <tr className="fila-fuerte"><td>Debería haber</td><td className="num">{usd(c.esperadoUsd)}</td><td className="num">{bs(c.esperadoBs)}</td></tr>
            {contado && (
              <>
                <tr><td>Contado</td><td className="num">{usd(c.contadoUsd)}</td><td className="num">{bs(c.contadoBs)}</td></tr>
                <tr><td>Resultado</td>
                  <td className="num celda-cuadre"><Cuadre estado={c.cuadreUsd} diferencia={c.diferenciaUsd} moneda="USD" /></td>
                  <td className="num celda-cuadre"><Cuadre estado={c.cuadreBs} diferencia={c.diferenciaBs} moneda="BS" /></td>
                </tr>
              </>
            )}
          </tbody>
        </table>
        </div>
        {contado && c.contadoPor && <p className="nota">Contó {c.contadoPor}.</p>}
        {cerrado && !editando && (
          <button type="button" className="btn btn-linea" onClick={() => setEditando(true)}>
            {contado ? 'Corregir conteo' : 'Registrar conteo'}
          </button>
        )}
        {!cerrado && <p className="nota">El conteo se registra al cerrar el turno.</p>}
        {editando && (
          <form className="form" onSubmit={guardar}>
            <div className="fila-2">
              <label>Efectivo contado en $<input inputMode="decimal" value={usdTxt} onChange={(e) => setUsdTxt(e.target.value)} placeholder="0" required /></label>
              <label>Efectivo contado en Bs<input inputMode="decimal" value={bsTxt} onChange={(e) => setBsTxt(e.target.value)} placeholder="0" required /></label>
            </div>
            <div className="fila-2">
              <button type="button" className="btn btn-linea" onClick={() => setEditando(false)}>Cancelar</button>
              <button className="btn btn-oro" disabled={!valido || enviando}>Guardar conteo</button>
            </div>
          </form>
        )}
      </div>
    </details>
  )
}
