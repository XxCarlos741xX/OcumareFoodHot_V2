import { useEffect, useMemo, useRef, useState } from 'react'
import { bs, usd } from '../lib/dinero'

const ALTO = 220
const MARGEN = { arriba: 12, derecha: 8, abajo: 28, izquierda: 52 }

/** Escala "bonita" para el eje: 0 a un máximo redondo, con 4 divisiones. */
function escala(max) {
  if (max <= 0) return { tope: 100, paso: 25 }
  const crudo = max / 4
  const mag = 10 ** Math.floor(Math.log10(crudo))
  const paso = [1, 2, 2.5, 5, 10].map((f) => f * mag).find((p) => p >= crudo)
  return { tope: paso * 4, paso }
}

const etiquetaDia = (f) => {
  const d = new Date(`${f}T12:00`)
  return d.toLocaleDateString('es-VE', { day: 'numeric', month: 'numeric' })
}
const diaLargo = (f) => {
  const t = new Date(`${f}T12:00`).toLocaleDateString('es-VE', { weekday: 'long', day: 'numeric', month: 'long' })
  return t.charAt(0).toUpperCase() + t.slice(1)
}

/**
 * Recaudo diario expresado en dólares (una sola serie, un solo eje).
 * Al pasar el dedo o el mouse por una barra se ve el detalle en $ y en Bs.
 */
export default function GraficaRecaudo({ dias }) {
  const [activo, setActivo] = useState(null)
  const [ancho, setAncho] = useState(640)
  const { tope, paso } = useMemo(() => escala(Math.max(0, ...dias.map((d) => d.totalEquivUsd))), [dias])

  const zona = { x: MARGEN.izquierda, w: ancho - MARGEN.izquierda - MARGEN.derecha, h: ALTO - MARGEN.arriba - MARGEN.abajo }
  const banda = zona.w / dias.length
  const grosor = Math.max(4, Math.min(36, banda - 2))   // 2px de separación mínima entre barras
  const y = (v) => MARGEN.arriba + zona.h - (v / tope) * zona.h
  const cadaCuanto = dias.length > 14 ? Math.ceil(dias.length / 8) : 1
  const sel = activo == null ? null : dias[activo]

  // El SVG ocupa el 100% del contenedor (no le impone ancho), así que medir no provoca bucles.
  const caja = useRef(null)
  useEffect(() => {
    const el = caja.current
    if (!el) return undefined
    const obs = new ResizeObserver(([e]) => {
      const w = Math.round(e.contentRect.width)
      setAncho((prev) => (w > 0 && Math.abs(w - prev) > 2 ? w : prev))
    })
    obs.observe(el)
    return () => obs.disconnect()
  }, [])

  return (
    <div className="grafica" ref={caja} onMouseLeave={() => setActivo(null)}>
      <svg viewBox={`0 0 ${ancho} ${ALTO}`} width="100%" height={ALTO} preserveAspectRatio="none" role="img" aria-label={`Recaudo diario en dólares, ${dias.length} días`}>
        {Array.from({ length: 5 }, (_, i) => i * paso).map((v) => (
          <g key={v}>
            <line x1={zona.x} x2={ancho - MARGEN.derecha} y1={y(v)} y2={y(v)} className={v === 0 ? 'eje-base' : 'eje-guia'} />
            <text x={zona.x - 8} y={y(v)} dy="0.32em" textAnchor="end" className="eje-texto">${v >= 1000 ? `${v / 1000}k` : v}</text>
          </g>
        ))}
        {dias.map((d, i) => {
          const cx = zona.x + banda * i + banda / 2
          const h = Math.max(0, y(0) - y(d.totalEquivUsd))
          const r = Math.min(4, grosor / 2, h)
          const x0 = cx - grosor / 2
          const top = y(0) - h
          // Barra anclada a la base con las esquinas de arriba redondeadas (4px).
          const ruta = h <= 0 ? '' : `M${x0},${y(0)} V${top + r} Q${x0},${top} ${x0 + r},${top} H${x0 + grosor - r} Q${x0 + grosor},${top} ${x0 + grosor},${top + r} V${y(0)} Z`
          return (
            <g key={d.fecha} className={`barra-dia ${activo === i ? 'activa' : ''}`}
               tabIndex={0} role="button" aria-label={`${diaLargo(d.fecha)}: ${usd(d.totalEquivUsd)}`}
               onMouseEnter={() => setActivo(i)} onFocus={() => setActivo(i)} onBlur={() => setActivo(null)} onClick={() => setActivo(i)}>
              <rect x={zona.x + banda * i} y={MARGEN.arriba} width={banda} height={zona.h} fill="transparent" />
              {ruta && <path d={ruta} />}
              {i % cadaCuanto === 0 && (
                <text x={cx} y={ALTO - 8} textAnchor="middle" className="eje-texto">{etiquetaDia(d.fecha)}</text>
              )}
            </g>
          )
        })}
      </svg>
      {sel && (
        <div className="grafica-detalle" style={{ left: Math.min(Math.max(zona.x + banda * activo + banda / 2, 90), ancho - 90) }} role="status">
          <strong>{diaLargo(sel.fecha)}</strong>
          <span>Total {usd(sel.totalEquivUsd)}</span>
          <span>{usd(sel.recaudadoUsd)} en dólares</span>
          <span>{bs(sel.recaudadoBs)} en bolívares</span>
        </div>
      )}
    </div>
  )
}
