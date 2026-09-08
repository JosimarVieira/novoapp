package com.novoapp.finance;

import java.util.UUID;

/**
 * Categoria como <code>nlu</code> a enxerga.
 *
 * <p>Existe pra que <code>nlu</code> monte o enum da tool de function calling
 * (ADR-0004) sem receber a entidade JPA: leitura de categoria e a unica coisa
 * que <code>nlu</code> pode fazer em <code>finance</code>, e um record fechado
 * torna impossivel escrever por acidente.
 *
 * <p>Ganhou a hierarquia na Etapa 2a. A ADR-0026 aponta a ausencia dela como o
 * furo que impedia o chat de criar subcategoria: sem saber quem e raiz e quem e
 * filha, nem o contexto do modelo nem a validacao de um nivel (ADR-0016) tinham
 * como existir.
 *
 * @param parentId   nulo quando a categoria e raiz
 * @param parentName nulo quando a categoria e raiz
 */
public record CategoryView(UUID id, String name, UUID parentId, String parentName) {

    public static CategoryView root(UUID id, String name) {
        return new CategoryView(id, name, null, null);
    }

    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * Como a categoria aparece pro usuario quando o pai importa: o formato que a
     * ADR-0026 escreveu pro recibo ("Restaurantes (dentro de Alimentacao)").
     */
    public String displayName() {
        return isRoot() ? name : "%s (dentro de %s)".formatted(name, parentName);
    }
}
