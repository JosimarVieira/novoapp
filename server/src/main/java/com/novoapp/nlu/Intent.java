package com.novoapp.nlu;

import com.novoapp.shopping.ItemDraft;

import java.util.List;
import java.util.UUID;

/**
 * Resultado da interpretacao: qual acao o usuario quis, com quais parametros e
 * com que confianca (glossario, ADR-0004).
 *
 * <p>Virou interface selada na Etapa 2a. Na Etapa 1 havia uma acao so e um
 * record chapado dava conta; com despesa, mercado e convite no mesmo pipeline,
 * um record com todos os campos de todas as acoes deixaria metade nula em toda
 * chamada, e nada obrigaria <code>conversation</code> a tratar um caso novo.
 * Selada, o <code>switch</code> de la nao compila quando uma acao nova aparece.
 *
 * <p>A confianca vem do modelo, num parametro <code>confianca</code> declarado
 * em toda tool -- e o desenho que a propria ADR-0004 pressupoe ao registrar como
 * fraqueza central que "<code>confidence</code> reportado por LLM e mal
 * calibrado por natureza" e que o limiar tera de ser calibrado empiricamente. O
 * limiar nao mora aqui: e config, lida por
 * {@link com.novoapp.conversation.ConfidencePolicy} ([decisao aberta #7]).
 */
public sealed interface Intent {

    double confidence();

    /**
     * Despesa (ADR-0004, ADR-0023, ADR-0024).
     *
     * <p>Exatamente um entre {@code categoryId} e {@code suggestedCategory} vem
     * preenchido -- a exclusividade nao e garantida pelo schema, so por instrucao
     * ao modelo (ADR-0024 registra isso como fraqueza), entao quem monta este
     * record ja resolveu a disputa.
     *
     * @param alternatives categorias que tambem poderiam servir. So importa
     *        quando a confianca e media: viram as opcoes numeradas da pergunta
     * @param amountCents nulo quando a pessoa nao disse o valor -- vira pergunta,
     *        nunca valor inventado
     * @param description residuo semantico (ADR-0023). Nulo e o caso normal e
     *        jamais vira pergunta
     */
    record RegisterExpense(UUID categoryId,
                           String categoryDisplayName,
                           List<CategoryChoice> alternatives,
                           String suggestedCategory,
                           Long amountCents,
                           String description,
                           double confidence) implements Intent {

        public boolean needsCategoryCreation() {
            return categoryId == null && suggestedCategory != null;
        }

        public boolean hasAmount() {
            return amountCents != null && amountCents > 0;
        }
    }

    /** Uma ou mais coisas que estao faltando, numa mensagem so. */
    record AddListItems(List<ItemDraft> items, double confidence) implements Intent {
    }

    record MarkItemPurchased(String itemName, double confidence) implements Intent {
    }

    record QueryList(double confidence) implements Intent {
    }

    /** Emissao de convite pelo OWNER (ADR-0020). */
    record InviteMember(String memberName, String phoneNumber, double confidence) implements Intent {
    }

    /**
     * Correcao livre de uma pergunta de criacao de categoria (ADR-0026). So
     * aparece na segunda chamada ao modelo, nunca numa mensagem comum.
     */
    record ConfirmSuggestedCategory(String name, String parentName, double confidence) implements Intent {
    }

    /** Nenhuma tool escolhida, ou argumento fora do que a tool aceita. */
    record Unknown(double confidence) implements Intent {
    }

    /** Uma categoria oferecida como opcao numerada. */
    record CategoryChoice(UUID id, String label) {
    }

    static Intent unknown() {
        return new Unknown(0.0d);
    }
}
