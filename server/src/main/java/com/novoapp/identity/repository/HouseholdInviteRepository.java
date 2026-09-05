package com.novoapp.identity.repository;

import com.novoapp.identity.entity.HouseholdInvite;
import com.novoapp.identity.entity.InviteStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class HouseholdInviteRepository implements PanacheRepositoryBase<HouseholdInvite, UUID> {

    /** Resolucao do convite pelo link (ADR-0020). */
    public Optional<HouseholdInvite> findByToken(String token) {
        return find("token", token).firstResultOptional();
    }

    /**
     * Convite ainda gravado como PENDING pra este telefone. Expiracao nao e
     * filtrada aqui de proposito: quem chama precisa distinguir "nao existe
     * convite" de "existe mas venceu", que sao mensagens diferentes no chat.
     */
    public Optional<HouseholdInvite> findPendingByPhoneNumber(String phoneNumber) {
        return find("phoneNumber = ?1 and status = ?2", phoneNumber, InviteStatus.PENDING).firstResultOptional();
    }
}
