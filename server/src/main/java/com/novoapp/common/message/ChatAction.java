package com.novoapp.common.message;

import java.util.UUID;

/**
 * Acao de botao (glossario, ADR-0040, ADR-0041): o toque num botao que o bot
 * enviou, normalizado sem traco do canal.
 *
 * <p>Carrega o estado de destino, nunca "alternar": {@link Type#ADD_TO_CART} e
 * {@link Type#REMOVE_FROM_CART} sao dois tipos, e nao um so. E o que torna o
 * toque duplo e o clique numa mensagem velha inofensivos -- repetir a acao da o
 * mesmo resultado que fazer uma vez (ADR-0040, item 6). No recibo vale o mesmo
 * pelo alvo: o botao de desfazer leva o lancamento <b>daquele</b> recibo, e nao
 * "o mais recente", e por isso o segundo toque encontra o mesmo lancamento ja
 * estornado em vez de estornar o proximo da fila (ADR-0041, item 7).
 *
 * <p>Como o botao vira <code>callback_data</code> no Telegram e assunto so de
 * <code>channel</code> (regra 5 do CLAUDE.md). Daqui para baixo so existe este
 * record.
 *
 * @param reference a que o botao se refere: um item da lista ou um lancamento.
 *        Nula so em {@link Type#CLOSE_CART}, que age sobre o carrinho inteiro.
 *        Vem do cliente, e portanto nao e confiavel: quem a usa le o item ou o
 *        lancamento sob RLS (ADR-0003), e id de outro household simplesmente nao
 *        e encontrado
 */
public record ChatAction(Type type, Reference reference) {

    public enum Type {
        /** <code>carrinho+</code>: separar o item no carrinho de quem tocou. */
        ADD_TO_CART(Reference.Kind.LIST_ITEM),
        /** <code>carrinho-</code>: tirar o item do carrinho de quem tocou. */
        REMOVE_FROM_CART(Reference.Kind.LIST_ITEM),
        /** <code>remover</code>: tirar o item da lista da familia (ADR-0039). */
        REMOVE_ITEM(Reference.Kind.LIST_ITEM),
        /** <code>devolver</code>: o item removido volta a ser pedido. */
        RESTORE_ITEM(Reference.Kind.LIST_ITEM),
        /** <code>fechar</code>: fechar a compra com o que esta no carrinho. */
        CLOSE_CART(null),
        /**
         * <code>desfazer</code> no recibo (ADR-0041): estorna o lancamento do
         * proprio recibo dentro da janela; fora dela, so pede confirmacao.
         */
        UNDO(Reference.Kind.TRANSACTION),
        /**
         * <code>estornar</code>: o segundo toque da confirmacao de fora da janela.
         * Estorna sem olhar a janela -- e deliberado, vendo o que vai sumir.
         */
        REVERSE(Reference.Kind.TRANSACTION),
        /** <code>voltar</code>: so redesenha o recibo como ele esta no banco. */
        BACK(Reference.Kind.TRANSACTION);

        private final Reference.Kind reference;

        Type(Reference.Kind reference) {
            this.reference = reference;
        }

        /** O tipo de referencia que o botao leva; nulo so em {@link #CLOSE_CART}. */
        public Reference.Kind reference() {
            return reference;
        }
    }

    /**
     * A referencia tipada do botao (ADR-0041, item 9). Tipada, e nao um UUID
     * solto, porque o botao deixou de ser so de item de lista: quem le precisa
     * saber de que tabela o id e sem deduzir isso do tipo da acao.
     */
    public record Reference(Kind kind, UUID id) {

        public enum Kind {
            LIST_ITEM,
            TRANSACTION
        }

        public Reference {
            if (kind == null || id == null) {
                throw new IllegalArgumentException("Referencia de botao incompleta: " + kind + " " + id);
            }
        }
    }

    public ChatAction {
        if (type == null) {
            throw new IllegalArgumentException("Acao de botao sem tipo");
        }
        Reference.Kind carried = reference == null ? null : reference.kind();
        if (type.reference() != carried) {
            throw new IllegalArgumentException("Acao " + type + " com referencia " + reference);
        }
    }

    public static ChatAction onItem(Type type, UUID itemId) {
        return new ChatAction(type, new Reference(Reference.Kind.LIST_ITEM, itemId));
    }

    public static ChatAction onTransaction(Type type, UUID transactionId) {
        return new ChatAction(type, new Reference(Reference.Kind.TRANSACTION, transactionId));
    }

    public static ChatAction closeCart() {
        return new ChatAction(Type.CLOSE_CART, null);
    }

    /** O item da lista em que o botao estava; nulo quando o botao nao e de item. */
    public UUID itemId() {
        return idOf(Reference.Kind.LIST_ITEM);
    }

    /** O lancamento do recibo em que o botao estava; nulo quando o botao nao e de recibo. */
    public UUID transactionId() {
        return idOf(Reference.Kind.TRANSACTION);
    }

    private UUID idOf(Reference.Kind kind) {
        return reference != null && reference.kind() == kind ? reference.id() : null;
    }
}
