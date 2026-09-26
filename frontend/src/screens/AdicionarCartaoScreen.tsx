import { CartaoForm } from '../components/CartaoForm'
import './AdicionarCartaoScreen.css'

// Criação manual (docs/01-fluxos-de-usuario.md, seção 4a). Ao confirmar, não
// persiste nada aqui — o cartão vai pré-carregado para a tela de Revisão
// (seção 4c), que é quem chama a API. "Gerar com IA" (seção 4b) é Épico 5,
// fora de escopo desta tela por enquanto.
interface AdicionarCartaoScreenProps {
  onCancelar: () => void
  onConfirmar: (pergunta: string, resposta: string) => void
}

export function AdicionarCartaoScreen({ onCancelar, onConfirmar }: AdicionarCartaoScreenProps) {
  return (
    <main className="adicionar-cartao-screen">
      <h1>Adicionar cartão</h1>
      <p className="adicionar-cartao-hint">Criação manual — vai para a revisão antes de salvar.</p>
      <CartaoForm rotuloConfirmar="Ir para revisão" onConfirmar={onConfirmar} onCancelar={onCancelar} />
    </main>
  )
}
