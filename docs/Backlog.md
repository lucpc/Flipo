# Backlog — Flipo

Este arquivo é o roadmap por épico e as convenções do projeto — não o rastreador tarefa-a-tarefa.
O estado do que já foi feito, o que está em progresso e o que falta vive nas
[GitHub Issues](https://github.com/lucpc/Flipo/issues) e no
[Project board](https://github.com/users/lucpc/projects/1) (`gh project item-list 1 --owner lucpc`,
ou peça pro agente `github-project`). Mantido assim de propósito — duplicar checkbox por tarefa
aqui e status na issue são duas fontes que divergem cedo ou tarde.

Existe pra que uma sessão de desenvolvimento (humana ou assistida por IA) não precise reconstruir
contexto lendo todo o histórico de commits ou adivinhando pelo código — e para reduzir o risco de
uma sessão de IA reabrir uma decisão já fechada (ver seção "Decisões de produto já fechadas" no
`CLAUDE.md` e o skill `product-invariants`) por falta de contexto.

## Épico 0 — Bootstrap do projeto

- [x] Decidir ferramenta de build do backend (**Maven**) e gerar o esqueleto Spring Boot 4.1.1 /
      Java 21 em `backend/` (`./mvnw test` verificado contra Postgres real)
- [x] Esqueleto frontend Vite + React + TypeScript em `frontend/`, com a paleta Rosé Pine Dawn
      como tokens CSS em `src/theme.css` (`npm run lint` e `npm run build` verificados)
- [x] `docker-compose.yml` com Postgres local (para rodar migrations Flyway sem depender do
      Neon/Railway durante o desenvolvimento) — `make up` / `make psql` / `make reset`
- [x] Repositório Git inicializado (branch `main`)
- [x] Repositório remoto criado em [lucpc/Flipo](https://github.com/lucpc/Flipo) e branch `main`
      protegida via ruleset (`main-protection`): PR obrigatório, 0 aprovações exigidas (projeto
      solo — ver "Branch protection" abaixo), CI obrigatório (`Backend CI` + `Frontend CI`), sem
      force-push, sem exclusão da branch. `current_user_can_bypass: never`, vale até pro dono.
      Merge restrito a squash (`allow_squash_merge` only, `delete_branch_on_merge` ativado).
- [x] CI básico (`.github/workflows/ci-backend.yml`, `ci-frontend.yml`) — build + testes, sem
      filtro de path no gatilho `pull_request` (roda em todo PR, mesmo um que só mexe em `docs/`)
      para que o required status check sempre reporte e nunca trave um PR indefinidamente

## Épico 1 — Modelo de dados e autenticação

Entidades JPA + migrations Flyway, registro/login JWT, testes de posse de dados.
[Milestone](https://github.com/lucpc/Flipo/milestone/1) · issues `epic:E1`

## Épico 2 — CRUD de matérias

Endpoints de matéria, contagem de ativos/arquivados, tela de lista (sem `MAX_MATERIAS`).
[Milestone](https://github.com/lucpc/Flipo/milestone/2) · issues `epic:E2`

## Épico 3 — CRUD de cartões (manual)

Endpoints de cartão, criação manual → tela de revisão, edição/arquivar/excluir.
[Milestone](https://github.com/lucpc/Flipo/milestone/3) · issues `epic:E3`

## Épico 4 — Sessão de estudo

Consumo de `GET /cartoes?arquivado=false`, flip + avançar sem avaliação obrigatória, edição inline.
[Milestone](https://github.com/lucpc/Flipo/milestone/4) · issues `epic:E4`

## Épico 5 — Geração por IA (fase de concepção — API estabelecida)

Interface `GeradorDeCartoes`, implementação Anthropic/Gemini, endpoints `gerar-ia` + `lote`.
[Milestone](https://github.com/lucpc/Flipo/milestone/5) · issues `epic:E5`

## Épico 6 — BYOK

Entidade `ChaveApi`, endpoints de chave, seleção de implementação por usuário.
[Milestone](https://github.com/lucpc/Flipo/milestone/6) · issues `epic:E6`

## Épico 7 — Redução de custo (opcional, pós-validação)

Avaliar Ollama/Groq como cota padrão; parsing tolerante a JSON malformado se adotado.
[Milestone](https://github.com/lucpc/Flipo/milestone/7) · issues `epic:E7`

## Épico 8 — Polimento

Paleta Rosé Pine Dawn aplicada em todo o frontend; aba de cartões arquivados por matéria.
[Milestone](https://github.com/lucpc/Flipo/milestone/8) · issues `epic:E8`

---

## Convenções de processo

### Branches

```
main                    # sempre deployável
feat/E3-crud-cartoes
fix/E5-parsing-ia
chore/E0-bootstrap-ci
```

- Trunk-based: branches curtas, merges frequentes.
- PR pequeno é a regra — se passar de ~400 linhas, a tarefa provavelmente devia ter sido quebrada.

### Commits

[Conventional Commits](https://www.conventionalcommits.org/), mesmos tipos do prefixo de branch:

```
feat(E3): endpoint de criação manual de cartão
fix(E5): parsing tolerante a JSON malformado do gerador
chore(E0): bootstrap do projeto (backend, frontend, docker-compose, CI)
docs(backlog): marca épico 3 como concluído
```

- Tipo obrigatório: `feat`, `fix`, `chore`, `docs`, `refactor` ou `test`.
- Escopo entre parênteses referencia o épico (`E0`-`E8`) quando a tarefa pertence a um; use um
  escopo descritivo (`docs`, `deps`) quando não pertencer.
- Mensagem no imperativo, minúscula, sem ponto final — a mesma convenção do título de PR.
- Squash merge no PR: o título do squash vira o commit em `main`, então ele também segue esse
  padrão (o histórico de commits individuais da branch pode ser mais solto).

### Branch protection em `main`

Aplicada via [ruleset](https://github.com/lucpc/Flipo/rules/22697263) `main-protection`:

- CI obrigatório verde (`Backend CI`, `Frontend CI`)
- 0 aprovações exigidas — projeto solo, ninguém além do dono pode aprovar o próprio PR
- PR obrigatório, sem push direto (vale até para o dono do repo)
- Sem force-push, sem exclusão da branch
- Histórico linear (squash merge — único método habilitado no repo)

### Definição de pronto (por tarefa)

1. Endpoint(s)/tela(s) implementados conforme `03-contrato-api.md` / `01-fluxos-de-usuario.md`.
2. Testes de serviço (JUnit + Mockito) para regra de negócio não trivial.
3. Nenhuma decisão da lista "Decisões de produto já fechadas" (`CLAUDE.md`) violada.
4. Nenhum endpoint novo devolve dado de outro usuário nem uma chave de API em texto puro.
5. Issue fechada como `completed` e Project Status = `Done` (ver skill `github-project`).
6. Documentação (`docs/0x-*.md`) atualizada se o contrato mudou.
