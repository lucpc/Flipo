import type { Materia } from '../api/client'
import './MateriaScreen.css'

// Tela "Matéria selecionada" (docs/01-fluxos-de-usuario.md, seção 3): menu com
// as três ações. "Estudar cartões" (Épico 4) e "Apagar matéria" (ação real
// fora de escopo desta issue) ficam desabilitadas de propósito.
interface MateriaScreenProps {
  materia: Materia
  onVoltar: () => void
  onAdicionarCartao: () => void
}

export function MateriaScreen({ materia, onVoltar, onAdicionarCartao }: MateriaScreenProps) {
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
        <button type="button" disabled title="Confirmação de exclusão ainda não implementada — em breve">
          Apagar matéria
        </button>
      </nav>
    </main>
  )
}
