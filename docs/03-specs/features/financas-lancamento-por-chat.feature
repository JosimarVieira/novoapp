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
