---
description: Escreve ou revisa um arquivo .feature em Gherkin
---

Escreva a especificação de comportamento para: $ARGUMENTS

Passos:
1. Leia `docs/00-produto/glossario.md` e use exclusivamente os termos de lá.
   Termo novo entra no glossário primeiro.
2. Leia as features existentes em `docs/03-specs/features/` para manter estilo
   e evitar duplicar cenário já coberto.
3. Escreva em `docs/03-specs/features/<dominio>-<assunto>.feature`, em
   português (`# language: pt`).

Regras de qualidade:
- Cenário descreve **comportamento observável pelo usuário**, nunca chamada de
  método, tabela ou classe. Se aparece nome de tabela no Gherkin, está errado.
- Todo caminho feliz precisa de pelo menos um cenário de **ambiguidade** e um
  de **erro** ao lado. Feature só com caminho feliz será rejeitada.
- Use `Esquema do Cenário` quando houver 3+ variações da mesma regra.
- Valores monetários em reais explícitos (`R$ 50,00`); datas sempre relativas
  e ancoradas (`hoje`, `ontem`), nunca absolutas.
