# GMP Gameport — Save State estilo PS5

Este zip contém **apenas o arquivo alterado**. Extraia por cima da raiz do
repositório (substitui `UI/PauseScreen.cpp`) e commite.

## O que mudou

A tela de Save State foi redesenhada para se parecer com os cards de save
do PS5 (ver referência que você mandou), em vez da lista vertical numerada
que existia antes.

### Antes
Cada slot era uma linha horizontal: número → miniatura pequena (164×94) →
coluna de texto com botões "Salvar state"/"Carregar state" e a data. As 5
linhas ficavam empilhadas verticalmente, com scroll vertical.

### Agora
Cada slot é um **card vertical** (280×170), com a miniatura preenchendo o
card inteiro — como uma capa de jogo. Por cima da miniatura:
- Número do slot no canto superior esquerdo, com sombra para ler bem sobre
  qualquer imagem de fundo.
- Uma faixa escura (scrim) na parte de baixo do card, com a data do save e
  os botões "Salvar state"/"Carregar state" sobrepostos nela — para ficarem
  legíveis em cima da miniatura, do jeito que os cards do PS5 fazem.

Os 5 cards agora ficam lado a lado num **carrossel horizontal com scroll
próprio**, em vez de empilhados verticalmente. O resto da tela de pause
(coluna de ações à direita: Continuar, Configurações, etc.; avisos de rede;
resumo de conquistas) não foi tocado — só a área dos slots mudou.

## O que foi preservado (nada de lógica mudou)

- Salvar estado, carregar estado, confirmação antes de carregar.
- Seleção de slot clicando no número (com destaque visual do slot ativo).
- Clique na miniatura para ver o save em tela cheia.
- Undo de save/load, rewind, modo hardcore de conquistas (que desativa o
  botão de carregar quando ativo) — tudo intacto.
- Os 5 slots continuam sendo os mesmos 5 slots de sempre; não mexi no
  sistema de save state em si, só em como os cards são desenhados.

## Detalhe técnico (para quem for revisar o código)

Criei uma pequena classe auxiliar `ScrimView` só para desenhar a faixa
escura atrás do texto/botões, inserida na árvore de views **entre** a
miniatura e o conteúdo de texto — isso garante que ela fica por cima da
imagem mas por baixo do texto (ordem de inserção = ordem de desenho), sem
precisar de nenhum hack de desenho manual fora de ordem.

## Não testado por compilação real

Revisão manual feita (balanceamento de chaves/parênteses no arquivo
inteiro, assinaturas de método conferidas, nenhuma referência quebrada),
mas não compilado de fato aqui — o `build.yml` no Actions valida isso.
