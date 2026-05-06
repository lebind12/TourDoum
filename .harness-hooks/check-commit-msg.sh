#!/usr/bin/env bash
# Conventional Commits 검사. pre-commit commit-msg 훅으로 호출됨.
# 인자: $1 = 커밋 메시지 파일 경로
set -euo pipefail

msg_file="${1:?usage: check-commit-msg.sh <COMMIT_EDITMSG>}"
first_line="$(head -n1 "$msg_file")"

# 머지/리버트 자동 메시지는 통과
case "$first_line" in
  Merge\ *|Revert\ *|"fixup! "*|"squash! "*) exit 0 ;;
esac

if ! printf '%s\n' "$first_line" | \
    grep -Eq '^(feat|fix|refactor|test|docs|chore|perf|build|ci|revert)(\(.+\))?: '; then
  cat <<'EOF' >&2
✗ commit message must follow Conventional Commits.
  형식: <type>(<scope>)?: <subject>
  type: feat|fix|refactor|test|docs|chore|perf|build|ci|revert
  예시: feat(be): add member entity
        chore(infra): bump jenkins image tag
EOF
  exit 1
fi
