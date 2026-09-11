# GMP Gameport — Redesign completo (Fases A a E)

Todas as fases pedidas foram concluídas. Este pacote contém só os arquivos
novos/editados, nos mesmos caminhos do projeto original.

## Como aplicar

**Substituir** (mesmo caminho, mesmo nome):
- `android/AndroidManifest.xml`
- `android/res/values/strings.xml`
- `android/res/values/lumina_colors.xml`
- `android/res/layout/activity_login.xml`
- `android/res/layout/activity_library.xml`
- `android/res/layout/activity_account.xml`
- `android/res/layout/activity_patches.xml`
- `android/res/layout/item_game_card.xml`
- `android/normal/res/values/ic_launcher_background.xml`
- `android/src/org/ppsspp/ppsspp/LoginActivity.java`
- `android/src/org/ppsspp/ppsspp/AccountActivity.java`
- `android/src/org/ppsspp/ppsspp/PatchesActivity.java`
- `android/src/org/ppsspp/ppsspp/LibraryActivity.java`
- `Core/Config.cpp`
- Todos os `ic_launcher*.png` dentro de `android/normal/res/mipmap-*/` (5
  densidades: mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi)
- `android/src/normal/ic_launcher-playstore.png`

**Adicionar** (arquivos novos):
- `android/res/layout/activity_profile_selector.xml`
- `android/res/layout/activity_store.xml`
- `android/res/layout/item_profile.xml`
- `android/res/layout/item_store_card.xml`
- `android/res/drawable/gmp_avatar_add_bg.xml`
- `android/res/drawable/gmp_avatar_bg.xml`
- `android/res/drawable/gmp_badge_bg.xml`
- `android/res/drawable/gmp_button_ghost_bg.xml`
- `android/res/drawable/gmp_button_primary_bg.xml`
- `android/res/drawable/gmp_card_bg.xml`
- `android/res/drawable/gmp_game_cover_bg.xml`
- `android/res/drawable/gmp_hero_bg.xml`
- `android/res/drawable/gmp_input_bg.xml`
- `android/res/drawable/gmp_login_hero_bg.xml`
- `android/res/drawable/gmp_logo_card_bg.xml`
- `android/res/drawable/gmp_nav_item_selected.xml`
- `android/res/drawable/gmp_sidebar_bg.xml`
- `android/res/drawable-xxhdpi/gmp_symbol.png`
- `android/src/org/ppsspp/ppsspp/LocalAccount.java`
- `android/src/org/ppsspp/ppsspp/AccountStore.java`
- `android/src/org/ppsspp/ppsspp/AuthClient.java`
- `android/src/org/ppsspp/ppsspp/ProfileSelectorActivity.java`
- `android/src/org/ppsspp/ppsspp/StoreItem.java`
- `android/src/org/ppsspp/ppsspp/StoreCatalog.java`
- `android/src/org/ppsspp/ppsspp/StoreAdapter.java`
- `android/src/org/ppsspp/ppsspp/StoreActivity.java`
- `assets/themes/gmp_gameport.ini`

**Remover** (obsoletos, substituídos ou não usados por nada):
- `android/src/org/ppsspp/ppsspp/SessionManager.java`
- `android/src/org/ppsspp/ppsspp/SupabaseAuthClient.java`
- `assets/themes/lumina.ini`
- `android/res/drawable/lumina_hero_bg.xml` (se ainda existir de antes)
- `android/res/drawable/lumina_button_bg.xml` (se ainda existir de antes)
- `android/res/drawable/lumina_input_bg.xml` (se ainda existir de antes)
- `android/res/drawable/lumina_badge_bg.xml` (se ainda existir de antes)

**Não remover:** `android/res/drawable/lumina_online_dot.xml` continua em
uso (ponto de "online" no avatar).

---

## FASE A — Limpeza + fundação de multi-perfil

- **Supabase da Lumina removido por completo.** A URL e a chave pública de
  um projeto Supabase de outra pessoa (o "Lumina+") estavam hardcoded no
  antigo `SupabaseAuthClient.java`. Esse arquivo foi apagado, assim como o
  `SessionManager.java` que dependia dele.
- **Multi-perfil local, 100% funcional, sem backend:**
  - `LocalAccount` — representa uma conta salva no aparelho.
  - `AccountStore` — guarda a lista de contas e qual está ativa
    (SharedPreferences).
  - `AuthClient` — substitui o cliente Supabase antigo. Por enquanto só
    valida formato de e-mail/senha preenchida (não há verificação de
    senha real, pois não há servidor). A interface foi desenhada para
    trocar a implementação interna por uma chamada real mais tarde sem
    precisar mudar as telas.
- **Fluxo implementado:** primeiro login preenche formulário → conta vira
  perfil salvo → próximas aberturas do app pulam direto para o seletor de
  perfil → escolher perfil entra direto na biblioteca sem pedir senha de
  novo → "+" no seletor abre o formulário de novo para adicionar outra
  conta → "Sair" na tela de Conta volta ao seletor (não apaga o perfil).

## FASE B — Redesign visual de login e seletor de perfil

- **Login inteiramente reconstruído**, agora em duas colunas bem
  diferenciadas: painel de marca à esquerda (logo real do GMP Gameport
  dentro de um card branco arredondado — igual ao ícone do app — título
  grande, texto de apoio, badges "180+ Patches" / "PSP e PPSSPP") e
  formulário à direita com campos maiores (56dp de altura), mais
  espaçamento entre eles, hierarquia tipográfica clara.
