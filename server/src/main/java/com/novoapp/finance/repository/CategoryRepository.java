package com.novoapp.finance.repository;

import com.novoapp.finance.entity.Category;
import com.novoapp.finance.entity.EntryKind;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class CategoryRepository implements PanacheRepositoryBase<Category, UUID> {

    /** RLS ja limita ao household do contexto (ADR-0003). */
    public List<Category> listExpenseCategories() {
        return list("kind = ?1 and archivedAt is null order by name", EntryKind.EXPENSE);
    }
}
