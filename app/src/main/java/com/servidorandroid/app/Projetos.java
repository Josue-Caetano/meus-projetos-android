package com.servidorandroid.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Guarda, importa e analisa os projetos (ficam na memória interna do app). */
public final class Projetos {

    static final String ARQUIVO_ORIGEM = ".servidorandroid-origem";
    private static final Set<String> IGNORAR = new HashSet<>(Arrays.asList(
            ".git", "node_modules", "__MACOSX", ".DS_Store", "__pycache__", ".venv", "venv", ".idea", ".gradle"));
    private static final int PORTA_INICIAL = 8101;

    private static final Map<String, ServidorLocal> servidores = new HashMap<>();

    private Projetos() {
    }

    // ------------------------------------------------------------ pastas

    static File pasta(Context ctx) {
        File p = new File(ctx.getFilesDir(), "projetos");
        //noinspection ResultOfMethodCallIgnored
        p.mkdirs();
        return p;
    }

    static List<File> listar(Context ctx) {
        File[] itens = pasta(ctx).listFiles();
        List<File> lista = new ArrayList<>();
        if (itens != null) {
            for (File f : itens) {
                if (f.isDirectory() && !f.getName().startsWith(".")) lista.add(f);
            }
        }
        Collections.sort(lista, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return lista;
    }

    static String nomeSeguro(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.toLowerCase().endsWith(".zip")) n = n.substring(0, n.length() - 4);
        n = n.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("^\\.+", "");
        return n.isEmpty() ? "projeto" : n;
    }

    // ------------------------------------------------------------ análise

    static final class Info {
        String tipo;
        String aviso;
        File entrada; // pasta com o index.html (ou a raiz do projeto, se não houver)
        boolean temIndex;
        String origem; // "usuario/repo" se veio do GitHub
    }

    static Info analisar(File projeto) {
        Info info = new Info();
        Set<String> arquivos = new HashSet<>();
        String[] nomes = projeto.list();
        if (nomes != null) arquivos.addAll(Arrays.asList(nomes));
        boolean node = arquivos.contains("package.json");

        String[] ordem = node
                ? new String[]{"dist", "build", "out", "", "public", "www", "docs"}
                : new String[]{"", "dist", "build", "public", "www", "docs", "site", "out", "src"};
        for (String sub : ordem) {
            File dir = sub.isEmpty() ? projeto : new File(projeto, sub);
            if (new File(dir, "index.html").isFile()) {
                info.entrada = dir;
                info.temIndex = true;
                break;
            }
        }
        if (info.entrada == null) info.entrada = projeto;

        if (node) {
            info.tipo = "Node.js / JavaScript";
            if (!info.temIndex) {
                info.aviso = "Não achei a versão compilada. Rode \"npm run build\" no PC e importe o projeto "
                        + "com a pasta dist/build junto. O app não executa Node.js.";
            } else if (info.entrada == projeto) {
                info.aviso = "Se a página ficar em branco, o projeto precisa ser compilado (npm run build) "
                        + "e importado com a pasta dist/build.";
            }
        } else if (arquivos.contains("manage.py") || arquivos.contains("requirements.txt")
                || arquivos.contains("app.py") || arquivos.contains("main.py")) {
            info.tipo = "Python";
            info.aviso = "O app mostra páginas HTML/CSS/JS. Código Python de servidor não roda aqui.";
        } else if (arquivos.contains("index.php") || arquivos.contains("composer.json")) {
            info.tipo = "PHP";
            info.aviso = "O app mostra páginas HTML/CSS/JS. Código PHP não roda aqui.";
        } else if (info.temIndex) {
            info.tipo = "Site (HTML)";
        } else {
            info.tipo = "Pasta";
            info.aviso = "Nenhum index.html encontrado. \"Abrir\" mostra a lista de arquivos.";
        }

        File origem = new File(projeto, ARQUIVO_ORIGEM);
        if (origem.isFile()) {
            try {
                info.origem = lerTexto(origem).trim();
            } catch (IOException ignorado) {
            }
        }
        return info;
    }

