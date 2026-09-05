package com.novoapp.finance;

import com.novoapp.common.tenancy.TenantContext;
import com.novoapp.identity.spi.HouseholdCreated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Household novo ganha uma conta WALLET implicita (ADR-0011): e o que faz
 * <code>mercado 50</code> funcionar sem o usuario nomear conta nenhuma.
 *
 * <p><code>identity</code> nunca escreve em <code>account</code> -- ele publica
 * {@link HouseholdCreated} e quem escreve e este observador
 * (sdd-modulo-identity.md). O observador e sincrono, na mesma transacao:
 * household sem conta seria um estado que {@link AccountResolver} nao sabe
 * tratar.
 */
@ApplicationScoped
public class HouseholdCreatedListener {

    @Inject
    ImplicitWalletCreator implicitWallet;

    void onHouseholdCreated(@Observes HouseholdCreated event) {
        // O onboarding roda sob o papel pre-tenant e sem household no contexto:
        // este e o instante em que o household passa a existir, entao e aqui que
        // ele entra no contexto pro escopo de dominio conseguir escrever.
        TenantContext.withHousehold(event.householdId(), () -> implicitWallet.create(event.householdId()));
    }
}
