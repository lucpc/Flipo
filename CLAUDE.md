# Flipo — guia para desenvolvimento

Este arquivo orienta qualquer sessão de desenvolvimento assistido neste repositório. Os
documentos detalhados estão em `docs/` — leia-os antes de implementar qualquer funcionalidade
relacionada ao tema que cobrem.

- `docs/00-visao-geral.md` — produto, diferencial, filosofia de revisão, escopo do MVP
- `docs/01-fluxos-de-usuario.md` — telas e navegação
- `docs/02-modelo-de-dados.md` — schema do banco (entidades, campos, relacionamentos)
- `docs/03-contrato-api.md` — endpoints REST (request/response)
- `docs/04-arquitetura-tecnica.md` — stack, estratégia de IA, segurança
- `docs/Backlog.md` — roadmap por épico e convenções de branch/PR/commit/DoD. O estado
  tarefa-a-tarefa vive nas [GitHub Issues](https://github.com/lucpc/Flipo/issues) e no
  [Project board](https://github.com/users/lucpc/projects/1) — `docs/Backlog.md` não duplica
  checkbox por tarefa, só linka o milestone de cada épico, pra não ter duas fontes divergindo

## Agentes do projeto — delegar sempre que possível

Este repositório tem uma equipe enxuta de agentes em `.claude/agents/`, cada um carregando as
skills `careful-engineering` (disciplina de engenharia) e `product-invariants` (decisões
fechadas + regras de segurança, resumidas dessa mesma forma para não precisar reler todo o
`docs/` a cada sessão).

| Agente | Quando delegar |
|---|---|
| `backend-java` | Entidades JPA, migrations, controllers, serviços, testes do backend |
| `frontend-react` | Telas, navegação, integração com a API, testes do frontend |
| `code-reviewer` | Revisão final independente antes de marcar uma issue como concluída |
| `github-project` | Ler/mover issues no board, abrir/fechar issue, status do roadmap ("o que fazer agora?") |

Para uma mudança que atravessa backend e frontend (ex: mudança de contrato de API), alinhe
`docs/03-contrato-api.md` primeiro e só então delegue cada lado.

Trabalho trivial (typo, ajuste de uma linha, texto de doc) pode ser feito diretamente na sessão.

## Contexto do projeto

Reconstrução, como aplicação full-stack de portfolio, de um protótipo p5.js que já validava o
conceito de flashcards livres organizados por matéria. O diferencial da nova versão é permitir
criação manual **e** criação assistida por IA (a partir de texto colado ou PDF), convergindo
numa tela de revisão comum antes de qualquer persistência.

## Stack (resumo — detalhes em `04-arquitetura-tecnica.md`)

- Backend: Java 21, Spring Boot, Spring Data JPA, Spring Security (JWT)
- Banco: PostgreSQL (Flyway para migrations)
- Frontend: React + Vite + TypeScript
- IA: interface `GeradorDeCartoes`, implementação trocável (Ollama/Groq em desenvolvimento,
  Anthropic em produção), com suporte a BYOK (chave própria do usuário)

## Decisões de produto já fechadas — não reabrir sem alinhar antes

Estas decisões passaram por várias iterações na fase de design e são intencionais, não
lacunas a preencher livremente:

1. **Sem repetição espaçada com datas/algoritmo.** Cogitado e descartado. Não implementar
   `intervalo`, `fatorFacilidade` ou `proximaRevisao`.
2. **Sem avaliação obrigatória por cartão durante o estudo.** A sessão é passiva: vira e avança.
3. **"Arquivar" é uma ação sobre o cartão, nunca sobre a matéria**, e só acontece via edição
   deliberada do cartão (nunca automaticamente, nunca durante a sessão de estudo). É reversível
   a qualquer momento.
4. **Geração por IA nunca persiste diretamente.** Sempre passa por `gerar-ia` (sugestão) →
   revisão do usuário → `lote` (persistência).
5. **Sem limite artificial de matérias por usuário** (diferente do protótipo original).
6. **Sem microsserviço em Rust.** Cogitado, descartado.
7. **Chaves de API de terceiros nunca em texto puro**, nunca retornadas por endpoint algum.

## Identidade visual

Preservar a paleta Rosé Pine Dawn do protótipo original como tema base do frontend — valores em
`docs/00-visao-geral.md`.

## Ordem de implementação e definição de pronto

Não duplicado aqui — ver `docs/Backlog.md` para a ordem por épico (com link pro milestone de cada
um) e a definição de pronto. O que está feito/em progresso/pendente tarefa-a-tarefa vive nas
GitHub Issues (`gh issue list`, ou peça pro agente `github-project`), não neste arquivo.
