// GMP Gameport — "Contratar Técnico" (ML-PRO nativo).
//
// Versão nativa do antigo app ML-PRO (Capacitor/WebView): em vez de ser um app
// separado que pede pro usuário escolher o patch numa lista e depois pede
// permissão de "todos os arquivos" pra escrever o textures.ini via plugin Java,
// esta tela roda dentro do próprio GMP Gameport, detecta automaticamente qual
// patch está instalado e escreve o textures.ini direto pelo File:: nativo
// (ver LuminaTecnicoData.h/.cpp). O usuário só precisa tocar no técnico desejado
// -- a troca é aplicada na hora, sem passo de confirmação extra.
#pragma once

#include "Common/UI/UIScreen.h"
#include "Common/UI/ViewGroup.h"
#include "UI/BaseScreens.h"

class LuminaTecnicoScreen : public UIBaseDialogScreen {
public:
	LuminaTecnicoScreen();

	const char *tag() const override { return "LuminaTecnico"; }

protected:
	void CreateViews() override;

private:
	void OnPickTecnico(UI::EventParams &e);

	// Índice em kLuminaPatches detectado na abertura da tela (-1 = nenhum patch reconhecido).
	int detectedPatchIndex_ = -1;
};
