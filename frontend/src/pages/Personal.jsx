import { Link } from 'react-router-dom'
import { useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { useAviso } from '../components/Aviso'
import Hoja from '../components/Hoja'
import { useCarga } from '../lib/useCarga'

const ROLES = { MESERO: 'Mesero', GERENTE: 'Gerente', ADMIN: 'Administrador' }

/** Personal: el gerente maneja meseros; el administrador, a todos. */
export default function Personal() {
  const { usuario } = useAuth()
  const esAdmin = usuario.rol === 'ADMIN'
  const { datos: usuarios, recargar, error } = useCarga('/api/usuarios')
  const [editar, setEditar] = useState(null)

  const puedeEditar = (u) => esAdmin || u.rol === 'MESERO'
  const activos = (usuarios ?? []).filter((u) => u.activo)
  const inactivos = (usuarios ?? []).filter((u) => !u.activo)

  const fila = (u) => (
    <li key={u.id} className={u.activo ? '' : 'apagado'}>
      <span className="gestion-info">
        <strong>{u.nombre}{u.id === usuario.id && ' (tú)'}</strong>
        <small>{ROLES[u.rol]}, usuario {u.username}</small>
      </span>
      {puedeEditar(u) && <button type="button" className="btn btn-texto" onClick={() => setEditar(u)}>Editar</button>}
    </li>
  )

  return (
    <div className="pagina">
      <div className="pagina-cabeza">
        <Link to="/gestion" className="volver" aria-label="Volver a gestión">‹</Link>
        <h1>Personal</h1>
        <p className="resumen">{activos.length} activos</p>
      </div>
      {!esAdmin && <p className="nota">Puedes agregar y editar meseros. Los gerentes los maneja el administrador.</p>}
      <div className="acciones-rapidas">
        <button type="button" className="btn btn-oro" onClick={() => setEditar({})}>Agregar persona</button>
      </div>
      {error && <p className="error">{error.message}</p>}
      <ul className="gestion-lista">{activos.map(fila)}</ul>
      {inactivos.length > 0 && (
        <details className="desplegable">
          <summary><span>Desactivados</span><span className="resumen-cuadre pendiente">{inactivos.length}</span></summary>
          <ul className="gestion-lista desplegable-cuerpo">{inactivos.map(fila)}</ul>
        </details>
      )}
      {editar && <HojaPersona persona={editar} esAdmin={esAdmin} yo={usuario}
                              onCerrar={() => setEditar(null)} onListo={() => { setEditar(null); recargar() }} />}
    </div>
  )
}

function HojaPersona({ persona, esAdmin, yo, onCerrar, onListo }) {
  const aviso = useAviso()
  const nueva = !persona.id
  const [f, setF] = useState({ nombre: persona.nombre ?? '', username: persona.username ?? '', password: '', rol: persona.rol ?? 'MESERO' })
  const [clave, setClave] = useState('')
  const [enviando, setEnviando] = useState(false)
  const campo = (k) => ({ value: f[k], onChange: (e) => setF({ ...f, [k]: e.target.value }) })
  const soyYo = persona.id === yo.id

  async function ejecutar(fn, mensaje) {
    setEnviando(true)
    try { await fn(); aviso(mensaje); onListo() } catch (err) { aviso(err.message, 'error'); setEnviando(false) }
  }

  const guardar = (e) => {
    e.preventDefault()
    if (nueva) {
      ejecutar(() => api('/api/usuarios', { method: 'POST', body: { nombre: f.nombre.trim(), username: f.username.trim(), password: f.password, rol: f.rol } }),
        `${f.nombre.trim()} puede entrar con el usuario ${f.username.trim().toLowerCase()}`)
    } else {
      ejecutar(() => api(`/api/usuarios/${persona.id}`, { method: 'PUT', body: { nombre: f.nombre.trim(), rol: esAdmin && !soyYo ? f.rol : null } }),
        'Datos guardados')
    }
  }

  return (
    <Hoja titulo={nueva ? 'Agregar persona' : persona.nombre} onCerrar={onCerrar}>
      <form className="form" onSubmit={guardar}>
        <label>Nombre<input {...campo('nombre')} required maxLength={100} /></label>
        {nueva && (
          <>
            <label>Usuario para entrar<input {...campo('username')} required minLength={3} maxLength={50} autoCapitalize="none" autoComplete="off" pattern="[A-Za-z0-9._\-]+" title="Letras, números, punto, guion y guion bajo" /></label>
            <label>Contraseña<input type="password" {...campo('password')} required minLength={8} autoComplete="new-password" /></label>
          </>
        )}
        {esAdmin && !soyYo && (
          <label>Rol
            <select {...campo('rol')}>{Object.entries(ROLES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select>
          </label>
        )}
        <button className="btn btn-oro btn-grande" disabled={enviando || !f.nombre.trim() || (nueva && f.password.length < 8)}>
          {nueva ? 'Agregar persona' : 'Guardar'}
        </button>
      </form>

      {!nueva && (
        <>
          <form className="form grupo" onSubmit={(e) => {
            e.preventDefault()
            ejecutar(() => api(`/api/usuarios/${persona.id}/password`, { method: 'PUT', body: { passwordNueva: clave } }), 'Contraseña cambiada')
          }}>
            <label>Nueva contraseña<input type="password" value={clave} onChange={(e) => setClave(e.target.value)} minLength={8} autoComplete="new-password" placeholder="Mínimo 8 caracteres" /></label>
            <button className="btn btn-linea" disabled={clave.length < 8 || enviando}>Cambiar contraseña</button>
          </form>
          {!soyYo && (
            <button type="button" className={`btn btn-texto ${persona.activo ? 'btn-peligro' : ''}`} disabled={enviando}
                    onClick={() => ejecutar(() => api(`/api/usuarios/${persona.id}`, { method: 'PUT', body: { activo: !persona.activo } }),
                      persona.activo ? `${persona.nombre} ya no puede entrar` : `${persona.nombre} puede volver a entrar`)}>
              {persona.activo ? 'Desactivar acceso' : 'Reactivar acceso'}
            </button>
          )}
        </>
      )}
    </Hoja>
  )
}
