---
tipo: adr
numero: 13
status: aceita
data: 2026-08-31
modulos:
  - banco
  - onboarding
  - nlu
depende_de: []
supera: []
superada_por:
---

# ADR-0013 — Household novo começa sem categoria pré-criada

- **Impacta**: banco (`category`), onboarding, interpretação por chat (Etapa
  1); resolve [decisão aberta #11](../DECISOES-ABERTAS.md)
- **Termos**: [Categoria](../00-produto/glossario.md#finanças)

## Contexto

`docs/DECISOES-ABERTAS.md` registrava como aberto: "Categorias iniciais no
onboarding, ou household começa vazio? Vazio força o fluxo de criação por
chat (bom para testar), mas piora a primeira impressão."

O primeiro exemplo do próprio `CLAUDE.md` já descreve o comportamento
esperado: "O usuário manda `mercado 50` no chat e a despesa é registrada,
categorizada, sem abrir o app" — ou seja, a criação de categoria não é uma
etapa de onboarding separada, é algo que acontece organicamente na primeira
mensagem que precisa de uma categoria que ainda não existe.

Isso é diferente da decisão já tomada para `account` na ADR-0011, que cria
uma `WALLET` implícita no onboarding sem perguntar nada: conta tem um default
óbvio (a carteira do household) e o usuário nunca escolheria um nome
diferente. Categoria não tem esse default — é o household que decide como
quer nomear e agrupar seus gastos, e são bastante conhecidos exemplos onde a
nomenclatura difere de família pra família ("Mercado" vs. "Supermercado" vs.
"Alimentação").

## Decisão

Household novo começa sem nenhuma categoria pré-criada. A primeira menção a
uma categoria inexistente, em qualquer canal, dispara o fluxo de criação —
sujeito à mesma política de confiança/confirmação de qualquer outra
interpretação (ADR-0004), não a um passo de onboarding à parte.

## Alternativas consideradas

### A. Categorias padrão pré-criadas (ex.: Mercado, Transporte, Lazer, Saúde)
Descartada: adia o teste do fluxo de criação de categoria até o household
precisar de uma categoria fora da lista padrão — o que pode nunca acontecer
durante a validação, se as categorias padrão cobrirem os primeiros gastos por
acaso. Contraria o objetivo da Etapa 1-3 de provar o parser com uso real, não
com dado plantado.

### B. Poucas categorias essenciais pré-criadas (ex.: só "Mercado") + resto livre
Descartada, apesar de reduzir o risco de atrito na primeira mensagem: perde a
chance de validar o fluxo de criação de categoria já na primeira interação —
que é justamente o caso mais comum (household que nunca usou o produto manda
a primeira mensagem sobre qualquer gasto, sem saber se "Mercado" já existe).

## Consequências

### Positivas
- O fluxo de criação de categoria é testado desde a primeira mensagem real,
  não fica sem cobertura até um caso de borda aparecer.
- Nenhuma categoria fica parada sem uso porque ninguém pediu — toda categoria
  que existe foi criada porque o household precisou dela.
- Consistente com o exemplo de comportamento já descrito no `CLAUDE.md`.

### Negativas
- A primeira mensagem de um household novo sempre encontra uma categoria
  inexistente — pior primeira impressão do que categorias já prontas, e
  possivelmente mais atrito logo na primeira interação, dependendo de como a
  política de confiança (ADR-0004) tratar "criar categoria nova" na prática:
  se cada criação de categoria for tratada como baixa confiança, todo
  household novo começa com uma pergunta em vez de um recibo direto.
- Essa ADR não decide se a criação de categoria em si é alta ou baixa
  confiança — isso fica para a calibração da Etapa 5 (decisão aberta #7).

## Gatilhos de revisão

Se, na Etapa 5, a primeira mensagem de household novo se mostrar um ponto de
abandono real (não hipotético), avaliar pré-criar só a categoria "Mercado"
(alternativa B) como concessão pontual — sem voltar para o conjunto completo
de categorias padrão.
