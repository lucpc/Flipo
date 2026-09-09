# Backlog — Flipo

Este arquivo é o estado vivo do projeto: o que já foi feito, o que falta, e em que ordem. Existe
para que uma sessão de desenvolvimento (humana ou assistida por IA) não precise reconstruir esse
contexto lendo todo o histórico de commits ou adivinhando pelo código — e para reduzir o risco de
uma sessão de IA reabrir uma decisão já fechada (ver seção "Decisões de produto já fechadas" no
`CLAUDE.md` e o skill `product-invariants`) por falta de contexto.

Ao terminar uma tarefa, marque a caixa correspondente **na mesma alteração** que fecha a tarefa.
Ao começar uma tarefa fora de ordem, anote por quê (ex: bloqueio, prioridade de portfolio).

## Épico 0 — Bootstrap do projeto

- [x] Decidir ferramenta de build do backend (**Maven**) e gerar o esqueleto Spring Boot 4.1.1 /
      Java 21 em `backend/` (`./mvnw test` verificado contra Postgres real)
- [x] Esqueleto frontend Vite + React + TypeScript em `frontend/`, com a paleta Rosé Pine Dawn
      como tokens CSS em `src/theme.css` (`npm run lint` e `npm run build` verificados)
- [x] `docker-compose.yml` com Postgres local (para rodar migrations Flyway sem depender do
      Neon/Railway durante o desenvolvimento) — `make up` / `make psql` / `make reset`
- [x] Repositório Git inicializado (branch `main`)
- [ ] Repositório remoto no GitHub criado e branch `main` protegida (mandatory CI, 1 aprovação,
      sem push direto — ver seção "Branch protection" abaixo) — feito pelo dono do projeto
- [x] CI básico (`.github/workflows/ci-backend.yml`, `ci-frontend.yml`) — build + testes por path,
      só passa a rodar de verdade quando houver remoto

## Épico 1 — Modelo de dados e autenticação

- [ ] Entidades JPA + migrations Flyway a partir de `02-modelo-de-dados.md`
- [ ] Registro/login com JWT (Spring Security)
- [ ] Testes de serviço para regras de posse (usuário só acessa os próprios dados)

## Épico 2 — CRUD de matérias

- [ ] `GET/POST/DELETE /api/materias`
- [ ] Contagem de ativos/arquivados por matéria (`totalAtivos`, `totalArquivados`)
- [ ] Tela de lista de matérias (sem `MAX_MATERIAS`)

## Épico 3 — CRUD de cartões (manual)

- [ ] `GET/POST/PATCH/DELETE /api/cartoes` conforme contrato
- [ ] Criação manual → tela de revisão (mesmo componente usado pelo fluxo de IA)
- [ ] Edição de cartão (pergunta/resposta, arquivar/desarquivar, excluir)

## Épico 4 — Sessão de estudo

- [ ] Frontend consumindo `GET /cartoes?arquivado=false`
- [ ] Flip + avançar, sem avaliação obrigatória
- [ ] Edição inline a partir da sessão de estudo

## Épico 5 — Geração por IA (fase de concepção — API estabelecida)

- [ ] Interface `GeradorDeCartoes`
- [ ] Implementação `GeradorAnthropic` e/ou `GeradorGemini` (camada barata: Haiku / Gemini Flash)
- [ ] `POST /cartoes/gerar-ia` (sugestão, sem persistência) + `POST /cartoes/lote` (persistência)

## Épico 6 — BYOK

- [ ] Tabela/entidade `ChaveApi` (criptografada em repouso)
- [ ] `GET/POST/DELETE /usuarios/me/chaves` (nunca devolve a chave)
- [ ] Seleção da implementação de `GeradorDeCartoes` por config quando o usuário tem chave própria

## Épico 7 — Redução de custo (opcional, pós-validação)

- [ ] Avaliar mover a cota padrão (não-BYOK) para Ollama ou Groq
- [ ] Parsing tolerante a JSON malformado do modelo (necessário só se este épico for adotado)

## Épico 8 — Polimento

- [ ] Paleta Rosé Pine Dawn aplicada em todo o frontend
- [ ] Aba de cartões arquivados por matéria

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

- CI obrigatório verde (`Backend CI`, `Frontend CI`)
- Mínimo 1 aprovação (ou auto-merge liberado, por ser projeto solo — decisão do dono do repo)
- Sem push direto
- Histórico linear (squash merge)

### Definição de pronto (por tarefa)

1. Endpoint(s)/tela(s) implementados conforme `03-contrato-api.md` / `01-fluxos-de-usuario.md`.
2. Testes de serviço (JUnit + Mockito) para regra de negócio não trivial.
3. Nenhuma decisão da lista "Decisões de produto já fechadas" (`CLAUDE.md`) violada.
4. Nenhum endpoint novo devolve dado de outro usuário nem uma chave de API em texto puro.
5. Caixa correspondente marcada neste arquivo.
6. Documentação (`docs/0x-*.md`) atualizada se o contrato mudou.
