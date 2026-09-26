import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError, criarMateria, listarMaterias, type Materia } from '../api/client'
import './MateriasScreen.css'

interface MateriasScreenProps {
  onUnauthorized: () => void
  onSelecionarMateria: (materia: Materia) => void
}

export function MateriasScreen({ onUnauthorized, onSelecionarMateria }: MateriasScreenProps) {
  const [materias, setMaterias] = useState<Materia[] | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [mostrarFormulario, setMostrarFormulario] = useState(false)
  const [novoNome, setNovoNome] = useState('')
  const [salvando, setSalvando] = useState(false)

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

  const carregarMaterias = useCallback(async () => {
    try {
      setMaterias(await listarMaterias())
    } catch (err) {
      tratarErro(err, 'Não foi possível carregar as matérias.')
    }
  }, [tratarErro])

  useEffect(() => {
    carregarMaterias()
  }, [carregarMaterias])

  async function handleCriar(event: FormEvent) {
    event.preventDefault()
    const nome = novoNome.trim()
    if (!nome) {
      return
    }

    setSalvando(true)
    setErro(null)
    try {
      const materia = await criarMateria(nome)
      setMaterias((atuais) => [...(atuais ?? []), materia])
      setNovoNome('')
      setMostrarFormulario(false)
    } catch (err) {
      tratarErro(err, 'Não foi possível criar a matéria.')
    } finally {
      setSalvando(false)
    }
  }

  return (
    <main className="materias-screen">
      <header className="materias-header">
        <h1>Minhas matérias</h1>
        <button type="button" onClick={() => setMostrarFormulario((atual) => !atual)}>
          Nova matéria
        </button>
      </header>

      {mostrarFormulario && (
        <form className="nova-materia-form" onSubmit={handleCriar}>
          <label>
            Nome da matéria
            <input
              type="text"
              value={novoNome}
              onChange={(event) => setNovoNome(event.target.value)}
              autoFocus
              required
            />
          </label>
          <button type="submit" disabled={salvando}>
            {salvando ? 'Salvando...' : 'Salvar'}
          </button>
        </form>
      )}

      {erro && <p className="materias-erro">{erro}</p>}

      {materias === null && !erro && <p>Carregando...</p>}

      {materias !== null && materias.length === 0 && <p>Nenhuma matéria ainda. Crie a primeira.</p>}

      {materias !== null && materias.length > 0 && (
        <ul className="materias-lista">
          {materias.map((materia) => (
            <li key={materia.id}>
              <button
                type="button"
                className="materia-item"
                onClick={() => onSelecionarMateria(materia)}
              >
                <span className="materia-nome">{materia.nome}</span>
                <span className="materia-contador">
                  {materia.totalAtivos > 0
                    ? `${materia.totalAtivos} ativos`
                    : materia.totalArquivados > 0
                      ? 'tudo arquivado'
                      : 'sem cartões ainda'}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
