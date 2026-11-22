package com.servidorandroid.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Abre um projeto num navegador embutido, com console, prints e ferramentas de teste. */
public class NavegadorActivity extends Activity {

    static final String EXTRA_URL = "url";
    static final String EXTRA_NOME = "nome";
    static final String EXTRA_CAMINHO = "caminho";
    private static final int PEDIDO_ARQUIVO = 10;
    private static final int LIMITE_CONSOLE = 300;
    /** Limite de pixels do print longo (~80 MB de memória). */
    private static final long LIMITE_PIXELS = 20_000_000L;
    private static final String UA_PC =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final String JS_VIEWPORT_PC =
            "(function(){var m=document.querySelector('meta[name=viewport]');"
                    + "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}"
                    + "m.setAttribute('content','width=1280');})()";

    private WebView web;
    private LinearLayout barra;
    private LinearLayout painel;
    private ScrollView rolagemConsole;
    private TextView console;
    private Button botaoConsole;
    private final SpannableStringBuilder linhas = new SpannableStringBuilder();
    private int quantidade, erros;
    private ValueCallback<Uri[]> callbackArquivo;
    private String urlInicial;
    private String uaOriginal;
    private boolean modoPC, telaCheia;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        urlInicial = getIntent().getStringExtra(EXTRA_URL);
        String nome = getIntent().getStringExtra(EXTRA_NOME);
        // Se o Android fechou o app em segundo plano, o servidor precisa ser religado.
        try {
            urlInicial = Projetos.urlPara(this, projeto(), getIntent().getStringExtra(EXTRA_CAMINHO));
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao iniciar o servidor: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(MainActivity.FUNDO);

        // ---------------- barra superior
        barra = new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setPadding(dp(6), dp(6), dp(6), dp(6));
        barra.setBackgroundColor(MainActivity.CARTAO);
        Button fechar = botao("✕");
        fechar.setOnClickListener(v -> finish());
        barra.addView(fechar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView titulo = new TextView(this);
        titulo.setText(nome);
        titulo.setTextColor(MainActivity.TEXTO);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titulo.setSingleLine(true);
        titulo.setPadding(dp(8), 0, dp(4), 0);
        barra.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1));
        Button paginas = botao("☰");
        paginas.setOnClickListener(v -> escolherPagina());
        barra.addView(paginas, margem(dp(40)));
        Button recarregar = botao("⟳");
        recarregar.setOnClickListener(v -> web.reload());
        barra.addView(recarregar, margem(dp(40)));
        Button print = botao("📷");
        print.setOnClickListener(v -> menuPrint());
        barra.addView(print, margem(dp(40)));
        botaoConsole = botao("Console");
        botaoConsole.setOnClickListener(v -> painel.setVisibility(
                painel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        barra.addView(botaoConsole, margem(-2));
        Button mais = botao("⋮");
        mais.setOnClickListener(this::menuMais);
        barra.addView(mais, margem(dp(36)));
        raiz.addView(barra);

        web = new WebView(this);
        raiz.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));

        // ---------------- painel do console
        painel = new LinearLayout(this);
        painel.setOrientation(LinearLayout.VERTICAL);
        painel.setBackgroundColor(Color.parseColor("#020617"));
        painel.setVisibility(View.GONE);

        LinearLayout topoConsole = new LinearLayout(this);
        topoConsole.setPadding(dp(8), dp(6), dp(8), 0);
        Button limpar = botao("Limpar console");
        limpar.setOnClickListener(v -> limparConsole());
        topoConsole.addView(limpar, new LinearLayout.LayoutParams(-2, dp(32)));
        painel.addView(topoConsole);

        rolagemConsole = new ScrollView(this);
        console = new TextView(this);
        console.setTypeface(Typeface.MONOSPACE);
        console.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        console.setTextColor(MainActivity.TEXTO);
        console.setTextIsSelectable(true);
        console.setPadding(dp(10), dp(6), dp(10), dp(6));
        rolagemConsole.addView(console);
        painel.addView(rolagemConsole, new LinearLayout.LayoutParams(-1, 0, 1));

