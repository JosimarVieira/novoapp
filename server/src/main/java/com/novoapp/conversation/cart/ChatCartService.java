package com.novoapp.conversation.cart;

import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.conversation.cart.repository.ChatCartItemRepository;
import com.novoapp.identity.ContextResolution.ResolvedContext;
import com.novoapp.shopping.ListItemView;
import com.novoapp.shopping.ShoppingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * O Carrinho (glossario, ADR-0040): poe, tira, le e esvazia.
 *
 * <p>Estado do fio de quem tocou -- {@code (household, membro)}, e nao do
 * household nem da mensagem. Duas mensagens do mesmo fio mostram o mesmo
 * carrinho; a lista da outra pessoa nao mostra nada dele.
 *
 * <p><b>Nao muda o status de item nenhum.</b> E a diferenca inteira entre este
 * carrinho e o botao "comprei" que a ADR-0040 recusou: o item continua pendente,
 * e por isso continua ao alcance de {@code checkout} -- o elo nao se desliga no
 * corredor do mercado.
 */
@ApplicationScoped
public class ChatCartService {

    @Inject
    ChatCartItemRepository cart;

    @Inject
    ShoppingService shopping;

    @Inject
    Clock clock;

    /**
     * Quanto tempo o item fica no carrinho. Config global, provisoria e
     * explicita, nunca constante no codigo: 3 horas e chute sem base, a calibrar
     * com uso real (decisao aberta #28). Avaliada na leitura, sem job -- item mais
     * velho que o prazo so deixa de aparecer.
     */
    @ConfigProperty(name = "novoapp.conversation.cart.ttl")
    Duration timeToLive;

    @Transactional
    @HouseholdScoped
    public void put(ResolvedContext context, UUID listItemId) {
        Instant now = Instant.now(clock);
        cart.put(context.householdId(), context.memberId(), listItemId, now, now.minus(timeToLive));
        // Flush dentro do escopo: no commit o SET LOCAL ROLE ja teria voltado pro
        // papel de fora, que nao tem permissao nesta tabela.
        cart.flush();
    }

    @Transactional
    @HouseholdScoped
    public void take(ResolvedContext context, UUID listItemId) {
        cart.take(context.memberId(), listItemId);
        cart.flush();
    }

    /**
     * Os ids no carrinho, dentro do prazo. <b>Nao</b> cruzado com o status: quem
     * desenha a lista ja tem os pendentes na mao e cruza la, sem segunda leitura.
     */
    @Transactional
    @HouseholdScoped
    public Set<UUID> itemIds(ResolvedContext context) {
        return new LinkedHashSet<>(cart.listFresh(context.memberId(), Instant.now(clock).minus(timeToLive)));
    }

    /**
     * O carrinho como a pessoa o enxerga: o que ela separou e ainda esta
     * pendente, na ordem da lista. O que outro membro fechou ou removeu nesse
     * meio tempo nao aparece -- ninguem precisou limpa-lo.
     */
    @Transactional
    @HouseholdScoped
    public List<ListItemView> pendingItems(ResolvedContext context) {
        Set<UUID> ids = itemIds(context);
        return shopping.pendingItems(context.householdId()).stream()
                .filter(item -> ids.contains(item.id()))
                .toList();
    }

    /**
     * Depois de um fechamento: sai do carrinho de quem fechou o que deixou de
     * estar pendente. Pelo carrinho isso e tudo, e ele fica vazio; o que ainda
     * esta pendente -- separado depois do toque em fechar, ou fora de um
     * fechamento parcial por texto -- continua separado.
     */
    @Transactional
    @HouseholdScoped
    public void dropClosed(ResolvedContext context) {
        Set<UUID> stillPending = new LinkedHashSet<>();
        shopping.pendingItems(context.householdId()).forEach(item -> stillPending.add(item.id()));
        cart.keepOnly(context.memberId(), stillPending);
        cart.flush();
    }
}
