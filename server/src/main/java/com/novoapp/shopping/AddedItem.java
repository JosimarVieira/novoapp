package com.novoapp.shopping;

import java.util.UUID;

/**
 * O desfecho de um item dentro de uma chamada de
 * {@link ShoppingService#addItems}.
 *
 * @param alreadyPending o item ja estava faltando. Nada foi gravado; o cenario
 *        "Item ja pendente na lista" pede que a resposta diga quem tinha pedido
 * @param requestedByMemberId quem pediu -- quem pede agora, ou quem tinha pedido
 *        antes quando {@code alreadyPending}. So o id: traduzir para nome e
 *        trabalho de <code>conversation</code> (sdd-modulo-shopping.md)
 */
public record AddedItem(UUID itemId, String name, boolean alreadyPending, UUID requestedByMemberId) {
}
