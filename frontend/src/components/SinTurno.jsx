import { useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useOperacion } from '../auth/OperacionContext'
import { leerMonto } from '../lib/dinero'
import { useAviso } from './Aviso'

/** Lo que se ve cuando no hay turno abierto. El gerente lo puede abrir aquí mismo. */
export default function SinTurno() {
  const { esGerencia } = useAuth()
  const { refrescar } = useOperacion()
  const aviso = useAviso()
  const hora = new Date().getHours()
  const [tipo, setTipo] = useState(hora >= 6 && hora < 18 ? 'DIA' : 'NOCHE')
  const [fondoUsd, setFondoUsd] = useState('')
  const [fondoBs, setFondoBs] = useState('')
  const [enviando, setEnviando] = useState(false)

  if (!esGerencia) {
    return (
      <section className="vacio">
        <h2>No hay un turno abierto</h2>
        <p>Pide al gerente que abra el turno para empezar a tomar pedidos.</p>
      </section>
    )
  }

  async function abrir(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      await api('/api/turnos/abrir', { method: 'POST', body: {
        tipo,
        fondoInicialUsd: fondoUsd ? leerMonto(fondoUsd) : 0,
        fondoInicialBs: fondoBs ? leerMonto(fondoBs) : 0,
      } })
      await refrescar()
      aviso('Turno abierto')
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <section className="vacio">
      <h2>Abre el turno para empezar</h2>
      <form onSubmit={abrir} className="form form-estrecho">
        <fieldset className="segmentado">
          <legend>Turno</legend>
          <label><input type="radio" name="tipo" checked={tipo === 'DIA'} onChange={() => setTipo('DIA')} />Día</label>
          <label><input type="radio" name="tipo" checked={tipo === 'NOCHE'} onChange={() => setTipo('NOCHE')} />Noche</label>
        </fieldset>
        <div className="fila-2">
          <label>Fondo en $<input inputMode="decimal" value={fondoUsd} onChange={(e) => setFondoUsd(e.target.value)} placeholder="0" /></label>
          <label>Fondo en Bs<input inputMode="decimal" value={fondoBs} onChange={(e) => setFondoBs(e.target.value)} placeholder="0" /></label>
        </div>
        <p className="nota">El fondo es el efectivo con que arranca la caja para dar vuelto.</p>
        <button className="btn btn-oro btn-grande" disabled={enviando}>Abrir turno</button>
      </form>
    </section>
  )
}