        // Linha para digitar JavaScript
        LinearLayout entrada = new LinearLayout(this);
        entrada.setGravity(Gravity.CENTER_VERTICAL);
        entrada.setPadding(dp(8), dp(4), dp(8), dp(6));
        EditText comando = new EditText(this);
        comando.setHint("JavaScript… ex.: document.title");
        comando.setHintTextColor(MainActivity.TEXTO_FRACO);
        comando.setTextColor(MainActivity.TEXTO);
        comando.setTypeface(Typeface.MONOSPACE);
        comando.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        comando.setSingleLine(true);
        comando.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        comando.setImeOptions(EditorInfo.IME_ACTION_GO);
        comando.setBackground(fundo(MainActivity.CARTAO));
        comando.setPadding(dp(10), dp(6), dp(10), dp(6));
        Button executar = botao("▶");
        executar.setOnClickListener(v -> executarJs(comando));
        comando.setOnEditorActionListener((v, acao, ev) -> {
            if (acao == EditorInfo.IME_ACTION_GO
                    || (ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN)) {
                executarJs(comando);
                return true;
            }
            return false;
        });
        entrada.addView(comando, new LinearLayout.LayoutParams(0, dp(40), 1));
        entrada.addView(executar, margem(dp(44)));
        painel.addView(entrada);

        int altura = (int) (getResources().getDisplayMetrics().heightPixels * 0.38);
        raiz.addView(painel, new LinearLayout.LayoutParams(-1, altura));

