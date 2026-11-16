import { useEffect, useState } from 'react'
import { ApiError, listarCartoes, type Cartao, type Materia } from '../api/client'
import { SessaoCartoes } from '../components/SessaoCartoes'
import './EstudoScreen.css'

// Sessão de estudo (docs/01-fluxos-de-usuario.md, seção 5): busca só os
// cartões ativos (arquivado=false) na ordem devolvida pela API e entrega a
// lista ao baralho. Nada é gravado durante a sessão — nem avaliação nem
// `ultimaRevisao`.
interface EstudoScreenProps {
  materia: Materia
  onUnauthorized: () => void
  onVoltar: () => void
  onAdicionarCartao: () => void
}

export function EstudoScreen({ materia, onUnauthorized, onVoltar, onAdicionarCartao }: EstudoScreenProps) {
  const [cartoes, setCartoes] = useState<Cartao[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    listarCartoes(materia.id, false)
      .then(setCartoes)
      .catch((err: unknown) => {
        if (err instanceof ApiError && err.status === 401) {
          onUnauthorized()
          return
        }
        setErro(err instanceof ApiError ? err.message : 'Não foi possível carregar os cartões.')
      })
  }, [materia.id, onUnauthorized])

  return (
    <main className="estudo-screen">
      <header className="estudo-header">
        <button type="button" className="estudo-voltar" onClick={onVoltar}>
          ← {materia.nome}
        </button>
        <h1>Estudar cartões</h1>
      </header>

      {erro && <p className="estudo-erro">{erro}</p>}

      {cartoes === null && !erro && <p>Carregando...</p>}

      {cartoes !== null && cartoes.length === 0 && (
        <div className="estudo-vazio">
          <p>Nenhum cartão ativo nesta matéria.</p>
          <button type="button" onClick={onAdicionarCartao}>
            Adicionar cartão
          </button>
        </div>
      )}

      {cartoes !== null && cartoes.length > 0 && <SessaoCartoes cartoes={cartoes} onSair={onVoltar} />}
    </main>
  )
}
