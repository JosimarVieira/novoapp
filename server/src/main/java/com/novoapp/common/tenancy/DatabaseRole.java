package com.novoapp.common.tenancy;

/**
 * Os dois papeis de banco em que a aplicacao roda (ADR-0003, ADR-0022).
 *
 * <p>O papel de login (<code>novoapp_runtime</code>) e NOINHERIT: sozinho ele
 * nao tem privilegio nenhum. Todo acesso a dado passa obrigatoriamente por um
 * <code>SET LOCAL ROLE</code> para um destes dois, aplicado pelos interceptores
 * deste pacote. Esquecer a anotacao nao vaza dado -- estoura permissao negada.
 */
public enum DatabaseRole {

    /** Dominio: enxerga so o household do contexto atual. */
    APP("novoapp_app"),

    /**
     * Pre-tenant: resolucao de identidade e ingestao de mensagem. Precisa
     * enxergar linha antes de existir household resolvido -- e justamente o que
     * este caminho vai descobrir.
     */
    IDENTITY("novoapp_identity");

    private final String sqlName;

    DatabaseRole(String sqlName) {
        this.sqlName = sqlName;
    }

    /**
     * Nome do papel no Postgres. Constante do enum, nunca vem de entrada do
     * usuario -- e o que permite interpola-lo no SQL, ja que identificador nao
     * aceita parametro de bind.
     */
    public String sqlName() {
        return sqlName;
    }
}
