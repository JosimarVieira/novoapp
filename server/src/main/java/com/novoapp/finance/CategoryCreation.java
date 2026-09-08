package com.novoapp.finance;

import java.util.UUID;

/**
 * Desfecho de {@link CategoryService#createExpenseCategory}.
 *
 * <p>A recusa nao e excecao: e o cenario "Correcao livre nao pode criar
 * subcategoria de subcategoria", que termina em pergunta nova, nao em erro
 * generico. Modelar como resultado -- e nao como {@code throw} -- e o que deixa
 * <code>conversation</code> escolher o texto sem inspecionar mensagem de
 * excecao.
 */
public sealed interface CategoryCreation {

    /**
     * @param parentCreated a categoria-pai tambem nasceu agora. E o caso de
     *        "restaurante dentro de alimentacao" num household onde nenhuma das
     *        duas existia (ADR-0026)
     */
    record Created(UUID categoryId, String name, String parentName, boolean parentCreated)
            implements CategoryCreation {

        public boolean hasParent() {
            return parentName != null;
        }
    }

    /**
     * O pai indicado ja e, ele mesmo, uma subcategoria. Dois niveis violaria a
     * ADR-0016, entao nada e criado.
     */
    record ParentIsSubcategory(String parentName, String grandparentName) implements CategoryCreation {
    }
}
