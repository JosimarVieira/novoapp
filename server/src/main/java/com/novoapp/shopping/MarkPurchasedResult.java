package com.novoapp.shopping;

/**
 * Os dois desfechos de <code>marcarItemComprado</code>.
 *
 * <p>{@code NotOnTheList} nao e erro: e o cenario "Item mencionado nao existe na
 * lista", que termina em pergunta. Quem transforma isso em {@code PendingAction}
 * e <code>conversation</code> -- <code>shopping</code> nao pergunta nada
 * (ADR-0018).
 */
public sealed interface MarkPurchasedResult {

    record Purchased(String name) implements MarkPurchasedResult {
    }

    record NotOnTheList(String name) implements MarkPurchasedResult {
    }
}
