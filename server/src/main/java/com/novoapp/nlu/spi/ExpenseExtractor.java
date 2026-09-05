package com.novoapp.nlu.spi;

import java.util.List;
import java.util.Optional;

/**
 * Fronteira entre <code>nlu</code> e o provedor de LLM.
 *
 * <p>Existe por dois motivos. Primeiro, a ADR-0009 exige que trocar de provedor
 * na Etapa 5 seja configuracao, nao reescrita -- nenhum tipo do LangChain4j
 * atravessa esta interface. Segundo, a estrategia-de-testes.md manda stubbar o
 * LLM em todo teste de aceitacao: o que se testa la e a politica de confianca e
 * a execucao, nunca o modelo.
 */
public interface ExpenseExtractor {

    /**
     * @param categoryNames categorias de despesa que existem no household. Vira
     *        o enum do parametro da tool -- o modelo nao tem como escolher
     *        categoria que a familia nao criou (ADR-0004).
     * @return vazio quando o modelo nao escolheu tool nenhuma
     */
    Optional<ExtractedExpense> extract(String text, List<String> categoryNames);
}
