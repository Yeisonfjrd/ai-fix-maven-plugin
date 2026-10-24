#!/usr/bin/env bash
# Sube el arreglo a una rama nueva y abre un PR contra la rama que falló. Nunca empuja a esa rama.
set -euo pipefail
base="${BASE_BRANCH:-}"
[ -n "$base" ] || base="${GITHUB_HEAD_REF:-$GITHUB_REF_NAME}"
branch="ai-fix/${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT:-1}"

git config user.name "ai-fix"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git checkout -b "$branch"
# Solo lo que tocó ai-fix: código de src/main/java. Nada de target/ ni otros archivos del runner.
git add -- ':(glob)**/src/main/java/**'
if git diff --cached --quiet; then
  echo "::warning::ai-fix dijo FIXED pero no hay cambios en src/main/java."
  exit 0
fi
git commit -q -m "fix: arreglo de ai-fix para los tests que fallaban en $base"
git push -q origin "$branch"

# La primera oración de la última respuesta del modelo: su explicación del bug.
why=$(awk '/^## assistant/{getline; getline; last=$0} END{print last}' target/ai-fix-transcript.md 2>/dev/null || true)
diff=$(git diff HEAD~1 -- . ':(glob)**/src/main/java/**')

body=$(mktemp)
cat > "$body" <<EOF
Tests were failing on \`$base\`. ai-fix asked \`$MODEL\` for a fix and **every test passes** with this change.

> ${why:-(no explanation)}

\`\`\`diff
$diff
\`\`\`

- Only \`src/main/java\` was changed. Tests were not touched.
- The full conversation with the model is in the workflow artifacts (\`ai-fix-transcript.md\`).
- Review it like any other pull request before merging.
EOF

# Justo después del push, GitHub a veces todavía no ve la rama nueva ("No commits between…"):
# se reintenta unas veces antes de darse por vencido.
url=""
for wait in 3 5 10 15 20; do
  if url=$(gh pr create --base "$base" --head "$branch" --title "ai-fix: fix failing tests on $base" --body-file "$body" 2>"$RUNNER_TEMP/pr-error.txt"); then
    break
  fi
  echo "GitHub todavía no ve la rama; reintento en ${wait}s ($(head -c 200 "$RUNNER_TEMP/pr-error.txt"))"
  url=""
  sleep "$wait"
done
if [ -z "$url" ]; then
  cat "$RUNNER_TEMP/pr-error.txt"
  echo "::error::No se pudo abrir el PR. El arreglo quedó en la rama $branch."
  exit 1
fi
echo "url=$url" >> "$GITHUB_OUTPUT"
echo "Pull request: $url" | tee -a "$GITHUB_STEP_SUMMARY"
