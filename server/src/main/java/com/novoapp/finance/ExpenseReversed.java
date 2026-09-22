package com.novoapp.finance;

import java.util.UUID;

/**
 * Evento CDI publicado quando um lancamento e estornado (ADR-0032).
 *
 * <p>Existe para resolver uma direcao de dependencia, como
 * {@code HouseholdCreated} antes dele. O <code>desfazer</code> de um fechamento
 * de compra tem de devolver os itens a <code>PENDING</code> junto com o estorno,
 * na mesma transacao -- mas <code>finance</code> nao pode importar
 * <code>shopping</code>, entao {@link FinanceService#reverseLatest} nao tem como
 * perguntar se este lancamento veio de um <code>list_checkout</code>. Entao
 * <code>finance</code> anuncia e <code>shopping</code> observa. Decisao
 * registrada em <code>sdd-modulo-shopping.md</code> e
 * <code>sdd-modulo-finance.md</code>.
 *
 * <p><b>O observador e sincrono, na mesma transacao</b>, e isso nao contraria a
 * alternativa D da ADR-0031: aquela descartou evento <i>assincrono</i>, com a
 * objecao de que a lista mudaria primeiro. Aqui nao ha duas transacoes.
 *
 * <p>Lancamento que nao veio de fechamento nenhum -- o caso comum -- tambem
 * publica: quem decide que nao ha nada a fazer e o observador, olhando
 * <code>list_checkout</code>, que e uma tabela que so <code>shopping</code>
 * enxerga.
 */
public record ExpenseReversed(UUID transactionId, UUID householdId) {
}
