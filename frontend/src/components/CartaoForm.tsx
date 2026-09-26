import { useState, type FormEvent } from 'react'
import './CartaoForm.css'

// Componente de pergunta/resposta reaproveitado tanto pela criação manual
// (AdicionarCartaoScreen) quanto pela edição em memória na tela de Revisão
// (docs/01-fluxos-de-usuario.md, seção 4c) — evita duplicar a validação entre
// os dois fluxos, inclusive quando o fluxo de IA (Épico 5) passar a editar
// sugestões na mesma tela.
interface CartaoFormProps {
  perguntaInicial?: string
  respostaInicial?: string
  rotuloConfirmar: string
  onConfirmar: (pergunta: string, resposta: string) => void
  onCancelar: () => void
  desabilitado?: boolean
}

export function CartaoForm({
  perguntaInicial = '',
  respostaInicial = '',
  rotuloConfirmar,
  onConfirmar,
  onCancelar,
  desabilitado = false,
}: CartaoFormProps) {
  const [pergunta, setPergunta] = useState(perguntaInicial)
  const [resposta, setResposta] = useState(respostaInicial)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const perguntaTratada = pergunta.trim()
    const respostaTratada = resposta.trim()
    if (!perguntaTratada || !respostaTratada) {
      return
    }
    onConfirmar(perguntaTratada, respostaTratada)
  }

  return (
    <form className="cartao-form" onSubmit={handleSubmit}>
      <label>
        Pergunta
        <textarea
          value={pergunta}
          onChange={(event) => setPergunta(event.target.value)}
          disabled={desabilitado}
          autoFocus
          required
        />
      </label>
      <label>
        Resposta
        <textarea
          value={resposta}
          onChange={(event) => setResposta(event.target.value)}
          disabled={desabilitado}
          required
        />
      </label>
      <div className="cartao-form-acoes">
        <button type="button" onClick={onCancelar} disabled={desabilitado}>
          Cancelar
        </button>
        <button type="submit" disabled={desabilitado}>
          {rotuloConfirmar}
        </button>
      </div>
    </form>
  )
}
