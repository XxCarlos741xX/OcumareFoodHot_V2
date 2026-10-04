/** − cantidad + con botones grandes para el dedo. */
export default function Contador({ valor, onCambiar, max = 50, deshabilitado, etiqueta }) {
  if (!valor) {
    return (
      <button type="button" className="contador-agregar" disabled={deshabilitado}
              onClick={() => onCambiar(1)} aria-label={`Agregar ${etiqueta}`}>+</button>
    )
  }
  return (
    <div className="contador" role="group" aria-label={`Cantidad de ${etiqueta}`}>
      <button type="button" onClick={() => onCambiar(valor - 1)} aria-label="Quitar uno">−</button>
      <output aria-live="polite">{valor}</output>
      <button type="button" onClick={() => onCambiar(Math.min(max, valor + 1))} disabled={valor >= max} aria-label="Agregar uno">+</button>
    </div>
  )
}
