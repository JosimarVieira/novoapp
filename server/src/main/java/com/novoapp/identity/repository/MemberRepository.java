package com.novoapp.identity.repository;

import com.novoapp.identity.entity.Member;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MemberRepository implements PanacheRepositoryBase<Member, UUID> {

    /**
     * Reaproveita a pessoa quando ela ja e membro de outro household
     * (ADR-0007): o mesmo telefone e sempre a mesma pessoa.
     */
    public Optional<Member> findByPhoneNumber(String phoneNumber) {
        return find("phoneNumber", phoneNumber).firstResultOptional();
    }
}
