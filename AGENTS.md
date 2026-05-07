# AGENTS.md — TourDoum Codex Guide

This project follows the SSAFY_Advance harness. The workspace-level Codex guide
is `/Users/woolee/SSAFY_Advance/SSAFY_Advance/AGENTS.md`, and the design source
of truth is `/Users/woolee/SSAFY_Advance/SSAFY_Advance/HARNESS_DESIGN.md`.

## Harness Paths

- Workspace root: `/Users/woolee/SSAFY_Advance/SSAFY_Advance`
- Harness root: `/Users/woolee/SSAFY_Advance/SSAFY_Advance/.harness`
- Role prompts: `/Users/woolee/SSAFY_Advance/SSAFY_Advance/.harness/agent-prompts`

## Worktree Rule

- One agent works in one worktree on one branch.
- Worktrees live under `20-spec-tourdoum/.worktrees/20-spec-tourdoum-<role>-<slug>/`.
- Use `/Users/woolee/SSAFY_Advance/SSAFY_Advance/.harness/scripts/wt-new.sh 20-spec-tourdoum <role> <slug>`
  to create one.
- Do not edit files outside the current worktree unless the user explicitly asks
  for integration, review, or harness-level changes.

## Codex Fallback

Codex may take any Claude-defined role when Claude quota is unavailable. Keep the
role's responsibility boundary and use the matching prompt under the harness
root's `agent-prompts/` directory.

For non-interactive role execution:

```bash
/Users/woolee/SSAFY_Advance/SSAFY_Advance/.harness/scripts/codex-agent.sh 20-spec-tourdoum <role> <slug> <task-file>
```

Supported roles are `be`, `fe`, `ui`, `infra`, `qa`, `review`, and `codex`.

## Required Lifecycle

Maintain these worktree-root files:

- `plan.md`
- `diff.md`
- `self-review.md`
- `handoff.md`
- `codex-context.md` when Codex has implemented, reviewed, or taken over work.

`agent-init.sh` checks for Codex history (`codex-context.md`, `.codex-*.log`,
or a `codex/*` branch) and appends a "Codex 작업 이력 확인" section to
`plan.md`. Read that section before editing. If you are Codex and you make or
verify changes, update `codex-context.md` so Claude can inspect the work later.

Before reporting implementation work as complete, run:

```bash
/Users/woolee/SSAFY_Advance/SSAFY_Advance/.harness/scripts/agent-finalize.sh "$PWD"
/Users/woolee/SSAFY_Advance/SSAFY_Advance/.harness/scripts/harness-check.sh "$PWD"
```

Record any user-authorized `harness skip <id...>` in `self-review.md`.

## Secrets

Never commit `.env`, API keys, DB passwords, JWT secrets, generated credentials,
or real production data. `OPENAI_API_KEY` must stay in the environment only.
