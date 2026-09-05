---
tipo: sdd
modulo: tenancy
status: escrito
atualizado_em: 2026-09-05
adrs:
  - ADR-0003
  - ADR-0022
---

# SDD — Pacote técnico `common/tenancy`

Não é um dos sete módulos de domínio do `sdd-visao-geral.md`. É a infraestrutura
que aplica o isolamento multi-tenant na conexão, e por isso todo módulo de
domínio depende dela sem que ela dependa de nenhum.

## Responsabilidade

Traduzir "esta mensagem é do household X" em estado de sessão de banco: qual
papel ([ADR-0022](../01-adr/0022-papel-de-banco-pre-tenant-para-identidade.md)) e qual `app.household_id` ([ADR-0003](../01-adr/0003-isolamento-multi-tenant-por-household.md)) valem dentro da
transação atual.

**Por que aqui e não em `identity`**: decisão tomada ao escrever a Etapa 1, e que
o `sdd-modulo-identity.md` registrava como "não decidido" nos gatilhos de
revisão. `identity` resolve *qual* é o household — isso é domínio. Aplicar o
resultado na conexão é infraestrutura, e `finance`, `shopping` e `tasks`
precisam dela sem terem nada a ver com identidade. Pendurar o interceptor em
`identity` daria a ele um papel que o `sdd-visao-geral.md` não lhe atribui.

## Não faz

- Não descobre o household. Recebe-o pronto de quem resolveu a identidade.
- Não infere tenant a partir de argumento de método. Inferência mágica de tenant
  é exatamente o tipo de coisa de que a [ADR-0003](../01-adr/0003-isolamento-multi-tenant-por-household.md) existe para não depender.
- Não conhece canal, domínio nem LLM.

## Depende de

Nada de `com.novoapp`. Barrado por ArchUnit (`commonDependsOnNoModule`): se
`common` depender de domínio, vira dependência circular disfarçada.

## Estrutura

```
common/tenancy/
  TenantContext              -- qual household está sendo atendido nesta thread
  DatabaseRole               -- APP (domínio) | IDENTITY (pré-tenant)
  HouseholdScoped            -- anotação: roda sob o papel de domínio
  IdentityScoped             -- anotação: roda sob o papel pré-tenant
  HouseholdScopedInterceptor -- @Priority(PLATFORM_BEFORE + 300)
  IdentityScopedInterceptor  -- idem
  TenantSession              -- o único lugar que emite SET LOCAL
```

## Contrato

1. **Todo acesso a dado passa por um dos dois escopos.** O papel de login
   (`novoapp_runtime`) é `NOINHERIT`: sem `SET LOCAL ROLE` não há privilégio
   nenhum, então esquecer a anotação estoura permissão negada em vez de vazar.
2. **Escopo exige transação ativa.** `SET LOCAL` fora de transação não tem efeito
   e faria toda policy negar em silêncio. O interceptor confere o estado do
   `TransactionManager` e falha alto.
3. **Prioridade `PLATFORM_BEFORE + 300`**, portanto *dentro* do interceptor de
   `@Transactional` (`+ 200`): a transação já precisa estar aberta quando o
   `SET LOCAL` sai.
4. **Os escopos aninham e se restauram.** O onboarding roda sob o papel
   pré-tenant e, dentro da mesma transação, publica `HouseholdCreated`, que faz
   `finance` criar a conta `WALLET` sob o papel de domínio. Na saída do escopo
   interno, o externo é reaplicado. Fora de todo escopo, `SET LOCAL ROLE NONE`.
5. **Todo escrita dentro de um escopo dá `flush()` antes de sair.** Se o `INSERT`
   ficasse para o commit, rodaria depois do papel já ter voltado — e o papel de
   fora não tem permissão na tabela. Não é detalhe de performance, é correção.

## Testes

- `TenantIsolationTest` — dois households simultâneos; nenhuma operação de um
  alcança dado do outro. É o teste mais importante do projeto
  (`estrategia-de-testes.md`), e cobre também os dois modos de falha desenhados:
  sem escopo não lê nada, e o papel de domínio não alcança tabela pré-tenant.
- ArchUnit `onlyChannelAndIdentityUseThePreTenantRole` — `finance`, `shopping`,
  `tasks`, `nlu` e `conversation` não podem sequer referenciar `@IdentityScoped`.

## Gatilhos de revisão

- Job em background (Etapa 8, proatividade) não tem mensagem de origem para
  resolver tenant: precisará estabelecer o contexto explicitamente, e isso é uma
  entrada nova neste pacote — a [ADR-0003](../01-adr/0003-isolamento-multi-tenant-por-household.md) já antecipa o problema.
- Se aparecer necessidade de ler duas famílias na mesma transação (relatório
  cross-household), este desenho não cobre: hoje é um household por transação,
  de propósito.
