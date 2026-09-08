package com.novoapp.finance.repository;

import com.novoapp.finance.entity.Transaction;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TransactionRepository implements PanacheRepositoryBase<Transaction, UUID> {

    /**
     * O alvo do <code>desfazer</code> (ADR-0025): a mais recente ainda nao
     * estornada, sem janela de tempo.
     *
     * <p>Nao filtra por <code>created_by_member_id</code> de proposito -- o
     * escopo e o household inteiro, qualquer membro (ADR-0012). Filtrar pelo
     * remetente seria a implementacao escolhendo em silencio um comportamento
     * mais restrito do que a ADR ja decidiu, que e justamente o que a ADR-0025
     * existe pra impedir.
     *
     * <p>Nao filtra household_id: quem filtra e a policy de RLS (ADR-0003).
     *
     * <p>Desempate por <code>createdAt</code>, e nao por <code>occurred_on</code>:
     * a data do lancamento e do dia, e dois lancamentos do mesmo dia empatariam.
     * "Mais recente" aqui e "o ultimo que entrou".
     */
    public Optional<Transaction> findLatestNotReversed() {
        return find("reversedAt is null order by createdAt desc, id desc").firstResultOptional();
    }
}
