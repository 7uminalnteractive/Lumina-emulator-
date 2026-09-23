# GMP Gameport — Tela cheia, ICON0/PIC1, Castellano como ROM padrão

Este zip contém **somente os arquivos alterados** nesta sessão. Extraia por
cima da raiz do repositório e commite. (Só Android.)

## 1. Tela cheia (sem o cinza) + ícone de perfil não corta mais

**Causa:** o tema já pedia `windowLayoutInDisplayCutoutMode=shortEdges`, mas isso
só *permite* desenhar sob o notch. As telas do launcher nunca pediram para
desenhar por baixo das barras do sistema, então o Android deixava a faixa
cinza do notch (esquerda) e a barra preta de gestos (embaixo) — e essa barra
cobria o ícone de perfil, no fim da sidebar.

**Correção:**
- `GmpWindowHelper.java` (NOVO) — estende a janela por baixo das barras, deixa
  status/nav transparentes, desliga o "contrast enforcement" que recriava a
  faixa escura, e liga o modo imersivo (as barras voltam com um swipe da borda).
  Os insets viram **padding só na sidebar e no conteúdo**, então o *fundo* cobre
  100% da tela mas nenhum ícone fica sob o notch ou o gesto.
- Aplicado em: `LibraryActivity`, `StoreActivity` (com sidebar) e
  `AccountActivity`, `LoginActivity`, `ProfileSelectorActivity` (fundo full-bleed).
  Todas reaplicam o imersivo em `onWindowFocusChanged`, porque o Android mostra
  as barras de novo depois de permissão/diálogo.
- `activity_library.xml` / `activity_store.xml` — sidebar passou de `76dp` fixo
  para `wrap_content` + `minWidth=76dp`, para crescer com o notch em vez de
  espremer os ícones. IDs adicionados nas raízes.

## 2. ICON0.png e PIC1.png reconhecidos

- `GameArtwork.java` (NOVO) — procura `ICON0` (card) e `PIC1` (banner) na pasta
  do jogo e, se não achar, em `PSP_GAME/`. Ignora maiúsculas/minúsculas
  (`ICON0.PNG` do PSP original vs `ICON0.png` do instalador — no Android são
  arquivos diferentes). Aceita png/jpg/jpeg/webp.
- `GameItem` — novo campo `backgroundUri` (PIC1). Construtor antigo mantido.
- `LibraryActivity` — ICON0 tem prioridade sobre as capas soltas antigas
  (`covers/`, `capas/`...), que continuam funcionando como fallback. PIC1 vai
  para o banner "hero" do jogo em destaque.
- `activity_library.xml` + `gmp_hero_scrim.xml` (NOVO) — `ImageView` do PIC1
  atrás do texto, com camada escura para o título/botão ficarem legíveis.
  Sem PIC1, o banner é o mesmo gradiente verde de antes.

## 3. Castellano como ROM padrão, interface continua pt-BR

São dois settings separados no PPSSPP, e antes ambos seguiam o português:
- `sLanguageIni` = idioma da **interface** → agora fixo em `pt_BR` por padrão
  (`DefaultLangRegion()`), em vez de seguir o idioma do sistema Android.
- `iLanguage` (`GameLanguage`) = idioma que o **jogo** recebe do PSP → padrão
  mudou de `-1` (Auto, seguia a interface = português) para
  `PSP_SYSTEMPARAM_LANGUAGE_SPANISH`.

`Core/Config.cpp` é o único arquivo alterado. O `es_ES.ini` (que é cópia do
`pt_BR.ini`, da sessão anterior) **não foi tocado**.

> **Atenção:** o padrão só vale onde a chave não existe. Quem já abriu o app
> antes e tem `GameLanguage = -1` (ou `Language = en_US` etc.) no `ppsspp.ini`
> continua com o valor antigo. Para testar, limpe os dados do app ou apague
> essas duas linhas do `GMP/SYSTEM/ppsspp.ini`.

## 4. Caminho do ICON0/PIC1 e formato .gmp (2ª rodada)

**Estrutura confirmada:** `Jogo/Game/<Jogo>/PSP_GAME/ICON0.png` e `PIC1.png`.
- `GameArtwork` procura primeiro em `PSP_GAME/` e só depois na pasta do jogo.
- Aceita "Psp Game", "psp-game", "PSP_GAME"... **apenas para achar as imagens**.
- **Para listar como jogo a pasta tem que se chamar exatamente `PSP_GAME`.**
  O núcleo (`Core/Loaders.cpp`, `System.cpp`) usa esse nome literal e o caminho
  `disc0:/PSP_GAME/USRDIR` é fixo; uma pasta "Psp Game" apareceria na
  Biblioteca mas não iniciaria. O instalador deve criar `PSP_GAME`.

**`.gmp`:** o launcher agora lista `*.gmp`. Nenhuma mudança no núcleo foi
necessária: `Identify_File` e `ConstructBlockDevice` decidem o formato pelo
**cabeçalho do arquivo** (`PK`=ZIP, `CISO`, `CD001`=ISO), nunca pela extensão.
Logo um `.gmp` que seja ISO, CSO ou ZIP-de-ISO renomeado já abre.

⚠ Limitações do `.gmp` como ZIP renomeado (medidas, não estimadas):
- `ZipFileLoader` descomprime o ISO **inteiro para a RAM** (`malloc(dataFileSize_)`).
  Um UMD de 1,1 GB exige ~1,1 GB de RAM livre; jogos de 1,8 GB tendem a fechar
  o app em celulares com pouca memória. ISO/CSO renomeados não têm esse custo.
- Só reconhece ZIP com **um** `.iso/.cso/.chd` na raiz ou 1 nível abaixo. Um zip
  com a estrutura `PSP_GAME/...` (UMD extraído) **não** roda.
- **Não impede distribuição:** renomear de volta para `.iso`/`.zip` restaura o
  arquivo idêntico (testado com `cmp`). É só ofuscação de extensão.

## Não testado por compilação real

Sem Android SDK/`javac` aqui. Validei: XML de todos os layouts/drawables
(parse OK), balanço de chaves/parênteses dos Java alterados, todos os
`R.id.*` novos existem nos layouts, e `androidx.core` já é usado por
`PpssppActivity`. O `build.yml` no Actions faz a compilação de verdade.
