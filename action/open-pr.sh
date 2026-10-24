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

url=$(gh pr create --base "$base" --head "$branch" --title "ai-fix: fix failing tests on $base" --body-file "$body")
echo "url=$url" >> "$GITHUB_OUTPUT"
echo "Pull request: $url" | tee -a "$GITHUB_STEP_SUMMARY"
