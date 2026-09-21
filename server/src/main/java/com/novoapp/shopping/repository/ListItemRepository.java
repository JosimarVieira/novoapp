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
