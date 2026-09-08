package com.novoapp.identity;

import java.util.List;
import java.util.Locale;
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
     * @param channelIdentityId a conversa de onde a mensagem veio. Chat e sempre
     *        1:1 (ADR-0008), entao e este id -- e nao o household -- que
     *        identifica o fio em que uma pergunta pendente espera resposta
     *        (ADR-0018). Vem daqui porque <code>pending_action</code> e escrita
     *        sob o papel de dominio, que nao enxerga <code>channel_identity</code>
     *        (ADR-0022).
     * @param locale idioma em que a resposta sai (ADR-0015). E do membro, e nao
     *        do household: dois membros da mesma familia podem preferir idiomas
     *        diferentes. Resolvido aqui, junto do resto do contexto, porque
     *        esquecer de propaga-lo nao quebra nada -- so responde no idioma
     *        errado, em silencio, que e a consequencia negativa que a propria
     *        ADR-0015 registra.
     * @param multipleHouseholds a pessoa tem mais de um vinculo. Quando
     *        verdadeiro, todo recibo nomeia o household, pra que erro de
     *        contexto fique visivel sem o usuario perguntar (ADR-0007).
     */
    record ResolvedContext(UUID householdId,
                           String householdName,
                           UUID memberId,
                           String memberName,
                           UUID channelIdentityId,
                           Locale locale,
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
