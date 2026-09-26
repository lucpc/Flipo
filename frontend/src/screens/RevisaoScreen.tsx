import { useState } from 'react'
import { ApiError, criarCartao, type Materia } from '../api/client'
import { CartaoForm } from '../components/CartaoForm'
import './RevisaoScreen.css'

// Tela de Revisão (docs/01-fluxos-de-usuario.md, seção 4c) — "carrinho" de
// cartões pendentes antes de persistir. Modelada como lista mesmo no fluxo
// manual (que sempre entra com um único item) porque essa mesma tela será
// reaproveitada pelo fluxo de geração por IA (Épico 5, issue #16), que traz
// vários cartões sugeridos de uma vez.
export interface CartaoPendente {
  id: string
  pergunta: string
  resposta: string
  aceito: boolean
}

interface RevisaoScreenProps {
  materia: Materia
  itensIniciais: CartaoPendente[]
  onUnauthorized: () => void
  onSair: () => void
}

export function RevisaoScreen({ materia, itensIniciais, onUnauthorized, onSair }: RevisaoScreenProps) {
  const [itens, setItens] = useState<CartaoPendente[]>(itensIniciais)
  const [editandoId, setEditandoId] = useState<string | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [salvando, setSalvando] = useState(false)

  function aceitar(id: string) {
    setItens((atuais) => atuais.map((item) => (item.id === id ? { ...item, aceito: true } : item)))
  }

  function descartar(id: string) {
    setItens((atuais) => atuais.filter((item) => item.id !== id))
    if (editandoId === id) {
      setEditandoId(null)
    }
  }

  function salvarEdicao(id: string, pergunta: string, resposta: string) {
    setItens((atuais) => atuais.map((item) => (item.id === id ? { ...item, pergunta, resposta } : item)))
    setEditandoId(null)
  }

  // Itens nem aceitos nem descartados explicitamente também não são persistidos —
  // "confirmar" só grava o que foi aceito, o resto fica pra trás silenciosamente.
  const aceitos = itens.filter((item) => item.aceito)

  async function handleConfirmar() {
    setErro(null)
    setSalvando(true)
    try {
      // Persiste um a um (não existe endpoint de lote ainda — Épico 5). Cada
      // item confirmado sai da lista pendente assim que salvo, então uma
      // falha no meio do caminho preserva o restante para nova tentativa em
      // vez de perder o progresso já feito.
      for (const item of aceitos) {
        await criarCartao(materia.id, item.pergunta, item.resposta)
        setItens((atuais) => atuais.filter((atual) => atual.id !== item.id))
      }
      onSair()
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        onUnauthorized()
        return
      }
      setErro(err instanceof ApiError ? err.message : 'Não foi possível salvar os cartões. Tente novamente.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <main className="revisao-screen">
      <header className="revisao-header">
        <h1>Revisão — {materia.nome}</h1>
        <button type="button" onClick={onSair} disabled={salvando}>
          Cancelar
        </button>
      </header>

      <p className="revisao-hint">
        Confira cada cartão antes de salvar: aceite, edite ou descarte. Só os cartões aceitos são
        persistidos.
      </p>

      {erro && <p className="revisao-erro">{erro}</p>}

      {itens.length === 0 ? (
        <p>Nenhum cartão pendente.</p>
      ) : (
        <ul className="revisao-lista">
          {itens.map((item) =>
            editandoId === item.id ? (
              <li key={item.id} className="revisao-item revisao-item-editando">
                <CartaoForm
                  perguntaInicial={item.pergunta}
                  respostaInicial={item.resposta}
                  rotuloConfirmar="Salvar edição"
                  onConfirmar={(pergunta, resposta) => salvarEdicao(item.id, pergunta, resposta)}
                  onCancelar={() => setEditandoId(null)}
                />
              </li>
            ) : (
              <li key={item.id} className="revisao-item">
                <div className="revisao-item-conteudo">
                  <p className="revisao-item-pergunta">{item.pergunta}</p>
                  <p className="revisao-item-resposta">{item.resposta}</p>
                </div>
                <div className="revisao-item-acoes">
                  {item.aceito ? (
                    <span className="revisao-item-status">Aceito</span>
                  ) : (
                    <button type="button" onClick={() => aceitar(item.id)} disabled={salvando}>
                      Aceitar
                    </button>
                  )}
                  <button type="button" onClick={() => setEditandoId(item.id)} disabled={salvando}>
                    Editar
                  </button>
                  <button type="button" onClick={() => descartar(item.id)} disabled={salvando}>
                    Descartar
                  </button>
                </div>
              </li>
            ),
          )}
        </ul>
      )}

      <div className="revisao-confirmar">
        <button
          type="button"
          disabled={aceitos.length === 0 || salvando || editandoId !== null}
          onClick={handleConfirmar}
        >
          {salvando ? 'Salvando...' : `Confirmar revisão (${aceitos.length})`}
        </button>
      </div>
    </main>
  )
}
