package com.novoapp.common.message;

import java.util.UUID;

/**
 * Acao de botao (glossario, ADR-0040): o toque num botao que o bot enviou,
 * normalizado sem traco do canal.
 *
 * <p>Carrega o estado de destino, nunca "alternar": {@link Type#ADD_TO_CART} e
 * {@link Type#REMOVE_FROM_CART} sao dois tipos, e nao um so. E o que torna o
 * toque duplo e o clique numa mensagem velha inofensivos -- repetir a acao da o
 * mesmo resultado que fazer uma vez (ADR-0040, item 6).
 *
 * <p>Como o botao vira <code>callback_data</code> no Telegram e assunto so de
 * <code>channel</code> (regra 5 do CLAUDE.md). Daqui para baixo so existe este
 * enum.
 *
 * @param itemId o item da lista em que o botao estava. Nulo so em
 *        {@link Type#CLOSE_CART}, que age sobre o carrinho inteiro. Vem do
 *        cliente, e portanto nao e confiavel: quem o usa le o item sob RLS
 *        (ADR-0003), e id de outro household simplesmente nao e encontrado
 */
public record ChatAction(Type type, UUID itemId) {

    public enum Type {
        /** <code>carrinho+</code>: separar o item no carrinho de quem tocou. */
        ADD_TO_CART,
        /** <code>carrinho-</code>: tirar o item do carrinho de quem tocou. */
        REMOVE_FROM_CART,
        /** <code>remover</code>: tirar o item da lista da familia (ADR-0039). */
        REMOVE_ITEM,
        /** <code>devolver</code>: o item removido volta a ser pedido. */
        RESTORE_ITEM,
        /** <code>fechar</code>: fechar a compra com o que esta no carrinho. */
        CLOSE_CART;

        public boolean needsItem() {
            return this != CLOSE_CART;
        }
    }

    public ChatAction {
        if (type == null) {
            throw new IllegalArgumentException("Acao de botao sem tipo");
        }
        if (type.needsItem() != (itemId != null)) {
            throw new IllegalArgumentException("Acao " + type + " com item " + itemId);
        }
    }

    public static ChatAction closeCart() {
        return new ChatAction(Type.CLOSE_CART, null);
    }
}
