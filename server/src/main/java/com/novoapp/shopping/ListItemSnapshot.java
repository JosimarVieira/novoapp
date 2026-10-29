package com.novoapp.shopping;

import java.util.UUID;

/**
 * Um item por id, com o nome atual e se ainda esta pendente (ADR-0040).
 *
 * <p>So leitura, de proposito. O botao carrega o id; <code>conversation</code>
 * precisa do nome para chamar {@link ShoppingService#removeItems} e
 * {@link ShoppingService#addItems} -- os mesmos metodos que o texto chama -- e
 * do status para saber se o clique ainda vale. Escrita por id nao existe e nao
 * deve existir: seria um segundo caminho para a mesma regra
 * (sdd-modulo-shopping.md).
 *
 * @param pending falso para comprado e para removido: nos dois casos, para quem
 *        tocou, o item "ja saiu da lista"
 */
public record ListItemSnapshot(UUID id, String name, boolean pending) {
}
