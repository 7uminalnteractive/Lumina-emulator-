#include "Common/Data/Text/I18n.h"
#include "Common/System/OSD.h"
#include "Common/UI/Context.h"
#include "Common/UI/View.h"
#include "Common/UI/ViewGroup.h"

#include "UI/LuminaTecnicoData.h"
#include "UI/LuminaTecnicoScreen.h"

LuminaTecnicoScreen::LuminaTecnicoScreen() : UIBaseDialogScreen() {
	detectedPatchIndex_ = LuminaDetectInstalledPatch();
}

void LuminaTecnicoScreen::CreateViews() {
	using namespace UI;

	auto mp = GetI18NCategory(I18NCat::MAINMENU);
	auto di = GetI18NCategory(I18NCat::DIALOG);

	root_ = new LinearLayout(ORIENT_VERTICAL);

	LinearLayout *topBar = root_->Add(new LinearLayout(ORIENT_HORIZONTAL, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, Margins(10, 10, 10, 0))));
	topBar->Add(new Choice(di->T("Back"), ImageID("I_NAVIGATE_BACK"), new LinearLayoutParams(WRAP_CONTENT, WRAP_CONTENT)))->OnClick.Add([this](UI::EventParams &) {
		TriggerFinish(DR_BACK);
		return UI::EVENT_DONE;
	});
	topBar->Add(new TextView("Contratar Técnico", new LinearLayoutParams(1.0f, Gravity::G_VCENTER, Margins(12, 0))));

	if (detectedPatchIndex_ < 0) {
		// GMP Gameport: nenhum patch reconhecido instalado -- não tem como aplicar
		// um técnico sem saber onde fica o texture.ini certo, então avisamos e
		// paramos aqui em vez de mostrar uma lista que não vai funcionar.
		root_->Add(new TextView(
			"Nenhum patch de Master League reconhecido foi encontrado no seu cartão de memória. "
			"Instale um patch suportado (The Best Patch, ...) antes de contratar um técnico.",
			new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, Margins(20, 20))));
		return;
	}

	const LuminaPatchDef &patch = kLuminaPatches[detectedPatchIndex_];
	root_->Add(new TextView("Patch detectado: " + patch.displayName, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, Margins(20, 10, 20, 4))));
	root_->Add(new TextView("Escolha o técnico que vai assumir o time:", new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, Margins(20, 0, 20, 10))));

	ScrollView *scroll = root_->Add(new ScrollView(ORIENT_VERTICAL, new LinearLayoutParams(FILL_PARENT, FILL_PARENT, 1.0f)));
	LinearLayout *list = scroll->Add(new LinearLayoutList(ORIENT_VERTICAL, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT)));

	if (kLuminaTecnicos.empty()) {
		// TODO: remover este aviso quando kLuminaTecnicos (UI/LuminaTecnicoData.cpp)
		// estiver preenchido com os 19 técnicos reais.
		list->Add(new TextView(
			"A lista de técnicos ainda não foi configurada neste build.",
			new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, Margins(20, 10))));
		return;
	}

	for (size_t i = 0; i < kLuminaTecnicos.size(); i++) {
		Choice *choice = list->Add(new Choice(kLuminaTecnicos[i].displayName, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT)));
		choice->OnClick.Add([this, i](UI::EventParams &) {
			const LuminaPatchDef &patch = kLuminaPatches[detectedPatchIndex_];
			const LuminaTecnicoDef &tecnico = kLuminaTecnicos[i];
			std::string errorStr;
			if (LuminaApplyTecnico(patch, tecnico, &errorStr)) {
				g_OSD.Show(OSDType::MESSAGE_SUCCESS, tecnico.displayName + " contratado!");
				TriggerFinish(DR_OK);
			} else {
				g_OSD.Show(OSDType::MESSAGE_ERROR, errorStr);
			}
			return UI::EVENT_DONE;
		});
	}
}
