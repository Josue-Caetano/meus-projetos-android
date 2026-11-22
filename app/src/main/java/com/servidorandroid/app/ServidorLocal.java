package com.servidorandroid.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP mínimo que publica uma pasta em http://127.0.0.1:PORTA/.
 * Java puro (sem dependências do Android), para poder ser testado no PC.
 *
 * - Serve index.html automaticamente nas pastas.
 * - Rotas de SPA (React Router, Vue Router...): caminho inexistente sem extensão -> /index.html.
 * - Suporta requisições Range (necessário para vídeo/áudio).
 */
public class ServidorLocal {

    private static final Map<String, String> MIME = new HashMap<>();

    static {
        String[][] tipos = {
                {"html", "text/html"}, {"htm", "text/html"}, {"css", "text/css"},
                {"js", "text/javascript"}, {"mjs", "text/javascript"}, {"cjs", "text/javascript"},
                {"jsx", "text/javascript"}, {"ts", "text/plain"}, {"tsx", "text/plain"},
                {"json", "application/json"}, {"map", "application/json"},
                {"webmanifest", "application/manifest+json"},
                {"txt", "text/plain"}, {"md", "text/plain"}, {"csv", "text/csv"}, {"xml", "text/xml"},
                {"py", "text/plain"}, {"php", "text/plain"}, {"java", "text/plain"}, {"kt", "text/plain"},
                {"svg", "image/svg+xml"}, {"png", "image/png"}, {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"},
                {"gif", "image/gif"}, {"webp", "image/webp"}, {"avif", "image/avif"}, {"ico", "image/x-icon"},
                {"bmp", "image/bmp"}, {"woff", "font/woff"}, {"woff2", "font/woff2"}, {"ttf", "font/ttf"},
                {"otf", "font/otf"}, {"eot", "application/vnd.ms-fontobject"},
                {"mp3", "audio/mpeg"}, {"wav", "audio/wav"}, {"ogg", "audio/ogg"}, {"m4a", "audio/mp4"},
                {"mp4", "video/mp4"}, {"webm", "video/webm"}, {"wasm", "application/wasm"},
                {"pdf", "application/pdf"}, {"glb", "model/gltf-binary"}, {"gltf", "model/gltf+json"},
        };
        for (String[] t : tipos) MIME.put(t[0], t[1]);
    }

    private final File raiz;
    private final String raizCanonica;
    private ServerSocket socket;
    private ExecutorService pool;

    public ServidorLocal(File raiz) throws IOException {
        this.raiz = raiz;
        this.raizCanonica = raiz.getCanonicalPath();
    }

    public File getRaiz() {
        return raiz;
    }

    /** Inicia na porta preferida; se estiver ocupada, usa uma porta livre qualquer. */
    public synchronized int iniciar(int portaPreferida) throws IOException {
        if (socket != null) return socket.getLocalPort();
        InetAddress local = InetAddress.getByName("127.0.0.1");
        try {
            socket = new ServerSocket(portaPreferida, 64, local);
        } catch (IOException e) {
            socket = new ServerSocket(0, 64, local);
        }
        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "servidor-requisicao");
            t.setDaemon(true);
            return t;
        });
        final ServerSocket s = socket;
        Thread t = new Thread(() -> aceitar(s), "servidor-" + s.getLocalPort());
        t.setDaemon(true);
        t.start();
        return s.getLocalPort();
    }

    public synchronized int getPorta() {
        return socket == null ? -1 : socket.getLocalPort();
    }

    public synchronized boolean rodando() {
        return socket != null && !socket.isClosed();
    }

    public synchronized void parar() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignorado) {
            }
            socket = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    private void aceitar(ServerSocket s) {
        while (!s.isClosed()) {
            try {
                Socket cliente = s.accept();
                ExecutorService p = pool;
                if (p == null) {
                    cliente.close();
                    break;
                }
                p.execute(() -> atender(cliente));
            } catch (IOException e) {
                if (s.isClosed()) break;
            } catch (RuntimeException e) {
                // pool desligado durante o accept
                break;
            }
        }
    }

    private void atender(Socket cliente) {
        try (Socket c = cliente) {
            c.setSoTimeout(15000);
            InputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream out = new BufferedOutputStream(c.getOutputStream(), 64 * 1024);

            String linha = lerLinha(in);
            if (linha == null || linha.isEmpty()) return;
            Map<String, String> cabecalhos = new HashMap<>();
            String h;
            while ((h = lerLinha(in)) != null && !h.isEmpty()) {
                int i = h.indexOf(':');
                if (i > 0) cabecalhos.put(h.substring(0, i).trim().toLowerCase(Locale.ROOT), h.substring(i + 1).trim());
            }

            String[] partes = linha.split(" ");
            if (partes.length < 2) {
                responderTexto(out, 400, "Bad Request", "Requisição inválida", true);
                return;
            }
            String metodo = partes[0];
            boolean head = "HEAD".equals(metodo);
            if (!"GET".equals(metodo) && !head) {
                responderTexto(out, 405, "Method Not Allowed",
                        "Este servidor só entrega arquivos (GET). Projetos com back-end não rodam no app.", head);
                return;
            }
            rotear(out, partes[1], cabecalhos, head);
            out.flush();
        } catch (IOException ignorado) {
            // cliente desconectou
        }
    }

    private void rotear(OutputStream out, String alvo, Map<String, String> cabecalhos, boolean head) throws IOException {
        int q = alvo.indexOf('?');
        if (q >= 0) alvo = alvo.substring(0, q);
        int hash = alvo.indexOf('#');
        if (hash >= 0) alvo = alvo.substring(0, hash);
        if (!alvo.startsWith("/")) alvo = "/" + alvo;

        String caminho;
        try {
            caminho = URLDecoder.decode(alvo.replace("+", "%2B"), "UTF-8");
        } catch (IllegalArgumentException e) {
            responderTexto(out, 400, "Bad Request", "Endereço inválido", head);
            return;
        }

        File arquivo = new File(raiz, caminho);
        if (!dentroDaRaiz(arquivo)) {
            responderTexto(out, 403, "Forbidden", "Acesso negado", head);
            return;
        }

        if (arquivo.isDirectory()) {
            if (!caminho.endsWith("/")) {
                redirecionar(out, alvo + "/");
                return;
            }
            File indice = new File(arquivo, "index.html");
            if (indice.isFile()) {
                enviarArquivo(out, indice, cabecalhos, head);
            } else {
                responderHtml(out, 200, "OK", listarPasta(arquivo, caminho), head);
            }
            return;
        }

        if (arquivo.isFile()) {
            enviarArquivo(out, arquivo, cabecalhos, head);
            return;
        }

        // Rotas de aplicações de página única (SPA): /perfil/123 -> /index.html
        String ultimo = caminho.substring(caminho.lastIndexOf('/') + 1);
        File indiceRaiz = new File(raiz, "index.html");
        if (!ultimo.contains(".") && indiceRaiz.isFile()) {
            enviarArquivo(out, indiceRaiz, cabecalhos, head);
            return;
        }

        responderHtml(out, 404, "Not Found", pagina("Não encontrado",
                "<h2>404 — arquivo não encontrado</h2><p><code>" + escapar(caminho) + "</code></p>"
                        + "<p><a href=\"/\">Ir para o início do projeto</a></p>"), head);
    }

    private boolean dentroDaRaiz(File f) throws IOException {
        String c = f.getCanonicalPath();
        return c.equals(raizCanonica) || c.startsWith(raizCanonica + File.separator);
    }

    private void enviarArquivo(OutputStream out, File f, Map<String, String> cabecalhos, boolean head) throws IOException {
        long tamanho = f.length();
        long inicio = 0, fim = tamanho - 1;
        int status = 200;
        String motivo = "OK";

        String range = cabecalhos.get("range");
        if (range != null && range.startsWith("bytes=") && !range.contains(",") && tamanho > 0) {
            String[] r = range.substring(6).split("-", 2);
            try {
                if (r[0].isEmpty()) {
                    long sufixo = Long.parseLong(r[1].trim());
                    inicio = Math.max(0, tamanho - sufixo);
                } else {
                    inicio = Long.parseLong(r[0].trim());
                    if (r.length > 1 && !r[1].trim().isEmpty()) fim = Math.min(Long.parseLong(r[1].trim()), tamanho - 1);
                }
                if (inicio > fim || inicio >= tamanho) {
                    escreverCabecalhos(out, 416, "Range Not Satisfiable", "text/plain", 0,
                            "Content-Range: bytes */" + tamanho + "\r\n");
                    return;
                }
                status = 206;
                motivo = "Partial Content";
            } catch (NumberFormatException e) {
                inicio = 0;
                fim = tamanho - 1;
            }
        }

        long quantidade = tamanho == 0 ? 0 : fim - inicio + 1;
        String extra = "Accept-Ranges: bytes\r\n";
        if (status == 206) extra += "Content-Range: bytes " + inicio + "-" + fim + "/" + tamanho + "\r\n";
        escreverCabecalhos(out, status, motivo, tipoMime(f.getName()), quantidade, extra);
        if (head || quantidade == 0) return;

        try (InputStream in = new FileInputStream(f)) {
            long pulado = 0;
            while (pulado < inicio) {
                long n = in.skip(inicio - pulado);
                if (n <= 0) throw new IOException("falha ao posicionar no arquivo");
                pulado += n;
            }
            byte[] buf = new byte[64 * 1024];
            long restante = quantidade;
            while (restante > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, restante));
                if (n < 0) break;
                out.write(buf, 0, n);
                restante -= n;
            }
        }
    }

    static String tipoMime(String nome) {
        int i = nome.lastIndexOf('.');
        String ext = i >= 0 ? nome.substring(i + 1).toLowerCase(Locale.ROOT) : "";
        String tipo = MIME.getOrDefault(ext, "application/octet-stream");
        if (tipo.startsWith("text/") || tipo.equals("application/json") || tipo.equals("image/svg+xml")) {
            tipo += "; charset=utf-8";
        }
        return tipo;
    }

    private void escreverCabecalhos(OutputStream out, int status, String motivo, String tipo, long tamanho, String extra)
            throws IOException {
        String h = "HTTP/1.1 " + status + " " + motivo + "\r\n"
                + "Content-Type: " + tipo + "\r\n"
                + "Content-Length: " + tamanho + "\r\n"
                + "Cache-Control: no-cache\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n"
                + extra + "\r\n";
        out.write(h.getBytes(StandardCharsets.UTF_8));
    }

    private void redirecionar(OutputStream out, String destino) throws IOException {
        escreverCabecalhos(out, 301, "Moved Permanently", "text/plain", 0, "Location: " + destino + "\r\n");
    }

    private void responderTexto(OutputStream out, int status, String motivo, String texto, boolean head) throws IOException {
        byte[] b = texto.getBytes(StandardCharsets.UTF_8);
        escreverCabecalhos(out, status, motivo, "text/plain; charset=utf-8", b.length, "");
        if (!head) out.write(b);
        out.flush();
    }

    private void responderHtml(OutputStream out, int status, String motivo, String html, boolean head) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        escreverCabecalhos(out, status, motivo, "text/html; charset=utf-8", b.length, "");
        if (!head) out.write(b);
    }

    private String listarPasta(File pasta, String caminho) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(escapar(caminho)).append("</h2><ul>");
        if (!caminho.equals("/")) sb.append("<li><a href=\"../\">⬆ ..</a></li>");
        File[] itens = pasta.listFiles();
        if (itens != null) {
            Arrays.sort(itens, (a, b) -> a.isDirectory() != b.isDirectory()
                    ? (a.isDirectory() ? -1 : 1)
                    : a.getName().compareToIgnoreCase(b.getName()));
            for (File f : itens) {
                String nome = f.getName();
                String link;
                try {
                    link = URLEncoder.encode(nome, "UTF-8").replace("+", "%20");
                } catch (IOException e) {
                    link = nome;
                }
                if (f.isDirectory()) link += "/";
                sb.append("<li><a href=\"").append(link).append("\">")
                        .append(f.isDirectory() ? "📁 " : "📄 ").append(escapar(nome)).append("</a></li>");
            }
        }
        sb.append("</ul>");
        return pagina(caminho, sb.toString());
    }

    private static String pagina(String titulo, String corpo) {
        return "<!DOCTYPE html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + escapar(titulo) + "</title><style>"
                + "body{font-family:system-ui,sans-serif;margin:0;padding:16px;background:#0f172a;color:#e2e8f0}"
                + "h2{word-break:break-all}ul{list-style:none;padding:0}a{color:#93c5fd}"
                + "li a{display:block;padding:12px;margin-bottom:6px;background:#1e293b;border-radius:8px;"
                + "color:#e2e8f0;text-decoration:none;word-break:break-all}"
                + "code{background:#1e293b;padding:2px 6px;border-radius:4px;word-break:break-all}"
                + "</style></head><body>" + corpo + "</body></html>";
    }

    static String escapar(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String lerLinha(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(128);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 16 * 1024) throw new IOException("linha muito longa");
        }
        if (c == -1 && b.size() == 0) return null;
        return b.toString("UTF-8");
    }
}
