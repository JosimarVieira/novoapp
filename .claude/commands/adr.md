---
description: Cria uma nova ADR a partir de uma decisão em discussão
---

Crie uma ADR em `docs/01-adr/` sobre: $ARGUMENTS

Passos:
1. Leia `docs/01-adr/TEMPLATE.md` e as ADRs existentes para pegar o próximo
   número sequencial e o tom usado. O frontmatter do TEMPLATE é obrigatório e
   alimenta `docs/adr.base` — ADR sem ele fica fora do índice.
2. Verifique se alguma ADR aceita já cobre ou contradiz esta decisão. Se
   contradiz, a nova ADR deve declarar explicitamente que supera a anterior,
   e a anterior muda no frontmatter para `status: superada` e
   `superada_por: ADR-NNNN`, enquanto a nova declara `supera: [ADR-NNNN]` —
   sem editar o resto do conteúdo da anterior.
3. Escreva a ADR. Regras de qualidade:
   - **Alternativas consideradas é a seção mais importante.** Uma ADR sem
     alternativa real descartada não é decisão, é anotação. Mínimo duas.
   - Consequências devem incluir as **negativas**. Se você não conseguiu
     listar nenhuma, a análise está incompleta.
   - `status: proposta` no frontmatter. Só eu mudo para `aceita`.
   - `modulos` usa o vocabulário fechado listado no TEMPLATE. Termo novo entra
     antes no glossário.
4. Ao final, remova de `docs/DECISOES-ABERTAS.md` os itens que esta ADR resolve.
