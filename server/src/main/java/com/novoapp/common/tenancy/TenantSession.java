package com.novoapp.common.tenancy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import org.hibernate.Session;
import org.jboss.logging.Logger;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Ponto unico onde a sessao de banco recebe o papel e o household da transacao
 * atual (ADR-0003: "definido em um unico ponto de entrada").
 *
 * <p>Decisao registrada em <code>sdd-modulo-identity.md</code> e na ADR-0022: o
 * interceptor de RLS nao vive em <code>identity</code>, e sim neste pacote
 * tecnico. <code>identity</code> resolve <em>qual</em> e o household; aplicar
 * isso na conexao e infraestrutura, e todo modulo de dominio precisa dela --
 * pendurar em <code>identity</code> daria a ele um papel que o
 * <code>sdd-visao-geral.md</code> nao lhe atribui.
 *
 * <p>Tudo aqui usa <code>SET LOCAL</code>: some sozinho no fim da transacao, que
 * e o que impede o contexto de vazar entre requisicoes que reusam a mesma
 * conexao do pool -- o risco que a ADR-0003 chama de "o risco real da decisao".
 */
@ApplicationScoped
public class TenantSession {

    private static final Logger LOG = Logger.getLogger(TenantSession.class);

    private static final ThreadLocal<Applied> APPLIED = new ThreadLocal<>();

    @Inject
    EntityManager entityManager;

    @Inject
    TransactionManager transactionManager;

    private record Applied(DatabaseRole role, UUID householdId) {
    }

    /**
     * Executa o corpo com a sessao de banco no papel pedido, restaurando o
     * escopo anterior na saida.
     *
     * <p>A restauracao existe porque os escopos aninham de verdade: o onboarding
     * roda sob o papel de identidade e, dentro da mesma transacao, publica
     * <code>HouseholdCreated</code>, que faz <code>finance</code> criar a conta
     * WALLET sob o papel de dominio. Sem restaurar, o resto do onboarding
     * continuaria no papel errado.
     */
    public <T> T runWith(DatabaseRole role, UUID householdId, Callable<T> body) throws Exception {
        requireActiveTransaction(role);

        Applied previous = APPLIED.get();
        apply(role, householdId);
        APPLIED.set(new Applied(role, householdId));
        try {
            return body.call();
        } finally {
            if (previous == null) {
                APPLIED.remove();
            } else {
                APPLIED.set(previous);
            }
            restoreQuietly(previous);
        }
    }

    /**
     * Se o corpo falhou, a transacao pode ja estar abortada, e qualquer SQL nela
     * estoura "current transaction is aborted". Insistir aqui nao adianta -- o
     * rollback descarta o <code>SET LOCAL</code> de qualquer jeito -- e lancar
     * de dentro do <code>finally</code> substituiria a excecao original pela
     * desta linha, escondendo a causa real.
     */
    private void restoreQuietly(Applied previous) {
        try {
            if (previous == null) {
                clearSession();
            } else {
                apply(previous.role(), previous.householdId());
            }
        } catch (RuntimeException e) {
            LOG.debugf(e, "Nao foi possivel restaurar o escopo de tenancy; a transacao provavelmente ja abortou");
        }
    }

    private void requireActiveTransaction(DatabaseRole role) {
        try {
            if (transactionManager.getStatus() != Status.STATUS_ACTIVE) {
                throw new IllegalStateException(
                        "Escopo de tenancy (" + role + ") exige transacao ativa: SET LOCAL fora de transacao "
                                + "nao tem efeito e faria toda policy de RLS negar em silencio");
            }
        } catch (jakarta.transaction.SystemException e) {
            throw new IllegalStateException("Nao foi possivel ler o estado da transacao atual", e);
        }
    }

    private void apply(DatabaseRole role, UUID householdId) {
        String household = householdId == null ? "" : householdId.toString();
        entityManager.unwrap(Session.class).doWork(connection -> {
            // Identificador nao aceita bind; o nome vem de constante do enum, nunca de entrada externa.
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET LOCAL ROLE " + role.sqlName());
            }
            try (PreparedStatement statement = connection
                    .prepareStatement("SELECT set_config('app.household_id', ?, true)")) {
                statement.setString(1, household);
                statement.execute();
            }
        });
    }

    private void clearSession() {
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (Statement statement = connection.createStatement()) {
                // Volta pro papel de login, que e NOINHERIT: fora de um escopo, a
                // conexao nao consegue ler nem escrever nada.
                statement.execute("SET LOCAL ROLE NONE");
                statement.execute("SELECT set_config('app.household_id', '', true)");
            }
        });
    }
}
