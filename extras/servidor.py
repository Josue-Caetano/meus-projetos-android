#!/usr/bin/env python3
"""
Servidor Android - painel para abrir e testar seus projetos pelo celular.

Funciona no Termux (Android) e também no PC (Windows/Linux/macOS).
Usa apenas a biblioteca padrão do Python: nada para instalar além do Python.

Uso:
    python servidor.py                      # serve a pasta ~/projetos na porta 8080
    python servidor.py -d /caminho -p 3000  # outra pasta / outra porta
"""

import argparse
import html
import json
import mimetypes
import os
import socket
import sys
import time
import urllib.parse
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

PASTAS_IGNORADAS = {".git", "node_modules", "__pycache__", ".venv", "venv", ".idea", ".vscode"}
PASTAS_DE_BUILD = ["dist", "build", "public", "www", "docs", "site", "out", "src"]

LIVE_RELOAD_JS = """
<script>
/* recarregamento automático (servidor android) */
(function () {
  var ultimo = null;
  function checar() {
    fetch('/__mudancas?projeto=' + encodeURIComponent(%s), {cache: 'no-store'})
      .then(function (r) { return r.json(); })
      .then(function (d) {
        if (ultimo !== null && d.versao !== ultimo) { location.reload(); }
        ultimo = d.versao;
      })
      .catch(function () {})
      .finally(function () { setTimeout(checar, 1000); });
  }
  checar();
})();
</script>
"""


def ip_local():
    """Descobre o IP da máquina na rede Wi-Fi."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def ultima_modificacao(pasta):
    """Maior mtime dos arquivos da pasta (ignorando node_modules etc.)."""
    maior = 0.0
    for raiz, dirs, arquivos in os.walk(pasta):
        dirs[:] = [d for d in dirs if d not in PASTAS_IGNORADAS]
        for nome in arquivos:
            try:
                maior = max(maior, os.path.getmtime(os.path.join(raiz, nome)))
            except OSError:
                pass
    return maior


def analisar_projeto(caminho):
    """Detecta o tipo de projeto e onde está o index.html."""
    info = {"tipo": "Pasta", "entrada": None, "dica": None}
    arquivos = set(os.listdir(caminho))

    if "index.html" in arquivos:
        info["entrada"] = ""
    else:
        for sub in PASTAS_DE_BUILD:
            if os.path.isfile(os.path.join(caminho, sub, "index.html")):
                info["entrada"] = sub + "/"
                break

    if "package.json" in arquivos:
        info["tipo"] = "Node.js"
        try:
            with open(os.path.join(caminho, "package.json"), encoding="utf-8") as f:
                scripts = json.load(f).get("scripts", {})
        except (OSError, ValueError):
            scripts = {}
        if "dev" in scripts:
            info["dica"] = "npm install && npm run dev -- --host"
        elif "start" in scripts:
            info["dica"] = "npm install && npm start"
    elif {"requirements.txt", "manage.py", "app.py", "main.py"} & arquivos:
        info["tipo"] = "Python"
        if "manage.py" in arquivos:
            info["dica"] = "python manage.py runserver 0.0.0.0:8000"
        elif "app.py" in arquivos:
            info["dica"] = "python app.py"
        elif "main.py" in arquivos:
            info["dica"] = "python main.py"
    elif "index.php" in arquivos:
        info["tipo"] = "PHP"
        info["dica"] = "php -S 0.0.0.0:8000"
    elif info["entrada"] is not None:
        info["tipo"] = "Site (HTML)"
    return info


def pagina_painel(raiz, endereco):
    projetos = []
    try:
        nomes = sorted(os.listdir(raiz), key=str.lower)
    except OSError:
        nomes = []
    for nome in nomes:
        caminho = os.path.join(raiz, nome)
        if nome.startswith(".") or not os.path.isdir(caminho):
            continue
        projetos.append((nome, analisar_projeto(caminho)))

    cartoes = []
    for nome, info in projetos:
        base = "/p/" + urllib.parse.quote(nome) + "/"
        botoes = []
        if info["entrada"] is not None:
            botoes.append(f'<a class="btn" href="{base}{info["entrada"]}">▶ Abrir</a>')
        botoes.append(f'<a class="btn sec" href="{base}">📁 Arquivos</a>')
        dica = ""
        if info["dica"]:
            dica = (
                '<div class="dica">Para rodar o servidor do próprio projeto, no Termux:'
                f'<code>cd {html.escape(os.path.join(raiz, nome))}<br>{html.escape(info["dica"])}</code></div>'
            )
        cartoes.append(
            f'<div class="card"><div class="topo"><b>{html.escape(nome)}</b>'
            f'<span class="tag">{html.escape(info["tipo"])}</span></div>'
            f'<div class="acoes">{"".join(botoes)}</div>{dica}</div>'
        )

    if not cartoes:
        cartoes.append(
            '<div class="card vazio">Nenhum projeto encontrado em<br><code>'
            f"{html.escape(raiz)}</code><br><br>Copie suas pastas de projeto para lá "
            "ou use <code>./clonar.sh URL-do-repositorio</code>.</div>"
        )

    return f"""<!DOCTYPE html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Meus Projetos</title>
