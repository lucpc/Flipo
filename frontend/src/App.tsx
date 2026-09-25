import { useState } from 'react'
import { clearToken, getToken, setToken as saveToken } from './api/client'
import { LoginScreen } from './screens/LoginScreen'
import { MateriasScreen } from './screens/MateriasScreen'

function App() {
  const [token, setToken] = useState<string | null>(() => getToken())

  function handleLoginSuccess(novoToken: string) {
    saveToken(novoToken)
    setToken(novoToken)
  }

  function handleUnauthorized() {
    clearToken()
    setToken(null)
  }

  if (!token) {
    return <LoginScreen onLoginSuccess={handleLoginSuccess} />
  }

  return <MateriasScreen onUnauthorized={handleUnauthorized} />
}

export default App
