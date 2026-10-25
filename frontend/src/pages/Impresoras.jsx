import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAviso } from '../components/Aviso'
import Hoja from '../components/Hoja'
import { useCarga } from '../lib/useCarga'

const fechaHora = (iso) => (iso ? new Date(iso).toLocaleString('es-VE', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '')
const ESTADO_COMANDA = { IMPRESA: 'Impresa', PENDIENTE: 'Pendiente', FALLIDA: 'No se pudo', VENCIDA: 'No salió' }
const TIPO_COMANDA = { NUEVA: 'Pedido', ADICIONAL: 'Adicional', CANCELADO: 'Cancelar', ANULADO: 'Anulado', REIMPRESION: 'Copia', PRUEBA: 'Prueba' }

/**
 * Impresoras de cocina (gerencia). Cada impresora tiene una dirección secreta que se configura
 * en la impresora con nube o en el puente. La dirección solo se muestra al crearla.
 */
export default function Impresoras() {
  const aviso = useAviso()
  const { datos: impresoras, recargar, error } = useCarga('/api/impresoras', 15)
  const { datos: comandas, recargar: recargarComandas } = useCarga('/api/comandas', 15)
  const [agregar, setAgregar] = useState(false)
  const [creada, setCreada] = useState(null)
  const activas = (impresoras ?? []).filter((i) => i.activa)

  async function probar() {
    try {
      await api('/api/impresoras/prueba', { method: 'POST', body: {} })
      aviso('Hoja de prueba enviada. Debe salir en unos segundos.')
      recargarComandas()
    } catch (err) {
      aviso(err.message, 'error')
    }
  }

  async function desactivar(i) {
    try {
      await api(`/api/impresoras/${i.id}`, { method: 'DELETE' })
      aviso(`${i.nombre} desactivada: su dirección ya no sirve`)
      recargar()
    } catch (err) {
      aviso(err.message, 'error')
    }
  }

  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        <Link to="/gestion" className="volver" aria-label="Volver a gestión">‹</Link>
        <h1>Impresoras</h1>
      </div>
      <p className="nota">
        Cada vez que un pedido se envía a cocina, sale una comanda en la impresora de cocina, venga del mesero,
        de la caja o de la web. La impresora busca sola las comandas en el sistema, así que funciona aunque el pedido
        se haga desde otro lugar.
      </p>
      <div className="acciones-rapidas">
        <button type="button" className="btn btn-oro" onClick={() => setAgregar(true)}>Agregar impresora</button>
        {activas.length > 0 && <button type="button" className="btn btn-linea" onClick={probar}>Imprimir hoja de prueba</button>}
      </div>
      {error && <p className="error">{error.message}</p>}
      {impresoras && impresoras.length === 0 && (
        <p className="vacio-texto">Todavía no hay impresoras. Mientras tanto, los pedidos se siguen viendo en la pestaña Pedidos.</p>
      )}
      <ul className="gestion-lista">
        {(impresoras ?? []).map((i) => (
          <li key={i.id} className={i.activa ? '' : 'apagado'}>
            <span className="gestion-info">
              <strong>{i.nombre} <span className={`chip ${i.conectada ? 'chip-vivo' : 'chip-apagado'}`}>{!i.activa ? 'Desactivada' : i.conectada ? 'En línea' : 'Sin conexión'}</span></strong>
              <small>
                {i.columnas >= 42 ? 'Papel de 80 mm' : 'Papel de 58 mm'}
                {i.ultimaConexion ? `, se conectó ${fechaHora(i.ultimaConexion)}` : ', nunca se ha conectado'}
                {i.ultimaVia && ` (${i.ultimaVia === 'CLOUDPRNT' ? 'nube' : 'puente'})`}
                {i.ultimoEstado && `, dice: ${i.ultimoEstado}`}
              </small>
            </span>
            {i.activa && <button type="button" className="btn btn-texto btn-peligro" onClick={() => desactivar(i)}>Desactivar</button>}
          </li>
        ))}
      </ul>

      {comandas && comandas.length > 0 && (
        <section className="bloque">
          <h2>Últimas comandas</h2>
          <div className="tabla-scroll">
            <table className="tabla">
              <thead><tr><th>Pedido</th><th>Tipo</th><th>Hora</th><th>Estado</th></tr></thead>
              <tbody>
                {comandas.map((c) => (
                  <tr key={c.id}>
                    <td>{c.pedidoId ? <Link to={`/pedido/${c.pedidoId}`}>#{c.numeroPedido}</Link> : '—'}</td>
                    <td>{TIPO_COMANDA[c.tipo]}</td>
                    <td>{fechaHora(c.creadaEn)}</td>
                    <td className={`comanda-${c.estado.toLowerCase()}`}>{ESTADO_COMANDA[c.estado]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {agregar && <HojaAgregar onCerrar={() => setAgregar(false)} onCreada={(r) => { setAgregar(false); setCreada(r); recargar() }} />}
      {creada && <HojaConfigurar creada={creada} onCerrar={() => setCreada(null)} />}
    </div>
  )
}

function HojaAgregar({ onCerrar, onCreada }) {
  const aviso = useAviso()
  const [nombre, setNombre] = useState('Cocina')
  const [columnas, setColumnas] = useState(48)
  const [enviando, setEnviando] = useState(false)

  async function crear(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      onCreada(await api('/api/impresoras', { method: 'POST', body: { nombre: nombre.trim(), columnas } }))
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo="Agregar impresora" onCerrar={onCerrar}>
      <form className="form" onSubmit={crear}>
        <label>Nombre<input id="impresora-nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} maxLength={60} required /></label>
        <fieldset className="segmentado">
          <legend>Ancho del papel</legend>
          <label><input type="radio" name="columnas" checked={columnas === 48} onChange={() => setColumnas(48)} />80 mm</label>
          <label><input type="radio" name="columnas" checked={columnas === 32} onChange={() => setColumnas(32)} />58 mm</label>
        </fieldset>
        <button className="btn btn-oro btn-grande" disabled={!nombre.trim() || enviando}>Crear y ver cómo conectarla</button>
      </form>
    </Hoja>
  )
}

function Copiable({ texto }) {
  const aviso = useAviso()
  async function copiar() {
    try {
      await navigator.clipboard.writeText(texto)
      aviso('Copiado')
    } catch {
      aviso('No se pudo copiar: selecciónalo y cópialo a mano', 'error')
    }
  }
  return (
    <div className="copiable">
      <code>{texto}</code>
      <button type="button" className="btn btn-texto" onClick={copiar}>Copiar</button>
    </div>
  )
}

/** Se muestra una sola vez, al crear: la dirección lleva el token secreto. */
function HojaConfigurar({ creada, onCerrar }) {
  const sitio = window.location.origin
  return (
    <Hoja titulo={`Conectar ${creada.impresora.nombre}`} onCerrar={onCerrar}>
      <div className="form">
        <p className="error">Guarda estas direcciones ahora: por seguridad no se vuelven a mostrar. Si las pierdes, desactiva la impresora y crea otra.</p>
        <section className="grupo">
          <h3>Si es una impresora con nube (Star CloudPRNT)</h3>
          <p className="nota">En la página de configuración de la impresora, activa CloudPRNT y pon esta dirección como servidor. Intervalo de consulta: 5 segundos.</p>
          <Copiable texto={sitio + creada.rutaCloudPrnt} />
        </section>
        <section className="grupo">
          <h3>Si es una impresora de red común (puente)</h3>
          <p className="nota">En el PC del local, abre <code>deploy\puente-impresora\puente-impresora.bat</code> con el Bloc de notas y pega esta dirección en SERVIDOR. Pon la IP de la impresora en IMPRESORA.</p>
          <Copiable texto={sitio + creada.rutaPuente} />
        </section>
        <p className="nota">Cuando esté conectada, aquí dirá "En línea". Usa "Imprimir hoja de prueba" para comprobarlo.</p>
        <button type="button" className="btn btn-oro btn-grande" onClick={onCerrar}>Ya las guardé</button>
      </div>
    </Hoja>
  )
}
