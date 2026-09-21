package com.novoapp.shopping.repository;

import com.novoapp.shopping.entity.ListCheckout;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

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
}
