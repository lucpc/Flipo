import { useState, type FormEvent } from 'react'
import { ApiError, login } from '../api/client'
import './LoginScreen.css'

interface LoginScreenProps {
  onLoginSuccess: (token: string) => void
}

export function LoginScreen({ onLoginSuccess }: LoginScreenProps) {
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErro(null)
    setCarregando(true)
    try {
      const { token } = await login(email, senha)
      onLoginSuccess(token)
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : 'Não foi possível entrar. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <main className="login-screen">
      <form className="login-form" onSubmit={handleSubmit}>
        <h1>Flipo</h1>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            value={senha}
            onChange={(event) => setSenha(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>
        {erro && <p className="login-erro">{erro}</p>}
        <button type="submit" disabled={carregando}>
          {carregando ? 'Entrando...' : 'Entrar'}
        </button>
      </form>
    </main>
  )
}
