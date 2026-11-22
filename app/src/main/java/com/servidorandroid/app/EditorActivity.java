package com.servidorandroid.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Editor de prints: marcar com lápis, círculo, seta, texto… e salvar/compartilhar. */
public class EditorActivity extends Activity {

    private static final String EXTRA_NOME = "nome";
    private static final int PEDIDO_PERMISSAO = 20;

    /** O print é passado em memória (pode ser grande demais para um Intent). */
    private static Bitmap pendente;

    static void abrir(Activity origem, Bitmap print, String nomeProjeto) {
        pendente = print;
        Intent i = new Intent(origem, EditorActivity.class);
        i.putExtra(EXTRA_NOME, nomeProjeto);
        origem.startActivity(i);
    }

    private static final int[] CORES = {
            Color.parseColor("#ef4444"), Color.parseColor("#facc15"), Color.parseColor("#22c55e"),
            Color.parseColor("#3b82f6"), Color.parseColor("#d946ef"), Color.WHITE, Color.BLACK};
    private static final float[] ESPESSURAS = {2, 4, 8};
    private static final String[] NOMES_ESPESSURA = {"Fina", "Média", "Grossa"};

    private EditorView editor;
    private final List<Button> botoesFerramenta = new ArrayList<>();
    private final List<EditorView.Ferramenta> ferramentasDosBotoes = new ArrayList<>();
    private Button botaoCor, botaoEspessura;
    private int indiceEspessura = 1;
    private Uri salvo; // último arquivo salvo (para compartilhar sem salvar de novo)
    private int versaoSalva = -1;
    private Runnable aposPermissao;
    private final Handler principal = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle estado) {
        super.onCreate(estado);
        Bitmap imagem = pendente;
        if (imagem == null) { // o Android fechou o app enquanto estava em segundo plano
            finish();
            return;
        }

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(MainActivity.FUNDO);

        // ---------------- barra superior
        LinearLayout topo = new LinearLayout(this);
        topo.setGravity(Gravity.CENTER_VERTICAL);
        topo.setPadding(dp(6), dp(6), dp(6), dp(6));
        topo.setBackgroundColor(MainActivity.CARTAO);
        Button fechar = botao("✕", MainActivity.CINZA);
        fechar.setOnClickListener(v -> sair());
        topo.addView(fechar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView titulo = new TextView(this);
        titulo.setText(imagem.getWidth() + " × " + imagem.getHeight());
        titulo.setTextColor(MainActivity.TEXTO_FRACO);
        titulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        titulo.setPadding(dp(8), 0, dp(4), 0);
        titulo.setSingleLine(true);
        topo.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1));
        Button desfazer = botao("↶", MainActivity.CINZA);
        desfazer.setOnClickListener(v -> editor.desfazer());
        topo.addView(desfazer, margem(dp(40)));
        Button limpar = botao("🗑", MainActivity.CINZA);
        limpar.setOnClickListener(v -> {
            if (!editor.temMarcacoes()) return;
            new AlertDialog.Builder(this).setTitle("Apagar todas as marcações?")
                    .setPositiveButton("Apagar", (d, w) -> editor.limpar())
                    .setNegativeButton("Cancelar", null).show();
        });
        topo.addView(limpar, margem(dp(40)));
        Button compartilhar = botao("Enviar", MainActivity.CINZA);
        compartilhar.setOnClickListener(v -> salvar(true));
        topo.addView(compartilhar, margem(-2));
        Button salvarBtn = botao("Salvar", MainActivity.AZUL);
        salvarBtn.setOnClickListener(v -> salvar(false));
        topo.addView(salvarBtn, margem(-2));
        raiz.addView(topo);

        // ---------------- área de desenho
        editor = new EditorView(this, imagem);
        editor.setPedidoDeTexto(this::pedirTexto);
        raiz.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));

        // ---------------- ferramentas
        HorizontalScrollView rolagem = new HorizontalScrollView(this);
        rolagem.setBackgroundColor(MainActivity.CARTAO);
        rolagem.setHorizontalScrollBarEnabled(false);
        LinearLayout ferramentas = new LinearLayout(this);
        ferramentas.setGravity(Gravity.CENTER_VERTICAL);
        ferramentas.setPadding(dp(6), dp(6), dp(6), dp(6));
        adicionarFerramenta(ferramentas, "✋ Mover", EditorView.Ferramenta.MOVER);
        adicionarFerramenta(ferramentas, "✏ Lápis", EditorView.Ferramenta.LAPIS);
        adicionarFerramenta(ferramentas, "◯ Círculo", EditorView.Ferramenta.CIRCULO);
        adicionarFerramenta(ferramentas, "▭ Retângulo", EditorView.Ferramenta.RETANGULO);
        adicionarFerramenta(ferramentas, "➜ Seta", EditorView.Ferramenta.SETA);
        adicionarFerramenta(ferramentas, "T Texto", EditorView.Ferramenta.TEXTO);
        adicionarFerramenta(ferramentas, "▮ Marca-texto", EditorView.Ferramenta.MARCA_TEXTO);

        botaoCor = botao("● Cor", MainActivity.CINZA);
        botaoCor.setOnClickListener(v -> escolherCor());
        ferramentas.addView(botaoCor, margem(-2));
        botaoEspessura = botao("", MainActivity.CINZA);
        botaoEspessura.setOnClickListener(v -> {
            indiceEspessura = (indiceEspessura + 1) % ESPESSURAS.length;
            atualizarEspessura();
        });
        ferramentas.addView(botaoEspessura, margem(-2));
        rolagem.addView(ferramentas);
        raiz.addView(rolagem);

        setContentView(raiz);
        selecionarFerramenta(EditorView.Ferramenta.LAPIS);
        atualizarCor();
        atualizarEspessura();
        Toast.makeText(this, "Um dedo desenha · dois dedos rolam e dão zoom", Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------ ferramentas

    private void adicionarFerramenta(LinearLayout barra, String texto, EditorView.Ferramenta f) {
        Button b = botao(texto, MainActivity.CINZA);
        b.setOnClickListener(v -> selecionarFerramenta(f));
        barra.addView(b, botoesFerramenta.isEmpty() ? new LinearLayout.LayoutParams(-2, dp(40)) : margem(-2));
        botoesFerramenta.add(b);
        ferramentasDosBotoes.add(f);
    }

    private void selecionarFerramenta(EditorView.Ferramenta f) {
        editor.setFerramenta(f);
        for (int i = 0; i < botoesFerramenta.size(); i++) {
            botoesFerramenta.get(i).setBackground(fundo(ferramentasDosBotoes.get(i) == f
                    ? MainActivity.AZUL : MainActivity.CINZA));
        }
        if (f == EditorView.Ferramenta.TEXTO) {
            Toast.makeText(this, "Toque onde o texto deve ficar", Toast.LENGTH_SHORT).show();
        }
    }

    private void escolherCor() {
        LinearLayout linha = new LinearLayout(this);
        linha.setGravity(Gravity.CENTER);
        linha.setPadding(dp(12), dp(16), dp(12), dp(8));
        AlertDialog dialogo = new AlertDialog.Builder(this).setTitle("Cor").setView(linha).create();
        for (int c : CORES) {
            Button b = new Button(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(dp(c == editor.getCor() ? 4 : 1), c == editor.getCor() ? MainActivity.AZUL : Color.GRAY);
            b.setBackground(g);
            b.setStateListAnimator(null);
            b.setOnClickListener(v -> {
                editor.setCor(c);
                atualizarCor();
                dialogo.dismiss();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.setMargins(dp(4), 0, dp(4), 0);
            linha.addView(b, lp);
        }
        dialogo.show();
    }

    private void atualizarCor() {
        botaoCor.setTextColor(editor.getCor() == Color.BLACK ? Color.WHITE : editor.getCor());
        botaoCor.setText(editor.getCor() == Color.BLACK ? "● Cor (preto)" : "● Cor");
    }

    private void atualizarEspessura() {
        editor.setEspessuraDp(ESPESSURAS[indiceEspessura]);
        botaoEspessura.setText("≡ " + NOMES_ESPESSURA[indiceEspessura]);
    }

    private void pedirTexto(float x, float y) {
        EditText campo = new EditText(this);
        campo.setHint("Escreva aqui");
        campo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(campo, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this)
                .setTitle("Texto")
                .setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    String t = campo.getText().toString().trim();
                    if (!t.isEmpty()) editor.adicionarTexto(x, y, t);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ------------------------------------------------------------ salvar e compartilhar

    @SuppressWarnings("deprecation")
    private void salvar(boolean compartilharDepois) {
        if (salvo != null && editor.versao == versaoSalva) {
            if (compartilharDepois) compartilhar(salvo);
            else Toast.makeText(this, "Já salvo na galeria", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            aposPermissao = () -> salvar(compartilharDepois);
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PEDIDO_PERMISSAO);
            return;
        }

        Bitmap saida;
        try {
            saida = editor.exportar();
        } catch (OutOfMemoryError e) {
            Toast.makeText(this, "Sem memória para gerar a imagem", Toast.LENGTH_LONG).show();
            return;
        }
        ProgressDialog progresso = new ProgressDialog(this);
        progresso.setMessage("Salvando…");
        progresso.setCancelable(false);
        progresso.show();
        String projeto = getIntent().getStringExtra(EXTRA_NOME);
        int versaoAtual = editor.versao;
        new Thread(() -> {
            Uri uri = null;
            String erro = null;
            try {
                uri = gravarNaGaleria(saida, projeto);
            } catch (Exception e) {
                erro = e.getMessage();
            }
            final Uri u = uri;
            final String err = erro;
            principal.post(() -> {
                if (isDestroyed()) return;
                progresso.dismiss();
                if (u == null) {
                    Toast.makeText(this, "Não foi possível salvar: " + err, Toast.LENGTH_LONG).show();
                    return;
                }
                salvo = u;
                versaoSalva = versaoAtual;
                if (compartilharDepois) compartilhar(u);
                else Toast.makeText(this, "Salvo na galeria (Imagens/Meus Projetos)", Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private Uri gravarNaGaleria(Bitmap bmp, String projeto) throws Exception {
        String data = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
        String nome = Projetos.nomeSeguro(projeto == null ? "print" : projeto).replace(' ', '_') + "_" + data + ".png";
        ContentResolver cr = getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, nome);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        Uri colecao;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Meus Projetos");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            colecao = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            colecao = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        Uri uri = cr.insert(colecao, v);
        if (uri == null) throw new Exception("a galeria recusou o arquivo");
        try (OutputStream out = cr.openOutputStream(uri)) {
            if (out == null || !bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new Exception("falha ao gravar a imagem");
            }
        } catch (Exception e) {
            cr.delete(uri, null, null);
            throw e;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues pronto = new ContentValues();
            pronto.put(MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(uri, pronto, null, null);
        }
        return uri;
    }

    private void compartilhar(Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i, "Enviar print"));
    }

    @Override
    public void onRequestPermissionsResult(int pedido, String[] permissoes, int[] resultados) {
        super.onRequestPermissionsResult(pedido, permissoes, resultados);
        if (pedido != PEDIDO_PERMISSAO) return;
        if (resultados.length > 0 && resultados[0] == PackageManager.PERMISSION_GRANTED && aposPermissao != null) {
            aposPermissao.run();
        } else {
            Toast.makeText(this, "Sem permissão para salvar na galeria", Toast.LENGTH_LONG).show();
        }
        aposPermissao = null;
    }

    // ------------------------------------------------------------ saída

    private void sair() {
        if (editor.temMarcacoes() && editor.versao != versaoSalva) {
            new AlertDialog.Builder(this)
                    .setTitle("Sair sem salvar?")
                    .setMessage("As marcações deste print serão perdidas.")
                    .setPositiveButton("Sair", (d, w) -> finish())
                    .setNegativeButton("Continuar editando", null)
                    .show();
        } else {
            finish();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        sair();
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) pendente = null;
        super.onDestroy();
    }

    // ------------------------------------------------------------ visual

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private LinearLayout.LayoutParams margem(int largura) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(largura, dp(40));
        lp.leftMargin = dp(6);
        return lp;
    }

    private GradientDrawable fundo(int cor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cor);
        g.setCornerRadius(dp(8));
        return g;
    }

    private Button botao(String s, int cor) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setBackground(fundo(cor));
        return b;
    }
}
