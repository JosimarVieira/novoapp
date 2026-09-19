# language: pt
Funcionalidade: Lançamento de despesa por chat
  Como membro de um household
  Quero registrar uma despesa mandando uma mensagem curta
  Para não precisar abrir o aplicativo

  # @etapa1 = esqueleto andante (ROADMAP Etapa 1: so tool reconhecida +
  # idempotencia + identidade nao vinculada). @etapa2 = ambiguidade, criacao
  # de categoria, valor ausente, desfazer -- exigem PendingAction e politica
  # de confianca media/baixa (ADR-0004), decidido em 2026-09-05 que fica pra
  # depois do esqueleto andante.

  Contexto:
    Dado que existe o household "Silva"
    E que "Ana" é membro do household "Silva" com o Telegram vinculado
    E que o household "Silva" tem as categorias de despesa "Mercado" e "Farmácia"

  @etapa1
  Cenário: Despesa com categoria reconhecida
    Quando "Ana" envia "mercado 50"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado" com data de hoje
    E o lançamento fica atribuído a "Ana"
    E "Ana" recebe um recibo informando valor, categoria e como desfazer

  @etapa1
  Esquema do Cenário: Variações de escrita da mesma despesa
    Quando "Ana" envia "<mensagem>"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"

    Exemplos:
      | mensagem                  |
      | mercado 50                |
      | 50 mercado                |
      | gastei 50 no mercado      |
      | paguei 50 reais de mercado|

  @etapa2
  Cenário: Categoria não existe no household
    Quando "Ana" envia "pet shop 80"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma única pergunta oferecendo criar a categoria "Pet shop"
    Quando "Ana" responde "sim"
    Então a categoria de despesa "Pet shop" é criada no household "Silva"
    E uma despesa de R$ 80,00 é registrada nessa categoria

  # Os três cenários abaixo cobrem a ADR-0026 (hierarquia na criação de
  # categoria por chat). A extração continua sem inventar hierarquia --
  # "restaurante eu e esposa 90" só sugere "Restaurante", nunca supõe
  # "Alimentação" sozinho; é a correção da pessoa que traz o pai.

  @etapa2
  Cenário: Categoria sugerida vira subcategoria por correção livre
    Dado que o household "Silva" também tem a categoria de despesa "Alimentação"
    Quando "Ana" envia "restaurante eu e esposa 90"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma única pergunta oferecendo criar a categoria "Restaurante"
    Quando "Ana" responde "restaurante dentro de alimentação"
    Então a categoria de despesa "Restaurante" é criada no household "Silva" como subcategoria de "Alimentação"
    E uma despesa de R$ 90,00 é registrada nessa categoria

  @etapa2
  Cenário: Correção livre cria categoria-pai e subcategoria quando nenhuma das duas existe
    Quando "Ana" envia "restaurante eu e esposa 90"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma única pergunta oferecendo criar a categoria "Restaurante"
    Quando "Ana" responde "restaurante dentro de alimentação"
    Então a categoria de despesa "Alimentação" é criada no household "Silva" como categoria raiz
    E a categoria de despesa "Restaurante" é criada como subcategoria de "Alimentação"
    E uma despesa de R$ 90,00 é registrada na categoria "Restaurante"

  @etapa2
  Cenário: Correção livre não pode criar subcategoria de subcategoria
    Dado que o household "Silva" tem a categoria "Alimentação" com a subcategoria "Restaurante"
    Quando "Ana" envia "rodízio de pizza 40"
    Então "Ana" recebe uma única pergunta oferecendo criar a categoria "Rodízio de pizza"
    Quando "Ana" responde "rodízio de pizza dentro de restaurante"
    Então nenhuma categoria é criada
    E "Ana" recebe uma pergunta pedindo a categoria correta, não um erro genérico

  @etapa2
  Cenário: Household novo sem nenhuma categoria já oferece criar categoria na primeira mensagem
    Dado que existe o household "Costa", sem nenhuma categoria de despesa
    E que "Bruno" é membro do household "Costa" com o Telegram vinculado
    Quando "Bruno" envia "mercado 50"
    Então nenhuma despesa é registrada ainda
    E "Bruno" recebe uma única pergunta oferecendo criar a categoria "Mercado"
    Quando "Bruno" responde "sim"
    Então a categoria de despesa "Mercado" é criada no household "Costa"
    E uma despesa de R$ 50,00 é registrada nessa categoria

  @etapa2
  Cenário: Mensagem ambígua entre duas categorias
    Dado que o household "Silva" também tem a categoria de despesa "Mercado livre"
    Quando "Ana" envia "mercado 50"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma pergunta com as opções numeradas "Mercado" e "Mercado livre"
    Quando "Ana" responde "1"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"

  @etapa2
  Cenário: Valor ausente
    Quando "Ana" envia "paguei o mercado"
    Então nenhuma despesa é registrada
    E "Ana" recebe uma pergunta curta pedindo o valor
    E o sistema não inventa um valor

  # Descrição (ADR-0023): o LLM extrai o que sobra da mensagem depois de
  # categoria, valor, conta e data. Nunca gera pergunta, nunca reduz a
  # confiança -- por isso os três cenários abaixo terminam em lançamento
  # registrado, nunca em pergunta.

  @etapa2
  Cenário: Despesa com informação além de categoria e valor
    Quando "Ana" envia "60 farmacia - remedio joaquim"
    Então uma despesa de R$ 60,00 é registrada na categoria "Farmácia"
    E o lançamento fica com uma descrição contendo "remédio" e "Joaquim"
    E a descrição não repete a categoria nem o valor
    E o recibo de "Ana" mostra a descrição registrada

  @etapa2
  Cenário: Mensagem sem nada além de categoria e valor
    Quando "Ana" envia "mercado 50"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"
    E o lançamento fica sem descrição
    E o sistema não inventa uma descrição a partir da categoria

  @etapa2
  Cenário: Ausência de descrição nunca vira pergunta
    Quando "Ana" envia "farmácia 60"
    Então uma despesa de R$ 60,00 é registrada na categoria "Farmácia"
    E "Ana" recebe um recibo, não uma pergunta
    E "Ana" não é perguntada sobre descrição em nenhum momento

  @etapa2
  Cenário: Desfazer um lançamento recém-criado
    Dado que "Ana" registrou uma despesa de R$ 50,00 em "Mercado" há 2 minutos
    Quando "Ana" envia "desfazer"
    Então a despesa é estornada
    E a despesa continua visível no histórico marcada como estornada
    E "Ana" recebe a confirmação do estorno

  # ADR-0025: pendência aberta tem precedência absoluta sobre estorno.
  # "Desfazer" só reabre o fluxo de estorno quando não há pergunta esperando
  # resposta.

  @etapa2
  Cenário: Desfazer resolve pendência aberta em vez de estornar lançamento
    Dado que "Ana" registrou uma despesa de R$ 50,00 em "Mercado" há 2 minutos
    E que "Ana" tem uma pergunta pendente oferecendo criar a categoria "Pet shop"
    Quando "Ana" envia "desfazer"
    Então a pergunta pendente é cancelada
    E a despesa de R$ 50,00 em "Mercado" continua sem estorno
    E "Ana" recebe a confirmação de que a pergunta foi cancelada, não de um estorno

  # ADR-0029. Três furos encontrados na auditoria de 2026-09-14, todos com a
  # mesma raiz: `PendingAction` foi construída como "pergunta sobre uma despesa"
  # e não como "intenção esperando confirmação".

  @saneamento
  Cenário: Confiança média com uma única categoria candidata pergunta antes de lançar
    Quando "Ana" envia "acho que foi mercado, 50"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma pergunta pedindo para confirmar a despesa de R$ 50,00 em "Mercado"
    Quando "Ana" responde "sim"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"

  @saneamento
  Cenário: Mensagem sobre outro assunto não vira correção da categoria pendente
    Quando "Ana" envia "pet shop 80"
    E "Ana" envia "acabou o arroz"
    Então nenhuma categoria é criada
    E nenhuma despesa é registrada
    E o item "Arroz" entra na lista de compras com status pendente
    Quando "Ana" responde "sim"
    Então nenhuma categoria é criada
    E nenhuma despesa é registrada

  # Achado em uso real em 2026-09-17, e não pela suíte: "Pet shop 80" chegava do
  # Mistral com confiança 0,3 — o modelo se declara inseguro justamente quando
  # tem de sugerir nome novo — e a faixa baixa da ADR-0004 engolia a mensagem
  # antes de alguém reparar que havia um nome de categoria ali. O usuário recebia
  # "não entendi essa" com o bot sabendo exatamente o que ele quis.
  @saneamento
  Cenário: Categoria inexistente oferece criação mesmo com confiança baixa
    Quando "Ana" envia "talvez pet shop 80"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma única pergunta oferecendo criar a categoria "Pet shop"
    Quando "Ana" responde "sim"
    Então a categoria de despesa "Pet shop" é criada no household "Silva"
    E uma despesa de R$ 80,00 é registrada nessa categoria

  # A conversão para centavos saiu do modelo e passou para o código em
  # 2026-09-18: "Mercado 500 fechar lista" foi registrado como R$ 5,00 e
  # "comprei toda lista 500 mercado" como R$ 50,00 — erro silencioso, em
  # dinheiro. Este cenário guarda a conversão, não o erro do modelo: o stub
  # nunca errou a conta, e a prova do furo é o log de produção.
  @saneamento
  Cenário: Valor com centavos é registrado exato
    Quando "Ana" envia "mercado 49,90"
    Então uma despesa de R$ 49,90 é registrada na categoria "Mercado"

  # Também de uso real, em 2026-09-18. "Madeireira 300" virava "não entendi
  # essa" enquanto "Pet shop 80" — mensagem da mesma forma — funcionava: o
  # modelo escreveu o nome novo no campo do enum em vez do campo de sugestão, e
  # o código descartava a mensagem inteira por erro de campo.
  @saneamento
  Cenário: Categoria nova que o modelo escreveu no campo errado ainda oferece criação
    Quando "Ana" envia "madeireira 300"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma única pergunta oferecendo criar a categoria "Madeireira"
    Quando "Ana" responde "sim"
    Então a categoria de despesa "Madeireira" é criada no household "Silva"
    E uma despesa de R$ 300,00 é registrada nessa categoria

  # ADR-0030. `nlu` já casava acento ao reconhecer categoria existente; a busca
  # da categoria-pai na correção livre é que ficara de fora, e criava uma raiz
  # homônima em silêncio.
  @saneamento
  Cenário: Categoria-pai escrita sem acento é reconhecida, não duplicada
    Dado que o household "Silva" também tem a categoria de despesa "Alimentação"
    Quando "Ana" envia "restaurante eu e esposa 90"
    Então "Ana" recebe uma única pergunta oferecendo criar a categoria "Restaurante"
    Quando "Ana" responde "restaurante dentro de alimentacao"
    Então a categoria de despesa "Restaurante" é criada no household "Silva" como subcategoria de "Alimentação"
    E o household "Silva" continua com uma única categoria chamada "Alimentação"
    E uma despesa de R$ 90,00 é registrada nessa categoria

  # Achado em uso real em 2026-09-18, e não pela suíte. `petshop` sozinho —
  # categoria que existe na família, `categoryId` resolvido, só o valor
  # faltando — recebeu "não entendi essa", minutos depois de o mesmo bot ter
  # perguntado "Quanto foi em Ração?" na mesma conversa. O modelo devolveu
  # confiança 0,3 e a faixa baixa decidia antes do passo que pergunta o valor.
  # ADR-0033: o SDD já afirmava "valor ausente sempre pergunta" desde a Etapa
  # 2a, sem ADR que sustentasse a frase e sem código que a cumprisse.
  @saneamento
  Cenário: Categoria existente sem valor pergunta o valor, mesmo com confiança baixa
    Quando "Ana" envia "talvez mercado"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma pergunta curta pedindo o valor em "Mercado"
    Quando "Ana" responde "50"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"

  # Achado em uso real em 2026-09-19. "não" sem pergunta nenhuma em aberto foi
  # parar no Mistral, que devolveu `consultarLista` com confiança 0,3 — e o
  # usuário recebeu "não entendi essa". A regra 6 do CLAUDE.md não condiciona a
  # existência de pendência: "sim", "não", "1" e "desfazer" são resolvidos por
  # curto-circuito determinístico antes de qualquer chamada de modelo.
  @saneamento
  Cenário: "não" sem pergunta em aberto não gasta chamada de modelo
    Quando "Ana" envia "não"
    Então nenhuma despesa é registrada
    E nenhuma chamada ao modelo é feita
    E "Ana" é avisada de que não há pergunta em aberto

  @saneamento
  Cenário: Responder com o nome da opção, e não com o número, continua sendo resposta
    Dado que o household "Silva" também tem a categoria de despesa "Mercado livre"
    Quando "Ana" envia "mercado 50"
    E "Ana" responde "mercado"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma pergunta com as opções numeradas "Mercado" e "Mercado livre"
    Quando "Ana" responde "1"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"

  @etapa1
  Cenário: Reentrega da mesma mensagem pelo provedor
    Quando o provedor entrega duas vezes a mesma mensagem "mercado 50" de "Ana"
    Então exatamente uma despesa de R$ 50,00 é registrada
    E "Ana" recebe exatamente um recibo

  @etapa1
  Cenário: Mensagem de número não vinculado
    Quando uma mensagem "mercado 50" chega de um número desconhecido
    Então nenhuma despesa é registrada em nenhum household
    E o remetente recebe apenas uma orientação de como vincular o número
