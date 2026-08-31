# language: pt
Funcionalidade: Fechamento de compra gerando lançamento
  Como membro de um household
  Quero fechar a lista e registrar o gasto em uma única mensagem
  Para não ter que repetir a mesma compra em dois lugares

  # Esta é a feature que materializa o diferencial do produto.
  # Se algum cenário aqui for cortado por escopo, o produto perde a razão de existir.

  Contexto:
    Dado que existe o household "Silva"
    E que "Ana" e "Bruno" são membros do household "Silva" com Telegram vinculado
    E que o household "Silva" tem a categoria de despesa "Mercado"
    E que a lista de compras ativa tem os itens pendentes "Arroz", "Leite" e "Café"
    E que "Ana" solicitou o item "Arroz"

  Cenário: Fechar a lista inteira com valor
    Quando "Bruno" envia "comprei tudo, 180"
    Então os itens "Arroz", "Leite" e "Café" ficam com status comprado
    E os itens ficam registrados como comprados por "Bruno"
    E uma despesa de R$ 180,00 é registrada na categoria "Mercado"
    E o fechamento fica ligado à despesa criada
    E "Bruno" recebe um recibo com a quantidade de itens e o valor

  Cenário: Ana enxerga o que Bruno comprou
    Quando "Bruno" envia "comprei tudo, 180"
    E "Ana" envia "o que está faltando?"
    Então "Ana" recebe uma resposta informando que não falta nada

  Cenário: Falha ao registrar a despesa não deixa a lista fechada
    Dado que o registro de despesas está indisponível
    Quando "Bruno" envia "comprei tudo, 180"
    Então nenhum item muda de status
    E nenhuma despesa é registrada
    E "Bruno" é avisado da falha pelo chat

  Cenário: Fechamento parcial
    Quando "Bruno" envia "comprei o arroz e o leite, 60"
    Então os itens "Arroz" e "Leite" ficam com status comprado
    E o item "Café" continua pendente
    E uma despesa de R$ 60,00 é registrada na categoria "Mercado"

  Cenário: Segundo fechamento parcial na mesma lista
    Dado que "Bruno" já fechou "Arroz" e "Leite" por R$ 60,00
    Quando "Ana" envia "comprei o café, 20"
    Então o item "Café" fica com status comprado
    E uma segunda despesa de R$ 20,00 é registrada na categoria "Mercado"
    E a lista registra dois fechamentos distintos

  Cenário: Fechar sem informar valor
    Quando "Bruno" envia "comprei tudo"
    Então nenhum item muda de status ainda
    E "Bruno" recebe uma pergunta curta pedindo o valor da compra
    Quando "Bruno" responde "180"
    Então os três itens ficam com status comprado
    E uma despesa de R$ 180,00 é registrada na categoria "Mercado"

  Cenário: Desfazer o fechamento
    Dado que "Bruno" fechou a compra de R$ 180,00 há 1 minuto
    Quando "Bruno" envia "desfazer"
    Então os itens "Arroz", "Leite" e "Café" voltam a ficar pendentes
    E a despesa de R$ 180,00 é estornada
    E "Bruno" recebe a confirmação do estorno

  Cenário: Fechar compra sem lista ativa
    Dado que não existe lista de compras ativa no household "Silva"
    Quando "Bruno" envia "comprei tudo, 180"
    Então "Bruno" recebe uma pergunta oferecendo registrar apenas a despesa
