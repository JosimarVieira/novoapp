package com.novoapp.finance;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.finance.entity.Category;
import com.novoapp.finance.repository.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Leitura de categoria. E o unico ponto de <code>finance</code> que <code>nlu</code> toca. */
@ApplicationScoped
public class CategoryQueryService {

    @Inject
    CategoryRepository categories;

    /**
     * Raizes e subcategorias, cada uma sabendo quem e o pai.
     *
     * <p>O repositorio devolve tudo achatado (uma consulta so); o nome do pai e
     * resolvido aqui, em memoria, em vez de por join ou segunda consulta:
     * hierarquia tem um nivel so (ADR-0016) e a lista inteira ja esta na mao.
     */
    @Transactional
    @HouseholdScoped
    public List<CategoryView> listExpenseCategories() {
        List<Category> all = categories.listExpenseCategories();
        Map<UUID, String> nameById = new HashMap<>();
        for (Category category : all) {
            nameById.put(category.id, category.name);
        }
        return all.stream()
                .map(category -> new CategoryView(category.id, category.name, category.parentCategoryId,
                        category.parentCategoryId == null ? null : nameById.get(category.parentCategoryId)))
                .toList();
    }
}
