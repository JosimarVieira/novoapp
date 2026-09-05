---
description: Escreve ou atualiza o Software Design Document de um módulo
---

Escreva o SDD do módulo: $ARGUMENTS

Antes de escrever, leia nesta ordem: `CLAUDE.md`, as ADRs aceitas
(`status: aceita` no frontmatter — filtre por isso, não leia as 20),
`docs/02-arquitetura/modelo-de-dados.md` e as features do domínio em
`docs/03-specs/features/`.

O arquivo abre com frontmatter, no mesmo contrato das ADRs — sem ele o SDD
fica fora de `docs/sdd.base` e volta a ser rastreado por tabela manual:

```yaml
---
tipo: sdd
modulo: channel          # um dos pacotes do CLAUDE.md, ou `geral`
status: rascunho         # rascunho | escrito | desatualizado
atualizado_em: AAAA-MM-DD
adrs: [ADR-0001]         # as ADRs que sustentam este design
---
```

`adrs` não é enfeite: é o lastro. SDD que não consegue listar nenhuma ADR é
design sem decisão por trás — pare e escreva a ADR primeiro.

Estrutura obrigatória:
1. **Responsabilidade** — uma frase. Se precisar de duas, o módulo faz coisa
   demais.
2. **Fronteiras** — o que este módulo NÃO faz e quem faz.
3. **Contratos** — interfaces públicas com assinatura, e eventos publicados
   ou consumidos.
4. **Fluxos principais** — passo a passo, com o caminho de erro ao lado.
5. **Estado e persistência** — tabelas tocadas, transacionalidade.
6. **Falhas** — comportamento em timeout, reentrega, indisponibilidade do LLM
   ou do provedor de canal. Esta seção não é opcional.
7. **Pontos em aberto** — replicados em `docs/DECISOES-ABERTAS.md`.

Não escreva SDD de módulo fora das Etapas 1 a 3 do `ROADMAP.md`. Se eu pedir,
avise que é cedo e pergunte se quero prosseguir mesmo assim.
