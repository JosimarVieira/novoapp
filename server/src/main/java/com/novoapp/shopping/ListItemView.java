package com.novoapp.shopping;

import java.math.BigDecimal;

/** Item da lista como <code>conversation</code> e <code>nlu</code> o enxergam. */
public record ListItemView(String name, BigDecimal quantity, String unit) {
}
