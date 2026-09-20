# GMP Gameport — Navbar (estilo ilustração) + Login numa tela só

Este zip contém **apenas os arquivos alterados** nesta rodada. Extraia por
cima da raiz do repositório e commite.

## 1. Navbar redesenhada (estilo da ilustração azul)

`android/res/drawable/gmp_nav_item_selected.xml`:
- Trocado de "preenchimento fraco + barra lateral" para uma **pílula sólida
  cheia** (fundo de cor de destaque, cantos bem arredondados) — o mesmo
  visual do item "Home" na ilustração de referência.

`android/res/layout/activity_library.xml` e `activity_store.xml` (a Loja
tinha a mesma sidebar duplicada, recebeu o mesmo tratamento):
- Itens **inativos**: só o ícone, sem nenhum texto embaixo ou do lado.
- Item **ativo**: pílula expandida com ícone + texto ao lado (exatamente
  como "Home" aparece na ilustração — os outros itens da referência também
  são só ícone).
- Removido o label "Perfil" que ficava sempre visível embaixo do avatar,
  para manter consistência com a regra "só o ativo tem texto".

Todos os IDs que os `.java` precisam (`tab_games`, `tab_store`,
`tab_settings`, `tab_profile`, `profile_avatar`, `profile_status_dot`)
continuam presentes — nenhuma lógica foi tocada, só a estrutura visual.

## 2. Login numa tela só, sem scroll

Descoberta importante ao investigar: a tela de Login **já é travada em
paisagem** (`android:screenOrientation="landscape"` no
`AndroidManifest.xml`, não alterado). Minha primeira tentativa desta tarefa
foi uma coluna vertical (pensando em retrato) — não caberia direito na
altura curta que a paisagem realmente tem, então refiz do zero como um
**split horizontal**:

- Hero compacto à esquerda (~42% da largura): logo, nome do app, pitch
  curto, os números de destaque (180+ Patches / PSP e PPSSPP).
- Formulário à direita (~58% da largura): campos "Nome" e "E-mail" lado a
  lado (economiza altura usando a largura disponível), "Senha" embaixo,
  checkbox + "Esqueceu a senha?" na mesma linha, botão "Entrar" no final.

Sem `ScrollView` em lugar nenhum — a tela inteira é dimensionada para caber
na altura de paisagem disponível.

Todos os 8 IDs que `LoginActivity.java` usa (`name_field`, `email_field`,
`password_field`, `login_button`, `login_error_text`, `login_progress`,
`stay_logged_in_checkbox`, `forgot_password_link`) foram conferidos e
preservados — login e cadastro continuam funcionando exatamente como antes.

## Não testado por compilação real

Revisão manual feita (todos os IDs conferidos contra os `.java`
correspondentes), mas não compilado de fato aqui — o `build.yml` no Actions
valida isso. Como não consigo ver o resultado visual renderizado, se algo
ficar apertado ou desalinhado numa tela específica, me manda o print que eu
ajusto os tamanhos/margens.
