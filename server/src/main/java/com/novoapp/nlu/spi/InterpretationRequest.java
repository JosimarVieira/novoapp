package com.novoapp.nlu.spi;

import java.util.List;

/**
 * Tudo que o provedor de LLM precisa receber para uma interpretacao: o texto e o
 * contexto real do household (ADR-0004, "as categorias e listas reais do
 * household como contexto").
 *
 * <p>Nenhum tipo de biblioteca de LLM aparece aqui -- e o que faz trocar de
 * provedor na Etapa 5 ser configuracao e nao reescrita (ADR-0009), e o que
 * permite o stub dos testes de aceitacao imitar o resultado em vez da API.
 *
 * @param questionAsked so no proposito {@link Purpose#ANSWERING_PENDING}: a
 *        pergunta que o bot fez e que esta guardada na {@code pending_action}.
 *        E o contexto que permite ao modelo decidir se a mensagem responde a
 *        pergunta ou muda de assunto (ADR-0029)
 * @param categoryCorrectionOffered a pendencia aberta e de criacao de categoria,
 *        entao <code>confirmarCategoriaSugerida</code> entra no cardapio junto
 *        das tools do dia a dia (ADR-0026 + ADR-0029). Falso nos demais tipos:
 *        declarar a correcao onde nao ha categoria a corrigir so a poria
 *        competindo a toa
 * @param expenseCategories rotulos das categorias de despesa que ja existem.
 *        Vazio e caso normal em household novo (ADR-0013) -- e o que faz a
 *        propriedade {@code categoria} sumir do schema (ADR-0024)
 */
public record InterpretationRequest(Purpose purpose,
                                    String text,
                                    String questionAsked,
                                    boolean categoryCorrectionOffered,
                                    List<String> expenseCategories,
                                    List<String> pendingListItems) {

    public enum Purpose {
        /** Mensagem comum: todas as tools do dia a dia entram no contexto. */
        GENERAL,
        /**
         * Resposta que nao e atalho a uma pergunta em aberto.
         *
         * <p>Antes da ADR-0029 este proposito declarava <b>so</b>
         * <code>confirmarCategoriaSugerida</code>, e existia so para pendencia
         * de categoria. O modelo nao tinha como dizer "isto nao responde a
         * pergunta", e uma mensagem sobre outro assunto podia virar categoria
         * errada levando junto o valor guardado na pendencia. Agora o cardapio
         * e o mesmo do dia a dia, mais a correcao quando ela faz sentido.
         */
        ANSWERING_PENDING
    }

    public static InterpretationRequest general(String text,
                                                List<String> expenseCategories,
                                                List<String> pendingListItems) {
        return new InterpretationRequest(Purpose.GENERAL, text, null, false,
                expenseCategories, pendingListItems);
    }

    public static InterpretationRequest answeringPending(String text,
                                                         String questionAsked,
                                                         boolean categoryCorrectionOffered,
                                                         List<String> expenseCategories,
                                                         List<String> pendingListItems) {
        return new InterpretationRequest(Purpose.ANSWERING_PENDING, text, questionAsked,
                categoryCorrectionOffered, expenseCategories, pendingListItems);
    }
}
