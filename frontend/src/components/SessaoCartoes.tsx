import { useState, type ReactNode } from 'react'
import type { Cartao } from '../api/client'
import './SessaoCartoes.css'

// Baralho passivo (docs/01-fluxos-de-usuario.md, seção 5): tocar no cartão
// vira pergunta ↔ resposta, "Próximo cartão" avança. Sem avaliação por cartão
// — decisão de produto fechada. Separado da tela para ser reaproveitado pela
// revisão do Arquivo (seção 7, issue #23), que muda só a lista de entrada.
//
// `acoesDoCartao` é o slot do canto do cartão: o ícone de edição inline
// (issue #13) e o "Desarquivar este cartão" (issue #23) entram por ele. Se o
// dono da lista remover o cartão atual, o mesmo índice passa a mostrar o
// próximo — é o comportamento esperado ao desarquivar ou excluir.
interface SessaoCartoesProps {
  cartoes: Cartao[]
  onSair: () => void
  acoesDoCartao?: (cartao: Cartao) => ReactNode
}

export function SessaoCartoes({ cartoes, onSair, acoesDoCartao }: SessaoCartoesProps) {
  const [indice, setIndice] = useState(0)
  // Guardar o id (e não um booleano) faz o cartão seguinte começar sempre
  // pela pergunta, mesmo quando ele chega por remoção do atual.
  const [viradoId, setViradoId] = useState<string | null>(null)

  const atual = cartoes[indice]

  if (!atual) {
    return (
      <div className="sessao-fim">
        <p>Fim dos cartões.</p>
        <div className="sessao-controles">
          <button type="button" onClick={onSair}>
            Voltar
          </button>
          <button
            type="button"
            className="sessao-principal"
            onClick={() => {
              setIndice(0)
              setViradoId(null)
            }}
          >
            Recomeçar
          </button>
        </div>
      </div>
    )
  }

  const virado = viradoId === atual.id

  return (
    <div className="sessao">
      <p className="sessao-progresso">
        {indice + 1} / {cartoes.length}
      </p>

      <div className="sessao-cartao">
        <button
          type="button"
          className="sessao-cartao-face"
          onClick={() => setViradoId(virado ? null : atual.id)}
        >
          <span className="sessao-cartao-rotulo">{virado ? 'Resposta' : 'Pergunta'}</span>
          <span className="sessao-cartao-texto">{virado ? atual.resposta : atual.pergunta}</span>
          <span className="sessao-cartao-dica">Toque para virar</span>
        </button>
        {acoesDoCartao && <div className="sessao-cartao-acoes">{acoesDoCartao(atual)}</div>}
      </div>

      <div className="sessao-controles">
        <button type="button" className="sessao-principal" onClick={() => setIndice(indice + 1)}>
          Próximo cartão
        </button>
      </div>
    </div>
  )
}
