package com.novoapp.finance;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.finance.repository.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/** Leitura de categoria. E o unico ponto de <code>finance</code> que <code>nlu</code> toca. */
@ApplicationScoped
public class CategoryQueryService {

    @Inject
    CategoryRepository categories;

    @Transactional
    @HouseholdScoped
    public List<CategoryView> listExpenseCategories() {
        return categories.listExpenseCategories().stream()
                .map(category -> new CategoryView(category.id, category.name))
                .toList();
    }
}
