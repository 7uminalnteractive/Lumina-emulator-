# GMP Gameport — Round 6 + correção das abas (consolidado)

Este zip junta as duas entregas anteriores num só. Extraia por cima da raiz
do repositório e commite — `UI/GameSettingsScreen.cpp`/`.h` já estão no
estado final (round 6 seguido da correção), sem precisar aplicar as duas
entregas em sequência.

## Paisagem sempre ativa

`android/AndroidManifest.xml`: `PpssppActivity` (jogo, pause, Configurações)
agora tem `android:screenOrientation="landscape"` fixo, igual às outras
Activities do app. Antes seguia a rotação dinâmica do sistema. Você
confirmou que aceita desativar a rotação automática por jogo.

## Configurações: abas no topo, com ícone + texto

- `UI/GameSettingsScreen.h`: `ForceHorizontalTabs() { return true; }` — faz
  as abas ficarem sempre no topo (horizontal), em vez de irem para uma
  coluna lateral esquerda, que seria o padrão do motor agora que o app é
  sempre paisagem.
- `UI/GameSettingsScreen.cpp`: os 6 ícones das abas (Gráficos, Controles,
  Áudio, Rede, Ferramentas, Sistema) foram mantidos — numa tentativa
  anterior eu tinha removido por engano, já corrigido. Também removida a
  flag `TabDialogFlags::HorizontalOnlyIcons`, que fazia o modo horizontal
  mostrar só ícone sem texto (comportamento original do PPSSPP) — agora
  ícone e nome aparecem juntos em cada aba, como nas referências visuais.

## Navbar sem texto em nenhum item

`android/res/layout/activity_library.xml` e `activity_store.xml`: toda a
sidebar (Biblioteca e Loja) é só ícones, sem texto nem no item ativo —
resolve o "Jogos"/"Loja" que quebrava linha dentro do círculo.

## Login: sem a "bola" e sem o checkbox

- `android/res/drawable/gmp_login_hero_bg.xml`: o glow verde era um `oval`
  bem definido (parecia uma bola sólida); trocado por um gradiente radial
  sobre retângulo, sem silhueta de círculo visível.
- `android/res/layout/activity_login.xml` e
  `android/src/.../LoginActivity.java`: removido o checkbox "Permanecer
  conectado" — ele nunca influenciava nada de verdade no código (o valor
  nunca era lido), então o login já sempre "permanecia conectado" por
  padrão; só a UI enganosa foi removida (campo, `findViewById` e import
  correspondentes limpos no Java).

Os 7 IDs que `LoginActivity.java` usa (`name_field`, `email_field`,
`password_field`, `login_button`, `login_error_text`, `login_progress`,
`forgot_password_link`) foram conferidos e continuam batendo entre XML e
Java.

## Não testado por compilação real

Revisão manual feita (balanceamento de chaves/parênteses em todos os
arquivos, IDs conferidos, visibilidade de métodos `protected` conferida),
mas não compilado de fato aqui — o `build.yml` no Actions valida isso.
