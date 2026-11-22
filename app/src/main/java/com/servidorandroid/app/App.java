package com.servidorandroid.app;

import android.app.Application;
import android.webkit.WebView;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            // Necessário para o "print longo": deixa o WebView desenhar a página inteira,
            // não só a parte visível. Precisa ser chamado antes de criar qualquer WebView.
            WebView.enableSlowWholeDocumentDraw();
        } catch (Throwable ignorado) {
            // sem WebView disponível: o print longo vira print da área visível
        }
    }
}
