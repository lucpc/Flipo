import { useState } from 'react'
import { clearToken, getToken, setToken as saveToken, type Materia } from './api/client'
import { AdicionarCartaoScreen } from './screens/AdicionarCartaoScreen'
import { LoginScreen } from './screens/LoginScreen'
import { MateriaScreen } from './screens/MateriaScreen'
import { MateriasScreen } from './screens/MateriasScreen'
import { RevisaoScreen, type CartaoPendente } from './screens/RevisaoScreen'

// Navegação como uma pequena state machine (em vez de react-router): o app
// tem poucas telas e todas dependem de contexto sequencial (matéria
// selecionada, cartão em revisão) que já precisa viver em algum state — uma
// dependência de roteamento não compensaria pra esse tamanho.
type View =
  | { nome: 'materias' }
  | { nome: 'materia'; materia: Materia }
  | { nome: 'adicionar-cartao'; materia: Materia }
  | { nome: 'revisao'; materia: Materia; itens: CartaoPendente[] }

function App() {
  const [token, setToken] = useState<string | null>(() => getToken())
  const [view, setView] = useState<View>({ nome: 'materias' })

  function handleLoginSuccess(novoToken: string) {
    saveToken(novoToken)
    setToken(novoToken)
  }

  function handleUnauthorized() {
    clearToken()
    setToken(null)
    setView({ nome: 'materias' })
  }

  if (!token) {
    return <LoginScreen onLoginSuccess={handleLoginSuccess} />
  }

  switch (view.nome) {
    case 'materias':
      return (
        <MateriasScreen
          onUnauthorized={handleUnauthorized}
          onSelecionarMateria={(materia) => setView({ nome: 'materia', materia })}
        />
      )

    case 'materia':
      return (
        <MateriaScreen
          materia={view.materia}
          onVoltar={() => setView({ nome: 'materias' })}
          onAdicionarCartao={() => setView({ nome: 'adicionar-cartao', materia: view.materia })}
        />
      )

    case 'adicionar-cartao':
      return (
        <AdicionarCartaoScreen
          onCancelar={() => setView({ nome: 'materia', materia: view.materia })}
          onConfirmar={(pergunta, resposta) =>
            setView({
              nome: 'revisao',
              materia: view.materia,
              itens: [{ id: crypto.randomUUID(), pergunta, resposta, aceito: false }],
            })
          }
        />
      )

    case 'revisao':
      return (
        <RevisaoScreen
          materia={view.materia}
          itensIniciais={view.itens}
          onUnauthorized={handleUnauthorized}
          onSair={() => setView({ nome: 'materia', materia: view.materia })}
        />
      )
  }
}

export default App
