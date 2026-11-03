package com.novoapp.finance.repository;

import com.novoapp.finance.entity.TransactionEdit;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * So grava e le: o historico de edicao e append-only (ADR-0012), e o papel de
 * runtime nem tem permissao para outra coisa. Nenhuma consulta filtra
 * household_id -- quem filtra e a policy de RLS (ADR-0003).
 */
@ApplicationScoped
public class TransactionEditRepository implements PanacheRepositoryBase<TransactionEdit, UUID> {
}
