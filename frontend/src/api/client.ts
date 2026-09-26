// Client de API mínimo: injeta o JWT no header Authorization e centraliza o
// tratamento de erro (docs/03-contrato-api.md). Chama sempre `/api/...` — o
// proxy do Vite (vite.config.ts) encaminha para o backend em dev.

const TOKEN_KEY = 'flipo_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function extrairMensagemDeErro(response: Response): Promise<string> {
  try {
    const corpo = await response.json()
    if (corpo && typeof corpo.mensagem === 'string') {
      return corpo.mensagem
    }
  } catch {
    // corpo vazio ou não-JSON (ex.: 401 devolvido pelo filtro de JWT sem corpo)
  }
  return response.status === 401
    ? 'Sessão expirada. Faça login novamente.'
    : `Falha na requisição (status ${response.status}).`
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`/api${path}`, { ...init, headers })

  if (!response.ok) {
    if (response.status === 401) {
      // Token ausente/inválido/expirado, ou credenciais de login inválidas — em
      // ambos os casos não há um token válido para manter, então limpamos.
      clearToken()
    }
    throw new ApiError(response.status, await extrairMensagemDeErro(response))
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export interface Materia {
  id: string
  nome: string
  totalAtivos: number
  totalArquivados: number
}

export interface Cartao {
  id: string
  pergunta: string
  resposta: string
  origem: string
  arquivado: boolean
  ultimaRevisao: string | null
}

export function login(email: string, senha: string): Promise<{ token: string }> {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, senha }),
  })
}

export function listarMaterias(): Promise<Materia[]> {
  return request('/materias')
}

export function criarMateria(nome: string): Promise<Materia> {
  return request('/materias', {
    method: 'POST',
    body: JSON.stringify({ nome }),
  })
}

export function criarCartao(materiaId: string, pergunta: string, resposta: string): Promise<Cartao> {
  return request(`/materias/${materiaId}/cartoes`, {
    method: 'POST',
    body: JSON.stringify({ pergunta, resposta }),
  })
}
