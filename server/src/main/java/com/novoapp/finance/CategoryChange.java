package com.novoapp.finance;

/**
 * Desfecho de {@link FinanceService#changeCategory} (ADR-0041).
 *
 * <p>Selado, pelo mesmo motivo de {@link CategoryCreation}: lancamento ja
 * estornado nao e erro, e cenario -- vira aviso e redesenho em
 * <code>conversation</code>, que escolhe o texto pelo tipo, sem inspecionar
 * mensagem de excecao. Os tres desfechos com lancamento carregam como ele ficou,
 * para o recibo ser redesenhado a partir do banco.
 */
public sealed interface CategoryChange {

    /** Trocou, e a troca ficou no historico de edicao (ADR-0012). */
    record Changed(ExpenseView expense) implements CategoryChange {
    }

    /** O lancamento ja estava nessa categoria: nada mudou, e nada foi gravado. */
    record Unchanged(ExpenseView expense) implements CategoryChange {
    }

    /**
     * Estornado, por qualquer caminho. Nao se corrige lancamento estornado: ele
     * fica no historico como estava quando saiu de cena.
     */
    record AlreadyReversed(ExpenseView expense) implements CategoryChange {
    }

    /**
     * A categoria do botao nao esta no teclado de agora: de outro household (a
     * RLS a esconde), de receita, ou arquivada depois que o teclado foi desenhado.
     * Nada muda. Nao e erro: o teclado fica no chat para sempre, e a resposta e
     * redesenha-lo como ele e hoje.
     */
    record CategoryUnavailable(ExpenseView expense) implements CategoryChange {
    }

    /** O lancamento nao existe neste household -- inclusive o de outro, que a RLS esconde. */
    record NotFound() implements CategoryChange {
    }
}
