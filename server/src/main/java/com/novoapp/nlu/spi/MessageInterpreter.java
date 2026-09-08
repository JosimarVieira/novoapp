package com.novoapp.nlu.spi;

import java.util.Optional;

/**
 * Fronteira entre <code>nlu</code> e o provedor de LLM.
 *
 * <p>Existe por dois motivos. Primeiro, a ADR-0009 exige que trocar de provedor
 * na Etapa 5 seja configuracao, nao reescrita -- nenhum tipo do LangChain4j
 * atravessa esta interface. Segundo, a estrategia-de-testes.md manda stubbar o
 * LLM em todo teste de aceitacao: o que se testa la e a politica de confianca e
 * a execucao, nunca o modelo.
 *
 * <p>Sucedeu {@code ExpenseExtractor} na Etapa 2a. Aquela interface so sabia
 * falar de despesa; a partir de mercado e convite ha mais de uma tool possivel
 * na mesma mensagem, e quem escolhe entre elas e o modelo -- entao o que
 * atravessa a fronteira passou a ser "a chamada de funcao que ele escolheu", e
 * nao "a despesa que ele extraiu".
 */
public interface MessageInterpreter {

    /** @return vazio quando o modelo nao escolheu tool nenhuma */
    Optional<ToolCall> interpret(InterpretationRequest request);
}
