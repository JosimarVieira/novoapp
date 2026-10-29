package com.novoapp.shopping.repository;

import com.novoapp.shopping.entity.ListCheckout;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ListCheckoutRepository implements PanacheRepositoryBase<ListCheckout, UUID> {

    /**
     * "Este lancamento veio de um fechamento?" -- a pergunta que o
     * <code>desfazer</code> da ADR-0032 faz antes de decidir se reverte um lado
     * ou os dois. Sustentada por indice unico, e nao so por convencao.
     */
    public Optional<ListCheckout> findByTransaction(UUID transactionId) {
        return find("transactionId", transactionId).firstResultOptional();
    }

    /**
     * Os fechamentos do household, do mais recente ao mais antigo -- a ordem em
     * que se procura a categoria do fechamento pelo botao (ADR-0040). Nao filtra
     * household_id: quem filtra e a policy de RLS (ADR-0003).
     */
    public List<ListCheckout> listLatestFirst() {
        return list("order by performedAt desc");
    }
}
