package com.novoapp.identity;

import com.novoapp.common.tenancy.IdentityScoped;
import com.novoapp.identity.entity.Member;
import com.novoapp.identity.repository.MemberRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * O nome de uma pessoa, pra quem precisa escrever "pedido por Ana" num recibo.
 *
 * <p>Existe porque <code>member</code> nao tem <code>household_id</code>
 * (ADR-0007) e portanto nao tem RLS em que se apoiar: o papel de dominio nao
 * recebe grant nenhum sobre a tabela, so o pre-tenant (ADR-0022). Sem este
 * ponto, qualquer modulo de dominio que precisasse do nome de um membro teria de
 * usar o escopo pre-tenant por conta propria -- que e exatamente o que o teste
 * de arquitetura proibe, e por bom motivo.
 *
 * <p>Devolve so o nome, nunca a entidade: nao ha como um chamador de dominio
 * usar este ponto para varrer membros de outra familia. Quem chama ja tem em
 * maos um <code>member_id</code> lido de uma linha do proprio household.
 */
@ApplicationScoped
public class MemberDirectory {

    @Inject
    MemberRepository members;

    @Transactional
    @IdentityScoped
    public Optional<String> nameOf(UUID memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        Member member = members.findById(memberId);
        return Optional.ofNullable(member).map(found -> found.name);
    }
}
