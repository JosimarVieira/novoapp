package com.novoapp.finance;

import java.util.UUID;

/**
 * Categoria como <code>nlu</code> a enxerga.
 *
 * <p>Existe pra que <code>nlu</code> monte o enum da tool de function calling
 * (ADR-0004) sem receber a entidade JPA: leitura de categoria e a unica coisa
 * que <code>nlu</code> pode fazer em <code>finance</code>, e um record fechado
 * torna impossivel escrever por acidente.
 */
public record CategoryView(UUID id, String name) {
}