    // ------------------------------------------------------------ servidores

    /**
     * Endereço para abrir uma página do projeto.
     *
     * @param relativo caminho a partir da pasta do projeto (ex.: "jogos/snake/index.html");
     *                 vazio ou null abre a página principal.
     */
    static String urlPara(Context ctx, File projeto, String relativo) throws IOException {
        File entrada = analisar(projeto).entrada;
        File alvo = relativo == null || relativo.isEmpty() ? entrada : new File(projeto, relativo);

        // Páginas dentro da pasta principal (ex.: dist/) usam o servidor dela, para que
        // caminhos como "/assets/app.js" funcionem. As de fora usam um servidor na raiz do projeto.
        String alvoCanon = alvo.getCanonicalPath();
        File raiz = dentro(alvoCanon, entrada.getCanonicalPath()) ? entrada : projeto;
        String chave = raiz.equals(entrada) ? projeto.getName() : projeto.getName() + "/";
        int porta = iniciarServidor(ctx, chave, raiz);

        String raizCanon = raiz.getCanonicalPath();
        StringBuilder url = new StringBuilder("http://127.0.0.1:").append(porta);
        String resto = alvoCanon.substring(raizCanon.length()).replace(File.separatorChar, '/');
        for (String parte : resto.split("/")) {
            if (parte.isEmpty()) continue;
            url.append('/').append(java.net.URLEncoder.encode(parte, "UTF-8").replace("+", "%20"));
        }
        if (alvo.isDirectory() || resto.isEmpty()) url.append('/');
        return url.toString();
    }

    private static boolean dentro(String caminho, String pasta) {
        return caminho.equals(pasta) || caminho.startsWith(pasta + File.separator);
    }

    /** Cada servidor tem sua própria porta, para caminhos absolutos ("/assets/...") funcionarem. */
    private static synchronized int iniciarServidor(Context ctx, String chave, File raiz) throws IOException {
        ServidorLocal s = servidores.get(chave);
        if (s != null && s.rodando() && s.getRaiz().equals(raiz)) return s.getPorta();
        if (s != null) s.parar();
        s = new ServidorLocal(raiz);
        int porta = s.iniciar(portaDoProjeto(ctx, chave));
        servidores.put(chave, s);
        return porta;
    }

    static synchronized void pararServidor(String nome) {
        for (String chave : new String[]{nome, nome + "/"}) {
            ServidorLocal s = servidores.remove(chave);
            if (s != null) s.parar();
        }
    }

    // ------------------------------------------------------------ páginas

    static final class Pagina {
        String caminho; // relativo à pasta do projeto, com "/"
        String titulo;  // como aparece na lista
        boolean principal;
    }

    private static final int LIMITE_PAGINAS = 500;

    /** Todas as páginas .html do projeto (as de pastas como node_modules ficam de fora). */
    static List<Pagina> paginas(File projeto) {
        List<String> caminhos = new ArrayList<>();
        procurarHtml(projeto, "", caminhos);
        File entrada = analisar(projeto).entrada;
        String principal = entrada.equals(projeto) ? "index.html" : entrada.getName() + "/index.html";

        List<Pagina> lista = new ArrayList<>();
        for (String c : caminhos) {
            Pagina p = new Pagina();
            p.caminho = c;
            p.principal = c.equals(principal);
            if (p.principal) p.titulo = "🏠 Página principal  (" + c + ")";
            else if (c.endsWith("/index.html")) p.titulo = "📁 " + c.substring(0, c.length() - "index.html".length());
            else p.titulo = "📄 " + c;
            lista.add(p);
        }
        // Página principal primeiro, depois subprojetos (pastas com index.html), depois o resto
        Collections.sort(lista, (a, b) -> {
            if (a.principal != b.principal) return a.principal ? -1 : 1;
            boolean pa = a.caminho.endsWith("index.html"), pb = b.caminho.endsWith("index.html");
            if (pa != pb) return pa ? -1 : 1;
            return a.caminho.compareToIgnoreCase(b.caminho);
        });
        return lista;
    }

