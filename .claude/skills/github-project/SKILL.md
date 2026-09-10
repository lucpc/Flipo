---
name: github-project
description: Manage Flipo's GitHub Issues and GitHub Project (board) with gh. Use when the user asks what to work on next, wants roadmap/project status, starts or finishes an issue, wants an issue/project field updated, or wants progress communicated on an issue.
---

# GitHub Project Workflow (Flipo)

Use `gh` as the source of truth for GitHub state. Do not use browser automation.

Operate only on the repository in the current working tree unless the user explicitly names another repository. Resolve it instead of hardcoding it:

```bash
gh repo view --json nameWithOwner,owner,name,url
```

The Project is [Flipo Roadmap](https://github.com/users/lucpc/projects/1), project number `1`, owner `lucpc`. Each épico in `docs/Backlog.md` maps to one GitHub Milestone; each task line maps to one Issue carrying an `epic:E<n>` label plus a `type:*` and `platform:*` label.

## Core rules

- Read current GitHub state before mutating it.
- Reuse the existing Project (`1`, owner `lucpc`); never create a replacement.
- Issue state and Project `Status` are separate and must stay synchronized.
- Preserve labels, milestone, and assignees unless the task explicitly changes them.
- Never mark an issue `Done` merely because code was written. Verification must pass first — see "Definição de pronto" in `docs/Backlog.md`.
- Never close an issue when verification is incomplete, failing, or blocked.
- Do not commit, push, merge, modify repository settings, or change permissions unless explicitly requested — that's outside this skill (branch protection on `main` requires a PR regardless).
- Prefer idempotent operations. Re-running the same action should not create duplicate comments or project items.
- `docs/Backlog.md` is the roadmap/conventions doc, not a duplicate task tracker — épicos 1+ point at their milestone instead of re-listing task checkboxes, to avoid two sources of truth drifting apart. Don't reintroduce per-task checkboxes there.

## Authentication

Before Project writes, verify GitHub CLI authentication and scopes:

```bash
gh auth status
```

If `project` scope is missing, tell the user to run (in their own terminal, not through a backgrounded call — the device-code flow needs more time than a backgrounded process here reliably gets):

```bash
gh auth refresh --hostname github.com -s project,read:project
```

Do not request or print tokens.

## Supported workflows

### Project status / roadmap

For "what's the project status?", "what should I work on next?", etc.:

```bash
gh project item-list 1 --owner lucpc --limit 100 --format json
```

When choosing the next task:

1. Prefer the lowest-numbered open épico still with `Todo`/`In Progress` items (épicos are meant to be worked roughly in order — see `docs/Backlog.md`).
2. Within an épico, prefer an item already `In Progress` over starting a new one.
3. Épico 7 (redução de custo) is explicitly optional/deferrable — don't suggest it ahead of 1-6/8 unless the user asks.

Return the issue number, title, épico/milestone, and why it's the best next task.

### Start an issue

For "start #7", "work on #7", "implement #7":

1. Read the issue (`gh issue view <n>`); verify it's open.
2. Resolve its Project item ID and the `Status` field/option IDs (don't hardcode — re-resolve each time, they can differ if the board was recreated):
   ```bash
   gh project field-list 1 --owner lucpc --format json
   gh project item-list 1 --owner lucpc --limit 100 --format json
   ```
3. Set Project `Status` to `In Progress`:
   ```bash
   gh project item-edit --id <item-id> --project-id <project-id> --field-id <status-field-id> --single-select-option-id <in-progress-option-id>
   ```
4. Then begin implementation, delegating to `backend-java`/`frontend-react` per `CLAUDE.md`.

Do not add a start comment unless the user asks for one.

### Communicate progress

Use issue comments only for information useful after the current session ends: a blocker, a scope decision that changes how the issue should be understood, or completion evidence. Not routine narration.

```bash
gh issue comment <n> --body "<message>"
```

### Mark an issue done

For "mark #7 done", "finish #7", "close #7", or completion of an issue the user explicitly asked to implement:

1. **Verify completion** against the issue body and `docs/Backlog.md` → "Definição de pronto": targeted tests pass, broader relevant suite passes, `code-reviewer` passed for non-trivial changes, no closed product decision violated. If verification cannot be run, do not close — report what's unverified.
2. **Update Project Status** to `Done` (resolve IDs fresh, as above — never hardcode).
3. **Close the issue**:
   ```bash
   gh issue close <n> --reason completed --comment "Completed.

   Verification:
   - <verification 1>
   - <verification 2>"
   ```
4. **Verify sync**: `gh issue view <n> --json state,stateReason,projectItems` — state closed, reason completed, Project Status Done.

### Reopen an issue

Only when the user asks or to undo an incorrect completion:

```bash
gh issue reopen <n>
```

Then set Project `Status` back to `In Progress` (or `Todo` if work hasn't actually resumed).

### Synchronize state

- Closed issue + Project status not `Done` → set Project status to `Done`.
- Open issue + Project status `Done` → report the inconsistency; don't close automatically unless asked.
- Issue missing from the Project → add it (`gh project item-add 1 --owner lucpc --url <issue-url>`), don't create a duplicate.

## Project field resolution

Never hardcode field IDs or single-select option IDs across sessions — they're specific to this Project instance and this skill shouldn't assume they're stable forever. Resolve `project-id`, `item-id`, `status field-id`, and the target option's id fresh with `gh project field-list` / `gh project item-list --format json` before every `item-edit`.

## Final response

After a GitHub mutation, return a compact result:

```text
#7 — <title>
Issue: CLOSED (completed)
Project Status: Done
Milestone: Épico <n> — <title>
Verification: <one line>
```
