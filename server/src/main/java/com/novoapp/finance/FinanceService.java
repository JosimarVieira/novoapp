package com.novoapp.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novoapp.common.tenancy.HouseholdScoped;
import com.novoapp.finance.entity.Account;
import com.novoapp.finance.entity.Category;
import com.novoapp.finance.entity.EntryKind;
import com.novoapp.finance.entity.Transaction;
import com.novoapp.finance.entity.TransactionEdit;
import com.novoapp.finance.entity.TransactionSource;
import com.novoapp.finance.repository.CategoryRepository;
import com.novoapp.finance.repository.TransactionEditRepository;
import com.novoapp.finance.repository.TransactionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lancamentos, categorias, contas, estorno (sdd-modulo-finance.md).
 *
 * <p>Nao fala com canal nenhum: e chamado por <code>conversation</code> e, da
 * Etapa 4 em diante, pelo REST do Vue -- a mesma camada de servico, regra 4 do
 * CLAUDE.md.
 *
 * <p>Correcao de campo com historico (ADR-0012, <code>transaction_edit</code>)
 * existe para um campo so, a categoria, trocada pelo recibo (ADR-0041); valor e
 * descricao entram com a tela de correcao da Etapa 4. Cartao/fatura (ADR-0011) e
 * metas (ADR-0017) continuam fora: nenhum cenario os exercita.
 */
@ApplicationScoped
public class FinanceService {

    @Inject
    AccountResolver accountResolver;

    @Inject
    CategoryRepository categories;

    @Inject
    TransactionRepository transactions;

    @Inject
    TransactionEditRepository edits;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Clock clock;

    /**
     * O anuncio do estorno (ADR-0032). Sincrono e na mesma transacao -- o
     * observador que devolve os itens da lista a <code>PENDING</code> mora em
     * <code>shopping</code>, porque a direcao de dependencia so permite esse
     * sentido.
     */
    @Inject
    Event<ExpenseReversed> expenseReversed;

