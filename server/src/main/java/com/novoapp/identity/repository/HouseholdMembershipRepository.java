package com.novoapp.identity.repository;

import com.novoapp.identity.entity.HouseholdMembership;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class HouseholdMembershipRepository implements PanacheRepositoryBase<HouseholdMembership, UUID> {

    /** Quantos e quais households a pessoa tem -- decide se ha ambiguidade (ADR-0007). */
    public List<HouseholdMembership> findByMember(UUID memberId) {
        return list("memberId", memberId);
    }

    public Optional<HouseholdMembership> findByHouseholdAndMember(UUID householdId, UUID memberId) {
        return find("householdId = ?1 and memberId = ?2", householdId, memberId).firstResultOptional();
    }
}
