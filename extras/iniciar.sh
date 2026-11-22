#!/usr/bin/env bash
# Inicia o painel de projetos e abre no navegador do celular.
DIR="$(cd "$(dirname "$0")" && pwd)"
PORTA="${PORTA:-8080}"

# Impede o Android de "dormir" o Termux enquanto o servidor roda
command -v termux-wake-lock >/dev/null && termux-wake-lock

# Abre o navegador depois de 1 segundo (no Termux)
if command -v termux-open-url >/dev/null; then
  (sleep 1 && termux-open-url "http://localhost:$PORTA") &
fi

python "$DIR/servidor.py" -p "$PORTA" "$@" 2>/dev/null || python3 "$DIR/servidor.py" -p "$PORTA" "$@"
