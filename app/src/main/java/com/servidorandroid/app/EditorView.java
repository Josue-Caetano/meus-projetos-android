package com.servidorandroid.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Mostra o print e permite desenhar por cima.
 * Um dedo desenha com a ferramenta escolhida; dois dedos rolam e dão zoom.
 */
public class EditorView extends View {

    enum Ferramenta { MOVER, LAPIS, MARCA_TEXTO, CIRCULO, RETANGULO, SETA, TEXTO }

    interface PedidoDeTexto {
        void pedir(float x, float y);
    }

    static final class Forma {
        Ferramenta tipo;
        int cor;
        float espessura; // em pixels da imagem
        Path caminho;
        float x1, y1, x2, y2;
        String texto;
    }

    private final Bitmap imagem;
    private final List<Forma> formas = new ArrayList<>();
    private final Paint pinturaImagem = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float densidade;

    /** Aumenta a cada mudança nas marcações (para saber se há algo não salvo). */
    int versao;

    private Ferramenta ferramenta = Ferramenta.LAPIS;
    private int cor = Color.parseColor("#ef4444");
    private float espessuraDp = 4;
    private PedidoDeTexto pedidoDeTexto;

    // Visualização
    private float zoom = 1f, desX, desY;
    private boolean posicionado;

    // Gestos
    private Forma atual;
    private boolean doisDedos;
    private float ultX, ultY;
    private float dist0, zoom0, meioX0, meioY0, desX0, desY0;

    public EditorView(Context ctx, Bitmap imagem) {
        super(ctx);
        this.imagem = imagem;
        this.densidade = ctx.getResources().getDisplayMetrics().density;
        // Imagens longas passam do limite de textura da GPU: desenho por software.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        pincel.setStrokeCap(Paint.Cap.ROUND);
        pincel.setStrokeJoin(Paint.Join.ROUND);
    }

    // ------------------------------------------------------------ configuração

    void setFerramenta(Ferramenta f) {
        ferramenta = f;
    }

    Ferramenta getFerramenta() {
        return ferramenta;
    }

    void setCor(int c) {
        cor = c;
    }

    int getCor() {
        return cor;
    }

    void setEspessuraDp(float e) {
        espessuraDp = e;
    }

    void setPedidoDeTexto(PedidoDeTexto p) {
        pedidoDeTexto = p;
    }

    boolean temMarcacoes() {
        return !formas.isEmpty();
    }

    void desfazer() {
        if (!formas.isEmpty()) {
            formas.remove(formas.size() - 1);
            versao++;
            invalidate();
        }
    }

    void limpar() {
        formas.clear();
        versao++;
        invalidate();
    }

    void adicionarTexto(float x, float y, String texto) {
        Forma f = novaForma(Ferramenta.TEXTO);
        f.x1 = x;
        f.y1 = y;
        f.texto = texto;
        formas.add(f);
        versao++;
        invalidate();
    }

    /** Gera a imagem final, em tamanho real, com as marcações. */
    Bitmap exportar() {
        Bitmap saida = imagem.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(saida);
        for (Forma f : formas) desenhar(c, f);
        return saida;
    }

    // ------------------------------------------------------------ desenho

