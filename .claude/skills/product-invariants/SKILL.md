---
name: product-invariants
description: Non-negotiable product and security decisions for Flipo — closed product decisions, auth/BYOK security rules, and the AI-generation review boundary. Load for any product or engineering change.
---

# Flipo — Product & Security Invariants

`docs/00-visao-geral.md` through `docs/04-arquitetura-tecnica.md` are the source of truth. The
rules below are a compact, enforceable subset. If a doc and this skill differ, stop and surface
the conflict instead of silently choosing one.

## Closed product decisions — do not reopen without explicit alignment

These were considered and deliberately rejected. Do not reintroduce them as "improvements":

- No spaced repetition. No `intervalo`, `fatorFacilidade`, or `proximaRevisao` fields, no due
  dates, no scheduling algorithm.
- No mandatory per-card self-assessment during study. The study session is passive: flip, advance.
- No subject-level archive. `arquivado` is a boolean on `Cartao` only, set exclusively through
  deliberate card edit — never automatically, never during a study session, never as a side
  effect of a review score.
- No artificial limit on subjects per user (the original prototype's `MAX_MATERIAS = 5` is gone).
- No Rust microservice for text preprocessing.

## AI-generation boundary — never bypass

- AI card generation must never persist directly. The flow is always:
  `gerar-ia` (suggestion, no write) → user review (accept/edit/discard each card) →
  `lote` (persistence of only what the user approved).
- `POST .../cartoes/gerar-ia` must not write to the database under any circumstance, including
  error-recovery or retry paths.
- Generated cards are marked `origem = "ia"`; manually created cards `origem = "manual"`. Do not
  collapse this distinction.

## Authorization and data scoping

- Every `Materia`/`Cartao` query is implicitly scoped to the authenticated user via the JWT
  subject. Never accept `usuarioId` from a path parameter or request body to select whose data to
  read or write.
- Before any read/update/delete on a `Materia` or `Cartao` by id, verify it belongs to the
  authenticated user — a valid JWT for user A must never be able to touch user B's row by
  guessing/enumerating an id.

## Secrets and credentials

- Passwords are never stored or logged in plain text — hash only (e.g. BCrypt).
- Third-party API keys (`ChaveApi.chave_criptografada`) are encrypted before persisting and are
  never returned by any endpoint, including the owner's own `GET /usuarios/me/chaves` (provider
  name only, never the key value).
- Never log a decrypted API key, a JWT, or a password/hash in application logs or error messages.

## Architecture defaults from the docs

- Backend: Java 21, Spring Boot, Spring Data JPA, Spring Security (JWT).
- Database: PostgreSQL, migrations via Flyway — do not rely on `ddl-auto: update` beyond local
  prototyping.
- Frontend: React + Vite + TypeScript.
- AI generation goes through the `GeradorDeCartoes` interface; never call a provider SDK directly
  from a controller or from frontend code (provider keys must never reach the client).
- Visual identity: Rosé Pine Dawn palette (values in `docs/00-visao-geral.md`) — do not swap
  palettes as part of an unrelated change.

These are project defaults, not permission to rewrite architecture during unrelated work. Any
change to them requires an explicit decision, recorded back into `docs/04-arquitetura-tecnica.md`.

## Required behavior when a task conflicts with an invariant

Do not implement the conflicting behavior. Return:

1. the requested behavior,
2. the invariant it conflicts with,
3. the smallest product decision needed to proceed,
4. a safe alternative when one exists.
