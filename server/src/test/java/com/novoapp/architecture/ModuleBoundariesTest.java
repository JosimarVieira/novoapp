package com.novoapp.architecture;

import com.novoapp.common.tenancy.IdentityScoped;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * A regra de dependencia do <code>sdd-visao-geral.md</code> travada no build.
 *
 * <p>"Barrado por teste ArchUnit, nao por revisao de codigo" -- o SDD e explicito
 * que isto nao e sugestao.
 */
@AnalyzeClasses(packages = "com.novoapp", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    private static final String CHANNEL = "com.novoapp.channel..";
    private static final String IDENTITY = "com.novoapp.identity..";
    private static final String NLU = "com.novoapp.nlu..";
    private static final String CONVERSATION = "com.novoapp.conversation..";
    private static final String FINANCE = "com.novoapp.finance..";
    private static final String SHOPPING = "com.novoapp.shopping..";
    private static final String TASKS = "com.novoapp.tasks..";
    private static final String COMMON = "com.novoapp.common..";

    @ArchTest
    static final ArchRule channelIsTheOutermostModule = noClasses()
            .that().resideOutsideOfPackage(CHANNEL)
            .should().dependOnClassesThat().resideInAPackage(CHANNEL)
            .because("so channel conhece o protocolo do provedor; ninguem depende dele (sdd-modulo-channel.md)");

    @ArchTest
    static final ArchRule identityDependsOnNoDomainModule = noClasses()
            .that().resideInAPackage(IDENTITY)
            .should().dependOnClassesThat()
            .resideInAnyPackage(CHANNEL, NLU, CONVERSATION, FINANCE, SHOPPING, TASKS)
            .because("identity e o modulo mais de baixo: so e importado, nunca importa (sdd-modulo-identity.md)");

    @ArchTest
    static final ArchRule nluOnlyReadsFinance = noClasses()
            .that().resideInAPackage(NLU)
            .should().dependOnClassesThat()
            .resideInAnyPackage(CHANNEL, CONVERSATION, IDENTITY, SHOPPING, TASKS)
            .because("nlu so le categoria em finance, e nada mais (sdd-modulo-nlu.md)");

    @ArchTest
    static final ArchRule conversationDoesNotReachTheChannel = noClasses()
            .that().resideInAPackage(CONVERSATION)
            .should().dependOnClassesThat().resideInAnyPackage(CHANNEL, SHOPPING, TASKS)
            .because("conversation orquestra nlu, finance e identity -- nada alem disso (sdd-modulo-conversation.md)");

    @ArchTest
    static final ArchRule financeDependsOnlyOnIdentity = noClasses()
            .that().resideInAPackage(FINANCE)
            .should().dependOnClassesThat()
            .resideInAnyPackage(CHANNEL, NLU, CONVERSATION, SHOPPING, TASKS)
            .because("finance nao fala com canal nenhum e nao conhece mercado nem tarefas (sdd-modulo-finance.md)");

    @ArchTest
    static final ArchRule shoppingReachesFinanceButNotTheOtherWayAround = noClasses()
            .that().resideInAPackage(SHOPPING)
            .should().dependOnClassesThat().resideInAnyPackage(CHANNEL, NLU, CONVERSATION, TASKS)
            .because("o elo e dirigido: shopping pode depender de finance, finance nunca de shopping");

    @ArchTest
    static final ArchRule tasksIsIsolated = noClasses()
            .that().resideInAPackage(TASKS)
            .should().dependOnClassesThat().resideInAnyPackage(CHANNEL, NLU, CONVERSATION, FINANCE, SHOPPING)
            .because("tasks nao tem elo com nenhum outro dominio ate a Etapa 2 provar que precisa");

    @ArchTest
    static final ArchRule commonDependsOnNoModule = noClasses()
            .that().resideInAPackage(COMMON)
            .should().dependOnClassesThat()
            .resideInAnyPackage(CHANNEL, IDENTITY, NLU, CONVERSATION, FINANCE, SHOPPING, TASKS)
            .because("common e infraestrutura compartilhada: se depender de dominio, vira dependencia circular disfarcada");

    /**
     * Regra nao negociavel 5 do CLAUDE.md. Cobre o que o enum
     * {@code identity.Channel} nao cobre: tipo com nome de provedor -- DTO de
     * webhook, cliente de API -- nao pode existir fora de <code>channel</code>.
     */
    @ArchTest
    static final ArchRule noProviderSpecificTypeOutsideChannel = noClasses()
            .that().resideOutsideOfPackage(CHANNEL)
            .should().dependOnClassesThat().haveSimpleNameStartingWith("Telegram")
            .orShould().dependOnClassesThat().haveSimpleNameStartingWith("WhatsApp")
            .because("nenhum codigo abaixo de channel sabe de qual canal a mensagem veio (regra 5 do CLAUDE.md)");

    /**
     * O papel pre-tenant enxerga linha de qualquer household (ADR-0022). Deixar
     * dominio usa-lo por engano dissolveria o isolamento inteiro.
     */
    @ArchTest
    static final ArchRule onlyChannelAndIdentityUseThePreTenantRole = noClasses()
            .that().resideInAnyPackage(FINANCE, SHOPPING, TASKS, NLU, CONVERSATION)
            .should().dependOnClassesThat().areAssignableTo(IdentityScoped.class)
            .because("o escopo pre-tenant e so pra resolucao de identidade e ingestao (ADR-0022)");

    @ArchTest
    static final ArchRule modulesAreFreeOfCycles = slices()
            .matching("com.novoapp.(*)..")
            .should().beFreeOfCycles()
            .because("monolito modular so e modular enquanto as fronteiras sao aciclicas (ADR-0001)");
}
