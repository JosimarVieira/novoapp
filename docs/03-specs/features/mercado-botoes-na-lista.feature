# language: pt

# ADR-0040, aceita em 2026-10-29. Roda em AcoesNoChatAcceptanceTest, que
# nasceu desabilitado pelo mesmo motivo que o Etapa3AcceptanceTest nasceu
# assim: escopo que falta tem de ser visível na suíte. O @Disabled sai no
# primeiro passo de código (ROADMAP, "Botões na lista de compras").
#
# Arquivo próprio, e não cenários novos em mercado-lista-de-compras: lá a tag
# @etapa2 da Funcionalidade seria herdada e pintaria de vermelho o portão de uma
# etapa fechada (03-specs/README.md, "Sobre as tags").
#
# Os passos falam de "toca no botão" e "a mensagem é atualizada", e não de
# teclado inline nem de editMessageText: o Gherkin descreve o comportamento, e o
# adaptador de canal decide como ele aparece (regra 5 do CLAUDE.md).
@acoes-no-chat
Funcionalidade: Botões na lista de compras
  Como membro de um household fazendo compras
  Quero separar e tirar itens da lista com um toque
  Para fechar a compra e lançar a despesa sem digitar o nome de cada item

  Contexto:
    Dado que existe o household "Silva"
    E que "Ana" e "Bruno" são membros do household "Silva" com Telegram vinculado
    E que existe uma lista de compras ativa no household "Silva"
    E que os itens "Arroz", "Leite" e "Café" estão pendentes, pedidos por "Ana"
    E que o household "Silva" tem a categoria de despesa "Mercado"

  Cenário: Consultar a lista devolve botões por item
    Quando "Bruno" envia "o que está faltando?"
    Então "Bruno" recebe uma lista contendo "Arroz", "Leite" e "Café"
    E cada item da lista tem um botão de carrinho e um botão de remover
    E a lista não tem botão de fechar compra

  Cenário: Lista vazia não tem botões
    Dado que não há itens pendentes na lista
    Quando "Bruno" envia "o que está faltando?"
    Então "Bruno" recebe uma resposta informando que não falta nada
    E a resposta não tem botões

  # ---------------------------------------------------------------- remover

  Cenário: Remover pelo botão
    Dado que "Bruno" recebeu a lista com botões
    Quando "Bruno" toca no botão de remover do item "Café"
    Então o item "Café" sai da lista de pendentes
    E nenhum item é marcado como comprado
    E nenhum lançamento financeiro é criado
    E a mesma mensagem de "Bruno" é atualizada mostrando "Arroz" e "Leite"
    E a mensagem oferece um botão para devolver "Café" à lista
    E nenhuma chamada ao modelo é feita

  # O caminho de volta é o mesmo de "acabou o chocolate" depois de uma remoção
  # (ADR-0039): um item pendente novo. O alvo do desfazer não muda.
  Cenário: Devolver o item removido pelo botão
    Dado que "Bruno" removeu o item "Café" pelo botão
    Quando "Bruno" toca no botão de devolver "Café"
    Então a lista continua com um único item "Café" pendente
    E o item fica registrado como solicitado por "Bruno"
    E a mesma mensagem de "Bruno" é atualizada mostrando "Arroz", "Leite" e "Café"

  # ---------------------------------------------------------------- carrinho

  Cenário: Pôr item no carrinho não muda a lista da família
    Dado que "Bruno" recebeu a lista com botões
    Quando "Bruno" toca no botão de carrinho do item "Arroz"
    Então o item "Arroz" continua pendente
    E nenhum lançamento financeiro é criado
    E a mesma mensagem de "Bruno" é atualizada mostrando "Arroz" no carrinho
    E a mensagem tem um botão de fechar compra com 1 item

  Cenário: O carrinho é de quem tocou
    Dado que "Bruno" pôs o item "Arroz" no carrinho
    Quando "Ana" envia "o que está faltando?"
    Então "Ana" recebe uma lista contendo "Arroz", "Leite" e "Café"
    E nenhum item aparece no carrinho na lista de "Ana"

  Cenário: Tirar item do carrinho
    Dado que "Bruno" pôs os itens "Arroz" e "Leite" no carrinho
    Quando "Bruno" toca no botão de carrinho do item "Leite"
    Então o carrinho de "Bruno" contém apenas "Arroz"
    E o item "Leite" continua pendente

  # O clique carrega o estado de destino, não "alternar" (ADR-0040, item 6).
  Cenário: Toque duplo no mesmo botão
    Dado que "Bruno" recebeu a lista com botões
    Quando "Bruno" toca duas vezes no mesmo botão de carrinho do item "Arroz"
    Então o carrinho de "Bruno" contém apenas "Arroz"

  Cenário: Mensagem de texto no meio da compra não esvazia o carrinho
    Dado que "Bruno" pôs o item "Arroz" no carrinho
    Quando "Bruno" envia "farmácia 30"
    E "Bruno" envia "o que está faltando?"
    Então a lista de "Bruno" mostra "Arroz" no carrinho

  # ---------------------------------------------------------------- fechar

  Cenário: Fechar compra pelo carrinho
    Dado que o último fechamento do household "Silva" foi lançado em "Mercado"
    E que "Bruno" pôs os itens "Arroz" e "Leite" no carrinho
    Quando "Bruno" toca no botão de fechar compra
    Então nenhum item muda de status ainda
    E "Bruno" recebe uma pergunta curta pedindo o valor, que menciona a categoria "Mercado"
    Quando "Bruno" responde "60"
    Então os itens "Arroz" e "Leite" ficam com status comprado, registrados por "Bruno"
    E o item "Café" continua pendente
    E uma despesa de R$ 60,00 é registrada na categoria "Mercado"
    E o fechamento fica ligado à despesa criada
    E o carrinho de "Bruno" fica vazio
    E nenhuma chamada ao modelo é feita

  # Prova de que o botão gera o mesmo fechamento do texto, e não um parecido:
  # o desfazer da ADR-0032 alcança os dois lados.
  Cenário: Desfazer o fechamento feito pelo botão
    Dado que "Bruno" fechou pelo carrinho os itens "Arroz" e "Leite" por R$ 60,00
    Quando "Bruno" envia "desfazer"
    Então os itens "Arroz" e "Leite" voltam a ficar pendentes
    E a despesa de R$ 60,00 é estornada

  Cenário: Primeiro fechamento do household pergunta a categoria uma vez
    Dado que o household "Silva" nunca fechou uma compra
    E que o household "Silva" também tem a categoria de despesa "Farmácia"
    E que "Bruno" pôs o item "Arroz" no carrinho
    Quando "Bruno" toca no botão de fechar compra
    Então "Bruno" recebe uma pergunta com as categorias de despesa numeradas
    Quando "Bruno" responde "1"
    Então "Bruno" recebe uma pergunta curta pedindo o valor
    Quando "Bruno" responde "15"
    Então uma despesa de R$ 15,00 é registrada na categoria escolhida

  # O carrinho guarda a intenção, não o estado: o que fecha é o que ainda
  # está pendente no momento do fechamento.
  Cenário: Item do carrinho fechado por outro membro antes
    Dado que o último fechamento do household "Silva" foi lançado em "Mercado"
    E que "Bruno" pôs os itens "Arroz" e "Leite" no carrinho
    E que "Ana" enviou "comprei o arroz"
    Quando "Bruno" toca no botão de fechar compra
    Então a pergunta do valor menciona apenas "Leite"
    Quando "Bruno" responde "8"
    Então o item "Leite" fica com status comprado
    E o fechamento registra 1 item

  # ---------------------------------------------------------------- estado velho

  # Fio 1:1 (ADR-0008): a mensagem que a Ana recebeu de manhã não sabe o que o
  # Bruno fez depois. O banco é a fonte de verdade, nunca a mensagem.
  Cenário: Botão de uma lista desatualizada
    Dado que "Ana" recebeu a lista com botões
    E que "Bruno" removeu o item "Café" pelo botão
    Quando "Ana" toca no botão de remover do item "Café"
    Então nenhum item sai da lista
    E "Ana" é avisada de que "Café" já saiu da lista
    E a mesma mensagem de "Ana" é atualizada mostrando "Arroz" e "Leite"

  Cenário: Reentrega do mesmo clique pelo provedor
    Dado que "Bruno" recebeu a lista com botões
    Quando o provedor entrega duas vezes o mesmo clique de "Bruno" no botão de remover do item "Café"
    Então o clique é processado uma única vez
    E "Bruno" não recebe aviso de que "Café" já saiu da lista
