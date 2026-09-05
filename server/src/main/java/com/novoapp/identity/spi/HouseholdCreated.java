package com.novoapp.identity.spi;

import java.util.UUID;

/**
 * Evento CDI publicado quando um household nasce.
 *
 * <p>Existe pra resolver uma direcao de dependencia, nao por gosto de eventos:
 * o household novo precisa ganhar uma conta WALLET implicita (ADR-0011), mas
 * <code>identity</code> nao pode chamar <code>finance</code> -- seria ciclo
 * contra a regra do <code>sdd-visao-geral.md</code>. Entao <code>identity</code>
 * anuncia e <code>finance</code> observa. Decisao registrada em
 * <code>sdd-modulo-identity.md</code>.
 *
 * <p>O observador roda na mesma transacao: household sem conta seria um estado
 * que nenhum codigo daqui pra frente sabe tratar.
 */
public record HouseholdCreated(UUID householdId) {
}
