import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api } from '../api/client'

const OperacionContext = createContext(null)

/** Turno abierto y tasa vigente: los usa toda la app. Se refrescan cada minuto. */
export function OperacionProvider({ children }) {
  const [turno, setTurno] = useState(undefined)   // undefined = cargando, null = no hay
  const [tasa, setTasa] = useState(undefined)

  const refrescar = useCallback(async () => {
    const [t, ta] = await Promise.allSettled([api('/api/turnos/actual'), api('/api/tasas/vigente')])
    setTurno(t.status === 'fulfilled' ? t.value : null)
    setTasa(ta.status === 'fulfilled' ? ta.value : null)
  }, [])

  useEffect(() => {
    refrescar()
    const id = setInterval(refrescar, 60_000)
    return () => clearInterval(id)
  }, [refrescar])

  const valor = useMemo(() => ({ turno, tasa, refrescar }), [turno, tasa, refrescar])
  return <OperacionContext.Provider value={valor}>{children}</OperacionContext.Provider>
}

export const useOperacion = () => useContext(OperacionContext)
