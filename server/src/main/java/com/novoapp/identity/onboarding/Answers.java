package com.novoapp.identity.onboarding;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Leitura deterministica das respostas do onboarding.
 *
 * <p>Nao chama LLM de proposito: a arvore de onboarding tem duas ou tres saidas
 * por passo, e gastar modelo pra ler "sim" e a mesma coisa que a regra nao
 * negociavel 6 do CLAUDE.md ja proibe pras confirmacoes.
 */
final class Answers {

    private static final List<String> AFFIRMATIVE =
            List.of("sim", "quero", "claro", "pode", "ok", "vamos", "bora", "isso", "aceito", "s");

    private static final List<String> NEGATIVE =
            List.of("nao", "agora nao", "depois", "n");

    private Answers() {
    }

    static boolean isAffirmative(String text) {
        return startsWithAny(normalize(text), AFFIRMATIVE);
    }

    static boolean isNegative(String text) {
        return startsWithAny(normalize(text), NEGATIVE);
    }

    /** Cenario "Escolhe terminar configuracao no aplicativo". */
    static boolean prefersApp(String text) {
        String normalized = normalize(text);
        return normalized.contains("app") || normalized.contains("aplicativo");
    }

    /** Tira acento e caixa: "Nao", "nao" e "nAo" sao a mesma resposta. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String stripped = Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped;
    }

    private static boolean startsWithAny(String normalized, List<String> options) {
        return options.stream().anyMatch(option -> normalized.equals(option) || normalized.startsWith(option + " ")
                || normalized.startsWith(option + ","));
    }
}
