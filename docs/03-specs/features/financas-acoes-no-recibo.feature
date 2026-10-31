# language: pt

# ADR-0041, aceita em 2026-10-31. Roda em AcoesNoReciboAcceptanceTest, que
# nasceu desabilitado pelo mesmo motivo que o AcoesNoChatAcceptanceTest nasceu
# assim: escopo que falta tem de ser visível na suíte. O @Disabled sai no
# primeiro passo de código (ROADMAP, "Desfazer e trocar categoria pelo recibo").
#
# Arquivo e tag próprios, e não cenários novos em mercado-botoes-na-lista: lá
# a tag @acoes-no-chat já está no portão do CI, e escopo novo não pode pintar
# de vermelho uma suíte fechada (03-specs/README.md, "Sobre as tags").
#
# A janela do desfazer pelo botão não aparece como número em cenário nenhum.
# É config provisória, sem base (decisão aberta #30), e cenário com "24 horas"
# cristalizaria o número — o mesmo motivo de o prazo do carrinho não ter
# cenário. Os cenários falam de "dentro" e "fora" da janela.
@acoes-no-recibo
Funcionalidade: Desfazer e trocar categoria pelo recibo
  Como membro de um household que acabou de lançar uma despesa
  Quero desfazer ou corrigir a categoria tocando no próprio recibo
  Para consertar o lançamento que estou vendo, sem afetar o que outra pessoa lançou

  Contexto:
    Dado que existe o household "Silva"
    E que "Ana" e "Bruno" são membros do household "Silva" com Telegram vinculado
    E que o household "Silva" tem as categorias de despesa "Mercado", "Farmácia" e "Alimentação"

  Cenário: O recibo de despesa traz os botões de desfazer e de trocar categoria
    Quando "Bruno" envia "farmácia 30"
    Então uma despesa de R$ 30,00 é registrada na categoria "Farmácia"
    E o recibo de "Bruno" tem um botão de desfazer e um botão de trocar categoria

  Cenário: O recibo do fechamento traz os mesmos botões
    Dado que existe uma lista de compras ativa no household "Silva"
    E que os itens "Arroz" e "Leite" estão pendentes, pedidos por "Ana"
    Quando "Bruno" envia "comprei tudo, 60"
    Então o recibo de "Bruno" tem um botão de desfazer e um botão de trocar categoria

  # ---------------------------------------------------------------- desfazer

  # O alvo é o lançamento do recibo, e não o mais recente do household
  # (ADR-0025): é por isso que o botão existe.
  Cenário: Desfazer pelo botão estorna o lançamento do próprio recibo
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    E que "Ana" registrou "mercado 50" depois
    Quando "Bruno" toca no botão de desfazer do recibo de "farmácia 30"
    Então a despesa de R$ 30,00 é estornada
    E a despesa de R$ 50,00 continua de pé
    E a mesma mensagem de "Bruno" é atualizada como estornada, sem botões
    E nenhuma chamada ao modelo é feita

  # O mesmo evento do desfazer digitado (ADR-0032), com outro alvo.
  Cenário: Desfazer pelo botão no recibo do fechamento devolve os itens
    Dado que existe uma lista de compras ativa no household "Silva"
    E que os itens "Arroz" e "Leite" estão pendentes, pedidos por "Ana"
    E que "Bruno" enviou "comprei tudo, 60" e recebeu o recibo
    Quando "Bruno" toca no botão de desfazer do recibo do fechamento
    Então os itens "Arroz" e "Leite" voltam a ficar pendentes
    E a despesa de R$ 60,00 é estornada

  # O segundo toque não pode cair na regra da ADR-0025 e estornar o próximo da fila.
  Cenário: Toque duplo no desfazer estorna uma vez só
    Dado que "Ana" registrou "mercado 50"
    E que "Bruno" registrou "farmácia 30" depois e recebeu o recibo
    Quando "Bruno" toca duas vezes no botão de desfazer do recibo de "farmácia 30"
    Então a despesa de R$ 30,00 é estornada
    E a despesa de R$ 50,00 continua de pé

  Cenário: Reentrega do mesmo clique de desfazer
    Dado que "Ana" registrou "mercado 50"
    E que "Bruno" registrou "farmácia 30" depois e recebeu o recibo
    Quando o Telegram entrega duas vezes o mesmo toque de "Bruno" no botão de desfazer do recibo de "farmácia 30"
    Então a despesa de R$ 30,00 é estornada
    E a despesa de R$ 50,00 continua de pé

  # O clique é resolvido antes da pendência e não a toca (ADR-0041, item 8).
  Cenário: Desfazer pelo botão não cancela a pergunta aberta
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    E que "Bruno" enviou "mercado" e recebeu uma pergunta pedindo o valor
    Quando "Bruno" toca no botão de desfazer do recibo de "farmácia 30"
    Então a despesa de R$ 30,00 é estornada
    Quando "Bruno" responde "50"
    Então uma despesa de R$ 50,00 é registrada na categoria "Mercado"

  Cenário: Botão de um recibo cujo lançamento já foi estornado
    Dado que "Ana" registrou "mercado 50"
    E que "Bruno" registrou "farmácia 30" depois e recebeu o recibo
    E que "Bruno" enviou "desfazer"
    Quando "Bruno" toca no botão de desfazer do recibo de "farmácia 30"
    Então "Bruno" recebe um aviso curto de que o lançamento já foi estornado
    E a mesma mensagem de "Bruno" é atualizada como estornada, sem botões
    E a despesa de R$ 50,00 continua de pé

  # ------------------------------------------------- fora da janela do desfazer

  Cenário: Fora da janela, o desfazer pede confirmação
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    E que o lançamento de "farmácia 30" está fora da janela do desfazer
    Quando "Bruno" toca no botão de desfazer do recibo de "farmácia 30"
    Então nenhum lançamento é estornado
    E a mesma mensagem de "Bruno" é atualizada pedindo confirmação, com a data, a categoria "Farmácia" e o valor R$ 30,00
    E a mensagem tem um botão de estornar e um botão de voltar
    Quando "Bruno" toca no botão de estornar
    Então a despesa de R$ 30,00 é estornada
    E a mesma mensagem de "Bruno" é atualizada como estornada, sem botões

  Cenário: Voltar da confirmação não estorna
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    E que o lançamento de "farmácia 30" está fora da janela do desfazer
    E que "Bruno" tocou no botão de desfazer do recibo de "farmácia 30"
    Quando "Bruno" toca no botão de voltar
    Então nenhum lançamento é estornado
    E a mesma mensagem de "Bruno" volta a ser o recibo, com o botão de desfazer e o de trocar categoria

  # ---------------------------------------------------------- trocar categoria

  Cenário: Trocar categoria mostra as categorias da família
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    Quando "Bruno" toca no botão de trocar categoria do recibo de "farmácia 30"
    Então a mesma mensagem de "Bruno" é atualizada com um botão por categoria de despesa do household
    E a categoria "Farmácia" aparece marcada como a atual
    E a mensagem tem um botão de voltar
    E nenhum lançamento é alterado

  Cenário: Trocar a categoria pelo recibo
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    Quando "Bruno" toca no botão de trocar categoria do recibo de "farmácia 30"
    E "Bruno" escolhe a categoria "Mercado"
    Então a despesa de R$ 30,00 passa a estar na categoria "Mercado"
    E nenhum lançamento novo é criado
    E nenhum lançamento é estornado
    E o histórico de edição registra que "Bruno" trocou a categoria de "Farmácia" para "Mercado"
    E a mesma mensagem de "Bruno" volta a ser o recibo, mostrando "Mercado", com o botão de desfazer e o de trocar categoria
    E nenhuma chamada ao modelo é feita

  Cenário: Escolher a categoria que o lançamento já tem não registra edição
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    Quando "Bruno" toca no botão de trocar categoria do recibo de "farmácia 30"
    E "Bruno" escolhe a categoria "Farmácia"
    Então a despesa de R$ 30,00 continua na categoria "Farmácia"
    E o histórico de edição não registra nenhuma troca

  # A troca não destrói nada, então não tem janela nem confirmação.
  Cenário: Trocar categoria fora da janela do desfazer não pede confirmação
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    E que o lançamento de "farmácia 30" está fora da janela do desfazer
    Quando "Bruno" toca no botão de trocar categoria do recibo de "farmácia 30"
    E "Bruno" escolhe a categoria "Mercado"
    Então a despesa de R$ 30,00 passa a estar na categoria "Mercado"

  # O fechamento herda a categoria atual do último fechamento não estornado
  # (ADR-0040, item 4): corrigir um corrige a herança (ADR-0041, item 6).
  Cenário: A troca no fechamento vale para o próximo fechamento pelo botão
    Dado que existe uma lista de compras ativa no household "Silva"
    E que os itens "Arroz" e "Café" estão pendentes, pedidos por "Ana"
    E que o último fechamento do household "Silva" foi lançado em "Farmácia"
    E que "Bruno" trocou a categoria desse fechamento para "Mercado" pelo recibo
    E que "Bruno" pôs o item "Café" no carrinho
    Quando "Bruno" toca no botão de fechar compra
    Então "Bruno" recebe uma pergunta curta pedindo o valor, que menciona a categoria "Mercado"

  Cenário: Trocar a categoria do fechamento não mexe nos itens
    Dado que existe uma lista de compras ativa no household "Silva"
    E que os itens "Arroz" e "Leite" estão pendentes, pedidos por "Ana"
    E que "Bruno" enviou "comprei tudo, 60" e recebeu o recibo
    Quando "Bruno" toca no botão de trocar categoria do recibo do fechamento
    E "Bruno" escolhe a categoria "Alimentação"
    Então a despesa de R$ 60,00 passa a estar na categoria "Alimentação"
    E os itens "Arroz" e "Leite" continuam com status comprado

  # Desfazer estorna o lançamento, nunca a troca (ADR-0041, item 5).
  Cenário: Desfazer depois de trocar estorna o lançamento inteiro
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    E que "Bruno" trocou a categoria de "farmácia 30" para "Mercado" pelo recibo
    Quando "Bruno" toca no botão de desfazer do recibo
    Então a despesa de R$ 30,00 é estornada
    E a despesa estornada está na categoria "Mercado"

  Cenário: Trocar categoria de um lançamento já estornado
    Dado que "Bruno" registrou "farmácia 30" e recebeu o recibo
    E que "Bruno" enviou "desfazer"
    Quando "Bruno" toca no botão de trocar categoria do recibo de "farmácia 30"
    Então "Bruno" recebe um aviso curto de que o lançamento já foi estornado
    E a mesma mensagem de "Bruno" é atualizada como estornada, sem botões
    E o histórico de edição não registra nenhuma troca
