import { useState } from 'react'
import { esDemo } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Marca from '../components/Marca'

export default function Login() {
  const { entrar } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function enviar(e) {
    e.preventDefault()
    setError('')
    setEnviando(true)
    try {
      await entrar(username, password)
    } catch (err) {
      setError(err.message)
      setEnviando(false)
    }
  }

  return (
    <main className="login">
      <div className="login-caja">
        <Marca grande />
        <p className="login-sub">Caja y mesas</p>
        <form onSubmit={enviar} className="form">
          <label>Usuario
            <input autoComplete="username" autoCapitalize="none" value={username} onChange={(e) => setUsername(e.target.value)} required />
          </label>
          <label>Contraseña
            <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
          {error && <p className="error" role="alert">{error}</p>}
          <button className="btn btn-oro btn-grande" disabled={enviando}>{enviando ? 'Entrando…' : 'Entrar'}</button>
        </form>
        {esDemo && <p className="nota">Demo: ana / ana12345 (mesero) o gerente / gerente123</p>}
      </div>
    </main>
  )
}
