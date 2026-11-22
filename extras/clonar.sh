#!/usr/bin/env bash
# Baixa (ou atualiza) um repositório do GitHub para a pasta de projetos.
# Uso: bash clonar.sh https://github.com/usuario/repo
set -e
PASTA="${PROJETOS:-$HOME/projetos}"
mkdir -p "$PASTA"

if [ -z "$1" ]; then
  echo "Uso: bash clonar.sh URL-do-repositorio"
  echo "Para atualizar todos os projetos já baixados: bash clonar.sh --atualizar"
  exit 1
fi

if [ "$1" = "--atualizar" ]; then
  for d in "$PASTA"/*/; do
    if [ -d "$d/.git" ]; then
      echo ">> Atualizando $(basename "$d")"
      git -C "$d" pull --ff-only || echo "   (não foi possível atualizar)"
    fi
  done
  exit 0
fi

NOME="$(basename "$1" .git)"
if [ -d "$PASTA/$NOME/.git" ]; then
  echo ">> $NOME já existe, atualizando..."
  git -C "$PASTA/$NOME" pull --ff-only
else
  git clone "$1" "$PASTA/$NOME"
fi
echo "Pronto! Abra o painel e procure por: $NOME"
