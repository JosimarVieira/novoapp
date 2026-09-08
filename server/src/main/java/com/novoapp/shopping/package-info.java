/**
 * Lista de compras e itens -- o que a familia esta precisando comprar
 * (sdd-modulo-shopping.md, ROADMAP Etapa 2a).
 *
 * <p>O elo com <code>finance</code> (fechar a compra e gerar o lancamento) e
 * Etapa 3 e ainda nao existe aqui. A regra de dependencia ja esta travada por
 * ArchUnit: <code>shopping</code> pode depender de <code>finance</code> (o elo e
 * dirigido nesse sentido), nunca o contrario, e cria lancamento <em>atraves</em>
 * de <code>finance</code>, nunca escrevendo em <code>transaction</code>.
 */
package com.novoapp.shopping;
