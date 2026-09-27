import { useCallback, useEffect, useState } from 'react'
import {
  ApiError,
  arquivarCartao,
  desarquivarCartao,
  editarCartao,
  excluirCartao,
  listarCartoes,
  type Cartao,
  type Materia,
} from '../api/client'
import { CartaoForm } from '../components/CartaoForm'
import './CartoesScreen.css'

// Lista de gerenciamento de cartões da matéria (docs/01-fluxos-de-usuario.md,
// seção 6) — tela de apoio simples que existe só para dar acesso ao painel de
// edição de cada cartão ativo. A aba de arquivados (seção 7) é escopo da
// issue #23; por ora só a lista de ativos.
interface CartoesScreenProps {
  materia: Materia
  onUnauthorized: () => void
  onVoltar: () => void
}

export function CartoesScreen({ materia, onUnauthorized, onVoltar }: CartoesScreenProps) {
  const [cartoes, setCartoes] = useState<Cartao[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [editandoId, setEditandoId] = useState<string | null>(null)
  const [confirmandoExclusaoId, setConfirmandoExclusaoId] = useState<string | null>(null)
  const [processandoId, setProcessandoId] = useState<string | null>(null)

  const tratarErro = useCallback(
    (err: unknown, mensagemPadrao: string) => {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          onUnauthorized()
          return
        }
        setErro(err.message)
        return
      }
      setErro(mensagemPadrao)
    },
    [onUnauthorized],
  )

  const carregarCartoes = useCallback(async () => {
    try {
      setCartoes(await listarCartoes(materia.id))
    } catch (err) {
      tratarErro(err, 'Não foi possível carregar os cartões.')
    }
  }, [materia.id, tratarErro])

  useEffect(() => {
    carregarCartoes()
  }, [carregarCartoes])

  function fecharEdicao() {
    setEditandoId(null)
    setConfirmandoExclusaoId(null)
  }

  async function handleSalvar(cartaoId: string, pergunta: string, resposta: string) {
    setErro(null)
    setProcessandoId(cartaoId)
    try {
      const atualizado = await editarCartao(cartaoId, pergunta, resposta)
      setCartoes((atuais) => (atuais ?? []).map((cartao) => (cartao.id === cartaoId ? atualizado : cartao)))
      fecharEdicao()
    } catch (err) {
      tratarErro(err, 'Não foi possível salvar as alterações.')
    } finally {
      setProcessandoId(null)
    }
  }

  async function handleArquivarOuDesarquivar(cartao: Cartao) {
    setErro(null)
    setProcessandoId(cartao.id)
    try {
      const atualizado = cartao.arquivado ? await desarquivarCartao(cartao.id) : await arquivarCartao(cartao.id)
      // Esta tela só lista cartões ativos (arquivado=false na busca inicial): se
      // o cartão passou a estar arquivado, ele sai da lista em vez de ficar
      // exibido com estado incoerente; se voltou a ativo, permanece atualizado.
      setCartoes((atuais) => {
        const outros = (atuais ?? []).filter((atual) => atual.id !== cartao.id)
        return atualizado.arquivado ? outros : [...outros, atualizado]
      })
      fecharEdicao()
    } catch (err) {
      tratarErro(err, 'Não foi possível atualizar o cartão.')
    } finally {
      setProcessandoId(null)
    }
  }

  async function handleExcluir(cartaoId: string) {
    setErro(null)
    setProcessandoId(cartaoId)
    try {
      await excluirCartao(cartaoId)
      setCartoes((atuais) => (atuais ?? []).filter((cartao) => cartao.id !== cartaoId))
      fecharEdicao()
    } catch (err) {
      tratarErro(err, 'Não foi possível excluir o cartão.')
    } finally {
      setProcessandoId(null)
    }
  }

  return (
    <main className="cartoes-screen">
      <header className="cartoes-header">
        <button type="button" className="cartoes-voltar" onClick={onVoltar}>
          ← {materia.nome}
        </button>
        <h1>Gerenciar cartões</h1>
      </header>

      {erro && <p className="cartoes-erro">{erro}</p>}

      {cartoes === null && !erro && <p>Carregando...</p>}

      {cartoes !== null && cartoes.length === 0 && <p>Nenhum cartão ativo ainda.</p>}

      {cartoes !== null && cartoes.length > 0 && (
        <ul className="cartoes-lista">
          {cartoes.map((cartao) => {
            const emAndamento = processandoId === cartao.id
            return (
              <li key={cartao.id} className="cartoes-item">
                {editandoId === cartao.id ? (
                  <div className="cartoes-item-painel">
                    <CartaoForm
                      perguntaInicial={cartao.pergunta}
                      respostaInicial={cartao.resposta}
                      rotuloConfirmar="Salvar"
                      onConfirmar={(pergunta, resposta) => handleSalvar(cartao.id, pergunta, resposta)}
                      onCancelar={fecharEdicao}
                      desabilitado={emAndamento}
                    />
                    <div className="cartoes-item-painel-acoes">
                      <button
                        type="button"
                        onClick={() => handleArquivarOuDesarquivar(cartao)}
                        disabled={emAndamento}
                      >
                        {cartao.arquivado ? 'Desarquivar' : 'Arquivar'}
                      </button>
                      {confirmandoExclusaoId === cartao.id ? (
                        <span className="cartoes-confirmar-exclusao">
                          Excluir permanentemente?
                          <button type="button" onClick={() => handleExcluir(cartao.id)} disabled={emAndamento}>
                            Confirmar
                          </button>
                          <button
                            type="button"
                            onClick={() => setConfirmandoExclusaoId(null)}
                            disabled={emAndamento}
                          >
                            Cancelar
                          </button>
                        </span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => setConfirmandoExclusaoId(cartao.id)}
                          disabled={emAndamento}
                        >
                          Excluir
                        </button>
                      )}
                    </div>
                  </div>
                ) : (
                  <div className="cartoes-item-linha">
                    <div className="cartoes-item-conteudo">
                      <p className="cartoes-item-pergunta">{cartao.pergunta}</p>
                      <p className="cartoes-item-resposta">{cartao.resposta}</p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setEditandoId(cartao.id)
                        setConfirmandoExclusaoId(null)
                      }}
                    >
                      Editar
                    </button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </main>
  )
}