- **Fundo do painel de marca** ganhou gradiente + glow radial sutil em
  vez de cor chapada (`gmp_login_hero_bg.xml`), para não ficar "flat"
  demais.
- **Seletor de perfil também redesenhado**: mesmo fundo com glow do
  login, símbolo do app no topo, avatares maiores (104dp) com sombra.

## FASE C — Biblioteca vira Loja de Games

- **Separação clara de conceitos**: `LibraryActivity` continua sendo
  "jogos instalados no aparelho" (escaneia a pasta PSP/GAME, é o que já
  existia e funcionava bem). `StoreActivity` é nova: "jogos/patches
  disponíveis para baixar".
- **Sidebar da Biblioteca ganhou 3ª aba** ("Loja", entre "Jogos" e
  "Patches"), navegação cruzada entre as três telas.
- **Catálogo mock** (`StoreCatalog`) espelha manualmente os mesmos 2
  produtos do site GMPES (Patch Conmebol, Patch Europeu) — quando a Loja
  for ligada a um backend real, o ideal é que app e site consumam a
  mesma fonte de dados.
- Cards da loja mostram categoria, título, preço, selo e botão "Baixar"
  (por enquanto avisa que o download ainda não está disponível — sem
  backend, não há arquivo real para entregar ainda).

## FASE D — Tema nativo (cor verde-lima aplicada de verdade)

Este foi o ponto mais importante tecnicamente: a tela de configurações,
o menu de pausa/save states, e toda a UI nativa do emulador (renderizada
em C++, não em Android XML) usam um sistema de tema próprio, carregado
de arquivos `.ini` em `assets/themes/`. O tema ativo por padrão era
`"Lumina"` (azul, herdado do fork original) — por isso as capturas de
tela que você mandou (imagens 5 e 7) ainda apareciam sem o verde.

- **Novo arquivo `assets/themes/gmp_gameport.ini`**, com a mesma paleta
  verde-lima usada no Android e no site (fundo `#1B240C`, destaque
  `#9AE637`).
- **`Core/Config.cpp` atualizado** para usar `"GMP Gameport"` como tema
  padrão em vez de `"Lumina"`, mantendo a mesma lógica cuidadosa que já
  existia para forçar esse padrão mesmo em configs salvas antes dessa
  mudança (sem essa lógica, alguém que já teria aberto o app uma vez
  ficaria preso no tema antigo).
- **`assets/themes/lumina.ini` (azul) removido.**

## FASE E — Save states / menu de pausa estilo PlayStation

Depois de investigar `UI/PauseScreen.cpp` (a tela que aparece ao pausar
o jogo, com save states e o menu que você mostrou na imagem 5), a
conclusão foi que **a estrutura de layout já é praticamente idêntica à
referência do PS5**: já é horizontal em modo paisagem, já tem uma coluna
de saves à esquerda e uma coluna de ações à direita ("Continue", "Game
Settings", "Display layout & effects", "Edit touch control layout",
etc. — os mesmos itens da sua captura, só que em outro idioma).

O que fazia a tela parecer "diferente" da referência era puramente a
cor: confirmei no código (`Choice::Draw`, em `Common/UI/View.cpp`) que
esses itens de menu desenham seus ícones e texto usando
`style.fgColor`, vindo diretamente do tema ativo. Ou seja, **a correção
da Fase D já resolve a Fase E automaticamente** — não foi necessário
(nem seguro) reescrever a lógica C++ desta tela. Ela já vai aparecer em
verde-lima assim que o novo tema for aplicado.

---

## Validação feita

- Todo XML novo/editado (Android) validado sintaticamente com parser
- `AndroidManifest.xml` validado
- Todo `R.id.*` que cada `.java` procura foi cruzado contra o layout
  correspondente (6 Activities conferidas uma a uma)
- Todo `@drawable/` referenciado em todos os layouts existe de fato
- Chaves e parênteses de cada arquivo `.java` novo/editado contados e
  batendo
- Confirmado que nenhuma referência a `SessionManager` ou
  `SupabaseAuthClient` sobrou em nenhum arquivo antes de apagá-los
- Confirmado que nenhuma credencial ou URL de terceiro (Supabase da
  Lumina) existe em qualquer lugar do código -- as únicas menções a
  "Supabase" que restam são comentários meus explicando onde plugar o
  backend real no futuro
- `gmp_gameport.ini` validado como INI bem formado, com nome de seção
  batendo exatamente com o valor usado em `Config.cpp`
- Confirmado via leitura de código (`Theme.cpp`, `View.cpp`) que o
  sistema de temas nativo escaneia `assets/themes/*.ini`
  automaticamente por nome de seção, sem precisar registrar o arquivo
  em nenhum outro lugar, e que ícones/texto dos menus herdam a cor do
  tema ativo

## O que ficou de fora deste pacote (intencional)

- **Pacote técnico** (`org.ppsspp.ppsspp`) continua o mesmo, por decisão
  sua -- está amarrado a 43 funções JNI em C++ e mudar isso tinha risco
  real de quebrar o app inteiro sem eu poder compilar para testar antes.
- **"Patches"** continua sendo a WebView antiga, apontando para o
  domínio Vercel do fork Lumina -- por decisão sua, também não mexi
  nisso ainda.
- **Backend real (Supabase)**: por decisão sua, esta etapa é 100% local
  por enquanto. `AccountStore`, `AuthClient` e `StoreCatalog` foram
  desenhados para facilitar essa troca quando você estiver pronto -- o
  comentário no topo de cada um explica exatamente o que precisa mudar.
