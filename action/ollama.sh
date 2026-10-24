#!/usr/bin/env bash
# Deja Ollama escuchando en localhost:11434 con el modelo bajado.
# En un runner propio (tu PC) se usa el Ollama que ya tenés corriendo; en uno de GitHub se instala.
set -euo pipefail
MODEL="$1"
URL="http://localhost:11434"

running() { curl -fsS "$URL/api/tags" >/dev/null 2>&1; }

if ! running; then
  if [ "${RUNNER_ENVIRONMENT:-}" = "self-hosted" ]; then
    echo "::error::No hay Ollama en $URL. En un runner propio, dejá Ollama abierto antes de que corra el workflow."
    exit 1
  fi
  echo "Instalando Ollama…"
  curl -fsSL https://ollama.com/install.sh | sh >/dev/null
  # El instalador lo deja como servicio del sistema, con los modelos en una carpeta de root.
  # Lo corremos como el usuario del runner para que los modelos queden en ~/.ollama (la que se cachea).
  sudo systemctl stop ollama 2>/dev/null || true
  mkdir -p "$HOME/.ollama/models"
  OLLAMA_MODELS="$HOME/.ollama/models" nohup ollama serve >"$RUNNER_TEMP/ollama.log" 2>&1 &
  for _ in $(seq 1 60); do running && break; sleep 1; done
  running || { cat "$RUNNER_TEMP/ollama.log"; echo "::error::Ollama no arrancó"; exit 1; }
fi

if curl -fsS "$URL/api/tags" | grep -q "\"name\":\"$MODEL\""; then
  echo "$MODEL ya está."
else
  echo "Bajando $MODEL (la primera vez tarda; después queda en caché)…"
  curl -fsS "$URL/api/pull" -d "{\"name\":\"$MODEL\",\"stream\":false}" >/dev/null
fi
