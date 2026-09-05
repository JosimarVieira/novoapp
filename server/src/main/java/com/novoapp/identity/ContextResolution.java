package com.novoapp.identity;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de {@link IdentityResolutionService#resolveContext}. Os tres
 * desfechos possiveis descritos em <code>sdd-modulo-channel.md</code>.
 */
public sealed interface ContextResolution {

    /**
     * Identidade resolvida e household definido: a mensagem pode seguir pro
     * pipeline de interpretacao.
     *
     * @param multipleHouseholds a pessoa tem mais de um vinculo. Quando
     *        verdadeiro, todo recibo nomeia o household, pra que erro de
     *        contexto fique visivel sem o usuario perguntar (ADR-0007).
     */
    record ResolvedContext(UUID householdId,
                           String householdName,
                           UUID memberId,
                           String memberName,
                           boolean multipleHouseholds) implements ContextResolution {
    }

    /**
     * Pessoa com mais de um household e nenhum ativo: precisa dizer qual
     * familia antes de qualquer lancamento (ADR-0007). Nao e onboarding.
     */
    record ChooseHousehold(UUID memberId, List<String> householdNames) implements ContextResolution {
    }

    /**
     * Nao ha identidade vinculada, ou ha uma conversa de onboarding em aberto.
     * Quem assume daqui e {@code identity.onboarding} -- fluxo deterministico,
     * sem LLM (ADR-0020).
     */
    record OnboardingStep() implements ContextResolution {
    }
}
