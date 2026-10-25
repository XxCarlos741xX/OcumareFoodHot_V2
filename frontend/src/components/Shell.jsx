import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { api, esDemo } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useOperacion } from '../auth/OperacionContext'
import { leerMonto, numero } from '../lib/dinero'
import { ROLES } from '../lib/roles'
import { useAviso } from './Aviso'
import Hoja from './Hoja'
import Marca from './Marca'

/** Marco de la app: barra superior con turno y tasa, contenido y navegación inferior. */
export default function Shell() {
  const { usuario, esGerencia, esCaja, salir } = useAuth()
  const { turno, tasa } = useOperacion()
  const [editarTasa, setEditarTasa] = useState(false)
  const [cambiarClave, setCambiarClave] = useState(false)
  const ruta = useLocation().pathname
  useAvisoPedidosWeb(!!turno && esCaja)
  const cocina = useEstadoCocina(esCaja)

  return (
    <div className="app">
      <header className="barra">
        <Marca />
        <div className="barra-estado">
          <span className={`chip ${turno ? 'chip-vivo' : 'chip-apagado'}`} title={turno ? 'Turno abierto' : undefined}>
            {turno === undefined ? '…' : turno ? (turno.tipo === 'DIA' ? 'Día' : 'Noche') : 'Sin turno'}
          </span>
          <button type="button" className="chip chip-tasa" disabled={!esGerencia}
                  onClick={() => setEditarTasa(true)} title={esGerencia ? 'Cambiar la tasa' : undefined}>
            {tasa ? `Bs ${numero(tasa.bsPorUsd)}` : 'Sin tasa'}
          </button>
        </div>
        <details className="usuario">
          <summary aria-label="Menú de usuario">{usuario.nombre.trim().charAt(0).toUpperCase()}</summary>
          <div className="usuario-menu">
            <p>{usuario.nombre}<br /><small>{ROLES[usuario.rol]}</small></p>
            <button type="button" className="btn btn-linea" onClick={() => setCambiarClave(true)}>Cambiar mi contraseña</button>
            <button type="button" className="btn btn-linea" onClick={salir}>Cerrar sesión</button>
          </div>
        </details>
      </header>
      {esDemo && <p className="demo-aviso">Modo demostración: los datos son de prueba y se borran al recargar.</p>}
      {cocina && <p className="banda-cocina" role="status">{cocina}</p>}

      <main className="contenido"><Outlet /></main>

      <nav className="nav" aria-label="Secciones">
        <NavLink to="/mesas">Mesas</NavLink>
        <NavLink to="/pedidos">Pedidos</NavLink>
        {esCaja && <NavLink to="/caja">Caja</NavLink>}
        {esGerencia && (
          <NavLink to="/gestion" className={({ isActive }) => (isActive || ['/ventas', '/carta', '/salon', '/personal', '/impresoras'].includes(ruta) ? 'active' : '')}>
            Gestión
          </NavLink>
        )}
      </nav>

      {editarTasa && <HojaTasa actual={tasa} onCerrar={() => setEditarTasa(false)} />}
      {cambiarClave && <HojaClave onCerrar={() => setCambiarClave(false)} />}
    </div>
  )
}

function HojaTasa({ actual, onCerrar }) {
  const { refrescar } = useOperacion()
  const aviso = useAviso()
  const [valor, setValor] = useState(actual ? String(actual.bsPorUsd).replace('.', ',') : '')
  const [fuente, setFuente] = useState('BCV')
  const [enviando, setEnviando] = useState(false)
  const [trayendo, setTrayendo] = useState(false)
  const monto = leerMonto(valor)

  async function traerBcv() {
    setTrayendo(true)
    try {
      const r = await api('/api/tasas/bcv', { method: 'POST', body: {} })
      await refrescar()
      aviso(r.nueva ? `Tasa BCV registrada: Bs ${numero(r.tasa.bsPorUsd)} por dólar` : 'El BCV no ha cambiado la tasa. Se mantiene la actual.')
      onCerrar()
    } catch (err) {
      aviso(err.message, 'error')
      setTrayendo(false)
    }
  }

  async function guardar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      await api('/api/tasas', { method: 'POST', body: { bsPorUsd: monto, fuente } })
      await refrescar()
      aviso(`Tasa actualizada: Bs ${numero(monto)} por dólar`)
      onCerrar()
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo="Tasa del día" onCerrar={onCerrar}>
      <div className="tasa-bcv">
        {actual && <p className="nota">Actual: <strong>Bs {numero(actual.bsPorUsd)}</strong> · {actual.fuente ?? 'Manual'}</p>}
        <p className="nota">El sistema trae sola la tasa oficial del BCV cada hora. Puedes pedirla ahora o poner otra a mano.</p>
        <button type="button" className="btn btn-linea" disabled={trayendo || enviando} onClick={traerBcv}>
          {trayendo ? 'Consultando al BCV…' : 'Traer tasa del BCV ahora'}
        </button>
      </div>
      <form onSubmit={guardar} className="form">
        <label>Bolívares por dólar
          <input inputMode="decimal" value={valor} onChange={(e) => setValor(e.target.value)} placeholder="Ej. 40,50" />
        </label>
        <fieldset className="segmentado">
          <legend>Fuente</legend>
          {['BCV', 'Paralelo', 'Manual'].map((f) => (
            <label key={f}><input type="radio" name="fuente" checked={fuente === f} onChange={() => setFuente(f)} />{f}</label>
          ))}
        </fieldset>
        <p className="nota">Los pedidos nuevos usan esta tasa. Los que ya están abiertos conservan la suya.</p>
        <button className="btn btn-oro" disabled={!(monto > 0) || enviando || trayendo}>Guardar tasa a mano</button>
      </form>
    </Hoja>
  )
}

