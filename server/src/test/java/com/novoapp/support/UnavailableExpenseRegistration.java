package com.novoapp.support;

import com.novoapp.finance.AccountResolver;
import com.novoapp.finance.entity.Account;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * O passo <code>que o registro de despesas esta indisponivel</code>.
 *
 * <p>Existe para um cenario so -- "Falha ao registrar a despesa nao deixa a
 * lista fechada" --, que e o <b>terceiro dos quatro testes obrigatorios</b> da
 * estrategia-de-testes.md e o unico deles que nunca existiu. Ele e o que prova a
 * ADR-0031: quando <code>finance</code> falha, os itens ja foram marcados e
 * descarregados no banco por <code>shopping</code>, e so o rollback da transacao
 * unica os devolve a <code>PENDING</code>.
 *
 * <h2>Por que a falha entra pelo resolvedor de conta</h2>
 * A primeira versao deste arquivo estendia {@code FinanceService} e sobrescrevia
 * {@code registerExpense}. <b>Nao funciona</b>, e as duas tentativas quebraram
 * coisas diferentes:
 *
 * <ul>
 *   <li><b>Sem repetir as anotacoes</b>, o metodo sobrescrito perde
 *       {@code @Transactional} e {@code @HouseholdScoped} -- interceptor de CDI
 *       e escolhido pelo metodo que roda, e a chamada a {@code super} e uma
 *       chamada Java comum. Todo lancamento fora de um fechamento passou a rodar
 *       sem transacao e sem papel de banco, e as suites das Etapas 1 e 2
 *       ficaram vermelhas inteiras.</li>
 *   <li><b>Repetindo as anotacoes</b>, o escopo de tenancy passa a ser
 *       reaplicado no meio da transacao de {@code checkout}, entre os dois
 *       {@code flush} dele -- e o <code>UPDATE</code> que ja tinha marcado os
 *       itens como comprados se perdia. O fechamento gravava
 *       <code>list_checkout</code>, gravava <code>transaction</code>, escrevia
 *       <code>list_checkout_id</code> em cada item, e deixava os tres
 *       <code>PENDING</code>. Silencioso, e so visivel porque os cenarios do elo
 *       conferem o status.</li>
 * </ul>
 *
 * <p>{@link AccountResolver#resolveDefault} nao tem anotacao nenhuma: roda
 * dentro da transacao e do escopo de quem o chamou. Sobrescreve-lo nao
 * reintroduz interceptor nenhum, e a falha acontece <b>dentro</b> de
 * {@code registerExpense}, que e exatamente o que o cenario descreve. Resolver a
 * conta e parte de registrar a despesa; uma indisponibilidade real apareceria
 * assim.
 *
 * <p>Desligado, delega para a implementacao de verdade -- todo o resto da suite
 * continua usando <code>finance</code> como ele e.
 */
@Mock
@ApplicationScoped
public class UnavailableExpenseRegistration extends AccountResolver {

    private volatile boolean unavailable;

    public void makeUnavailable() {
        this.unavailable = true;
    }

    public void makeAvailable() {
        this.unavailable = false;
    }

    @Override
    public Account resolveDefault(UUID householdId, UUID memberId) {
        if (unavailable) {
            // Excecao generica, e nao uma de banco forjada: o cenario descreve
            // "o registro de despesas esta indisponivel", e qualquer excecao nao
            // tratada dentro da transacao produz o mesmo desfecho. Escolher o
            // tipo seria fingir saber qual falha vai acontecer em producao.
            throw new IllegalStateException("Registro de despesas indisponivel (cenario de falha)");
        }
        return super.resolveDefault(householdId, memberId);
    }
}
