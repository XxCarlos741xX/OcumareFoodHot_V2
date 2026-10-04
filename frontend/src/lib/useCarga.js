import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'

/** Carga una ruta de la API y (opcional) la refresca cada N segundos mientras la pantalla está visible. */
export function useCarga(ruta, cadaSegundos = 0) {
  const [datos, setDatos] = useState(null)
  const [error, setError] = useState(null)
  const vivo = useRef(true)

  const recargar = useCallback(async () => {
    if (!ruta) return
    try {
      const d = await api(ruta)
      if (vivo.current) { setDatos(d); setError(null) }
    } catch (e) {
      if (vivo.current) setError(e)
    }
  }, [ruta])

  useEffect(() => {
    vivo.current = true
    recargar()
    if (!cadaSegundos) return () => { vivo.current = false }
    const id = setInterval(() => { if (document.visibilityState === 'visible') recargar() }, cadaSegundos * 1000)
    return () => { vivo.current = false; clearInterval(id) }
  }, [recargar, cadaSegundos])

  return { datos, error, recargar, setDatos }
}
