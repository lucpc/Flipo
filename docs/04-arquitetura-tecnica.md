# Arquitetura técnica

## Stack

| Camada | Escolha | Motivo |
|---|---|---|
| Backend | Java 21 + Spring Boot | Alinhado com a experiência prévia do desenvolvedor (Java/Spring) |
| Persistência | Spring Data JPA + Hibernate | Mapeamento direto das entidades de `02-modelo-de-dados.md` |
| Autenticação | Spring Security + JWT | Protege endpoints; escopa dados por usuário via token |
| Validação | Bean Validation (`@NotBlank`, `@Size`, etc.) nos DTOs | |
| Banco de dados | PostgreSQL | Relacional, modela bem `Usuario → Materia → Cartao` |
| Hospedagem do banco | Neon ou Railway (Postgres gerenciado) | Free tier, sem manter servidor |
| Frontend | React + Vite + TypeScript | Backend já resolve API/servidor — não há motivo para Next.js |
| Deploy backend | Render ou Railway (Docker) | Deploy direto do GitHub |
| Deploy frontend | Vercel ou Netlify | |
| Testes backend | JUnit 5 + Mockito | Priorizar testes da camada de serviço (regras de arquivar/gerar-ia) |
| Migrations | Flyway | Versionadas, em vez de `ddl-auto: update` em produção |

**Rust foi cogitado e descartado** para um microsserviço de pré-processamento de texto — decisão
do dono do produto, não reabrir sem justificativa nova.

## Estratégia de IA

### Abstração de geração de cartões

Toda chamada de geração de cartões passa por uma interface, nunca é chamada diretamente pelos
controllers ou serviços de negócio:

```java
public interface GeradorDeCartoes {
    List<CartaoSugerido> gerar(String texto, int quantidade);
}
```

Isso permite trocar a implementação por configuração/perfil Spring, sem alterar nenhuma outra
parte do sistema.

### Fase de concepção (API estabelecida)

Para validar o fluxo completo (colar texto → gerar → revisar → salvar) rapidamente, sem perder
tempo depurando saída malformada de modelo pequeno, a primeira implementação usa uma API já
estabelecida, em camada barata:

- **Anthropic** (ex: Claude Haiku) e/ou **Gemini** (ex: Gemini Flash) — saída estruturada em JSON
  confiável, latência baixa. Custo por chamada é irrelevante no volume de desenvolvimento de um
  MVP solo.

`GeradorAnthropic` (e, se usado, `GeradorGemini`) `implements GeradorDeCartoes` — chamada sempre
feita a partir do backend (nunca do cliente, para não expor a chave), solicitando saída
estruturada em JSON. Essa mesma implementação cobre também a cota padrão em produção — não há uma
implementação de "fase de produção" separada a escrever depois.

### Redução de custo (depois de validado, opcional)

Uma vez o fluxo validado, avaliar mover a cota padrão (não-BYOK) para uma opção gratuita ou de
baixíssimo custo, se o volume de uso justificar:

- **Ollama + modelo local** (Llama 3.1/3.2, Mistral, Gemma 2) — roda na própria máquina, custo
  zero, mas exige hospedar o modelo e tem qualidade/confiabilidade de JSON inferiores a uma API
  estabelecida.
- **Groq API** — serve modelos open-source (Llama, Mixtral) com camada gratuita generosa e baixa
  latência, sem exigir hardware local.

Modelos menores são menos confiáveis para devolver JSON bem formado — se essa fase for adotada, a
camada de parsing precisa ficar tolerante a erro (ex: nova tentativa em caso de JSON malformado).
Isso não é necessário enquanto a cota padrão usa Anthropic/Gemini.

### BYOK (bring your own key)

Para não deixar o custo de produção recair sobre o desenvolvedor indefinidamente, o app suporta
que cada usuário configure sua própria chave de API (tabela `ChaveApi`, ver
`02-modelo-de-dados.md`). Quando uma chave de usuário está configurada, ela é usada nas chamadas
daquele usuário; caso contrário, cai para a cota padrão (Anthropic/Gemini em camada barata, ou o
gerador de baixo custo da fase de redução de custo, se essa fase já tiver sido adotada).

Chaves nunca são armazenadas em texto puro — sempre criptografadas antes de persistir, e nunca
retornadas pela API (ver contrato em `03-contrato-api.md`, endpoint `GET /usuarios/me/chaves`
devolve só o provedor, nunca a chave).

## Segurança

- JWT em todos os endpoints exceto `/api/auth/*`.
- Toda consulta a `Materia`/`Cartao` filtra implicitamente por `usuario_id` extraído do token —
  nunca confiar em um ID de matéria/cartão vindo do path sem checar que pertence ao usuário
  autenticado.
- Chaves de API de terceiros criptografadas em repouso.
- Senha armazenada como hash (ex: BCrypt via Spring Security), nunca em texto puro.
