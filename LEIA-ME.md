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

## 5. Instalador (3ª rodada)

**Fluxo:** Biblioteca consulta o catálogo -> mostra "Disponíveis para baixar"
(só o que o usuário tem direito: patch comprado OU plano ativo, e que ainda não
está instalado) -> tocar num card abre o Instalador -> baixa para
`GMP/Jogo/Game/` -> o jogo aparece na Biblioteca ao voltar.

| Arquivo | Papel |
|---|---|
| `GameInstaller` | Motor de download (Java puro). `.part` + rename atômico, retoma por HTTP Range, retenta sozinho, confere tamanho e SHA-256, valida o nome do arquivo, pede o link de novo a cada tentativa. |
| `InstallerActivity` | Tela de progresso. Voltar = pausa (o `.part` fica); "Baixar" de novo continua. Trava contra duas threads no mesmo arquivo. |
| `CatalogSource` | Interface: "o que este usuário pode baixar" e "link do arquivo". |
| `LocalJsonCatalogSource` | **Banco de teste**: lê `assets/gmp_catalog.json`. |
| `GmpCatalog` | **Único ponto de troca** para o banco real. |
| `CatalogAccess` | A regra "patch OU plano", em um lugar só. |
| `GmpFolders` | Tipo -> pasta (`game`->Jogo/Game, `save`->Jogo/Save, `texture`->Textura, `system`->Sistema). O catálogo informa o TIPO, nunca um caminho. |
| `DownloadAdapter` | Cards da seção nova (reusa `item_game_card`). |

**Como usar o banco de teste** (`assets/gmp_catalog.json`): preencha em cada
jogo `downloadUrl` (**https**), `sizeBytes` e `sha256`. Com `sizeBytes: 0` e
`sha256: ""` o instalador não confere nada -- aceitável só para testar. O
`unknownUser` faz qualquer e-mail receber acesso ao patch `conmebol`, porque o
login ainda é simulado; **remova-o** quando o login real entrar.

### ⚠ Segurança: leia antes de vender

1. **O login atual é falso.** `LoginActivity` usa `AuthClient`, que aceita
   qualquer e-mail e senha. O `SupabaseAuthClient` real existe mas nenhuma tela
   o usa. Enquanto for assim, "quem tem direito a quê" é só decoração.
2. **O catálogo de teste vai dentro do APK** -- qualquer pessoa o lê.
3. **Verificar acesso no app não protege nada**: o app roda no aparelho do
   usuário. O bloqueio de verdade precisa estar no servidor (política RLS no
   Supabase + link assinado que expira, para o arquivo em bucket privado).
   `CatalogSource.resolveDownloadUrl` já foi desenhado para receber esse link.
4. Um link público de download vaza com uma única pessoa compartilhando.

### Limitações desta versão
- O download vive na tela: se o Android encerrar o app, a retomada cobre, mas o
  ideal é um **serviço em primeiro plano** com notificação (próximo passo).
- Só a seção "game" aparece na Biblioteca; `save/texture/system` já têm pasta
  mas ainda não há fluxo para eles.
- O bloqueio do GMP inteiro para quem não comprou patch/plano **não** foi feito
  (depende do login real).
- Nada é descompactado: o `.gmp` é gravado como veio.

### Testes que rodam no computador (só JDK 17+; sem Android)
```
S=android/src/org/ppsspp/ppsspp; T=android/test
# 1) motor de download, contra um servidor HTTP local com falhas injetadas
javac -d out $S/GameInstaller.java $T/org/ppsspp/ppsspp/GameInstallerTest.java
java -cp out org.ppsspp.ppsspp.GameInstallerTest
# 2) regra de acesso, pastas, rótulo de tamanho
javac -d out $S/CatalogGame.java $S/CatalogAccess.java $S/GmpFolders.java $T/org/ppsspp/ppsspp/CatalogAccessTest.java
java -cp out org.ppsspp.ppsspp.CatalogAccessTest
# 3) leitor do catálogo contra o gmp_catalog.json real (org.json substituído por stubs)
javac -d out $(find $T/stubs -name '*.java') $S/LocalJsonCatalogSource.java $S/CatalogSource.java \
      $S/CatalogGame.java $S/CatalogAccess.java $S/GameInstaller.java $T/org/ppsspp/ppsspp/LocalJsonCatalogSourceTest.java
java -cp out org.ppsspp.ppsspp.LocalJsonCatalogSourceTest assets/gmp_catalog.json
```
`android/test/` fica fora de `android/src/`, então **não entra no APK**.

## Não testado por compilação real

Sem Android SDK aqui. **Testado de verdade (computador):** motor de download
(27 casos), regra de acesso (17) e leitor do catálogo (20).
**Só revisado, sem rodar:** `InstallerActivity`, `DownloadAdapter`, a integração
na `LibraryActivity` (seção nova, `empty_state`), os layouts e o manifest --
XML válido, chaves balanceadas, todo `R.id`/`R.layout` referenciado existe. O
`build.yml` no Actions faz a compilação de verdade; mande o log se der erro.
