# GMP Gameport — Correção de erro de compilação

Este zip contém **apenas o arquivo corrigido**. Extraia por cima da raiz do
repositório (substitui `UI/GameSettingsScreen.cpp`) e commite.

## O erro

```
UI/GameSettingsScreen.cpp:1399:53: error: variable 'sy' cannot be
implicitly captured in a lambda with no capture-default specified
```

No botão "Delete last auto save" que adicionei na rodada anterior, o
lambda do clique capturava só `[this]`, mas usava `sy` e `di` (as
categorias de tradução, variáveis locais da função) sem capturá-las —
erro meu, meu código nunca tinha sido compilado de verdade antes.

## A correção

```cpp
// antes
deleteAutoSave->OnClick.Add([this](UI::EventParams &e) {

// depois
deleteAutoSave->OnClick.Add([this, sy, di](UI::EventParams &e) {
```

`sy` e `di` são `std::shared_ptr<I18NCategory>`, então capturar por valor é
seguro e barato (só incrementa o contador de referência) — nada de
ponteiro pendurado.

Nada mais foi alterado neste arquivo além dessas duas palavras na lista de
captura do lambda.