    private static void procurarHtml(File pasta, String prefixo, List<String> saida) {
        File[] itens = pasta.listFiles();
        if (itens == null) return;
        for (File f : itens) {
            if (saida.size() >= LIMITE_PAGINAS) return;
            String nome = f.getName();
            if (IGNORAR.contains(nome) || nome.startsWith(".")) continue;
            if (f.isDirectory()) {
                procurarHtml(f, prefixo + nome + "/", saida);
            } else {
                String n = nome.toLowerCase();
                if (n.endsWith(".html") || n.endsWith(".htm")) saida.add(prefixo + nome);
            }
        }
    }

    /** Porta fixa por projeto, para o localStorage do site não se perder entre aberturas. */
    private static int portaDoProjeto(Context ctx, String nome) {
        SharedPreferences sp = ctx.getSharedPreferences("portas", Context.MODE_PRIVATE);
        int porta = sp.getInt(nome, -1);
        if (porta > 0) return porta;
        int maior = PORTA_INICIAL - 1;
        for (Object v : sp.getAll().values()) {
            if (v instanceof Integer) maior = Math.max(maior, (Integer) v);
        }
        porta = maior + 1;
        sp.edit().putInt(nome, porta).apply();
        return porta;
    }

    // ------------------------------------------------------------ importação

    /** Prepara uma pasta temporária; ao terminar chame {@link #concluir}. */
    static File temporaria(Context ctx, String nome) {
        File tmp = new File(pasta(ctx), ".importando-" + nome);
        apagar(tmp);
        //noinspection ResultOfMethodCallIgnored
        tmp.mkdirs();
        return tmp;
    }

    /** Substitui o projeto antigo (se houver) pelo recém-importado. */
    static File concluir(Context ctx, File tmp, String nome) throws IOException {
        pararServidor(nome);
        File destino = new File(pasta(ctx), nome);
        apagar(destino);
        if (!tmp.renameTo(destino)) throw new IOException("Não foi possível salvar o projeto");
        return destino;
    }

