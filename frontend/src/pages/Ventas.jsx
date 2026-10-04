import { Link } from 'react-router-dom'
import { useState } from 'react'
import { TablaMeseros, TablaMetodos, Totales } from '../components/Corte'
import GraficaRecaudo from '../components/GraficaRecaudo'
import { bs, usd } from '../lib/dinero'
import { useCarga } from '../lib/useCarga'

const hoyLocal = () => {
  const d = new Date()
  return new Date(d.getTime() - d.getTimezoneOffset() * 6e4).toISOString().slice(0, 10)
}
const menos = (fecha, dias) => {
  const d = new Date(`${fecha}T12:00`)
  d.setDate(d.getDate() - dias)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Ventas: el día elegido (los dos turnos) y la evolución del recaudo. */
export default function Ventas() {
  const [fecha, setFecha] = useState(hoyLocal())
  const [rango, setRango] = useState(7)
  const [verTabla, setVerTabla] = useState(false)
  const { datos: dia, error } = useCarga(`/api/dashboard/dia?fecha=${fecha}`, 60)
  const hasta = hoyLocal()
  const { datos: serie } = useCarga(`/api/dashboard/rango?desde=${menos(hasta, rango - 1)}&hasta=${hasta}`, 120)

  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        <Link to="/gestion" className="volver" aria-label="Volver a gestión">‹</Link>
        <h1>Ventas</h1>
        <label className="campo-fecha">
          <span className="oculto">Día</span>
          <input type="date" value={fecha} max={hoyLocal()} onChange={(e) => e.target.value && setFecha(e.target.value)} />
        </label>
      </div>
      {error && <p className="error">{error.message}</p>}

      {dia && (
        <>
          <Totales t={dia.totales} />
          {dia.turnos.length > 1 && (
            <section className="bloque">
              <h2>Por turno</h2>
              <table className="tabla">
                <thead><tr><th>Turno</th><th className="num">Pedidos</th><th className="num">Entró $</th><th className="num">Entró Bs</th></tr></thead>
                <tbody>
                  {dia.turnos.map((t) => (
                    <tr key={t.turnoId}>
                      <td>{t.tipo === 'DIA' ? 'Día' : 'Noche'}{t.estado === 'ABIERTO' && ' (abierto)'}</td>
                      <td className="num">{t.totales.pedidos}</td>
                      <td className="num">{usd(t.totales.recaudadoUsd)}</td>
                      <td className="num">{bs(t.totales.recaudadoBs)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          )}
          <TablaMetodos filas={dia.porMetodo} />
          <TablaMeseros filas={dia.porMesero} />
          {dia.turnos.length === 0 && dia.totales.recaudadoTotalEquivUsd === 0 && (
            <p className="vacio-texto">No hubo turnos ese día.</p>
          )}
        </>
      )}

      <section className="bloque">
        <div className="bloque-cabeza">
          <h2>Recaudo diario, en dólares</h2>
          <fieldset className="segmentado segmentado-chico">
            <legend className="oculto">Período</legend>
            {[7, 30].map((n) => (
              <label key={n}><input type="radio" name="rango" checked={rango === n} onChange={() => setRango(n)} />{n} días</label>
            ))}
          </fieldset>
        </div>
        {serie && (
          <>
            <p className="nota">
              En {rango} días entraron {usd(serie.recaudadoUsd)} y {bs(serie.recaudadoBs)}: {usd(serie.totalEquivUsd)} en total.
              Los bolívares se suman convertidos con la tasa de cada cobro.
            </p>
            {verTabla ? (
              <div className="tabla-scroll">
                <table className="tabla">
                  <thead><tr><th>Día</th><th className="num">Dólares</th><th className="num">Bolívares</th><th className="num">Total en $</th></tr></thead>
                  <tbody>
                    {[...serie.dias].reverse().map((d) => (
                      <tr key={d.fecha}><td>{d.fecha}</td><td className="num">{usd(d.recaudadoUsd)}</td><td className="num">{bs(d.recaudadoBs)}</td><td className="num">{usd(d.totalEquivUsd)}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <GraficaRecaudo dias={serie.dias} />
            )}
            <button type="button" className="btn btn-texto" onClick={() => setVerTabla(!verTabla)}>
              {verTabla ? 'Ver gráfica' : 'Ver como tabla'}
            </button>
          </>
        )}
      </section>
    </div>
  )
}
