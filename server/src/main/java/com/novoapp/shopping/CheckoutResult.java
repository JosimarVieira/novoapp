package com.novoapp.shopping;

import java.util.List;

/**
 * O desfecho de <code>fecharCompra</code>, selado: o modulo nao lanca excecao
 * para dizer "nao deu", pelo mesmo motivo de {@link MarkPurchasedResult} e de
 * {@code CategoryCreation} em <code>finance</code> -- recusa nomeada vira
 * pergunta em <code>conversation</code>, e excecao obrigaria inspecionar
 * mensagem de erro para escolher o texto.
 */
public sealed interface CheckoutResult {

    /**
     * Fechou. Carrega o que o recibo precisa dizer, e nada alem disso.
     *
     * <p>Que um modulo de mercado devolva valor e categoria e desconfortavel, e
     * a ADR-0031 registra isso como o preco de a operacao ser genuinamente das
     * duas coisas: a alternativa seria <code>conversation</code> consultar
     * <code>finance</code> de novo depois, reintroduzindo a segunda transacao
     * para efeito de leitura.
     */
    record Closed(int itemsPurchased,
                  List<String> itemNames,
                  long amountCents,
                  String categoryDisplayName) implements CheckoutResult {
    }

    /**
     * Nao havia lista ativa nenhuma. Cenario "Fechar compra sem lista ativa":
     * <code>conversation</code> transforma isto na pergunta que oferece registrar
     * apenas a despesa -- <code>shopping</code> nao pergunta nada.
     */
    record NoActiveList() implements CheckoutResult {
    }

    /**
     * Havia lista, mas nenhum item pendente correspondia ao que a pessoa disse
     * ter comprado. Tambem nao e erro: e pergunta, do mesmo jeito que
     * {@link MarkPurchasedResult.NotOnTheList}.
     */
    record NothingToClose() implements CheckoutResult {
    }
}
