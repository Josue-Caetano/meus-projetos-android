package com.servidorandroid.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    static final int FUNDO = Color.parseColor("#0f172a");
    static final int CARTAO = Color.parseColor("#1e293b");
    static final int CINZA = Color.parseColor("#334155");
    static final int AZUL = Color.parseColor("#3b82f6");
    static final int TEXTO = Color.parseColor("#e2e8f0");
    static final int TEXTO_FRACO = Color.parseColor("#94a3b8");
    static final int AMARELO = Color.parseColor("#fbbf24");

    private static final int PEDIDO_ZIP = 1;
    private static final int PEDIDO_PASTA = 2;

    private final ExecutorService tarefas = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());
    private LinearLayout lista;
    private TextView subtitulo;

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(FUNDO);
        raiz.setPadding(dp(16), dp(16), dp(16), 0);

        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titulos = new LinearLayout(this);
        titulos.setOrientation(LinearLayout.VERTICAL);
        TextView titulo = texto("📱 Meus Projetos", 22, TEXTO);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulos.addView(titulo);
        subtitulo = texto("", 13, TEXTO_FRACO);
        titulos.addView(subtitulo);
        topo.addView(titulos, new LinearLayout.LayoutParams(0, -2, 1));
        Button config = botao("⚙", CINZA);
        config.setOnClickListener(v -> mostrarConfiguracoes());
        topo.addView(config, new LinearLayout.LayoutParams(dp(48), dp(44)));
        raiz.addView(topo);

        TextView rotulo = texto("Adicionar projeto:", 13, TEXTO_FRACO);
        rotulo.setPadding(0, dp(14), 0, dp(6));
        raiz.addView(rotulo);
        LinearLayout acoes = new LinearLayout(this);
        Button gh = botao("GitHub", AZUL);
        gh.setOnClickListener(v -> pedirRepositorio(null));
        Button zip = botao("Arquivo ZIP", CINZA);
        zip.setOnClickListener(v -> escolherZip());
        Button pasta = botao("Pasta", CINZA);
        pasta.setOnClickListener(v -> escolherPasta());
        acoes.addView(gh, pesoComMargem());
        acoes.addView(zip, pesoComMargem());
        acoes.addView(pasta, new LinearLayout.LayoutParams(0, dp(44), 1));
        raiz.addView(acoes);

        ScrollView rolagem = new ScrollView(this);
        lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);
        lista.setPadding(0, dp(16), 0, dp(24));
        rolagem.addView(lista);
        raiz.addView(rolagem, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(raiz);
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarLista();
    }

    @Override
    protected void onDestroy() {
        tarefas.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------ lista

    private void atualizarLista() {
        lista.removeAllViews();
        List<File> projetos = Projetos.listar(this);
        subtitulo.setText(projetos.size() == 1 ? "1 projeto" : projetos.size() + " projetos");

        if (projetos.isEmpty()) {
            TextView vazio = texto("Nenhum projeto ainda.\n\n"
                    + "• GitHub: digite usuario/repositorio e o app baixa tudo.\n"
                    + "• Arquivo ZIP: compacte a pasta do projeto no PC e mande para o celular.\n"
                    + "• Pasta: escolha uma pasta que já está no celular.", 15, TEXTO_FRACO);
            vazio.setPadding(dp(16), dp(24), dp(16), dp(24));
            vazio.setBackground(fundo(CARTAO, 12));
            lista.addView(vazio);
            return;
        }
        for (File p : projetos) lista.addView(cartao(p));
    }

    private View cartao(File projeto) {
        Projetos.Info info = Projetos.analisar(projeto);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        c.setBackground(fundo(CARTAO, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);

        LinearLayout linha = new LinearLayout(this);
        linha.setGravity(Gravity.CENTER_VERTICAL);
        TextView nome = texto(projeto.getName(), 17, TEXTO);
        nome.setTypeface(Typeface.DEFAULT_BOLD);
        linha.addView(nome, new LinearLayout.LayoutParams(0, -2, 1));
        TextView tag = texto(info.tipo, 12, TEXTO);
        tag.setPadding(dp(8), dp(2), dp(8), dp(2));
        tag.setBackground(fundo(CINZA, 999));
        linha.addView(tag);
        c.addView(linha);

        String detalhe = formatarTamanho(Projetos.tamanho(projeto));
        if (info.origem != null) detalhe += " · GitHub: " + info.origem;
        if (info.temIndex && info.entrada != projeto) {
            detalhe += " · abre " + info.entrada.getName() + "/";
        }
        TextView det = texto(detalhe, 12, TEXTO_FRACO);
        det.setPadding(0, dp(4), 0, 0);
        c.addView(det);

        if (info.aviso != null) {
            TextView aviso = texto("⚠ " + info.aviso, 12, AMARELO);
            aviso.setPadding(0, dp(8), 0, 0);
            c.addView(aviso);
        }

        LinearLayout botoes = new LinearLayout(this);
        botoes.setPadding(0, dp(12), 0, 0);
        Button abrir = botao("▶ Abrir", AZUL);
        abrir.setOnClickListener(v -> abrir(projeto, null, false));
        Button chrome = botao("Chrome", CINZA);
        chrome.setOnClickListener(v -> abrir(projeto, null, true));
        Button mais = botao("⋮", CINZA);
        mais.setOnClickListener(v -> menuDoProjeto(v, projeto, info));
        botoes.addView(abrir, pesoComMargem());
        botoes.addView(chrome, pesoComMargem());
        botoes.addView(mais, new LinearLayout.LayoutParams(dp(48), dp(44)));
        c.addView(botoes);

        List<Projetos.Pagina> paginas = Projetos.paginas(projeto);
        if (paginas.size() > 1 || (paginas.size() == 1 && !paginas.get(0).principal)) {
            Button todas = botao("📄 " + paginas.size() + (paginas.size() == 1 ? " página" : " páginas")
                    + " no projeto — ver todas", CINZA);
            todas.setOnClickListener(v -> escolherPagina(this, projeto, paginas,
                    (caminho, noChrome) -> abrir(projeto, caminho, noChrome)));
            LinearLayout.LayoutParams lpTodas = new LinearLayout.LayoutParams(-1, dp(40));
            lpTodas.topMargin = dp(8);
            c.addView(todas, lpTodas);
        }
        return c;
    }

    private void menuDoProjeto(View ancora, File projeto, Projetos.Info info) {
        PopupMenu m = new PopupMenu(this, ancora);
        if (info.origem != null) m.getMenu().add(0, 1, 0, "Atualizar do GitHub");
        m.getMenu().add(0, 2, 0, "Excluir");
        m.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                String[] partes = info.origem.split("@", 2);
                baixar(partes[0], partes.length > 1 ? partes[1] : "", projeto.getName());
            } else if (item.getItemId() == 2) {
                new AlertDialog.Builder(this)
                        .setTitle("Excluir \"" + projeto.getName() + "\"?")
                        .setMessage("Os arquivos serão apagados do app.")
                        .setPositiveButton("Excluir", (d, w) -> {
                            Projetos.pararServidor(projeto.getName());
                            Projetos.apagar(projeto);
                            atualizarLista();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
            return true;
        });
        m.show();
    }

    interface AoEscolherPagina {
        void escolheu(String caminho, boolean noChrome);
    }

    /** Lista todas as páginas HTML do projeto. Toque: abre no app. Toque longo: abre no Chrome. */
    static void escolherPagina(Activity tela, File projeto, List<Projetos.Pagina> paginas, AoEscolherPagina acao) {
        if (paginas.isEmpty()) {
            Toast.makeText(tela, "Nenhuma página .html neste projeto", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] titulos = new String[paginas.size()];
        for (int i = 0; i < titulos.length; i++) titulos[i] = paginas.get(i).titulo;
        AlertDialog dialogo = new AlertDialog.Builder(tela)
                .setTitle("Páginas de " + projeto.getName())
                .setItems(titulos, (d, i) -> acao.escolheu(paginas.get(i).caminho, false))
                .setNegativeButton("Fechar", null)
                .create();
        dialogo.getListView().setOnItemLongClickListener((lista, v, i, id) -> {
            dialogo.dismiss();
            acao.escolheu(paginas.get(i).caminho, true);
            return true;
        });
        dialogo.show();
        Toast.makeText(tela, "Dica: toque e segure para abrir no Chrome", Toast.LENGTH_SHORT).show();
    }

    private void abrir(File projeto, String caminho, boolean noChrome) {
        String url;
        try {
            url = Projetos.urlPara(this, projeto, caminho);
        } catch (Exception e) {
            erro("Não foi possível iniciar o servidor", e);
            return;
        }
        if (noChrome) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                Toast.makeText(this, "Deixe o app aberto em segundo plano enquanto usa o Chrome",
                        Toast.LENGTH_LONG).show();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Nenhum navegador encontrado", Toast.LENGTH_SHORT).show();
            }
        } else {
            Intent i = new Intent(this, NavegadorActivity.class);
            i.putExtra(NavegadorActivity.EXTRA_URL, url);
            i.putExtra(NavegadorActivity.EXTRA_NOME, projeto.getName());
            i.putExtra(NavegadorActivity.EXTRA_CAMINHO, caminho);
            startActivity(i);
        }
    }

    // ------------------------------------------------------------ importação

    private void escolherZip() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream"});
        tentarAbrir(i, PEDIDO_ZIP);
    }

    private void escolherPasta() {
        tentarAbrir(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PEDIDO_PASTA);
    }

    private void tentarAbrir(Intent i, int pedido) {
        try {
            startActivityForResult(i, pedido);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Este celular não tem um seletor de arquivos disponível", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent dados) {
        super.onActivityResult(pedido, resultado, dados);
        if (resultado != RESULT_OK || dados == null || dados.getData() == null) return;
        Uri uri = dados.getData();
        if (pedido == PEDIDO_ZIP) {
            String nome = Projetos.nomeSeguro(Projetos.nomeDoDocumento(getContentResolver(), uri));
            executar("Extraindo " + nome + "…", () -> {
                File tmp = Projetos.temporaria(this, nome);
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("Não foi possível abrir o arquivo");
                    Projetos.extrairZip(in, tmp);
                }
                Projetos.concluir(this, tmp, nome);
                return "\"" + nome + "\" importado!";
            });
        } else if (pedido == PEDIDO_PASTA) {
            String nome = Projetos.nomeSeguro(Projetos.nomeDaPasta(getContentResolver(), uri));
            executar("Copiando " + nome + "…", () -> {
                File tmp = Projetos.temporaria(this, nome);
                Projetos.copiarPasta(getContentResolver(), uri, tmp);
                Projetos.concluir(this, tmp, nome);
                return "\"" + nome + "\" importado!";
            });
        }
    }

    private void pedirRepositorio(String sugestao) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        EditText repo = new EditText(this);
        repo.setHint("usuario/repositorio ou link");
        repo.setSingleLine(true);
        repo.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        if (sugestao != null) repo.setText(sugestao);
        EditText ramo = new EditText(this);
        ramo.setHint("ramo (opcional, ex.: main)");
        ramo.setSingleLine(true);
        form.addView(repo);
        form.addView(ramo);

        new AlertDialog.Builder(this)
                .setTitle("Baixar do GitHub")
                .setView(form)
                .setPositiveButton("Baixar", (d, w) -> {
                    String r = Projetos.normalizarRepo(repo.getText().toString());
                    if (r == null) {
                        Toast.makeText(this, "Use o formato usuario/repositorio", Toast.LENGTH_LONG).show();
                        pedirRepositorio(repo.getText().toString());
                        return;
                    }
                    baixar(r, ramo.getText().toString(), Projetos.nomeSeguro(r.substring(r.indexOf('/') + 1)));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void baixar(String repo, String ramo, String nome) {
        String token = preferencias().getString("token", "");
        executar("Baixando " + repo + "…", () -> {
            File tmp = Projetos.temporaria(this, nome);
            Projetos.baixarDoGitHub(repo, ramo, token, tmp);
            Projetos.concluir(this, tmp, nome);
            return "\"" + nome + "\" baixado!";
        });
    }

    // ------------------------------------------------------------ configurações

    private void mostrarConfiguracoes() {
        String token = preferencias().getString("token", "");
        String[] opcoes = {
                token.isEmpty() ? "Configurar token do GitHub (repositórios privados)" : "Alterar/remover token do GitHub",
                "Atualizar todos os projetos do GitHub",
                "Sobre"
        };
        new AlertDialog.Builder(this)
                .setTitle("Configurações")
                .setItems(opcoes, (d, qual) -> {
                    if (qual == 0) pedirToken();
                    else if (qual == 1) atualizarTodos();
                    else sobre();
                })
                .show();
    }

    private void pedirToken() {
        EditText campo = new EditText(this);
        campo.setHint("ghp_… ou github_pat_…");
        campo.setSingleLine(true);
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        campo.setText(preferencias().getString("token", ""));
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(campo, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Token do GitHub")
                .setMessage("Necessário só para repositórios privados. Crie em github.com → Settings → "
                        + "Developer settings → Personal access tokens, com permissão de leitura (Contents: Read).\n\n"
                        + "Fica salvo apenas neste celular.")
                .setView(box)
                .setPositiveButton("Salvar", (d, w) -> {
                    preferencias().edit().putString("token", campo.getText().toString().trim()).apply();
                    Toast.makeText(this, "Token salvo", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Remover", (d, w) -> preferencias().edit().remove("token").apply())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void atualizarTodos() {
        String token = preferencias().getString("token", "");
        executar("Atualizando projetos…", () -> {
            int ok = 0, falhas = 0;
            for (File p : Projetos.listar(this)) {
                Projetos.Info info = Projetos.analisar(p);
                if (info.origem == null) continue;
                String[] partes = info.origem.split("@", 2);
                try {
                    File tmp = Projetos.temporaria(this, p.getName());
                    Projetos.baixarDoGitHub(partes[0], partes.length > 1 ? partes[1] : "", token, tmp);
                    Projetos.concluir(this, tmp, p.getName());
                    ok++;
                } catch (Exception e) {
                    falhas++;
                }
            }
            if (ok == 0 && falhas == 0) return "Nenhum projeto veio do GitHub";
            return ok + " atualizado(s)" + (falhas > 0 ? ", " + falhas + " com erro" : "");
        });
    }

    private void sobre() {
        new AlertDialog.Builder(this)
                .setTitle("Meus Projetos")
                .setMessage("Servidor local para abrir e testar projetos web (HTML, CSS, JavaScript, "
                        + "React/Vue/Angular compilados) direto no celular.\n\n"
                        + "Cada projeto roda em http://127.0.0.1:PORTA, sem internet.\n\n"
                        + "No modo \"Abrir\", o botão Console mostra os erros e mensagens console.log do site.\n\n"
                        + "Versão " + versao())
                .setPositiveButton("OK", null)
                .show();
    }

    private String versao() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private SharedPreferences preferencias() {
        return getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ tarefas em segundo plano

    interface Tarefa {
        String executar() throws Exception;
    }

    @SuppressWarnings("deprecation")
    private void executar(String mensagem, Tarefa tarefa) {
        ProgressDialog progresso = new ProgressDialog(this);
        progresso.setMessage(mensagem);
        progresso.setCancelable(false);
        progresso.show();
        tarefas.execute(() -> {
            String resultado = null;
            Exception falha = null;
            try {
                resultado = tarefa.executar();
            } catch (Exception e) {
                falha = e;
            }
            final String r = resultado;
            final Exception f = falha;
            principal.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                progresso.dismiss();
                if (f != null) erro("Não deu certo", f);
                else Toast.makeText(this, r, Toast.LENGTH_LONG).show();
                atualizarLista();
            });
        });
    }

    private void erro(String titulo, Exception e) {
        String msg = e.getMessage();
        if (e instanceof java.net.UnknownHostException) msg = "Sem conexão com a internet.";
        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(msg == null ? e.toString() : msg)
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------------------------------------------------ visual

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView texto(String s, int sp, int cor) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(cor);
        return t;
    }

    private Button botao(String s, int cor) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(fundo(cor, 8));
        return b;
    }

    private LinearLayout.LayoutParams pesoComMargem() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
        lp.rightMargin = dp(8);
        return lp;
    }

    private GradientDrawable fundo(int cor, int raio) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(raio));
        return g;
    }

    private static String formatarTamanho(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