        setContentView(raiz);
        configurarWebView();
        if (estado != null) web.restoreState(estado);
        else web.loadUrl(urlInicial);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configurarWebView() {
        WebView.setWebContentsDebuggingEnabled(true); // permite inspecionar pelo chrome://inspect no PC
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setAllowFileAccess(false);
        uaOriginal = s.getUserAgentString();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String esquema = req.getUrl().getScheme();
                if ("http".equals(esquema) || "https".equals(esquema)) return false;
                abrirFora(req.getUrl().toString()); // tel:, mailto:, whatsapp:, etc.
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (modoPC) view.evaluateJavascript(JS_VIEWPORT_PC, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError erro) {
                registrar(ConsoleMessage.MessageLevel.ERROR,
                        "Falha ao carregar " + req.getUrl() + ": " + erro.getDescription(), null, 0);
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse resp) {
                String caminho = req.getUrl().getPath();
                if (caminho != null && caminho.endsWith("/favicon.ico")) return; // ruído comum
                registrar(ConsoleMessage.MessageLevel.ERROR,
                        "HTTP " + resp.getStatusCode() + " em " + req.getUrl(), null, 0);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                registrar(m.messageLevel(), m.message(), m.sourceId(), m.lineNumber());
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (callbackArquivo != null) callbackArquivo.onReceiveValue(null);
                callbackArquivo = callback;
                try {
                    startActivityForResult(params.createIntent(), PEDIDO_ARQUIVO);
                } catch (ActivityNotFoundException e) {
                    callbackArquivo = null;
                    return false;
                }
                return true;
            }
        });
    }

    // ------------------------------------------------------------ páginas e menus

    private File projeto() {
        return new File(Projetos.pasta(this), getIntent().getStringExtra(EXTRA_NOME));
    }

    private void escolherPagina() {
        MainActivity.escolherPagina(this, projeto(), Projetos.paginas(projeto()), (caminho, noChrome) -> {
            try {
                String url = Projetos.urlPara(this, projeto(), caminho);
                if (noChrome) abrirFora(url);
                else web.loadUrl(url);
            } catch (Exception e) {
                Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void menuMais(View ancora) {
        PopupMenu m = new PopupMenu(this, ancora);
        m.getMenu().add(0, 1, 0, "Abrir no Chrome");
        m.getMenu().add(0, 2, 0, modoPC ? "✓ Modo PC (desligar)" : "Modo PC (ver como no computador)");
        m.getMenu().add(0, 3, 0, "Tela cheia");
        m.getMenu().add(0, 4, 0, "Limpar dados do site");
        m.getMenu().add(0, 5, 0, "Informações da tela");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    abrirFora(web.getUrl() != null ? web.getUrl() : urlInicial);
                    break;
                case 2:
                    alternarModoPC();
                    break;
                case 3:
                    definirTelaCheia(true);
                    break;
                case 4:
                    confirmarLimpeza();
                    break;
                case 5:
                    informacoesDaTela();
                    break;
            }
            return true;
        });
        m.show();
    }

    private void alternarModoPC() {
        modoPC = !modoPC;
        web.getSettings().setUserAgentString(modoPC ? UA_PC : uaOriginal);
        web.reload();
        Toast.makeText(this, modoPC ? "Modo PC ligado (largura 1280px)" : "Modo celular", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("deprecation")
    private void definirTelaCheia(boolean ligar) {
        telaCheia = ligar;
        barra.setVisibility(ligar ? View.GONE : View.VISIBLE);
        if (ligar) painel.setVisibility(View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(ligar
                ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                : View.SYSTEM_UI_FLAG_VISIBLE);
        if (ligar) Toast.makeText(this, "Use o botão Voltar para sair da tela cheia", Toast.LENGTH_SHORT).show();
    }

    private void confirmarLimpeza() {
        new AlertDialog.Builder(this)
                .setTitle("Limpar dados do site?")
                .setMessage("Apaga localStorage, sessionStorage, IndexedDB, cookies e cache deste projeto e "
                        + "recarrega a página, como se fosse o primeiro acesso.")
                .setPositiveButton("Limpar", (d, w) -> {
                    web.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}", r -> {
                        String url = web.getUrl();
                        if (url != null) {
                            Uri u = Uri.parse(url);
                            WebStorage.getInstance().deleteOrigin(u.getScheme() + "://" + u.getAuthority());
                        }
                        CookieManager.getInstance().removeAllCookies(null);
                        web.clearCache(true);
                        web.reload();
                        Toast.makeText(this, "Dados do site apagados", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void informacoesDaTela() {
        web.evaluateJavascript("JSON.stringify({w:innerWidth,h:innerHeight,dpr:devicePixelRatio,"
                + "sh:document.documentElement.scrollHeight,url:location.href,"
                + "titulo:document.title,ua:navigator.userAgent})", r -> {
            String texto = r;
            try {
                // o resultado vem como string JSON "escapada"
                String json = new org.json.JSONArray("[" + r + "]").getString(0);
                org.json.JSONObject o = new org.json.JSONObject(json);
                texto = "Viewport: " + o.optInt("w") + " × " + o.optInt("h") + " px (CSS)\n"
                        + "Densidade (devicePixelRatio): " + o.optDouble("dpr") + "\n"
                        + "Altura total da página: " + o.optInt("sh") + " px\n\n"
                        + "Título: " + o.optString("titulo") + "\n"
                        + "Endereço: " + o.optString("url") + "\n\n"
                        + "User-Agent: " + o.optString("ua");
            } catch (Exception ignorado) {
            }
            new AlertDialog.Builder(this).setTitle("Informações da tela").setMessage(texto)
                    .setPositiveButton("OK", null).show();
        });
    }

    // ------------------------------------------------------------ prints

    private void menuPrint() {
        new AlertDialog.Builder(this)
                .setTitle("Tirar print")
                .setItems(new String[]{"📜 Página inteira (print longo)", "📱 Só o que aparece na tela"},
                        (d, qual) -> capturar(qual == 0))
                .show();
    }

    @SuppressWarnings("deprecation")
    private void capturar(boolean paginaInteira) {
        int largura = web.getWidth();
        int alturaVisivel = web.getHeight();
        if (largura <= 0 || alturaVisivel <= 0) return;

        int altura = alturaVisivel;
        if (paginaInteira) {
            altura = Math.max(alturaVisivel, (int) Math.ceil(web.getContentHeight() * web.getScale()));
        }
        // Páginas muito compridas são reduzidas para caber na memória
        float fator = 1f;
        if ((long) largura * altura > LIMITE_PIXELS) {
            fator = (float) Math.sqrt((double) LIMITE_PIXELS / ((double) largura * altura));
        }
        Bitmap bmp;
        try {
            bmp = Bitmap.createBitmap(Math.max(1, (int) (largura * fator)), Math.max(1, (int) (altura * fator)),
                    Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Toast.makeText(this, "Página grande demais para o print. Tente \"Só o que aparece na tela\".",
                    Toast.LENGTH_LONG).show();
            return;
        }
        bmp.eraseColor(Color.WHITE);
        Canvas c = new Canvas(bmp);
        c.scale(fator, fator);
        if (!paginaInteira) c.translate(-web.getScrollX(), -web.getScrollY());
        try {
            web.draw(c);
        } catch (RuntimeException e) {
            Toast.makeText(this, "Não foi possível capturar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        if (paginaInteira && altura <= alturaVisivel + 4) {
            Toast.makeText(this, "Esta página não rola como um todo (a rolagem é interna), "
                    + "então o print saiu do tamanho da tela.", Toast.LENGTH_LONG).show();
        } else if (fator < 1f) {
            Toast.makeText(this, "Página muito longa: o print foi reduzido para caber na memória.",
                    Toast.LENGTH_LONG).show();
        }
        EditorActivity.abrir(this, bmp, getIntent().getStringExtra(EXTRA_NOME));
    }

    // ------------------------------------------------------------ console

    private void executarJs(EditText campo) {
        String codigo = campo.getText().toString().trim();
        if (codigo.isEmpty()) return;
        campo.setText("");
        registrarTexto(Color.parseColor("#7dd3fc"), "» " + codigo + "\n");
        web.evaluateJavascript("(function(){try{var r=eval(" + jsString(codigo) + ");"
                + "if(r===undefined)return 'undefined';if(typeof r==='function')return String(r);"
                + "try{return JSON.stringify(r,null,1)}catch(e){return String(r)}}"
                + "catch(e){return '✖ '+e.name+': '+e.message}})()", r -> {
            String texto = r;
            try {
                texto = new org.json.JSONArray("[" + r + "]").getString(0);
            } catch (Exception ignorado) {
            }
            int cor = texto.startsWith("✖") ? Color.parseColor("#f87171") : Color.parseColor("#a7f3d0");
            registrarTexto(cor, "← " + texto + "\n");
        });
    }

    private static String jsString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case ' ': sb.append("\\u2028"); break;
                case ' ': sb.append("\\u2029"); break;
                default: sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }

    private void limparConsole() {
        linhas.clear();
        quantidade = 0;
        erros = 0;
        console.setText("");
        atualizarBotaoConsole();
    }

    private void registrar(ConsoleMessage.MessageLevel nivel, String msg, String fonte, int linha) {
        int cor;
        String prefixo;
        switch (nivel) {
            case ERROR:
                cor = Color.parseColor("#f87171");
                prefixo = "✖ ";
                break;
            case WARNING:
                cor = MainActivity.AMARELO;
                prefixo = "⚠ ";
                break;
            case DEBUG:
            case TIP:
                cor = MainActivity.TEXTO_FRACO;
                prefixo = "· ";
                break;
            default:
                cor = MainActivity.TEXTO;
                prefixo = "› ";
        }
        String local = "";
        if (fonte != null && !fonte.isEmpty()) {
            String arquivo = fonte.replaceAll("^https?://[^/]+", "");
            local = "  (" + (arquivo.isEmpty() ? "/" : arquivo) + ":" + linha + ")";
        }
        boolean erro = nivel == ConsoleMessage.MessageLevel.ERROR;
        String texto = prefixo + msg + local + "\n";
        runOnUiThread(() -> {
            if (erro) erros++;
            registrarTexto(cor, texto);
        });
    }

    private void registrarTexto(int cor, String texto) {
        int inicio = linhas.length();
        linhas.append(texto);
        linhas.setSpan(new ForegroundColorSpan(cor), inicio, linhas.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        quantidade++;
        while (quantidade > LIMITE_CONSOLE) {
            int fim = linhas.toString().indexOf('\n');
            if (fim < 0) break;
            linhas.delete(0, fim + 1);
            quantidade--;
        }
        console.setText(linhas);
        rolagemConsole.post(() -> rolagemConsole.fullScroll(View.FOCUS_DOWN));
        atualizarBotaoConsole();
    }

    private void atualizarBotaoConsole() {
        if (erros > 0) {
            botaoConsole.setText("Console (" + erros + ")");
            botaoConsole.setBackground(fundo(Color.parseColor("#b91c1c")));
        } else {
            botaoConsole.setText("Console");
            botaoConsole.setBackground(fundo(MainActivity.CINZA));
        }
    }

    // ------------------------------------------------------------ ciclo de vida

    private void abrirFora(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Nenhum app para abrir: " + url, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (pedido == PEDIDO_ARQUIVO && callbackArquivo != null) {
            callbackArquivo.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultado, dados));
            callbackArquivo = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle estado) {
        super.onSaveInstanceState(estado);
        web.saveState(estado);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (telaCheia) definirTelaCheia(false);
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    // ------------------------------------------------------------ visual

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private LinearLayout.LayoutParams margem(int largura) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(largura, dp(40));
        lp.leftMargin = dp(5);
        return lp;
    }

    private GradientDrawable fundo(int cor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(8));
        return g;
    }

    private Button botao(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(fundo(MainActivity.CINZA));
        return b;
    }
}
