# 20-spec-tourdoum

> stack: polyglot · created via `.harness/scripts/init-project.sh`

## Quickstart

```
# create a worktree for an agent
../.harness/scripts/wt-new.sh 20-spec-tourdoum be feat-bootstrap

# inside that worktree
../../../.harness/scripts/agent-init.sh
# … work …
../../../.harness/scripts/agent-finalize.sh
../../../.harness/scripts/harness-check.sh

# Codex fallback from project root
../.harness/scripts/codex-agent.sh 20-spec-tourdoum fe feat-example /tmp/20-spec-tourdoum-task.md
```

See workspace-level [AGENTS.md](../AGENTS.md), [CLAUDE.md](../CLAUDE.md), and [HARNESS_DESIGN.md](../HARNESS_DESIGN.md).
