import type { Materia } from '../api/client'
import './MateriaScreen.css'

// Tela "Matéria selecionada" (docs/01-fluxos-de-usuario.md, seção 3): menu com
// as ações disponíveis. "Estudar cartões" (Épico 4) e "Apagar matéria" (ação
// real fora de escopo desta issue) ficam desabilitadas de propósito.
// "Gerenciar cartões" leva à lista de cartões da matéria (seção 6), de onde
// se acessa o painel de edição de cada cartão.
interface MateriaScreenProps {
  materia: Materia
  onVoltar: () => void
  onAdicionarCartao: () => void
  onGerenciarCartoes: () => void
}

export function MateriaScreen({ materia, onVoltar, onAdicionarCartao, onGerenciarCartoes }: MateriaScreenProps) {
  return (
    <main className="materia-screen">
      <button type="button" className="materia-screen-voltar" onClick={onVoltar}>
        ← Matérias
      </button>

      <h1>{materia.nome}</h1>

      <nav className="materia-menu">
        <button type="button" disabled title="Sessão de estudo chega no Épico 4 — em breve">
          Estudar cartões
        </button>
        <button type="button" onClick={onAdicionarCartao}>
          Adicionar cartão
        </button>
        <button type="button" onClick={onGerenciarCartoes}>
          Gerenciar cartões
        </button>
        <button type="button" disabled title="Confirmação de exclusão ainda não implementada — em breve">
          Apagar matéria
        </button>
      </nav>
    </main>
  )
}
