package com.novoapp.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

/**
 * Relogio injetavel. "Data de hoje" e regra observavel no Gherkin
 * (`financas-lancamento-por-chat.feature`), entao precisa ser controlavel no
 * teste em vez de vir de {@code LocalDate.now()} espalhado pelo codigo.
 */
@ApplicationScoped
public class ClockProducer {

    @Produces
    @ApplicationScoped
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}
