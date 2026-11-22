#!/data/data/com.termux/files/usr/bin/bash
# Instala tudo o que é preciso no Termux (Android).
set -e

echo ">> Atualizando pacotes do Termux..."
pkg update -y && pkg upgrade -y

echo ">> Instalando Python, Git, Node.js e PHP..."
pkg install -y python git nodejs-lts php

echo ">> Liberando acesso ao armazenamento do celular (aceite a permissão)..."
termux-setup-storage || true

PASTA="$HOME/projetos"
mkdir -p "$PASTA"

DIR="$(cd "$(dirname "$0")" && pwd)"
chmod +x "$DIR"/*.sh

# Atalho: digitar "projetos" no Termux inicia o servidor
if ! grep -q "alias projetos=" "$HOME/.bashrc" 2>/dev/null; then
  echo "alias projetos='bash \"$DIR/iniciar.sh\"'" >> "$HOME/.bashrc"
fi

echo ""
echo "Pronto! Feche e abra o Termux e digite:  projetos"
echo "Seus projetos ficam em: $PASTA"
echo "Para baixar um projeto do GitHub:  bash $DIR/clonar.sh https://github.com/usuario/repo"
