package com.novoapp.common.tenancy;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Qual household esta sendo atendido na thread atual.
 *
 * <p>E deliberadamente explicito: quem resolveu a identidade declara o
 * household, e os interceptores de {@link HouseholdScoped} leem daqui para
 * montar a sessao de banco. Nada aqui adivinha o tenant a partir de argumento
 * de metodo -- inferencia magica de tenant e exatamente o tipo de coisa que a
 * ADR-0003 existe para nao depender.
 *
 * <p>O processamento roda fora do ciclo de request do webhook (ADR-0005), em
 * thread propria, entao o escopo e por thread e nao CDI de request.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_HOUSEHOLD = new ThreadLocal<>();

    private TenantContext() {
    }

    public static Optional<UUID> currentHousehold() {
        return Optional.ofNullable(CURRENT_HOUSEHOLD.get());
    }

    public static UUID requireHousehold() {
        UUID householdId = CURRENT_HOUSEHOLD.get();
        if (householdId == null) {
            throw new IllegalStateException(
                    "Nenhum household no contexto: metodo de dominio chamado fora de TenantContext.withHousehold");
        }
        return householdId;
    }

    public static void withHousehold(UUID householdId, Runnable body) {
        withHousehold(householdId, () -> {
            body.run();
            return null;
        });
    }

    public static <T> T withHousehold(UUID householdId, Supplier<T> body) {
        UUID previous = CURRENT_HOUSEHOLD.get();
        CURRENT_HOUSEHOLD.set(householdId);
        try {
            return body.get();
        } finally {
            if (previous == null) {
                CURRENT_HOUSEHOLD.remove();
            } else {
                CURRENT_HOUSEHOLD.set(previous);
            }
        }
    }

    /** Limpa a thread. Chamado no fim do processamento de cada mensagem. */
    public static void clear() {
        CURRENT_HOUSEHOLD.remove();
    }
}
