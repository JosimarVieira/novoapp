package com.novoapp.support;

import com.novoapp.nlu.spi.ExpenseExtractor;
import com.novoapp.nlu.spi.ExtractedExpense;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM stubbado (estrategia-de-testes.md): "o que se testa ali e a politica de
 * confianca e a execucao, nao o modelo".
 *
 * <p><b>Isto nao e, e nunca pode virar, o parser de producao.</b> A ADR-0004
 * descartou explicitamente gramatica fixa: ela cobre "mercado 50" e quebra em
 * "paguei o mercado hoje, uns 50". Aqui a regra burra existe so pra devolver
 * uma extracao previsivel para as variacoes de escrita do
 * `financas-lancamento-por-chat.feature`, sem chamada de rede em teste.
 */
@Mock
@ApplicationScoped
public class StubExpenseExtractor implements ExpenseExtractor {

    private static final Pattern AMOUNT = Pattern.compile("(\\d+(?:[.,]\\d{1,2})?)");

    /**
     * Latencia artificial. Chamada de LLM tem cauda imprevisivel (ADR-0005), e o
     * webhook precisa responder 200 em menos de 3s assim mesmo -- o teste de
     * orcamento de resposta usa isto pra provar que a interpretacao ficou de
     * fato fora do ciclo de request.
     */
    private volatile Duration artificialDelay = Duration.ZERO;

    public void delayEachCallBy(Duration delay) {
        this.artificialDelay = delay;
    }

    @Override
    public Optional<ExtractedExpense> extract(String text, List<String> categoryNames) {
        sleepIfAsked();
        if (text == null || categoryNames.isEmpty()) {
            return Optional.empty();
        }
        String normalized = normalize(text);

        Optional<String> category = categoryNames.stream()
                .filter(name -> normalized.contains(normalize(name)))
                // Nome mais longo primeiro: "Mercado livre" ganha de "Mercado"
                // quando os dois aparecem -- e o cenario @etapa2 de ambiguidade,
                // que aqui vira uma escolha qualquer, nunca uma pergunta.
                .max(java.util.Comparator.comparingInt(String::length));
        if (category.isEmpty()) {
            return Optional.empty();
        }

        Matcher amount = AMOUNT.matcher(normalized);
        if (!amount.find()) {
            return Optional.empty();
        }
        long cents = new BigDecimal(amount.group(1).replace(',', '.'))
                .movePointRight(2).longValueExact();

        return Optional.of(new ExtractedExpense(category.get(), cents, null));
    }

    private void sleepIfAsked() {
        if (artificialDelay.isZero()) {
            return;
        }
        try {
            Thread.sleep(artificialDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
