package com.novoapp.shopping.repository;

import com.novoapp.common.text.Normalization;
import com.novoapp.shopping.entity.ListItem;
import com.novoapp.shopping.entity.ListItemStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ListItemRepository implements PanacheRepositoryBase<ListItem, UUID> {

    /**
     * Os itens pendentes cujos nomes a pessoa citou ao fechar a compra,
     * comparados pela forma normalizada (ADR-0030) -- e o casamento que a Etapa 3
     * faz entre "comprei o arroz e o leite" e o que esta na lista.
     *
     * <p>Nome citado que nao esta pendente simplesmente nao entra: quem decide o
     * que fazer com isso e <code>conversation</code>, nunca este repositorio.
     */
    public List<ListItem> listPendingNamed(UUID shoppingListId, List<String> names) {
        List<String> normalized = names.stream().map(Normalization::of).toList();
        return list("shoppingListId = ?1 and status = ?2 and nameNormalized in ?3 order by createdAt",
                shoppingListId, ListItemStatus.PENDING, normalized);
    }

    /** O que esta faltando, na ordem em que foi pedido. */
    public List<ListItem> listPending(UUID shoppingListId) {
        return list("shoppingListId = ?1 and status = ?2 order by createdAt",
                shoppingListId, ListItemStatus.PENDING);
    }

    /**
     * Liga os itens ao fechamento que os comprou, por <code>UPDATE</code> em
     * massa -- uma coluna, uma instrucao, sem passar pelo estado das entidades.
     *
     * <p>Nao e otimizacao. O fechamento escreve em <code>list_item</code>
     * <b>duas vezes</b> na mesma transacao: primeiro o status, que precisa estar
     * no banco antes de <code>finance</code> ser chamado (e o que da ao rollback
     * o que desfazer, ADR-0031), e depois este vinculo, que so pode existir
     * depois de o <code>list_checkout</code> ter id. Escrever a segunda pelo
     * estado das entidades fazia o Hibernate reemitir a linha inteira com os
     * valores de <b>antes</b> do primeiro flush -- o status voltava a
     * <code>PENDING</code> e o <code>purchased_by_member_id</code> a nulo, em
     * silencio, com o fechamento e o lancamento gravados do mesmo jeito.
     * Observado em 2026-09-21, e so visivel porque os cenarios do elo conferem o
     * status.
     */
    public void linkToCheckout(List<UUID> itemIds, UUID listCheckoutId) {
        if (itemIds.isEmpty()) {
            return;
        }
        update("listCheckoutId = ?1 where id in ?2", listCheckoutId, itemIds);
    }

    /**
     * Os itens que <b>aquele</b> fechamento fechou -- nao a lista inteira
     * (ADR-0032: "o fechamento e a unidade"). E o que o <code>desfazer</code>
     * devolve a <code>PENDING</code>.
     */
    public List<ListItem> listPurchasedByCheckout(UUID listCheckoutId) {
        return list("listCheckoutId = ?1 and status = ?2 order by createdAt",
                listCheckoutId, ListItemStatus.PURCHASED);
    }

    /**
     * O que aquele fechamento fechou e ja voltou a <code>PENDING</code> -- a
     * leitura que o recibo do <code>desfazer</code> usa para nomear os dois
     * lados (ADR-0032). O vinculo com o fechamento sobrevive ao estorno de
     * proposito: e a procedencia do item, e e o que torna esta consulta possivel.
     */
    public List<ListItem> listPendingByCheckout(UUID listCheckoutId) {
        return list("listCheckoutId = ?1 and status = ?2 order by createdAt",
                listCheckoutId, ListItemStatus.PENDING);
    }

    /**
     * Existe outro item pendente com este nome normalizado?
     *
     * <p>A pergunta que o <code>desfazer</code> do fechamento faz item a item:
     * quem ja avisou de novo que o arroz acabou criou um <code>PENDING</code>, e
     * o indice unico parcial da ADR-0030 nao aceita um segundo. O item daquele
     * fechamento fica <code>PURCHASED</code>, e nao e perda -- o efeito
     * pretendido ja esta alcancado (ADR-0032).
     */
    public boolean hasOtherPendingWithSameName(UUID shoppingListId, String nameNormalized, UUID exceptId) {
        return count("shoppingListId = ?1 and status = ?2 and nameNormalized = ?3 and id <> ?4",
                shoppingListId, ListItemStatus.PENDING, nameNormalized, exceptId) > 0;
    }

    /**
     * Comparacao pela forma normalizada -- minuscula e sem acento (ADR-0030) --,
     * a mesma do indice unico parcial que sustenta "item repetido nao duplica"
     * (sdd-modulo-shopping.md).
     *
     * <p>Ate 2026-09-16 os dois eram <code>lower(name)</code>, e entao "acabou
     * cafe" com "Café" ja pendente inseria um segundo item: a consulta nao
     * achava o primeiro e o indice nao barrava o segundo. Sem pergunta, sem
     * aviso -- o pior desfecho possivel dos dois.
     */
    public Optional<ListItem> findPendingByName(UUID shoppingListId, String name) {
        return find("shoppingListId = ?1 and status = ?2 and nameNormalized = ?3",
                shoppingListId, ListItemStatus.PENDING, Normalization.of(name))
                .firstResultOptional();
    }
}
