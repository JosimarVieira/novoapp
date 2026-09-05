package com.novoapp.nlu;

import com.novoapp.finance.CategoryView;
import com.novoapp.nlu.spi.ExpenseExtractor;
import com.novoapp.nlu.spi.ExtractedExpense;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Interpretacao: contexto do household + LLM com tools -> {@link Intent} com
 * confianca (sdd-modulo-nlu.md). Nao executa nada, nao persiste dado de
 * dominio.
 *
 * <p>Escopo da Etapa 1: so a tool <code>registrarDespesa</code>, so o caso de
 * categoria que ja existe e foi reconhecida. Ambiguidade entre categorias
 * parecidas, criacao de categoria nova e valor ausente sao os cenarios
 * <code>@etapa2</code> -- exigem a politica de confianca media/baixa inteira da
 * ADR-0004 e {@code PendingAction}, que ainda nao existem.
 */
@ApplicationScoped
public class NluService {

    @Inject
    ContextBuilder contextBuilder;

    @Inject
    ExpenseExtractor expenseExtractor;

    /**
     * Nome em ingles, e nao <code>interpretar</code> como no SDD: identificador
     * em ingles e regra sem excecao no CLAUDE.md.
     */
    public Intent interpret(UUID householdId, String text) {
        if (text == null || text.isBlank()) {
            return Intent.unknown();
        }

        List<CategoryView> categories = contextBuilder.expenseCategories();
        Optional<ExtractedExpense> extracted = expenseExtractor
                .extract(text, categories.stream().map(CategoryView::name).toList());

        if (extracted.isEmpty()) {
            return Intent.unknown();
        }
        ExtractedExpense expense = extracted.get();

        if (expense.amountCents() <= 0) {
            // Valor ausente ou zerado e cenario @etapa2: aqui vira so confianca
            // baixa, nunca um valor inventado.
            return Intent.unknown();
        }

        Optional<CategoryView> match = categories.stream()
                .filter(category -> matches(category.name(), expense.categoryName()))
                .findFirst();

        // Categoria fora do enum tambem e @etapa2 ("oferecer criar categoria"):
        // por ora e so confianca baixa.
        return match
                .map(category -> Intent.registerExpense(category.id(), category.name(), expense.amountCents()))
                .orElseGet(Intent::unknown);
    }

    private boolean matches(String categoryName, String returnedByModel) {
        return returnedByModel != null
                && categoryName.toLowerCase(Locale.ROOT).equals(returnedByModel.trim().toLowerCase(Locale.ROOT));
    }
}