    private float escala() {
        return getWidth() / (float) imagem.getWidth() * zoom;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (!posicionado) {
            desX = 0;
            desY = 0;
            posicionado = true;
        }
        limitar();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.parseColor("#020617"));
        float e = escala();
        canvas.save();
        canvas.translate(desX, desY);
        canvas.scale(e, e);
        canvas.drawBitmap(imagem, 0, 0, pinturaImagem);
        for (Forma f : formas) desenhar(canvas, f);
        canvas.restore();
    }

    private void desenhar(Canvas c, Forma f) {
        pincel.setShader(null);
        pincel.clearShadowLayer();
        pincel.setColor(f.cor);
        pincel.setStrokeWidth(f.espessura);
        pincel.setStyle(Paint.Style.STROKE);
        switch (f.tipo) {
            case LAPIS:
                c.drawPath(f.caminho, pincel);
                break;
            case MARCA_TEXTO:
                pincel.setAlpha(90);
                pincel.setStrokeCap(Paint.Cap.SQUARE);
                c.drawPath(f.caminho, pincel);
                pincel.setStrokeCap(Paint.Cap.ROUND);
                pincel.setAlpha(255);
                break;
            case CIRCULO:
                c.drawOval(retangulo(f), pincel);
                break;
            case RETANGULO:
                c.drawRoundRect(retangulo(f), f.espessura, f.espessura, pincel);
                break;
            case SETA:
                c.drawLine(f.x1, f.y1, f.x2, f.y2, pincel);
                double ang = Math.atan2(f.y2 - f.y1, f.x2 - f.x1);
                float ponta = f.espessura * 4 + 10;
                for (double lado : new double[]{Math.PI * 0.8, -Math.PI * 0.8}) {
                    c.drawLine(f.x2, f.y2,
                            (float) (f.x2 + ponta * Math.cos(ang + lado)),
                            (float) (f.y2 + ponta * Math.sin(ang + lado)), pincel);
                }
                break;
            case TEXTO:
                pincel.setStyle(Paint.Style.FILL);
                pincel.setTextSize(f.espessura * 6 + 14);
                pincel.setFakeBoldText(true);
                // contorno escuro para ler em qualquer fundo
                pincel.setShadowLayer(f.espessura, 0, 0, Color.BLACK);
                float y = f.y1;
                for (String linha : f.texto.split("\n")) {
                    c.drawText(linha, f.x1, y, pincel);
                    y += pincel.getTextSize() * 1.2f;
                }
                pincel.clearShadowLayer();
                pincel.setFakeBoldText(false);
                break;
            default:
                break;
        }
    }

    private static RectF retangulo(Forma f) {
        return new RectF(Math.min(f.x1, f.x2), Math.min(f.y1, f.y2), Math.max(f.x1, f.x2), Math.max(f.y1, f.y2));
    }

    private Forma novaForma(Ferramenta tipo) {
        Forma f = new Forma();
        f.tipo = tipo;
        f.cor = cor;
        float base = espessuraDp * densidade / escala();
        f.espessura = tipo == Ferramenta.MARCA_TEXTO ? base * 4 : base;
        return f;
    }

    // ------------------------------------------------------------ toque

    private float imgX(float x) {
        return (x - desX) / escala();
    }

    private float imgY(float y) {
        return (y - desY) / escala();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int acao = e.getActionMasked();

        if (e.getPointerCount() >= 2) {
            if (!doisDedos) {
                // segundo dedo chegou: cancela o traço que acabou de começar e passa a rolar/zoom
                if (atual != null) {
                    formas.remove(atual);
                    atual = null;
                }
                doisDedos = true;
                iniciarGesto(e);
            } else if (acao == MotionEvent.ACTION_POINTER_DOWN || acao == MotionEvent.ACTION_POINTER_UP) {
                iniciarGesto(e);
            } else if (acao == MotionEvent.ACTION_MOVE) {
                float dist = distancia(e);
                float novoZoom = Math.max(1f, Math.min(6f, zoom0 * (dist0 > 0 ? dist / dist0 : 1)));
                float e0 = getWidth() / (float) imagem.getWidth() * zoom0;
                float px = (meioX0 - desX0) / e0, py = (meioY0 - desY0) / e0; // ponto da imagem sob os dedos
                zoom = novoZoom;
                float e1 = escala();
                desX = meioX(e) - px * e1;
                desY = meioY(e) - py * e1;
                limitar();
                invalidate();
            }
            return true;
        }

        if (doisDedos) {
            if (acao == MotionEvent.ACTION_UP || acao == MotionEvent.ACTION_CANCEL) doisDedos = false;
            return true;
        }

        float x = e.getX(), y = e.getY();
        switch (acao) {
            case MotionEvent.ACTION_DOWN:
                ultX = x;
                ultY = y;
                if (ferramenta == Ferramenta.MOVER || ferramenta == Ferramenta.TEXTO) return true;
                atual = novaForma(ferramenta);
                if (ferramenta == Ferramenta.LAPIS || ferramenta == Ferramenta.MARCA_TEXTO) {
                    atual.caminho = new Path();
                    atual.caminho.moveTo(imgX(x), imgY(y));
                    atual.caminho.lineTo(imgX(x) + 0.1f, imgY(y));
                }
                atual.x1 = atual.x2 = imgX(x);
                atual.y1 = atual.y2 = imgY(y);
                formas.add(atual);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (ferramenta == Ferramenta.MOVER) {
                    desX += x - ultX;
                    desY += y - ultY;
                    ultX = x;
                    ultY = y;
                    limitar();
                } else if (atual != null) {
                    if (atual.caminho != null) {
                        for (int i = 0; i < e.getHistorySize(); i++) {
                            atual.caminho.lineTo(imgX(e.getHistoricalX(i)), imgY(e.getHistoricalY(i)));
                        }
                        atual.caminho.lineTo(imgX(x), imgY(y));
                    }
                    atual.x2 = imgX(x);
                    atual.y2 = imgY(y);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (ferramenta == Ferramenta.TEXTO && pedidoDeTexto != null
                        && Math.abs(x - ultX) < 20 && Math.abs(y - ultY) < 20) {
                    pedidoDeTexto.pedir(imgX(x), imgY(y));
                }
                if (atual != null && atual.caminho == null
                        && Math.abs(atual.x2 - atual.x1) < 2 && Math.abs(atual.y2 - atual.y1) < 2) {
                    formas.remove(atual); // toque sem arrastar não cria forma
                    invalidate();
                } else if (atual != null) {
                    versao++;
                }
                atual = null;
                return true;

            case MotionEvent.ACTION_CANCEL:
                atual = null;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void iniciarGesto(MotionEvent e) {
        dist0 = distancia(e);
        zoom0 = zoom;
        meioX0 = meioX(e);
        meioY0 = meioY(e);
        desX0 = desX;
        desY0 = desY;
    }

    private static float distancia(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0;
        float dx = e.getX(0) - e.getX(1), dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static float meioX(MotionEvent e) {
        return (e.getX(0) + e.getX(1)) / 2;
    }

    private static float meioY(MotionEvent e) {
        return (e.getY(0) + e.getY(1)) / 2;
    }

    /** Mantém a imagem dentro da tela (centralizada quando menor que a tela). */
    private void limitar() {
        if (getWidth() == 0) return;
        float e = escala();
        float w = imagem.getWidth() * e, h = imagem.getHeight() * e;
        desX = w <= getWidth() ? (getWidth() - w) / 2 : Math.max(getWidth() - w, Math.min(0, desX));
        desY = h <= getHeight() ? (getHeight() - h) / 2 : Math.max(getHeight() - h, Math.min(0, desY));
    }
}
