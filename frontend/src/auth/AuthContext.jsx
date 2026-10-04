import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, onSesionExpirada, setToken } from '../api/client'

const CLAVE = 'ocumare.sesion'
const AuthContext = createContext(null)

function leerSesion() {
  try {
    const s = JSON.parse(localStorage.getItem(CLAVE))
    if (s?.token && new Date(s.expiraEn) > new Date()) return s
  } catch { /* sesión dañada: se ignora */ }
  return null
}

/** Guarda el token y el usuario. Al recargar la página la sesión sigue hasta que vence (12 h). */
export function AuthProvider({ children }) {
  const [sesion, setSesion] = useState(() => {
    const s = leerSesion()
    setToken(s?.token ?? null)
    return s
  })

  const salir = useCallback(() => {
    try { localStorage.removeItem(CLAVE) } catch { /* sin almacenamiento */ }
    setToken(null)
    setSesion(null)
  }, [])

  useEffect(() => { onSesionExpirada(salir) }, [salir])

  const entrar = useCallback(async (username, password) => {
    const r = await api('/api/auth/login', { method: 'POST', body: { username, password } })
    const s = { token: r.token, expiraEn: r.expiraEn, usuario: r.usuario }
    try { localStorage.setItem(CLAVE, JSON.stringify(s)) } catch { /* sin almacenamiento */ }
    setToken(r.token)
    setSesion(s)
  }, [])

  const valor = useMemo(() => {
    const usuario = sesion?.usuario ?? null
    return {
      usuario,
      esGerencia: usuario?.rol === 'GERENTE' || usuario?.rol === 'ADMIN',
      entrar,
      salir,
    }
  }, [sesion, entrar, salir])

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)
