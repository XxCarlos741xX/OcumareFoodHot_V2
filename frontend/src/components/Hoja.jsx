import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'

/** Panel que sube desde abajo (en celular) o aparece centrado (en pantalla grande). */
export default function Hoja({ titulo, onCerrar, children, pie }) {
  const ref = useRef(null)
  useEffect(() => {
    const previo = document.activeElement
    ref.current?.querySelector('input, select, textarea, button:not(.hoja-cerrar)')?.focus()
    const tecla = (e) => { if (e.key === 'Escape') onCerrar() }
    document.addEventListener('keydown', tecla)
    return () => { document.removeEventListener('keydown', tecla); previo?.focus?.() }
  }, [onCerrar])
  return createPortal(
    <div className="hoja-fondo" onClick={onCerrar}>
      <div className="hoja" role="dialog" aria-modal="true" aria-label={titulo} ref={ref} onClick={(e) => e.stopPropagation()}>
        <header className="hoja-cabeza">
          <h2>{titulo}</h2>
          <button type="button" className="hoja-cerrar" onClick={onCerrar} aria-label="Cerrar">×</button>
        </header>
        <div className="hoja-cuerpo">{children}</div>
        {pie && <footer className="hoja-pie">{pie}</footer>}
      </div>
    </div>,
    document.body,
  )
}
