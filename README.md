# 📱 Meus Projetos — servidor de projetos para Android

App Android (APK) para **abrir e testar seus projetos web direto no celular**, sem
Termux e sem precisar do PC ligado.

## Baixar e instalar

1. No celular, abra:
   **https://github.com/josue-caetano/meus-projetos-android/releases/download/apk/MeusProjetos.apk**
   (ou vá em **Releases** no GitHub e toque em `MeusProjetos.apk`).
2. Abra o arquivo baixado. Se o Android pedir, permita **"Instalar apps desconhecidos"** para o navegador.
3. Se aparecer o aviso do Play Protect, toque em **"Mais detalhes" → "Instalar mesmo assim"**
   (o aviso aparece em todo app que não vem da Play Store).

O APK é gerado automaticamente pelo GitHub Actions a cada alteração no código.
Para atualizar o app, é só baixar e instalar de novo: seus projetos são mantidos.

## Como usar

**Adicionar projetos** (botões no topo):

| Botão | O que faz |
|---|---|
| **GitHub** | Digite `usuario/repositorio` (ou cole o link) e o app baixa o projeto. Depois dá para atualizar com um toque. |
| **Arquivo ZIP** | No PC, clique com o botão direito na pasta do projeto → *Compactar (ZIP)*. Mande o ZIP para o celular (WhatsApp, Drive, cabo USB…) e importe. |
| **Pasta** | Escolhe uma pasta que já está na memória do celular. |

**Em cada projeto:**

- **▶ Abrir**: abre no navegador do app, com:
  - **☰**: lista de páginas do projeto.
  - **⟳**: recarrega a página.
  - **📷 Print**: da **página inteira** (print longo) ou só do que aparece na tela. O print abre num
    editor com **lápis, círculo, retângulo, seta, texto e marca-texto** (7 cores, 3 espessuras,
    desfazer). Um dedo desenha; dois dedos rolam e dão zoom. **Salvar** grava em
    *Imagens/Meus Projetos*; **Enviar** compartilha (WhatsApp, e-mail…).
  - **Console**: mostra os `console.log` e os **erros** do site, com arquivo e linha (o botão fica
    vermelho quando há erro). Embaixo dá para **digitar JavaScript** e ver o resultado
    (ex.: `document.title`, `localStorage`, `minhaFuncao()`).
  - **⋮**: **Modo PC** (vê o site como num computador, 1280px), **Tela cheia** (sai com Voltar),
    **Limpar dados do site** (localStorage, cookies, cache: testa como primeiro acesso) e
    **Informações da tela** (tamanho do viewport, densidade, altura da página).
- **Chrome**: abre o projeto no Chrome do celular.
- **📄 N páginas — ver todas**: lista **todas** as páginas `.html` do projeto, inclusive subpastas com
  o próprio `index.html` (vários projetos dentro de um só). Toque para abrir no app; toque e segure
  para abrir no Chrome.
- **⋮**: atualizar do GitHub ou excluir.

**Repositórios privados:** toque em ⚙ → *Configurar token do GitHub*. Crie o token em
github.com → Settings → Developer settings → Personal access tokens (permissão *Contents: Read*).

## O que roda no app

✅ Sites em **HTML, CSS e JavaScript**, jogos em canvas, PWAs, sites que usam APIs da internet.
✅ **React, Vue, Angular, Svelte, Vite...** depois de compilados (`npm run build`): importe o
projeto com a pasta `dist`/`build` junto, e o app abre essa pasta sozinho.
✅ Rotas de página única (React Router etc.), `localStorage`, vídeo e áudio.

❌ Código de **servidor** (Node.js/Express, Python/Django/Flask, PHP) não roda no app: só a parte
visual (HTML/CSS/JS). Para esses, use o Codespaces ou rode no PC (veja `extras/`).

> Cada projeto roda no seu próprio endereço `http://127.0.0.1:PORTA/`, só dentro do celular.
> Caminhos como `/assets/app.js` funcionam normalmente.

**Dica para depurar:** com o celular ligado no PC por USB (depuração USB ativada), abra
`chrome://inspect` no Chrome do PC para ter o DevTools completo da página aberta no app.

## Estrutura

```
app/src/main/java/com/servidorandroid/app/
  ServidorLocal.java      servidor HTTP (Java puro)
  Projetos.java           importação (ZIP, pasta, GitHub) e detecção do tipo de projeto
  MainActivity.java       tela com a lista de projetos
  NavegadorActivity.java  navegador embutido com console
.github/workflows/apk.yml compila e publica o APK
extras/                   versão em Python (rodar no PC e abrir pelo Wi-Fi, ou no Termux)
```

Para compilar no PC: instale o Android Studio, abra esta pasta e clique em *Run*,
ou rode `./gradlew assembleRelease`.

A chave de assinatura não fica no repositório. Sem ela, o APK é assinado com a chave de
depuração padrão do Android. Para usar uma chave fixa no GitHub Actions, cadastre os segredos
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`.

---

## Autor

Desenvolvido por [Josue-Caetano](https://github.com/Josue-Caetano), com auxílio de inteligência artificial no desenvolvimento.
