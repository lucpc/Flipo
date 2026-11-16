import type { Materia } from '../api/client'
import './MateriaScreen.css'

// Tela "Matéria selecionada" (docs/01-fluxos-de-usuario.md, seção 3): menu com
// as ações disponíveis. "Estudar cartões" abre a sessão de estudo (seção 5) e
// fica sempre habilitada: o total vem do snapshot da lista de matérias e pode
// estar defasado, então a própria sessão trata o caso sem cartões ativos.
// "Apagar matéria" (ação real fora de escopo desta issue) fica desabilitada.
// "Gerenciar cartões" leva à lista de cartões da matéria (seção 6), de onde
// se acessa o painel de edição de cada cartão.
interface MateriaScreenProps {
  materia: Materia
  onVoltar: () => void
  onEstudar: () => void
  onAdicionarCartao: () => void
  onGerenciarCartoes: () => void
}

export function MateriaScreen({ materia, onVoltar, onEstudar, onAdicionarCartao, onGerenciarCartoes }: MateriaScreenProps) {
  return (
    <main className="materia-screen">
      <button type="button" className="materia-screen-voltar" onClick={onVoltar}>
        ← Matérias
      </button>

      <h1>{materia.nome}</h1>

      <nav className="materia-menu">
        <button type="button" className="materia-menu-entrada" onClick={onEstudar}>
          Estudar cartões
          <span className="materia-menu-contagem">
            {materia.totalAtivos === 0
              ? 'nenhum ativo'
              : `${materia.totalAtivos} ${materia.totalAtivos === 1 ? 'ativo' : 'ativos'}`}
          </span>
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
