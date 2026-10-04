import { createContext, useCallback, useContext, useState } from 'react'

const AvisoContext = createContext(() => {})

/** Mensajes breves abajo de la pantalla: confirmaciones y errores. */
export function AvisoProvider({ children }) {
  const [aviso, setAviso] = useState(null)
  const mostrar = useCallback((texto, tipo = 'ok') => {
    const id = Date.now()
    setAviso({ id, texto, tipo })
    setTimeout(() => setAviso((a) => (a?.id === id ? null : a)), tipo === 'error' ? 5000 : 2500)
  }, [])
  return (
    <AvisoContext.Provider value={mostrar}>
      {children}
      <div className="aviso-zona" aria-live="polite">
        {aviso && <div key={aviso.id} className={`aviso aviso-${aviso.tipo}`} role={aviso.tipo === 'error' ? 'alert' : 'status'}>{aviso.texto}</div>}
      </div>
    </AvisoContext.Provider>
  )
}

export const useAviso = () => useContext(AvisoContext)
