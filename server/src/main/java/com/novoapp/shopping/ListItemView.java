package com.novoapp.shopping;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item da lista como <code>conversation</code> e <code>nlu</code> o enxergam.
 *
 * @param id carregado desde a ADR-0040: a lista que vira botoes precisa dele,
 *        porque o botao leva o id do item, e nao o nome. A lista que vira texto
 *        simplesmente o ignora
 */
public record ListItemView(UUID id, String name, BigDecimal quantity, String unit) {
}