<style>
  :root {{ color-scheme: light dark; }}
  body {{ font-family: system-ui, sans-serif; margin: 0; padding: 16px; background: #0f172a; color: #e2e8f0; }}
  h1 {{ font-size: 1.4rem; margin: 0 0 4px; }}
  .sub {{ color: #94a3b8; font-size: .85rem; margin-bottom: 16px; word-break: break-all; }}
  .card {{ background: #1e293b; border-radius: 12px; padding: 14px; margin-bottom: 12px; }}
  .topo {{ display: flex; justify-content: space-between; align-items: center; gap: 8px; }}
  .tag {{ font-size: .75rem; background: #334155; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }}
  .acoes {{ display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; }}
  .btn {{ background: #3b82f6; color: #fff; text-decoration: none; padding: 10px 14px; border-radius: 8px; font-weight: 600; }}
  .btn.sec {{ background: #334155; }}
  .dica {{ margin-top: 12px; font-size: .8rem; color: #94a3b8; }}
  code {{ display: block; background: #0f172a; padding: 8px; border-radius: 6px; margin-top: 4px; color: #a5f3fc; word-break: break-all; }}
  .vazio {{ text-align: center; color: #94a3b8; }}
</style></head><body>
<h1>📱 Meus Projetos</h1>
<div class="sub">{len(projetos)} projeto(s) · {html.escape(endereco)}</div>
{"".join(cartoes)}
</body></html>"""


class Manipulador(SimpleHTTPRequestHandler):
    raiz = "."
    endereco = ""
    live_reload = True

    def log_message(self, formato, *args):
        sys.stderr.write("[%s] %s\n" % (time.strftime("%H:%M:%S"), formato % args))

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def enviar(self, corpo, tipo="text/html; charset=utf-8", status=200):
        dados = corpo.encode("utf-8") if isinstance(corpo, str) else corpo
        self.send_response(status)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(dados)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(dados)

    def caminho_seguro(self, relativo):
        alvo = os.path.realpath(os.path.join(self.raiz, relativo))
        raiz = os.path.realpath(self.raiz)
        if alvo != raiz and not alvo.startswith(raiz + os.sep):
            return None
        return alvo

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        url = urllib.parse.urlsplit(self.path)
        rota = urllib.parse.unquote(url.path)

        if rota in ("/", "/index.html"):
            return self.enviar(pagina_painel(self.raiz, self.endereco))

        if rota == "/__mudancas":
            projeto = urllib.parse.parse_qs(url.query).get("projeto", [""])[0]
            pasta = self.caminho_seguro(projeto)
            versao = ultima_modificacao(pasta) if pasta and os.path.isdir(pasta) else 0
            return self.enviar(json.dumps({"versao": versao}), "application/json")

        if not rota.startswith("/p/"):
            return self.enviar("<h1>404</h1><a href='/'>Voltar</a>", status=404)

        relativo = rota[3:]
        alvo = self.caminho_seguro(relativo)
        if alvo is None or not os.path.exists(alvo):
            return self.enviar("<h1>404 - não encontrado</h1><a href='/'>Voltar ao painel</a>", status=404)

        if os.path.isdir(alvo):
            if not rota.endswith("/"):
                self.send_response(301)
                self.send_header("Location", urllib.parse.quote(rota) + "/")
                self.end_headers()
                return
            indice = os.path.join(alvo, "index.html")
            if os.path.isfile(indice):
                alvo = indice
            else:
                return self.enviar(self.listar_pasta(alvo, rota))

        projeto = relativo.split("/", 1)[0]
        tipo = mimetypes.guess_type(alvo)[0] or "application/octet-stream"
        try:
            with open(alvo, "rb") as f:
                dados = f.read()
        except OSError:
            return self.enviar("<h1>Erro ao ler arquivo</h1>", status=500)

        if tipo == "text/html" and self.live_reload:
            script = (LIVE_RELOAD_JS % json.dumps(projeto)).encode("utf-8")
            pos = dados.lower().rfind(b"</body>")
            dados = dados[:pos] + script + dados[pos:] if pos != -1 else dados + script
            tipo = "text/html; charset=utf-8"
        elif tipo.startswith("text/") or tipo in ("application/javascript", "application/json"):
            tipo += "; charset=utf-8"
        self.enviar(dados, tipo)

    def listar_pasta(self, pasta, rota):
        itens = []
        if rota.count("/") > 3:
            itens.append('<li><a href="../">⬆ ..</a></li>')
        else:
            itens.append('<li><a href="/">⬅ Voltar ao painel</a></li>')
        for nome in sorted(os.listdir(pasta), key=lambda n: (not os.path.isdir(os.path.join(pasta, n)), n.lower())):
            if nome in PASTAS_IGNORADAS:
                continue
            eh_pasta = os.path.isdir(os.path.join(pasta, nome))
            link = urllib.parse.quote(nome) + ("/" if eh_pasta else "")
            icone = "📁" if eh_pasta else "📄"
            itens.append(f'<li><a href="{link}">{icone} {html.escape(nome)}</a></li>')
        return f"""<!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>{html.escape(rota)}</title>
<style>body{{font-family:system-ui,sans-serif;margin:0;padding:16px;background:#0f172a;color:#e2e8f0}}
ul{{list-style:none;padding:0}}li a{{display:block;padding:12px;margin-bottom:6px;background:#1e293b;border-radius:8px;color:#e2e8f0;text-decoration:none}}</style>
</head><body><h2 style="word-break:break-all">{html.escape(rota)}</h2><ul>{"".join(itens)}</ul></body></html>"""


def main():
    padrao = os.environ.get("PROJETOS", os.path.join(os.path.expanduser("~"), "projetos"))
    p = argparse.ArgumentParser(description="Painel para abrir seus projetos pelo celular.")
    p.add_argument("-d", "--pasta", default=padrao, help=f"pasta com os projetos (padrão: {padrao})")
    p.add_argument("-p", "--porta", type=int, default=int(os.environ.get("PORTA", 8080)))
    p.add_argument("--sem-reload", action="store_true", help="desativa o recarregamento automático")
    p.add_argument("--local", action="store_true", help="aceita conexões só deste aparelho (127.0.0.1)")
    args = p.parse_args()

    raiz = os.path.abspath(os.path.expanduser(args.pasta))
    os.makedirs(raiz, exist_ok=True)
    host = "127.0.0.1" if args.local else "0.0.0.0"
    ip = ip_local()

    Manipulador.raiz = raiz
    Manipulador.endereco = f"http://{ip}:{args.porta}"
    Manipulador.live_reload = not args.sem_reload

    try:
        servidor = ThreadingHTTPServer((host, args.porta), Manipulador)
    except OSError as e:
        sys.exit(f"Não foi possível usar a porta {args.porta}: {e}\nTente outra: python servidor.py -p 8081")

    print("=" * 52)
    print("  📱 Servidor Android - Meus Projetos")
    print("=" * 52)
    print(f"  Pasta dos projetos: {raiz}")
    print(f"  Neste aparelho:     http://localhost:{args.porta}")
    if not args.local:
        print(f"  Na rede Wi-Fi:      http://{ip}:{args.porta}")
    print("  Ctrl+C para parar")
    print("=" * 52)
    try:
        servidor.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor parado.")


if __name__ == "__main__":
    main()