    static void extrairZip(InputStream entrada, File destino) throws IOException {
        String canon = destino.getCanonicalPath() + File.separator;
        byte[] buf = new byte[64 * 1024];
        int arquivos = 0;
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(entrada))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String nome = e.getName().replace('\\', '/');
                if (ignorado(nome)) continue;
                File f = new File(destino, nome);
                if (!f.getCanonicalPath().startsWith(canon)) throw new IOException("ZIP com caminho inválido: " + nome);
                if (e.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    f.mkdirs();
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    f.getParentFile().mkdirs();
                    try (OutputStream out = new FileOutputStream(f)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    arquivos++;
                }
            }
        }
        if (arquivos == 0) throw new IOException("O arquivo ZIP está vazio ou não é um ZIP válido");
        subirPastaUnica(destino);
    }

    private static boolean ignorado(String caminho) {
        for (String parte : caminho.split("/")) {
            if (IGNORAR.contains(parte)) return true;
        }
        return false;
    }

    /** ZIPs do GitHub (e muitos outros) têm tudo dentro de uma única pasta: tira esse nível extra. */
    private static void subirPastaUnica(File destino) throws IOException {
        File[] itens = destino.listFiles();
        if (itens == null || itens.length != 1 || !itens[0].isDirectory()) return;
        File unica = itens[0];
        File aux = new File(destino.getParentFile(), destino.getName() + ".aux");
        apagar(aux);
        if (!unica.renameTo(aux) || !destino.delete() || !aux.renameTo(destino)) {
            throw new IOException("Falha ao organizar os arquivos do ZIP");
        }
    }

    static String nomeDoDocumento(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignorado) {
        }
        String s = uri.getLastPathSegment();
        return s == null ? "projeto" : s.substring(s.lastIndexOf('/') + 1).replaceAll("^.*:", "");
    }

    static String nomeDaPasta(ContentResolver cr, Uri arvore) {
        String id = DocumentsContract.getTreeDocumentId(arvore);
        return nomeDoDocumento(cr, DocumentsContract.buildDocumentUriUsingTree(arvore, id));
    }

    static void copiarPasta(ContentResolver cr, Uri arvore, File destino) throws IOException {
        copiarPasta(cr, arvore, DocumentsContract.getTreeDocumentId(arvore), destino);
    }

    private static void copiarPasta(ContentResolver cr, Uri arvore, String docId, File destino) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        destino.mkdirs();
        Uri filhos = DocumentsContract.buildChildDocumentsUriUsingTree(arvore, docId);
        String[] colunas = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE};
        try (Cursor c = cr.query(filhos, colunas, null, null, null)) {
            if (c == null) throw new IOException("Não foi possível ler a pasta");
            while (c.moveToNext()) {
                String id = c.getString(0);
                String nome = c.getString(1);
                String mime = c.getString(2);
                if (nome == null || IGNORAR.contains(nome) || nome.equals(".") || nome.equals("..")) continue;
                File alvo = new File(destino, nome.replace('/', '_'));
                if (Document.MIME_TYPE_DIR.equals(mime)) {
                    copiarPasta(cr, arvore, id, alvo);
                } else {
                    Uri doc = DocumentsContract.buildDocumentUriUsingTree(arvore, id);
                    try (InputStream in = cr.openInputStream(doc); OutputStream out = new FileOutputStream(alvo)) {
                        if (in == null) continue;
                        copiar(in, out);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------ GitHub

    private static final Pattern REPO = Pattern.compile(
            "^(?:https?://)?(?:www\\.)?(?:github\\.com/)?([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?(?:/.*)?$");

    /** Aceita "usuario/repo" ou um link do GitHub. Retorna "usuario/repo" ou null. */
    static String normalizarRepo(String texto) {
        Matcher m = REPO.matcher(texto == null ? "" : texto.trim());
        return m.matches() ? m.group(1) + "/" + m.group(2) : null;
    }

    static void baixarDoGitHub(String repo, String ramo, String token, File destino) throws IOException {
        String url = "https://api.github.com/repos/" + repo + "/zipball"
                + (ramo == null || ramo.trim().isEmpty() ? "" : "/" + ramo.trim());
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(20000);
        con.setReadTimeout(60000);
        con.setRequestProperty("User-Agent", "ServidorAndroid");
        con.setRequestProperty("Accept", "application/vnd.github+json");
        if (token != null && !token.trim().isEmpty()) {
            con.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        int status = con.getResponseCode();
        if (status == 404) {
            throw new IOException("Repositório ou ramo não encontrado.\nSe o repositório for privado, "
                    + "configure um token do GitHub (botão ⚙).");
        }
        if (status == 401) throw new IOException("Token do GitHub inválido ou expirado.");
        if (status == 403) throw new IOException("O GitHub recusou o download (limite de uso ou token sem permissão). "
                + "Tente de novo mais tarde ou configure um token.");
        if (status >= 400) throw new IOException("Erro do GitHub: HTTP " + status);
        try (InputStream in = con.getInputStream()) {
            extrairZip(in, destino);
        } finally {
            con.disconnect();
        }
        escreverTexto(new File(destino, ARQUIVO_ORIGEM), repo + (ramo == null || ramo.trim().isEmpty() ? "" : "@" + ramo.trim()));
    }

    // ------------------------------------------------------------ utilidades

    static void apagar(File f) {
        if (f == null || !f.exists()) return;
        File[] filhos = f.listFiles();
        if (filhos != null) for (File c : filhos) apagar(c);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    static long tamanho(File f) {
        if (f.isFile()) return f.length();
        long t = 0;
        File[] filhos = f.listFiles();
        if (filhos != null) for (File c : filhos) t += tamanho(c);
        return t;
    }

    static void copiar(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    static String lerTexto(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) Math.min(f.length(), 64 * 1024)];
            int lidos = 0, n;
            while (lidos < b.length && (n = in.read(b, lidos, b.length - lidos)) > 0) lidos += n;
            return new String(b, 0, lidos, StandardCharsets.UTF_8);
        }
    }

    static void escreverTexto(File f, String texto) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(texto.getBytes(StandardCharsets.UTF_8));
        }
    }
}
