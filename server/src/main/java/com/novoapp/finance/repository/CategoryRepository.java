package com.novoapp.finance.repository;

import com.novoapp.finance.entity.Category;
import com.novoapp.finance.entity.EntryKind;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CategoryRepository implements PanacheRepositoryBase<Category, UUID> {

    /**
     * Raizes e subcategorias juntas, achatadas. Quem separa uma coisa da outra e
     * <code>CategoryQueryService</code>, que resolve o nome do pai -- a
     * ADR-0026 registra que a lista achatada sem marcar quem e quem era parte do
     * furo que ela veio fechar.
     *
     * <p>RLS ja limita ao household do contexto (ADR-0003).
     */
    public List<Category> listExpenseCategories() {
        return list("kind = ?1 and archivedAt is null order by name", EntryKind.EXPENSE);
    }

    /**
     * Busca por nome sem diferenciar maiuscula/minuscula, em toda a arvore --
     * raiz e subcategoria. Procurar so entre as raizes faria "restaurante dentro
     * de restaurante" criar uma raiz nova homonima em vez de ser recusado, que e
     * exatamente o que a ADR-0026 manda recusar (ADR-0016, limite de um nivel).
     */
    public Optional<Category> findExpenseByName(String name) {
        return find("kind = ?1 and archivedAt is null and lower(name) = ?2",
                EntryKind.EXPENSE, name.trim().toLowerCase(Locale.ROOT))
                .firstResultOptional();
    }
}
