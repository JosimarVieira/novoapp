package com.novoapp.shopping;

/**
 * O desfecho de <code>removerItemLista</code>, selado pelo mesmo motivo de
 * {@link MarkPurchasedResult} e {@link CheckoutResult}: <code>shopping</code>
 * nao pergunta e nao lanca excecao para dizer "nao deu" (ADR-0018, ADR-0039).
 */
public sealed interface RemoveItemResult {

    /** Saiu da lista. O nome vem do banco, e nao do texto da pessoa: e o que o recibo ecoa. */
    record Removed(String name) implements RemoveItemResult {
    }

    /**
     * Nao havia esse item pendente. Diferente de
     * {@link MarkPurchasedResult.NotOnTheList}, isto <b>nao</b> vira pergunta: a
     * ADR-0039 decide que remover o que nao esta la nao tem segunda leitura util
     * -- nao ha o que oferecer. O bot so informa.
     */
    record NotOnTheList(String name) implements RemoveItemResult {
    }
}