function HojaClave({ onCerrar }) {
  const aviso = useAviso()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [repetida, setRepetida] = useState('')
  const [enviando, setEnviando] = useState(false)
  const coinciden = nueva === repetida

  async function guardar(e) {
    e.preventDefault()
    setEnviando(true)
    try {
      await api('/api/auth/password', { method: 'PUT', body: { passwordActual: actual, passwordNueva: nueva } })
      aviso('Contraseña cambiada')
      onCerrar()
    } catch (err) {
      aviso(err.message, 'error')
      setEnviando(false)
    }
  }

  return (
    <Hoja titulo="Cambiar mi contraseña" onCerrar={onCerrar}>
      <form className="form" onSubmit={guardar}>
        <label>Contraseña actual<input type="password" autoComplete="current-password" value={actual} onChange={(e) => setActual(e.target.value)} required /></label>
        <label>Nueva contraseña<input type="password" autoComplete="new-password" minLength={8} value={nueva} onChange={(e) => setNueva(e.target.value)} required placeholder="Mínimo 8 caracteres" /></label>
        <label>Repite la nueva<input type="password" autoComplete="new-password" value={repetida} onChange={(e) => setRepetida(e.target.value)} required /></label>
        {repetida && !coinciden && <p className="error">Las contraseñas nuevas no coinciden.</p>}
        <button className="btn btn-oro btn-grande" disabled={!actual || nueva.length < 8 || !coinciden || enviando}>Cambiar contraseña</button>
      </form>
    </Hoja>
  )
}

/**
 * Cada 30 s revisa la impresora de cocina. Devuelve un aviso si hay comandas que no salen
 * o si la impresora dejó de conectarse; null si todo va bien (o si no hay impresora registrada).
 */
function useEstadoCocina(activo) {
  const [aviso, setAviso] = useState(null)
  useEffect(() => {
    if (!activo || esDemo) return undefined
    let vivo = true
    const revisar = async () => {
      try {
        const e = await api('/api/comandas/estado')
        if (!vivo) return
        if (!e.impresorasActivas) { setAviso(null); return }
        if (!e.conectada) {
          setAviso(e.minutosSinConexion != null
            ? `La impresora de cocina no se conecta desde hace ${e.minutosSinConexion} min. Revisa que esté prendida y con internet.`
            : 'La impresora de cocina todavía no se ha conectado nunca. Revisa su configuración en Gestión > Impresoras.')
        } else if (e.pendientes > 0 && e.minutosPendienteMasVieja >= 2) {
          setAviso(`${e.pendientes} ${e.pendientes === 1 ? 'comanda no ha salido' : 'comandas no han salido'} en cocina. Revisa el papel de la impresora.`)
        } else {
          setAviso(null)
        }
      } catch { /* sin conexión: se reintenta */ }
    }
    revisar()
    const id = setInterval(revisar, 30000)
    return () => { vivo = false; clearInterval(id) }
  }, [activo])
  return aviso
}

/**
 * Cada 15 s revisa si entraron pedidos de la web. Si hay uno nuevo: suena, avisa y marca la pestaña.
 * Los pedidos que ya estaban al abrir la app no suenan.
 */
function useAvisoPedidosWeb(activo) {
  const aviso = useAviso()
  const vistos = useRef(null)
  useEffect(() => {
    if (!activo) return undefined
    let vivo = true
    const tituloOriginal = document.title
    const revisar = async () => {
      try {
        const lista = await api('/api/pedidos?soloAbiertos=true')
        const web = lista.filter((p) => p.tipo === 'WEB')
        if (vistos.current === null) {
          vistos.current = new Set(web.map((p) => p.id))
          return
        }
        const nuevos = web.filter((p) => !vistos.current.has(p.id))
        nuevos.forEach((p) => vistos.current.add(p.id))
        if (vivo && nuevos.length) {
          const p = nuevos[0]
          aviso(nuevos.length === 1 ? `Nuevo pedido web #${p.numeroDiario} de ${p.clienteNombre ?? 'un cliente'}` : `${nuevos.length} pedidos web nuevos`)
          sonar()
          document.title = `(${nuevos.length}) Pedido web nuevo`
          setTimeout(() => { document.title = tituloOriginal }, 8000)
        }
      } catch { /* sin conexión: se reintenta */ }
    }
    revisar()
    const id = setInterval(revisar, 15000)
    return () => { vivo = false; clearInterval(id) }
  }, [activo, aviso])
}

/** Dos tonos cortos (sin archivos de audio). */
function sonar() {
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext
    const ctx = new Ctx()
    ;[880, 1175].forEach((f, i) => {
      const o = ctx.createOscillator()
      const g = ctx.createGain()
      o.frequency.value = f
      g.gain.setValueAtTime(0.0001, ctx.currentTime + i * 0.22)
      g.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + i * 0.22 + 0.02)
      g.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + i * 0.22 + 0.2)
      o.connect(g).connect(ctx.destination)
      o.start(ctx.currentTime + i * 0.22)
      o.stop(ctx.currentTime + i * 0.22 + 0.22)
    })
    setTimeout(() => ctx.close(), 800)
  } catch { /* el navegador no permite sonido todavía */ }
}
