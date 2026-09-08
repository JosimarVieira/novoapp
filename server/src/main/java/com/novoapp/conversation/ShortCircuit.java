package com.novoapp.conversation;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolucao deterministica de resposta curta, sem chamar o modelo (regra 6 do
 * CLAUDE.md: "confirmacoes nao gastam LLM").
 *
 * <p>Esta e a unica gramatica fixa que o sistema tem, e ela nao contradiz a
 * ADR-0004: aquela ADR descartou parser por regex para <em>interpretar
 * intencao</em>, num espaco aberto de mensagens. Aqui o espaco e fechado --
 * "sim", "nao", "desfazer" e um numero, em resposta a uma pergunta que o proprio
 * bot acabou de fazer. Pagar uma chamada de modelo pra ler "sim" seria custo e
 * latencia sem ganho nenhum.
 *
 * <p>A unica excecao e a correcao livre de categoria (ADR-0026), que este
 * resolvedor classifica como {@link Answer#OTHER} e o orquestrador manda pro
 * modelo.
 */
public final class ShortCircuit {

    private static final Set<String> YES = Set.of("sim", "s", "ok", "isso", "pode", "confirmo", "claro", "aceito");
    private static final Set<String> NO = Set.of("nao", "n", "nope", "negativo");
    private static final String UNDO = "desfazer";

    /** Numero puro: "1", "2". Nao casa "1 kg" nem "50 mercado". */
    private static final Pattern OPTION_NUMBER = Pattern.compile("^(\\d{1,2})$");

    /** Valor solto em reais: "50", "49,90", "1.234,50", "R$ 50". */
    private static final Pattern AMOUNT = Pattern.compile("^(?:r\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*|\\d+)(?:,(\\d{1,2}))?$");

    private ShortCircuit() {
    }

    public enum Answer {
        YES,
        NO,
        /** Cancela a pergunta. Precedencia absoluta sobre estorno (ADR-0025). */
        UNDO,
        /** Um numero, candidato a opcao numerada. */
        NUMBER,
        /** Nada disso. Pode ser correcao livre, ou mensagem nova. */
        OTHER
    }

    public static Answer classify(String text) {
        if (text == null) {
            return Answer.OTHER;
        }
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return Answer.OTHER;
        }
        if (YES.contains(normalized)) {
            return Answer.YES;
        }
        if (NO.contains(normalized)) {
            return Answer.NO;
        }
        if (UNDO.equals(normalized)) {
            return Answer.UNDO;
        }
        if (OPTION_NUMBER.matcher(normalized).matches()) {
            return Answer.NUMBER;
        }
        return Answer.OTHER;
    }

    /** O numero da opcao escolhida, base 1. Nulo quando a resposta nao e um numero. */
    public static Integer optionNumber(String text) {
        Matcher matcher = OPTION_NUMBER.matcher(normalize(text));
        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    /**
     * Valor em centavos, quando a resposta e so um numero de dinheiro.
     *
     * <p>Numa pendencia que pede o valor, "50" e cinquenta reais, e nao a opcao
     * numero 50 -- e por isso que o orquestrador so trata numero como opcao
     * quando a pendencia de fato tem opcoes (<code>options_json</code>
     * preenchido).
     */
    public static Long amountCents(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = AMOUNT.matcher(normalize(text));
        if (!matcher.matches()) {
            return null;
        }
        String whole = matcher.group(1).replace(".", "");
        String fraction = matcher.group(2) == null ? "00" : (matcher.group(2) + "0").substring(0, 2);
        return new BigDecimal(whole + "." + fraction).movePointRight(2).longValueExact();
    }

    /** Minuscula, sem acento, sem pontuacao final: "Sim!" e "sim" sao a mesma resposta. */
    private static String normalize(String text) {
        String stripped = Normalizer.normalize(text.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.replaceAll("[.!?;]+$", "").trim();
    }
}
