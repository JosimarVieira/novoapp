# language: pt
Funcionalidade: Lançamento de despesa por chat
  Como membro de um household
  Quero registrar uma despesa mandando uma mensagem curta
  Para não precisar abrir o aplicativo

  Contexto:
    Dado que existe o household "Silva"
    E que "Ana" é membro do household "Silva" com o Telegram vinculado
    E que o household "Silva" tem as categorias de despesa "Mercado" e "Farmácia"

  Cenário: Despesa com categoria reconhecida
    Quando "Ana" envia "mercado 50"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado" com data de hoje
    E o lançamento fica atribuído a "Ana"
    E "Ana" recebe um recibo informando valor, categoria e como desfazer

  Esquema do Cenário: Variações de escrita da mesma despesa
    Quando "Ana" envia "<mensagem>"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"

    Exemplos:
      | mensagem                  |
      | mercado 50                |
      | 50 mercado                |
      | gastei 50 no mercado      |
      | paguei 50 reais de mercado|

  Cenário: Categoria não existe no household
    Quando "Ana" envia "pet shop 80"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma única pergunta oferecendo criar a categoria "Pet shop"
    Quando "Ana" responde "sim"
    Então a categoria de despesa "Pet shop" é criada no household "Silva"
    E uma despesa de R$ 80,00 é registrada nessa categoria

  Cenário: Mensagem ambígua entre duas categorias
    Dado que o household "Silva" também tem a categoria de despesa "Mercado livre"
    Quando "Ana" envia "mercado 50"
    Então nenhuma despesa é registrada ainda
    E "Ana" recebe uma pergunta com as opções numeradas "Mercado" e "Mercado livre"
    Quando "Ana" responde "1"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"

  Cenário: Valor ausente
    Quando "Ana" envia "paguei o mercado"
    Então nenhuma despesa é registrada
    E "Ana" recebe uma pergunta curta pedindo o valor
    E o sistema não inventa um valor

  Cenário: Desfazer um lançamento recém-criado
    Dado que "Ana" registrou uma despesa de R$ 50,00 em "Mercado" há 2 minutos
    Quando "Ana" envia "desfazer"
    Então a despesa é estornada
    E a despesa continua visível no histórico marcada como estornada
    E "Ana" recebe a confirmação do estorno

  Cenário: Reentrega da mesma mensagem pelo provedor
    Quando o provedor entrega duas vezes a mesma mensagem "mercado 50" de "Ana"
    Então exatamente uma despesa de R$ 50,00 é registrada
    E "Ana" recebe exatamente um recibo

  Cenário: Mensagem de número não vinculado
    Quando uma mensagem "mercado 50" chega de um número desconhecido
    Então nenhuma despesa é registrada em nenhum household
    E o remetente recebe apenas uma orientação de como vincular o número
