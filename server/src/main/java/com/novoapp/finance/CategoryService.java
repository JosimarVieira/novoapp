package com.novoapp.finance;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.finance.entity.Category;
import com.novoapp.finance.entity.EntryKind;
import com.novoapp.finance.repository.CategoryRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Criacao de categoria a partir do chat (ADR-0024, ADR-0026).
 *
 * <p>Household novo nasce sem nenhuma categoria (ADR-0013): toda categoria que
 * existe foi criada porque a familia precisou dela, e este e o caminho por onde
 * isso acontece.
 *
 * <p>Este servico nao pergunta nada e nao decide se deve criar -- so cria o que
 * ja foi confirmado. A pergunta e a confirmacao sao de
 * <code>conversation</code>, pelo mesmo desenho que separa "interpretar" de
 * "executar" no resto do sistema.
 */
@ApplicationScoped
public class CategoryService {

    @Inject
    CategoryRepository categories;

    /**
     * Cria a categoria confirmada, e a categoria-pai junto quando ela tambem
     * faltar.
     *
     * <p>A ordem da ADR-0026: procura o pai pelo nome, cria como raiz se nao
     * existir, e pendura o filho nele. A busca do pai varre a arvore inteira, e
     * nao so as raizes -- e o que faz "rodizio de pizza dentro de restaurante"
     * ser recusado quando "Restaurante" ja e subcategoria de "Alimentacao", em
     * vez de criar uma raiz "Restaurante" homonima. Dois niveis violaria a
     * ADR-0016.
     *
     * @param parentName nulo no "sim" simples da ADR-0024: cria categoria raiz
     */
    @Transactional
    @HouseholdScoped
    public CategoryCreation createExpenseCategory(UUID householdId, UUID memberId,
                                                  String name, String parentName) {
        if (parentName == null || parentName.isBlank()) {
            Category created = findOrCreate(householdId, memberId, name, null);
            return new CategoryCreation.Created(created.id, created.name, null, false);
        }

        Optional<Category> existingParent = categories.findExpenseByName(parentName);
        if (existingParent.isPresent() && existingParent.get().parentCategoryId != null) {
            Category parent = existingParent.get();
            Category grandparent = categories.findById(parent.parentCategoryId);
            return new CategoryCreation.ParentIsSubcategory(parent.name,
                    grandparent == null ? null : grandparent.name);
        }

        boolean parentCreated = existingParent.isEmpty();
        Category parent = existingParent.orElseGet(() -> findOrCreate(householdId, memberId, parentName, null));
        Category child = findOrCreate(householdId, memberId, name, parent.id);
        return new CategoryCreation.Created(child.id, child.name, parent.name, parentCreated);
    }

    /**
     * Idempotente de proposito. A categoria confirmada normalmente nao existe --
     * se existisse, o enum da tool teria batido e nao haveria pergunta nenhuma
     * (ADR-0024). Mas duas confirmacoes da mesma pendencia, ou uma corrida entre
     * dois membros, nao podem virar violacao de indice unico no meio de um
     * lancamento.
     */
    private Category findOrCreate(UUID householdId, UUID memberId, String name, UUID parentCategoryId) {
        String trimmed = name.trim();
        Optional<Category> existing = categories.findExpenseByName(trimmed)
                .filter(category -> java.util.Objects.equals(category.parentCategoryId, parentCategoryId));
        if (existing.isPresent()) {
            return existing.get();
        }

        Category category = new Category();
        category.householdId = householdId;
        category.parentCategoryId = parentCategoryId;
        category.name = trimmed;
        // Subcategoria herda o kind do pai (ADR-0016). Nesta etapa so ha criacao
        // por despesa, entao herdar e o mesmo que fixar EXPENSE -- quando
        // registrarReceita existir, o kind passa a vir de quem chama.
        category.kind = EntryKind.EXPENSE;
        category.createdByMemberId = memberId;
        categories.persist(category);
        // Flush dentro do escopo: no commit o SET LOCAL ROLE ja teria voltado pro
        // papel de fora, que nao tem permissao nesta tabela.
        categories.flush();
        return category;
    }
}
