---
tipo: adr
numero: 30
status: aceita
data: 2026-09-16
modulos:
  - finance
  - shopping
  - banco
depende_de: [ADR-0016, ADR-0024, ADR-0026, ADR-0027]
supera: []
superada_por:
corrigida_em:
---

# ADR-0030 — Nome de categoria e de item casa pela forma normalizada

- **Impacta**: `finance` (categoria e a busca da categoria-pai da
  [ADR-0026](0026-hierarquia-na-criacao-de-categoria-por-chat.md)), `shopping`
  (item repetido, [ADR-0027](0027-lista-de-compras-unica-e-sob-demanda.md)),
  banco (dois índices únicos e uma coluna nova em cada tabela), e a Etapa 3 do
  [ROADMAP](../../ROADMAP.md), que fecha compra casando o que a pessoa disse com
  o que está na lista

## Contexto

Até 2026-09-16 nome de categoria e nome de item eram comparados por
`lower(name)` — no SQL do repositório **e** no índice único que sustenta a
invariante. Acento não era tratado em nenhum dos dois lados.

A consequência mais grave é silenciosa. `acabou cafe`, com "Café" já pendente na
lista: a consulta não acha o primeiro item e o índice único não barra o segundo.
Dois "café" pendentes, sem pergunta e sem aviso. A entrega da Etapa 2a já
registrava o risco em aberto; o que a auditoria acrescentou foi que o índice
único também não protegia, o que muda o diagnóstico de "a busca é frouxa" para
"a invariante não existe".

No lado de categoria o furo é mais estreito do que parece: `nlu` **já**
normaliza acento ao casar a categoria que o modelo escolheu contra as que
existem — é por isso que `60 farmacia` acha "Farmácia". Quem não normalizava era
a busca da categoria-pai na correção livre, e o sintoma é
`restaurante dentro de alimentacao` criando uma raiz "Alimentacao" ao lado da
"Alimentação" que já existe.

A Etapa 3 amplifica os dois: fechar compra é casar o que a pessoa escreveu com o
que está na lista, e é a operação que transforma item em dinheiro.

## Decisão

Nome de categoria e de item de lista são comparados pela **forma normalizada**:
minúscula, sem acento, espaço interno colapsado. Nada além disso.

A forma normalizada é **persistida** em `name_normalized`, e é ela que os dois
índices únicos passam a usar. Quem a calcula é uma função única em Java
(`common/text/Normalization`), que também substitui as três cópias que existiam
em `conversation`, `identity` e `nlu`.

**Plural e raiz de palavra ficam de fora, explicitamente.** "café" e "cafés"
continuam sendo coisas diferentes. Aproximar por forma é barato e previsível;
aproximar por sentido é outro problema — e o dado para decidi-lo sai da Etapa 5,
com mensagens reais, não de suposição agora.

## Alternativas consideradas

### A. `unaccent(lower(name))` direto no índice
Descartada por um detalhe do Postgres que não dá para contornar limpo:
`unaccent` é declarada `STABLE`, não `IMMUTABLE`, e índice exige `IMMUTABLE`.
Sairia uma função-invólucro marcada `IMMUTABLE` sobre um dicionário que pode
mudar — mentir para o planejador para conseguir o índice — mais a dependência da
extensão no banco gerenciado. A coluna evita as duas coisas e deixa a
normalização num lugar só, do lado que já a tinha escrita.

### B. Normalizar só na consulta, mantendo o índice em `lower(name)`
Descartada: conserta a busca e deixa a invariante de pé só pela disciplina do
serviço. É o mesmo tipo de raciocínio que a
[ADR-0003](0003-isolamento-multi-tenant-por-household.md) recusou para
isolamento — se a regra importa, ela vive no banco. E aqui a corrida é real:
dois membros mandando "acabou café" e "acabou cafe" ao mesmo tempo.

### C. Deixar como está e tratar na Etapa 5
Descartada: o caso que mais dói não produz erro nem pergunta — produz uma
segunda linha. Ninguém reclama do que não vê, então "esperar o uso real cobrar"
aqui significa não descobrir.

### D. Aproximação difusa (distância de edição, raiz de palavra, plural)
Descartada por ora, e é a única cuja recusa é de prazo e não de mérito: resolve
mais casos e traz falso positivo junto — "café" e "chá" estão a dois caracteres
de distância. Decidir o limiar sem dado é o mesmo erro que a
[decisão aberta #7](../DECISOES-ABERTAS.md) evita para confiança. Volta na
Etapa 5, se a matriz de confusão mostrar que a forma não bastou.

## Consequências

### Positivas
- "item repetido não duplica" e "irmãs não repetem nome" passam a ser
  invariantes de verdade, garantidas pelo índice, e não só pela consulta.
- A função de normalização deixa de existir em seis cópias (três em produção,
  três em teste). Duas implementações divergentes de "mesma coisa" viram linha
  duplicada no banco — o risco some junto com as cópias.
- A Etapa 3 herda o casamento de nome já resolvido, em vez de descobrir o
  problema no meio do elo.

### Negativas
- **Coluna derivada, com as duas fontes de verdade que isso implica.** `name` e
  `name_normalized` podem divergir se alguém escrever no banco por fora. Só a
  aplicação escreve hoje, e o backfill da migração é a única exceção declarada.
- **A migração pode falhar no banco de validação**, e falha de propósito: o
  índice novo é mais estrito, então linhas que só conviviam por causa do acento
  passam a colidir. A V5 detecta antes e aborta com a lista das linhas, em vez
  de deixar o `CREATE UNIQUE INDEX` falhar com uma mensagem que não diz qual é.
  Resolver é manual, e é o preço de ter deixado o índice frouxo até aqui.
- **A normalização do backfill é SQL, e não a função Java.** `translate()` cobre
  o conjunto latino que o português usa — não todo o Unicode. Vale uma vez, na
  migração; daqui em diante quem grava é a aplicação. Se um dia entrar household
  com nome fora desse alfabeto, o backfill não é mais o caminho.
- "café" e "cafés" continuam duplicando. Está decidido assim, e é a reclamação
  mais provável de aparecer no uso real.

## Gatilhos de revisão

- **Etapa 5**: se a lista de erros reais mostrar plural como causa frequente de
  item duplicado, a alternativa D volta — aí com dado para escolher o método e o
  limiar.
- Se algum dia a aplicação escrever categoria ou item por outro caminho que não
  os serviços de domínio (importação, API pública), a coluna derivada precisa de
  gatilho no banco ou a divergência vira real.
