import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, esDemo, onSesionExpirada, setSesionActiva, setToken } from '../api/client'

const CLAVE = 'ocumare.sesion'
const AuthContext = createContext(null)

/**
 * Aquí solo se recuerda QUIÉN entró y hasta cuándo, para pintar la pantalla al recargar.
 * La sesión de verdad es la cookie HttpOnly del servidor (este código no la puede leer ni robar).
 * El token solo se guarda en el modo demo, que no tiene servidor.
 */
function leerSesion() {
  try {
    const s = JSON.parse(localStorage.getItem(CLAVE))
    if (s?.usuario && new Date(s.expiraEn) > new Date() && (!esDemo || s.token)) return s
  } catch { /* sesión dañada: se ignora */ }
  return null
}

export function AuthProvider({ children }) {
  const [sesion, setSesion] = useState(() => {
    const s = leerSesion()
    setToken(esDemo ? s?.token ?? null : null)
    setSesionActiva(Boolean(s))
    return s
  })

  const olvidar = useCallback(() => {
    try { localStorage.removeItem(CLAVE) } catch { /* sin almacenamiento */ }
    setToken(null)
    setSesionActiva(false)
    setSesion(null)
  }, [])

  // "Cerrar sesión": el servidor borra la cookie de este equipo.
  const salir = useCallback(() => {
    if (!esDemo) api('/api/auth/salir', { method: 'POST' }).catch(() => { /* sin conexión: igual se olvida aquí */ })
    olvidar()
  }, [olvidar])

  // Sesión vencida o cerrada por la gerencia (usuario desactivado, clave cambiada…).
  useEffect(() => { onSesionExpirada(olvidar) }, [olvidar])

  // Al recargar, confirma con el servidor que la sesión sigue viva y trae los datos al día.
  useEffect(() => {
    if (!sesion || esDemo) return
    let vivo = true
    api('/api/auth/yo')
      .then((usuario) => {
        if (!vivo) return
        setSesion((s) => {
          if (!s) return s
          const nueva = { ...s, usuario }
          try { localStorage.setItem(CLAVE, JSON.stringify(nueva)) } catch { /* sin almacenamiento */ }
          return nueva
        })
      })
      .catch(() => { /* un 401 ya cierra la sesión; sin conexión se sigue con lo guardado */ })
    return () => { vivo = false }
    // Solo al montar: después, cualquier 401 cierra la sesión.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const entrar = useCallback(async (username, password) => {
    const r = await api('/api/auth/login', { method: 'POST', body: { username, password } })
    const s = { expiraEn: r.expiraEn, usuario: r.usuario, ...(esDemo ? { token: r.token } : {}) }
    try { localStorage.setItem(CLAVE, JSON.stringify(s)) } catch { /* sin almacenamiento */ }
    setToken(esDemo ? r.token : null)
    setSesionActiva(true)
    setSesion(s)
  }, [])

  const valor = useMemo(() => {
    const usuario = sesion?.usuario ?? null
    return {
      usuario,
      esGerencia: usuario?.rol === 'GERENTE' || usuario?.rol === 'ADMIN',
      // Cajero, gerente o admin: cobran, aceptan pedidos web, abren y cierran el turno y ven la bitácora.
      esCaja: Boolean(usuario) && usuario.rol !== 'MESERO',
      entrar,
      salir,
    }
  }, [sesion, entrar, salir])

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
