# GMP Gameport — mudanças de redesign (landscape + verde)

Este pacote contém APENAS os arquivos alterados/criados dentro da pasta
`android/` do projeto Lumina-emulator. Substitua-os no lugar dos originais.

## Como aplicar

1. **Substituir** (mesmo caminho, mesmo nome):
   - `android/AndroidManifest.xml`
   - `android/res/values/lumina_colors.xml`
   - `android/res/layout/activity_login.xml`
   - `android/res/layout/activity_library.xml`
   - `android/res/layout/activity_account.xml`
   - `android/res/layout/activity_patches.xml`
   - `android/res/layout/item_game_card.xml`

2. **Adicionar** (arquivos novos, não existiam antes):
   - `android/res/drawable/gmp_avatar_bg.xml`
   - `android/res/drawable/gmp_badge_bg.xml`
   - `android/res/drawable/gmp_button_ghost_bg.xml`
   - `android/res/drawable/gmp_button_primary_bg.xml`
   - `android/res/drawable/gmp_card_bg.xml`
   - `android/res/drawable/gmp_game_cover_bg.xml`
   - `android/res/drawable/gmp_hero_bg.xml`
   - `android/res/drawable/gmp_input_bg.xml`
   - `android/res/drawable/gmp_nav_item_selected.xml`
   - `android/res/drawable/gmp_sidebar_bg.xml`

3. **Remover** (ficaram obsoletos, não são mais usados por nenhum layout):
   - `android/res/drawable/lumina_hero_bg.xml`
   - `android/res/drawable/lumina_button_bg.xml`
   - `android/res/drawable/lumina_input_bg.xml`
   - `android/res/drawable/lumina_badge_bg.xml`

   (Não remova `lumina_online_dot.xml` — ele continua em uso.)

## O que mudou

- **Paleta**: fundo verde `#1B240C`, destaque lima vibrante `#9AE637`
  (as variáveis continuam se chamando `lumina_*` para não quebrar nada
  que ainda referencie o nome antigo — só os valores de cor mudaram)
- **Orientação**: as 4 telas (Login, Biblioteca, Conta, Patches) agora
  ficam travadas em paisagem (landscape)
- **Layout**: as 4 telas foram redesenhadas do zero em formato horizontal,
  inspiradas nas referências de GameHub/Play Gamestore que você mandou
  (sidebar de navegação, hero banner, cards com gradiente)
- **Patches**: só o visual (toolbar) mudou — o WebView e a URL continuam
  exatamente como estavam, por sua escolha

## Validação feita

Todo `findViewById(R.id....)` usado nos arquivos `.java` correspondentes
(`LoginActivity`, `LibraryActivity`, `AccountActivity`, `PatchesActivity`)
foi conferido contra os novos layouts — todos os IDs necessários existem.
Todos os XMLs foram validados sintaticamente. Todas as referências de
`@drawable/` e `@color/` usadas nos novos arquivos existem.

Isso reduz bastante a chance de erro de compilação, mas como não há como
compilar/rodar o projeto aqui, a validação final é o build real via
GitHub Actions.
