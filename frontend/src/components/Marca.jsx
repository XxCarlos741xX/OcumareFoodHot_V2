import { useState } from 'react'

/** Logo del local (el mismo de la web: /img/logo.jpg); si no, el nombre en letras. */
export default function Marca({ grande }) {
  const [sinLogo, setSinLogo] = useState(false)
  return (
    <span className={`marca ${grande ? 'marca-grande' : ''}`}>
      {!sinLogo && <img src="../img/logo.jpg" alt="" onError={() => setSinLogo(true)} />}
      <span className="marca-texto">Ocumare <span>Food Hot</span></span>
    </span>
  )
}
