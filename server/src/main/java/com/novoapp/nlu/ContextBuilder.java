package com.novoapp.nlu;

import com.novoapp.finance.CategoryQueryService;
import com.novoapp.finance.CategoryView;
import com.novoapp.shopping.ListItemView;
import com.novoapp.shopping.ShoppingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta o contexto do household que vai junto com a chamada ao modelo: as
 * categorias e as listas reais da familia (ADR-0004).
 *
 * <p>Le em <code>finance</code> e em <code>shopping</code>, nunca escreve em
 * nenhum dos dois. A aresta pra <code>shopping</code> entrou na Etapa 2a pelo
 * mesmo motivo que a de <code>finance</code> entrou na Etapa 1: sem os itens
 * pendentes no contexto, o modelo nao tem como casar "comprei o arroz" com o
 * item "Arroz" da lista.
 */
@ApplicationScoped
public class ContextBuilder {

    @Inject
    CategoryQueryService categories;

    @Inject
    ShoppingService shopping;

    /**
     * As categorias de despesa, indexadas pelo rotulo que vai virar valor do
     * enum da tool.
     *
     * <p>O rotulo e o nome simples quando ele e unico no household. Quando duas
     * categorias tem o mesmo nome em ramos diferentes -- "Presente" dentro de
     * "Educacao" e dentro de "Lazer", exemplo que a propria ADR-0016 usa --,
     * ambas passam a se apresentar como "Pai > Filho". Enum com valor repetido
     * seria uma escolha que o modelo faz e o codigo nao consegue desfazer.
     *
     * @return mapa preservando a ordem alfabetica que o repositorio devolve
     */
    public Map<String, CategoryView> expenseCategoriesByLabel(UUID householdId) {
        List<CategoryView> all = categories.listExpenseCategories();

        Map<String, Integer> occurrences = new HashMap<>();
        for (CategoryView category : all) {
            occurrences.merge(category.name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
        }

        Map<String, CategoryView> byLabel = new LinkedHashMap<>();
        for (CategoryView category : all) {
            boolean ambiguousName = occurrences.get(category.name().toLowerCase(java.util.Locale.ROOT)) > 1;
            String label = ambiguousName && !category.isRoot()
                    ? "%s > %s".formatted(category.parentName(), category.name())
                    : category.name();
            byLabel.put(label, category);
        }
        return byLabel;
    }

    /** Nomes dos itens que estao faltando, pro modelo casar a grafia do que a pessoa disse. */
    public List<String> pendingItemNames(UUID householdId) {
        return shopping.pendingItems(householdId).stream().map(ListItemView::name).toList();
    }
}
