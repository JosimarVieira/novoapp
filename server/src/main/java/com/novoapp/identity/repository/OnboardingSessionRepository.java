package com.novoapp.identity.repository;

import com.novoapp.identity.Channel;
import com.novoapp.identity.entity.OnboardingSession;
import com.novoapp.identity.entity.OnboardingState;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OnboardingSessionRepository implements PanacheRepositoryBase<OnboardingSession, UUID> {

    public Optional<OnboardingSession> findOpen(Channel channel, String externalId) {
        return find("channel = ?1 and externalId = ?2", channel, externalId).firstResultOptional();
    }

    /**
     * Grava o passo da conversa, criando ou atualizando.
     *
     * <p>E upsert, e nao "consulta e insere", porque o Telegram entrega em
     * paralelo (ate 40 conexoes): duas mensagens da mesma pessoa chegando juntas
     * viam as duas "nao existe sessao" e tentavam inserir, e uma estourava
     * violacao de unicidade. Mesmo raciocinio do guarda de idempotencia da
     * ADR-0005 -- quem decide e o indice unico, que nao tem janela.
     */
    public void save(Channel channel, String externalId, OnboardingState state, String inviteToken) {
        getEntityManager().createNativeQuery("""
                INSERT INTO onboarding_session
                    (id, channel, external_id, state, invite_token, created_at, updated_at)
                VALUES (gen_random_uuid(), ?1, ?2, ?3, ?4, now(), now())
                ON CONFLICT (channel, external_id) DO UPDATE
                SET state = excluded.state,
                    invite_token = excluded.invite_token,
                    updated_at = now()""")
                .setParameter(1, channel.name())
                .setParameter(2, externalId)
                .setParameter(3, state.name())
                .setParameter(4, inviteToken)
                .executeUpdate();
    }

    public void close(Channel channel, String externalId) {
        delete("channel = ?1 and externalId = ?2", channel, externalId);
    }
}
