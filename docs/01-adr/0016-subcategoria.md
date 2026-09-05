---
tipo: adr
numero: 16
status: aceita
data: 2026-09-04
modulos:
  - banco
  - finance
  - glossario
depende_de:
  - ADR-0003
supera: []
superada_por:
---

# ADR-0016 — Subcategoria como categoria com um nível de hierarquia

- **Impacta**: banco (`category`), `finance`, glossário; encerra a
  decisão aberta sobre subcategoria (registrada e removida do
  `DECISOES-ABERTAS.md` em 2026-09-04 — número #18 já reciclado por outro
  item nesse arquivo, não usar como referência); depende de [ADR-0003](0003-isolamento-multi-tenant-por-household.md)

## Contexto

O legado (`legacy/juntosnocontrole/server`) tem `Subcategory` como entidade
própria, um nível abaixo de `Category`, usada e validada em produção
anterior. O modelo novo ([modelo-de-dados.md](../02-arquitetura/modelo-de-dados.md)) só tinha `category` plana — a
funcionalidade ficou de fora da reescrita ([ADR-0010](0010-reescrever-modulo-financeiro-em-vez-de-reaproveitar-legado.md)) sem decisão, registrada
como decisão aberta em `DECISOES-ABERTAS.md` (já removida de lá, resolvida
por esta ADR). O autor confirmou em 2026-09-04: subcategoria precisa
estar na modelagem, não é opcional.

## Decisão

`category` ganha `parent_category_id` (nullable, auto-relacionamento à
própria tabela). Não existe tabela `subcategory` separada — subcategoria é
`category` com pai.

**Suposição registrada, a corrigir se estiver errada**: só um nível
(subcategoria não tem filha) — é o que o legado tinha e validou, ir além
disso é escopo não pedido. Enforcement é na camada de serviço (`finance`),
não no banco: um `CHECK` de auto-relacionamento não impede cadeia de 3
níveis sozinho; é regra de negócio, com teste de unidade cobrindo "criar
subcategoria de subcategoria" como erro.

Subcategoria herda o `kind` (`EXPENSE`/`INCOME`) do pai — não existe
subcategoria de despesa dentro de categoria de receita.

A unicidade de nome deixa de ser por household inteiro e passa a ser por
irmãos: `UNIQUE (household_id, parent_category_id, lower(name), kind)` (com
`parent_category_id` tratando `NULL` como valor de agrupamento — dois
categorias raiz não podem ter o mesmo nome, mas duas subcategorias de pais
diferentes podem, ex. "Presente" dentro de "Educação" e dentro de "Lazer").

## Alternativas consideradas

### A. Tabela própria `subcategory`, espelhando o legado
Descartada: duplicaria RLS, unicidade, `archived_at` e toda a infraestrutura
que `category` já tem, para uma entidade que é conceitualmente "categoria
com pai" — sem campo que `category` não tenha. Espelhar a tabela do legado
aqui repetiria o erro que [ADR-0010](0010-reescrever-modulo-financeiro-em-vez-de-reaproveitar-legado.md) evitou (portar schema, não conhecimento).

### B. Hierarquia de profundidade arbitrária (N níveis)
Descartada: o legado nunca precisou de mais de um nível, e nada na validação
pede isso agora. Profundidade arbitrária custa mais em validação de ciclo,
UI de seleção e query recursiva do que qualquer caso de uso real hoje
sustenta — mesmo racional de "não inventar agora" das decisões abertas #7/#8.

## Consequências

### Positivas
- Reaproveita toda a infraestrutura de `category` (RLS, `archived_at`,
  vínculo com `transaction`) sem tabela nova.
- Unicidade por irmãos, não por household inteiro, permite reuso de nome em
  ramos diferentes sem gambiarra.

### Negativas
- Limite de um nível não é garantido pelo banco — depende de validação na
  camada de serviço. Se essa validação faltar num caminho novo (ex. import
  em lote futuro), nada impede uma cadeia mais profunda silenciosamente.
- `transaction.category_id` pode apontar tanto para categoria raiz quanto
  para subcategoria — relatório que soma "por categoria" precisa decidir se
  agrega subcategoria dentro do pai ou trata como categoria separada; esta
  ADR não decide isso (é UX de relatório, não schema).

## Gatilhos de revisão

Se a UI de seleção de categoria (Etapa 4, PWA) mostrar que ninguém usa mais
de um nível na prática, avaliar se a complexidade de `parent_category_id`
compensa manter.
