import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const SECCIONES = [
  { a: '/ventas', titulo: 'Ventas', texto: 'Lo que entró hoy y en los últimos días, en dólares y bolívares.' },
  { a: '/carta', titulo: 'Carta', texto: 'Productos, precios, agotados y categorías.' },
  { a: '/salon', titulo: 'Salón', texto: 'Mesas: agregar, retirar, ordenar y renumerar.' },
  { a: '/personal', titulo: 'Personal', texto: 'Quién puede entrar al sistema y con qué rol.' },
]

export default function Gestion() {
  const { usuario } = useAuth()
  return (
    <div className="pagina">
      <div className="pagina-cabeza"><h1>Gestión</h1></div>
      <ul className="secciones">
        {SECCIONES.map((s) => (
          <li key={s.a}>
            <Link to={s.a}><strong>{s.titulo}</strong><span>{s.texto}</span></Link>
          </li>
        ))}
      </ul>
      <p className="nota">Entraste como {usuario.rol === 'ADMIN' ? 'administrador' : 'gerente'}.</p>
    </div>
  )
}
