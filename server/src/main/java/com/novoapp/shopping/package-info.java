/**
 * Lista de compras, itens e fechamento de compra -- o elo com
 * <code>finance</code> (ROADMAP Etapas 2 e 3).
 *
 * <p>Vazio de proposito nesta etapa. O pacote existe desde ja pra que a regra de
 * dependencia esteja travada por ArchUnit antes de haver codigo pra violar:
 * <code>shopping</code> pode depender de <code>finance</code> (o elo e dirigido
 * nesse sentido), nunca o contrario, e cria lancamento <em>atraves</em> de
 * <code>finance</code>, nunca escrevendo em <code>transaction</code>.
 */
package com.novoapp.shopping;
