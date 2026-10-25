#!/usr/bin/env bash
# Corre ai-fix en el proyecto y deja el resultado en $GITHUB_OUTPUT (status) y en el resumen del job.
set -uo pipefail
PLUGIN="io.github.yeisonfjrd:ai-fix-maven-plugin:1.1.0:fix"
BRANCH="${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-}}"

finish() {
  echo "status=$1" >> "$GITHUB_OUTPUT"
  { echo "## ai-fix: $1"; echo; echo "$2"; } >> "$GITHUB_STEP_SUMMARY"
  exit 0
}

# Un PR de ai-fix que falla no se vuelve a pasar por ai-fix: así no entra en un bucle.
case "$BRANCH" in ai-fix/*) finish SKIPPED "Esta rama la creó ai-fix; no se corre sobre sí misma." ;; esac

# "sh ./mvnw" y no "./mvnw": un mvnw commiteado desde Windows suele venir sin permiso de ejecución.
if [ -f ./mvnw ]; then MVN=(sh ./mvnw); else MVN=(mvn); fi
mkdir -p target
"${MVN[@]}" -B "$PLUGIN" -Daifix.model="$MODEL" -Daifix.attempts="$ATTEMPTS" -Daifix.review="${REVIEW:-true}" 2>&1 | tee target/ai-fix-console.log

report=target/ai-fix-report.json
[ -f "$report" ] || finish ERROR "ai-fix no dejó reporte. Mirá ai-fix-console.log en los artifacts."

field() { sed -n "s/.*\"$1\" *: *\"\{0,1\}\([^\",]*\)\"\{0,1\}.*/\1/p" "$report" | head -1; }
status=$(field status)
attempts=$(field attempts)
model_s=$(field modelSeconds)

case "$status" in
  FIXED)
    finish FIXED "$(printf 'Modelo `%s`, %s intento(s), %s s de modelo. Todos los tests pasan.\n\n```diff\n%s\n```' \
      "$MODEL" "$attempts" "$model_s" "$(git diff -- . ':(glob)**/src/main/java/**')")" ;;
  NOT_FIXED)
    finish NOT_FIXED "Modelo \`$MODEL\`: no encontró un arreglo en $attempts intento(s). El código quedó como estaba. La conversación completa está en los artifacts (ai-fix-transcript.md)." ;;
  FLAKY)
    finish FLAKY "Los tests que fallaron pasaron al repetirlos: son inestables (tiempos, orden, red), no un bug del código. ai-fix no propone nada." ;;
  *)
    finish "$status" "Nada para arreglar con ai-fix ($status)." ;;
esac
