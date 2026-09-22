package com.novoapp.shopping;

import com.novoapp.finance.ExpenseReversed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * O outro lado do <code>desfazer</code> (ADR-0032): estornou o lancamento de um
 * fechamento, os itens daquele fechamento voltam a <code>PENDING</code>.
 *
 * <p><b>Por que evento, e nao chamada direta.</b> A regra de dependencia elimina
 * metade do problema sozinha: <code>finance</code> nao pode importar
 * <code>shopping</code>, entao <code>reverseLatest</code> nao tem como consultar
 * <code>list_checkout</code>. Das duas saidas restantes, nenhuma servia --
 * <code>shopping</code> orquestrar todo <code>desfazer</code> faria o estorno de
 * uma despesa avulsa passar pelo modulo de mercado, e <code>conversation</code>
 * perguntar antes quebraria a transacao unica que a ADR-0032 exige. E o mesmo
 * padrao do {@code HouseholdCreated} -> conta WALLET, que existe desde a Etapa 1
 * exatamente para nao inverter a direcao de dependencia.
 *
 * <p><b>Sincrono, na mesma transacao</b>, e por isso sem {@code @Transactional}
 * e sem {@code @HouseholdScoped} proprios: o escopo de <code>finance</code> ja
 * esta aplicado, e abrir outro aqui esconderia que a atomicidade e a mesma da
 * ADR-0031, no sentido inverso. Nao contraria a alternativa D daquela ADR --
 * aquela descartou evento <i>assincrono</i>, cuja objecao era literalmente que a
 * lista mudaria primeiro.
 *
 * <p><b>O risco desta escolha esta declarado</b> em
 * <code>sdd-modulo-shopping.md</code>: observador de evento nao e um
 * <code>import</code>, entao o ArchUnit nao ve este acoplamento. Se aparecer um
 * <b>segundo</b> {@code @Observes} de evento de <code>finance</code> neste
 * modulo, a fronteira merece revisao antes do codigo.
 */
@ApplicationScoped
public class ExpenseReversedListener {

    @Inject
    ShoppingService shopping;

    void onExpenseReversed(@Observes ExpenseReversed event) {
        shopping.reopenCheckout(event.transactionId());
    }
}
