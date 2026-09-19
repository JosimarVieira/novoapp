---
tipo: adr
numero: 34
status: aceita
data: 2026-09-19
modulos:
  - nlu
depende_de: [ADR-0004, ADR-0033]
supera: []
superada_por:
corrigida_em:
---

# ADR-0034 — Valor só vale se a pessoa escreveu algum dígito

- **Impacta**: `nlu` (uma checagem antes de montar `Intent.RegisterExpense`), e
  nada mais — nenhum módulo de domínio, nenhuma tabela, nenhum recibo novo

## Contexto

Em uso real, em 2026-09-19, a mensagem **`mercado`** — uma palavra, nenhum
número — voltou do Mistral assim, duas vezes em cem segundos:

```json
{"categoria": "Mercado", "valor": 50, "confianca": 0.8}
```

O sistema gravou **R$ 50,00** nas duas. O usuário não escreveu valor nenhum.

O prompt proíbe isso em duas linhas independentes: "Nunca invente valor,
categoria, item nem hierarquia de categoria que a pessoa nao escreveu" no
sistema, e "Deixe vazio se a pessoa nao disse o valor -- nunca invente um" na
descrição do parâmetro. O modelo inventou assim mesmo, e a causa provável está
no próprio prompt: o par **`"mercado 50"` aparecia cinco vezes** como exemplo. A
mensagem `mercado` é o começo literal do exemplo mais repetido do contexto, e o
modelo completou o padrão.

A mesma mensagem, nas mesmas cem segundos, produziu ainda `valor: 50` com
confiança 0,7 (que virou pergunta) e duas chamadas vazias com 0,3 (que viraram
"não entendi"). Quatro desfechos para uma palavra, com `temperature` 0 — o que
também diz que não dá para tratar a saída do modelo como função do texto.

## Decisão

Antes de montar a intenção de despesa, `nlu` descarta o `valor` devolvido pelo
modelo **quando a mensagem do usuário não contém nenhum dígito**. A intenção
segue sem valor, e o desfecho passa a ser a pergunta da
[ADR-0033](0033-valor-ausente-com-categoria-conhecida-pergunta-o-valor.md):
"Quanto foi em Mercado?".

A checagem é sobre o **texto que a pessoa escreveu**, não sobre o que o modelo
respondeu. É o que a torna uma defesa de verdade: ela não pede nada ao modelo e
não muda quando o modelo muda.

## Consequências

**A favor, e é o motivo.** Dinheiro inventado é o erro mais caro que este
sistema pode cometer — pior que não entender, pior que perguntar demais. É a
terceira vez que o valor sai errado em produção (R$ 500 virando R$ 5,00 pela
conversão no modelo; R$ 500 virando R$ 50,00 no mesmo dia; agora R$ 50,00 do
nada), e as duas primeiras foram fechadas em código, não em prompt. Esta fecha
do mesmo jeito.

**Contra, e é real.** Valor escrito por extenso — "mercado cinquenta reais" —
passa a ser descartado junto. Hoje o modelo não extrai valor por extenso, então
não perdemos nada observável; se passar a extrair, perdemos. O custo de errar
para este lado é uma pergunta a mais ("Quanto foi em Mercado?"), contra um
lançamento falso do outro lado. Fica registrado como o que reabre esta ADR: se a
Etapa 5 mostrar gente escrevendo valor por extenso, a checagem precisa passar a
entender numeral escrito, não ser removida.

**Não substitui o prompt.** Os exemplos do prompt deixaram de repetir sempre o
mesmo par `"mercado 50"` — variá-los reduz a chance de o modelo completar o
padrão. Mas isso é mitigação; a garantia é o código.

## Alternativas

**Insistir no prompt.** Já era a instrução, em dois lugares. Repetir uma
proibição que o modelo acabou de ignorar não é decisão, é esperança.

**Exigir que o valor apareça literalmente no texto** (casar `50` contra a
mensagem). Mais estrito, e quebra o caso legítimo de "quarenta e nove e noventa"
virar 49.90 e de "mercado 49,90" virar 49.9 — formatos diferentes do mesmo
número. A checagem por dígito aceita o mesmo conjunto de mensagens que hoje
funciona, e recusa exatamente o conjunto que hoje é alucinação.
