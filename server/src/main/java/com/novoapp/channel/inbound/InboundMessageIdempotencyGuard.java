package com.novoapp.channel.inbound;

import com.novoapp.common.tenancy.IdentityScoped;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persiste a mensagem antes de qualquer processamento e descarta reentrega
 * (ADR-0005).
 *
 * <p>E {@code INSERT ... ON CONFLICT DO NOTHING} em vez de "consulta antes,
 * insere depois": duas entregas simultaneas da mesma mensagem passariam pela
 * consulta juntas. Aqui quem decide e o indice unico, que nao tem janela.
 *
 * <p>Tambem nao e "insere e trata a excecao": violacao de constraint marca a
 * transacao pra rollback no JTA, e a linha da entrega legitima concorrente iria
 * junto.
 */
@ApplicationScoped
public class InboundMessageIdempotencyGuard {

    private static final String INSERT_IF_NEW = """
            INSERT INTO inbound_message
                (id, channel, provider_message_id, external_id_from, raw_text, received_at, status)
            VALUES (gen_random_uuid(), ?1, ?2, ?3, ?4, now(), 'RECEIVED')
            ON CONFLICT (channel, provider_message_id) DO NOTHING
            RETURNING id""";

    @Inject
    EntityManager entityManager;

    /**
     * @return o id da linha criada, ou vazio quando o provedor reentregou uma
     *         mensagem que ja processamos -- caso em que o webhook responde 200
     *         e o processamento para aqui, em silencio.
     */
    @Transactional
    @IdentityScoped
    public Optional<UUID> registerIfNew(NormalizedInbound inbound) {
        @SuppressWarnings("unchecked")
        List<UUID> inserted = entityManager.createNativeQuery(INSERT_IF_NEW)
                .setParameter(1, inbound.channel().name())
                .setParameter(2, inbound.providerMessageId())
                .setParameter(3, inbound.externalId())
                .setParameter(4, inbound.rawText())
                .getResultList();

        return inserted.isEmpty() ? Optional.empty() : Optional.of(inserted.get(0));
    }
}
