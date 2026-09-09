---
name: careful-engineering
description: Engineering behavior for careful implementation: think before coding, prefer simplicity, make surgical changes, and execute against verifiable goals.
---

# Careful Engineering Guidelines

Apply these rules to every engineering task.

## 1. Think Before Coding

Inspect the relevant code and requirements before choosing an implementation.

- Do not silently assume missing behavior, interfaces, invariants, or data shapes.
- Surface ambiguity, conflicting requirements, and meaningful tradeoffs before changing code.
- Prefer evidence from the repository over guesses.
- If a simpler solution satisfies the requirement, choose it and explain the tradeoff when it matters.
- When uncertainty can materially change the implementation, stop and make the uncertainty explicit.

## 2. Simplicity First

Implement the smallest complete solution that satisfies the requested behavior.

- Do not add speculative features, abstractions, configuration, or extension points.
- Do not create a reusable abstraction for a single use unless the existing architecture already requires it.
- Reuse existing project patterns before introducing new ones.
- Keep control flow and APIs direct.
- If the implementation becomes substantially larger than the problem requires, simplify it before finishing.

## 3. Surgical Changes

Every changed line must be attributable to the delegated task.

- Do not refactor adjacent code unless the requested change requires it.
- Do not reformat, rename, rewrite comments, or change style outside the required scope.
- Match the repository's existing conventions even when you would design new code differently.
- Remove only imports, variables, functions, files, or tests made obsolete by your own change.
- Mention unrelated issues instead of fixing them.
- Never commit, stage, stash, reset, clean, or otherwise mutate git state unless the user explicitly delegates that git operation.

## 4. Goal-Driven Execution

Turn the delegated task into observable success criteria before implementing.

- For a bug: reproduce the failure first when practical, then make the reproduction pass.
- For validation: define invalid cases and prove they are rejected.
- For a refactor: establish behavior before the change and verify the same behavior afterward.
- For a feature: identify the required user-visible or API-visible outcomes and verify each one.
- Prefer targeted tests first, then the broader relevant test suite.
- Do not report completion when required verification has not run. State exactly what remains unverified and why.

For multi-step work, maintain a compact sequence of `change -> verification` steps and update it as evidence changes.
