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
 * @param questionAsked so no proposito {@link Purpose#CATEGORY_CORRECTION}: a
 *        pergunta original guardada na {@code pending_action}, que e o contexto
 *        da segunda chamada (ADR-0026)
 * @param expenseCategories rotulos das categorias de despesa que ja existem.
 *        Vazio e caso normal em household novo (ADR-0013) -- e o que faz a
 *        propriedade {@code categoria} sumir do schema (ADR-0024)
 */
public record InterpretationRequest(Purpose purpose,
                                    String text,
                                    String questionAsked,
                                    List<String> expenseCategories,
                                    List<String> pendingListItems) {

    public enum Purpose {
        /** Mensagem comum: todas as tools do dia a dia entram no contexto. */
        GENERAL,
        /**
         * Resposta livre a uma pergunta de criacao de categoria. So
         * <code>confirmarCategoriaSugerida</code> e declarada -- e a excecao a
         * regra 6 que a ADR-0026 abriu, e ela vale so pra este momento.
         */
        CATEGORY_CORRECTION
    }

    public static InterpretationRequest general(String text,
                                                List<String> expenseCategories,
                                                List<String> pendingListItems) {
        return new InterpretationRequest(Purpose.GENERAL, text, null, expenseCategories, pendingListItems);
    }

    public static InterpretationRequest categoryCorrection(String text,
                                                           String questionAsked,
                                                           List<String> expenseCategories) {
        return new InterpretationRequest(Purpose.CATEGORY_CORRECTION, text, questionAsked,
                expenseCategories, List.of());
    }
}
