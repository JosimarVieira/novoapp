package com.novoapp.nlu;

import com.novoapp.finance.CategoryQueryService;
import com.novoapp.finance.CategoryView;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Monta o contexto do household que vai junto com a chamada ao modelo.
 *
 * <p>Na Etapa 1 isso e so a lista de categorias de despesa. Le em
 * <code>finance</code> e nunca escreve nada la -- e a unica aresta que
 * <code>nlu</code> tem pra outro modulo de dominio (sdd-modulo-nlu.md).
 */
@ApplicationScoped
public class ContextBuilder {

    @Inject
    CategoryQueryService categories;

    public List<CategoryView> expenseCategories() {
        return categories.listExpenseCategories();
    }
}