    /**
     * Registra uma despesa ja interpretada.
     *
     * <p>O nome do metodo e ingles, e nao <code>registrarDespesa</code> como o
     * SDD escreveu: identificador em ingles e regra sem excecao no CLAUDE.md --
     * o portugues fica no Gherkin, na ADR e nos comentarios.
     *
     * @param description o residuo semantico da mensagem (ADR-0023). Nulo e o
     *        caso normal, nunca um erro: descricao ausente jamais vira pergunta
     * @param sourceMessageId mensagem que originou o lancamento -- e o que torna
     *        a Etapa 5 mensuravel
     */
    @Transactional
    @HouseholdScoped
    public RegisteredExpense registerExpense(UUID householdId,
                                             UUID memberId,
                                             UUID categoryId,
                                             long amountCents,
                                             String description,
                                             UUID sourceMessageId) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Valor de despesa precisa ser positivo: " + amountCents);
        }

        Category category = categories.findById(categoryId);
        if (category == null) {
            // RLS ja garante que so categoria do household atual aparece aqui:
            // nulo significa "nao existe neste household", nao "sem permissao".
            throw new IllegalArgumentException("Categoria inexistente neste household: " + categoryId);
        }

        Account account = accountResolver.resolveDefault(householdId, memberId);

        Transaction transaction = new Transaction();
        transaction.householdId = householdId;
        transaction.accountId = account.id;
        transaction.categoryId = category.id;
        transaction.kind = EntryKind.EXPENSE;
        transaction.amountCents = amountCents;
        transaction.occurredOn = LocalDate.now(clock);
        transaction.description = blankToNull(description);
        transaction.createdByMemberId = memberId;
        transaction.source = TransactionSource.CHAT;
        transaction.sourceMessageId = sourceMessageId;
        transactions.persist(transaction);
        // Flush dentro do escopo: se o INSERT so acontecesse no commit, ele
        // rodaria depois do SET LOCAL ROLE ja ter voltado, e o papel de fora
        // nao tem permissao nesta tabela.
        transactions.flush();

        return new RegisteredExpense(transaction.id, transaction.amountCents, displayNameOf(category),
                account.name, transaction.description, transaction.occurredOn);
    }

    /**
     * Estorna o lancamento alvo do <code>desfazer</code> (ADR-0025): o lancamento
     * mais recente do <b>household inteiro</b> ainda nao estornado, de qualquer
     * membro (ADR-0012), sem janela de tempo.
     *
     * <p>Marca <code>reversed_at</code> em vez de apagar. Historico auditavel
     * importa em financas compartilhadas -- quando duas pessoas mexem no mesmo
     * dado, "sumiu" e pior que "foi estornado por fulano" (modelo-de-dados.md).
     *
     * <p>Publica {@link ExpenseReversed} <b>dentro da propria transacao</b>
     * (ADR-0032). Este modulo nao sabe -- e nao pode saber -- que existem
     * fechamentos de compra: quem descobre que este lancamento veio de um
     * <code>list_checkout</code> e devolve os itens a <code>PENDING</code> e o
     * observador em <code>shopping</code>. Se ele falhar, o estorno cai junto,
     * que e o ponto.
     *
     * <p>O alvo e lido sem trava. Se ele for estornado por outro caminho entre a
     * leitura e a escrita -- o botao do recibo (ADR-0041), o outro telefone --, o
     * carimbo condicional nao acontece e o resultado e vazio. Nao procura o
     * proximo: quem digitou queria desfazer o que via como o mais recente, e esse
     * ja foi desfeito.
     *
     * @return vazio quando nao ha nada a estornar
     */
    @Transactional
    @HouseholdScoped
    public Optional<ReversedExpense> reverseLatest(UUID householdId, UUID memberId) {
        return transactions.findLatestNotReversed()
                .flatMap(transaction -> stampReversal(transaction, householdId, memberId));
    }

    /**
     * Estorna o lancamento <b>determinado</b> -- o do recibo onde estava o botao
     * (ADR-0041, item 2) --, e nao o mais recente do household. Irma de
     * {@link #reverseLatest}, e nao copia: o carimbo e o
     * {@link ExpenseReversed} sao os mesmos, e por isso o fechamento reabre pelo
     * observador que ja existe em <code>shopping</code> (ADR-0032), sem linha
     * nova la.
     *
     * <p>Le o lancamento com trava de escrita. Cada mensagem e processada na
     * propria thread, entao dois toques no mesmo botao podem chegar juntos: o
     * segundo espera o primeiro terminar e o encontra ja estornado. A trava so
     * serializa quem tambem trava -- contra o <code>desfazer</code> digitado, que
     * nao trava, quem protege e o carimbo condicional.
     *
     * @return vazio quando o lancamento nao existe neste household -- inclusive o
     *         de outro household, que a RLS esconde: o id vem do botao, e nao e
     *         confiavel -- ou ja foi estornado, por qualquer caminho. Nao e erro:
     *         quem chama redesenha o recibo como estornado
     */
    @Transactional
    @HouseholdScoped
    public Optional<ReversedExpense> reverse(UUID householdId, UUID memberId, UUID transactionId) {
        return Optional.ofNullable(transactions.findById(transactionId, LockModeType.PESSIMISTIC_WRITE))
                .filter(transaction -> transaction.reversedAt == null)
                .flatMap(transaction -> stampReversal(transaction, householdId, memberId));
    }

    /**
     * Troca a categoria de um lancamento pelo recibo (ADR-0041, item 4), com uma
     * linha no historico de edicao: quem trocou, quando, e de qual para qual
     * (ADR-0012). Qualquer membro troca lancamento de qualquer outro do mesmo
     * household, como no estorno.
     *
     * <p>A ordem das checagens e a do que o botao carrega. Primeiro o lancamento,
     * lido com trava de escrita: dois toques concorrentes em categorias
     * diferentes gravariam, sem ela, duas edicoes "de Farmacia" -- e a segunda
     * mentiria sobre de onde saiu. Lancamento inexistente ou estornado decide o
     * desfecho antes de a categoria importar. So entao a categoria, que tambem
     * vem do cliente: tem de ser de despesa, do household (a RLS esconde a de
     * outro, e a FK do banco nao segura isso -- checagem de FK ignora RLS) e em
     * uso.
     *
     * <p>Escolher a categoria que o lancamento ja tem nao grava nada: nao houve
     * correcao.
     */
    @Transactional
    @HouseholdScoped
    public CategoryChange changeCategory(UUID householdId, UUID memberId, UUID transactionId, UUID categoryId) {
        Transaction transaction = transactions.findById(transactionId, LockModeType.PESSIMISTIC_WRITE);
        if (transaction == null) {
            return new CategoryChange.NotFound();
        }
        if (transaction.reversedAt != null) {
            return new CategoryChange.AlreadyReversed(viewOf(transaction));
        }
        Category category = categories.findById(categoryId);
        if (category == null || category.kind != EntryKind.EXPENSE || category.archivedAt != null) {
            return new CategoryChange.CategoryUnavailable(viewOf(transaction));
        }
        if (category.id.equals(transaction.categoryId)) {
            return new CategoryChange.Unchanged(viewOf(transaction));
        }

        TransactionEdit edit = new TransactionEdit();
        edit.householdId = householdId;
        edit.transactionId = transaction.id;
        edit.editedByMemberId = memberId;
        edit.editedAt = Instant.now(clock);
        edit.changedFieldsJson = changedField("category_id", transaction.categoryId, category.id);
        transaction.categoryId = category.id;
        edits.persist(edit);
        // Flush dentro do escopo, pelo mesmo motivo de registerExpense: no commit
        // o SET LOCAL ROLE ja voltou, e o papel de fora nao escreve aqui.
        transactions.flush();

        return new CategoryChange.Changed(viewOf(transaction));
    }

    /**
     * O lancamento como o recibo o mostra (ADR-0041) -- vazio quando ele nao
     * existe neste household. Estornado continua aparecendo, marcado: o recibo
     * de um lancamento estornado e redesenhado como estornado, e nao some.
     */
    @Transactional
    @HouseholdScoped
    public Optional<ExpenseView> expense(UUID transactionId) {
        return Optional.ofNullable(transactions.findById(transactionId)).map(this::viewOf);
    }

    /**
     * A categoria de um lancamento que continua de pe -- vazio quando ele foi
     * estornado ou nao existe neste household.
     *
     * <p>Leitura para o fechamento pelo botao (ADR-0040): <code>shopping</code>
     * sabe qual lancamento cada fechamento gerou, e so este modulo sabe se ele
     * foi estornado e em que categoria caiu. Devolve {@link CategoryView}, e nao
     * a entidade, pelo mesmo motivo que <code>nlu</code> a recebe assim.
     */
    @Transactional
    @HouseholdScoped
    public Optional<CategoryView> categoryOfStandingExpense(UUID transactionId) {
        Transaction transaction = transactions.findById(transactionId);
        if (transaction == null || transaction.reversedAt != null) {
            return Optional.empty();
        }
        Category category = categories.findById(transaction.categoryId);
        if (category == null) {
            return Optional.empty();
        }
        Category parent = category.parentCategoryId == null ? null : categories.findById(category.parentCategoryId);
        return Optional.of(new CategoryView(category.id, category.name, category.parentCategoryId,
                parent == null ? null : parent.name));
    }

    /**
     * O estorno em si, comum aos dois alcances do <code>desfazer</code> -- o
     * digitado (ADR-0025) e o do botao (ADR-0041). Marca <code>reversed_at</code>
     * em vez de apagar, e publica {@link ExpenseReversed} dentro da transacao de
     * quem chamou.
     *
     * <p><b>Um UPDATE condicional, e so nas duas colunas do estorno</b> -- nunca
     * a linha inteira pelo estado da entidade. O desfazer digitado le o alvo sem
     * trava; regravando a linha inteira, um estorno concorrente com a troca de
     * categoria pelo botao devolveria a categoria antiga em silencio, e um
     * concorrente com o desfazer pelo botao carimbaria duas vezes e anunciaria o
     * estorno duas vezes. Quem decide se o estorno aconteceu e o banco, como na
     * idempotencia da ADR-0005.
     *
     * @return vazio quando o lancamento ja estava estornado no momento da escrita
     */
    private Optional<ReversedExpense> stampReversal(Transaction transaction, UUID householdId, UUID memberId) {
        int stamped = transactions.update("reversedAt = ?1, reversedByMemberId = ?2 where id = ?3 and reversedAt is null",
                Instant.now(clock), memberId, transaction.id);
        if (stamped == 0) {
            return Optional.empty();
        }

        expenseReversed.fire(new ExpenseReversed(transaction.id, householdId));

        // O UPDATE nao passou pela entidade: relida, ela traz a categoria que o
        // lancamento tem agora, inclusive a que uma troca concorrente acabou de
        // gravar -- e com essa que ele sai de cena (ADR-0041, item 5).
        transactions.getEntityManager().refresh(transaction);
        Category category = categories.findById(transaction.categoryId);
        return Optional.of(new ReversedExpense(transaction.id, transaction.amountCents,
                category == null ? null : displayNameOf(category), transaction.createdByMemberId));
    }

    private ExpenseView viewOf(Transaction transaction) {
        Category category = categories.findById(transaction.categoryId);
        return new ExpenseView(transaction.id, transaction.amountCents, transaction.categoryId,
                category == null ? null : displayNameOf(category), transaction.description,
                transaction.occurredOn, transaction.createdAt, transaction.createdByMemberId,
                transaction.reversedAt, transaction.reversedByMemberId);
    }

    /**
     * O diff da ADR-0012: {@code {"campo": [de, para]}}. Sem schema no banco, de
     * proposito; e aqui que ele tem de sair coerente. Falhar aqui derruba a troca
     * inteira -- correcao sem historico nao existe.
     */
    private String changedField(String field, UUID from, UUID to) {
        try {
            return objectMapper.writeValueAsString(Map.of(field, List.of(from.toString(), to.toString())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nao foi possivel montar o historico de edicao de " + field, e);
        }
    }

    /** Formato que a ADR-0026 fixou pro recibo quando a categoria tem pai. */
    private String displayNameOf(Category category) {
        if (category.parentCategoryId == null) {
            return category.name;
        }
        Category parent = categories.findById(category.parentCategoryId);
        return parent == null ? category.name : "%s (dentro de %s)".formatted(category.name, parent.name);
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
