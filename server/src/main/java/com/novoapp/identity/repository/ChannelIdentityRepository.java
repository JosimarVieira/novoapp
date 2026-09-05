package com.novoapp.identity.repository;

import com.novoapp.identity.Channel;
import com.novoapp.identity.entity.ChannelIdentity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ChannelIdentityRepository implements PanacheRepositoryBase<ChannelIdentity, UUID> {

    /** A busca que abre toda mensagem recebida. */
    public Optional<ChannelIdentity> findByChannelAndExternalId(Channel channel, String externalId) {
        return find("channel = ?1 and externalId = ?2", channel, externalId).firstResultOptional();
    }

    public Optional<ChannelIdentity> findByMemberAndChannel(UUID memberId, Channel channel) {
        return find("memberId = ?1 and channel = ?2", memberId, channel).firstResultOptional();
    }
}
