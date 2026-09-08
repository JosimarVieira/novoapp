package com.novoapp.finance;

import com.novoapp.common.tenancy.TenantContext;
import com.novoapp.support.Fixtures;
import com.novoapp.support.PostgresTestResource;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O limite de um nivel da ADR-0016, no ponto onde ele e aplicado.
 *
 * <p>A propria ADR-0016 exige este teste ("teste de unidade cobrindo 'criar
 * subcategoria de subcategoria' como erro"), e a ADR-0026 acrescenta que ele
 * precisa cobrir os dois caminhos de entrada. O caminho do chat esta no cenario
 * @etapa2 "Correcao livre nao pode criar subcategoria de subcategoria"; este
 * cobre o servico direto, que e por onde a tela de correcao da Etapa 4 vai
 * passar.
 *
 * <p>Integracao e nao unidade pura porque o enforcement depende de consulta ao
 * que ja existe no household, sob RLS -- testar com repositorio falso provaria
 * uma regra diferente da que roda em producao.
 */
@QuarkusTest
@WithTestResource(value = PostgresTestResource.class, scope = TestResourceScope.GLOBAL)
class CategoryHierarchyTest {

    @Inject
    Fixtures fixtures;

    @Inject
    CategoryService categories;

    private UUID householdId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        fixtures.truncateAll();
        householdId = fixtures.insertHousehold("Silva");
        memberId = fixtures.insertMember("Ana", null);
        fixtures.insertMembership(householdId, memberId, "OWNER");
        fixtures.insertWallet(householdId);
    }

    @Test
    @DisplayName("categoria-pai que ainda nao existe e criada junto com a filha")
    void createsParentAndChildTogether() {
        CategoryCreation creation = create("Restaurante", "Alimentação");

        assertThat(creation).isInstanceOf(CategoryCreation.Created.class);
        CategoryCreation.Created created = (CategoryCreation.Created) creation;
        assertThat(created.parentName()).isEqualTo("Alimentação");
        assertThat(created.parentCreated()).isTrue();

        assertThat(fixtures.count("""
                SELECT count(*) FROM category c JOIN category p ON p.id = c.parent_category_id
                WHERE c.name = 'Restaurante' AND p.name = 'Alimentação' AND p.parent_category_id IS NULL"""))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("categoria-pai que ja existe e reaproveitada, nao duplicada")
    void reusesExistingParent() {
        fixtures.insertExpenseCategory(householdId, "Alimentação");

        // Sem diferenciar maiuscula/minuscula, como a ADR-0026 escreveu.
        CategoryCreation.Created created = (CategoryCreation.Created) create("Restaurante", "alimentação");

        assertThat(created.parentCreated()).isFalse();
        assertThat(fixtures.count("SELECT count(*) FROM category WHERE lower(name) = 'alimentação'"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("subcategoria de subcategoria e recusada, e nada e criado")
    void refusesTwoLevels() {
        UUID parentId = fixtures.insertExpenseCategory(householdId, "Alimentação");
        fixtures.insertExpenseSubcategory(householdId, parentId, "Restaurante");

        CategoryCreation creation = create("Rodízio de pizza", "Restaurante");

        assertThat(creation).isInstanceOf(CategoryCreation.ParentIsSubcategory.class);
        CategoryCreation.ParentIsSubcategory refused = (CategoryCreation.ParentIsSubcategory) creation;
        assertThat(refused.parentName()).isEqualTo("Restaurante");
        assertThat(refused.grandparentName()).isEqualTo("Alimentação");

        // Recusa e recusa: nem a filha nem uma raiz homonima sobraram no banco.
        assertThat(fixtures.count("SELECT count(*) FROM category")).isEqualTo(2);
    }

    @Test
    @DisplayName("sem pai indicado, a categoria nasce raiz (o \"sim\" simples da ADR-0024)")
    void createsRootWhenNoParentGiven() {
        CategoryCreation.Created created = (CategoryCreation.Created) create("Pet shop", null);

        assertThat(created.hasParent()).isFalse();
        assertThat(fixtures.count(
                "SELECT count(*) FROM category WHERE name = 'Pet shop' AND parent_category_id IS NULL"))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("confirmar duas vezes a mesma categoria nao estoura o indice unico")
    void isIdempotent() {
        UUID first = ((CategoryCreation.Created) create("Pet shop", null)).categoryId();
        UUID again = ((CategoryCreation.Created) create("Pet shop", null)).categoryId();

        assertThat(again).isEqualTo(first);
        assertThat(fixtures.count("SELECT count(*) FROM category")).isEqualTo(1);
    }

    private CategoryCreation create(String name, String parentName) {
        return TenantContext.withHousehold(householdId,
                () -> categories.createExpenseCategory(householdId, memberId, name, parentName));
    }
}
