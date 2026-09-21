# `nlu-eval` — o gabarito

O conjunto de avaliação do interpretador. Mora aqui, versionado, e não no banco,
por decisão da [ADR-0036](../../01-adr/0036-instrumento-de-medicao-da-etapa-5.md):
anotar é trabalho de revisão, e revisão sem diff não é revisão. Congelar o
gabarito entre duas medições — gatilho de revisão da
[ADR-0035](../../01-adr/0035-o-que-a-etapa-5-mede.md) — é literalmente o que o git
faz.

**Ainda não há dataset.** Esta pasta existe com o formato decidido e vazia de
conteúdo, para que anotar seja preencher um arquivo e não inventar um esquema.

## O que se anota

O **desfecho esperado**, nunca a tool esperada
([ADR-0035](../../01-adr/0035-o-que-a-etapa-5-mede.md)). A pergunta que se
responde ao anotar é "o que o bot deveria ter feito com esta mensagem?", e ela
tem exatamente três respostas possíveis:

| `esperado` | Quando | Campos |
|---|---|---|
| `executar` | A mensagem basta para agir | `tool`, e os campos que a métrica considera: `categoria`, `valor`, `conta` |
| `perguntar` | Falta algo que não se adivinha, ou criar dado irreversível | `pergunta`: `valor`, `criar_categoria`, `escolher_categoria`, `item_fora_da_lista`, `confirmar` |
| `nao_entender` | A mensagem não é nenhuma das intenções do produto | — |

`descricao` fica fora da conta: é texto livre e não tem gabarito
([ADR-0023](../../01-adr/0023-descricao-de-lancamento-extraida-pelo-llm.md)).

## Formato

Um arquivo JSONL por rodada de anotação, nomeado pela data:
`2026-10-15.jsonl`. Uma linha por mensagem.

```json
{"id": "...", "texto": "petshop", "esperado": "perguntar", "pergunta": "valor", "nota": ""}
{"id": "...", "texto": "mercado 50", "esperado": "executar", "tool": "registrarDespesa", "categoria": "Mercado", "valor": 50}
{"id": "...", "texto": "bom dia", "esperado": "nao_entender"}
```

`id` é o `inbound_message.id`, que é o que liga a linha anotada ao que o sistema
de fato fez — inclusive a `prompt_version` e o `model_name` sob os quais aquela
mensagem foi interpretada
([ADR-0036](../../01-adr/0036-instrumento-de-medicao-da-etapa-5.md)).

## Duas regras de quem anota

**Anote antes de olhar o que o sistema respondeu.** A fronteira entre pergunta
necessária e pergunta desnecessária é julgamento, e quem anota é a mesma pessoa
que escreveu o produto — ver a negativa correspondente na
[ADR-0035](../../01-adr/0035-o-que-a-etapa-5-mede.md).

**Substitua nome próprio por marcador** (`[nome]`). São mensagens reais de uma
família num repositório; a ADR-0036 registra isso como mitigação, não como
solução, e com prazo.
