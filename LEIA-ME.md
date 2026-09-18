# GMP Gameport — Fase 3 (redesign visual) completa

Este zip contém **todos os arquivos alterados** da Fase 3, partes 1 e 2
juntas. Extraia por cima da raiz do repositório e commite.

Cobre os itens 2.1 (Sidebar), 2.2 (Biblioteca), 2.3 (Login), 2.4 (Save
State) e 2.5 (Configurações do PSP) do prompt original.

---

## Parte 1 — Sidebar, Biblioteca e Login

### Sidebar + Biblioteca
- `android/res/drawable/gmp_sidebar_bg.xml` — gradiente sutil + borda direita
  no lugar da cor chapada anterior.
- `android/res/drawable/gmp_nav_item_selected.xml` — item ativo com
  preenchimento sólido + barra de destaque à esquerda, em vez do tint
  translúcido genérico.
- `android/res/drawable/gmp_hero_bg.xml` — banner do topo com 3 camadas
  (gradiente diagonal + glow radial + fade inferior), mais rico que antes.
- `android/res/drawable/gmp_game_cover_bg.xml` — capas de jogo com borda
  sutil para mais definição contra o fundo escuro.
- `android/res/layout/activity_library.xml` — sidebar redesenhada (mesmos 3
  destinos: Jogos/Loja/Patches, + engrenagem + avatar); biblioteca com hero
  banner maior e grid de jogos.
- `android/res/layout/item_game_card.xml` — cards maiores e mais nítidos.
- `android/src/.../LibraryActivity.java` — trocado `LinearLayoutManager`
  horizontal (fileira única) por `GridLayoutManager` vertical multi-coluna,
  com colunas calculadas pela largura real da tela (mínimo 2). Muda só a
  **apresentação** — toda a lógica de escaneamento de pastas, permissões e
  clique para jogar continua igual. Conforme combinado, sem
  categorias/gêneros — grid único.
- `android/src/.../GameAdapter.java` — só o raio de cantos ajustado (14dp);
  nenhuma lógica alterada.

### Login
- `android/res/layout/activity_login.xml` — reorganizada de um split
  horizontal 46/54 (apertava em celular retrato) para coluna única com
  scroll: hero compacto no topo, formulário completo abaixo. Todos os 8 IDs
  que `LoginActivity.java` já usava foram preservados — login e cadastro
  continuam funcionando exatamente como antes. Sem seleção de país, sem
  campos extras, sem botão de login social inventado.

**Fora de escopo de propósito:** `activity_account.xml` (tela "Minha conta")
tem o mesmo problema de split apertado, mas não estava na lista de telas
que o prompt pediu para redesenhar — não mexi nela.

---

## Parte 2 — Save State e Configurações do PSP

Essas duas telas são renderizadas pelo motor em C++ (sistema de UI próprio
do PPSSPP), não por XML do Android — muito mais interligadas com o resto do
emulador (achievements, rede, VR, etc.), então a abordagem aqui foi
diferente: investiguei a fundo antes de reescrever qualquer estrutura.

### Descoberta principal
O tema visual `GMP Gameport` (`assets/themes/gmp_gameport.ini`) **já existia
no projeto**, já é o tema padrão (`Core/Config.cpp`), e já está corretamente
empacotado no build Android. Ou seja, as cores/identidade visual dessas duas
telas já deveriam estar corretas antes mesmo desta entrega — não precisei
(nem devia) recriar esse tema do zero.

### O que realmente precisava de correção
- `UI/PauseScreen.cpp`, `SaveSlotView::Draw()` — o destaque do slot
  selecionado usava preto/branco **hardcoded**, ignorando o tema ativo.
  Corrigido para usar o acento do tema (verde-lima no GMP Gameport), então
  agora reflete a identidade visual de verdade.
- `UI/PauseScreen.cpp`, `SaveSlotView::GetContentDimensions()` — a altura
  declarada do slot (90dp) era menor que a miniatura real dentro dele
  (94dp,= 47×2), um mismatch pré-existente. Corrigido para 100dp.

### O que foi verificado e não precisou de mudança
- `UI/GameSettingsScreen.cpp` (Configurações do PSP): nenhuma cor hardcoded
  fora do tema encontrada — já herda o tema corretamente. A estrutura de
  abas (Gráficos, Controles, Áudio, Rede, Ferramentas, Sistema) já bate com
  o que o item 2.5 pede, já é organizada e moderna (inclui busca embutida
  nas configurações). Reescrever essa estrutura teria alto risco de quebrar
  comportamento por pouco ganho visual, já que o tema resolve a identidade.

## Não incluído neste zip

`assets/themes/gmp_gameport.ini` não está aqui porque **não foi alterado** —
já existia correto no repositório antes desta entrega.

## Não testado por compilação real

Como nas entregas anteriores, não tenho ambiente Android SDK/NDK aqui.
Revisão manual feita (IDs preservados, balanceamento de sintaxe, assinaturas
de função conferidas), mas o `build.yml` no Actions é quem valida de fato.
