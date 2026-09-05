package com.novoapp.identity.repository;

import com.novoapp.identity.Channel;
import com.novoapp.identity.entity.OnboardingSession;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class OnboardingSessionRepository implements PanacheRepositoryBase<OnboardingSession, UUID> {

    public Optional<OnboardingSession> findOpen(Channel channel, String externalId) {
        return find("channel = ?1 and externalId = ?2", channel, externalId).firstResultOptional();
    }

    public void close(Channel channel, String externalId) {
        delete("channel = ?1 and externalId = ?2", channel, externalId);
    }
}
