// Copyright (c) 2014- PPSSPP Project.

// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, version 2.0 or later versions.

// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License 2.0 for more details.

// A copy of the GPL 2.0 should have been included with the program.
// If not, see http://www.gnu.org/licenses/

// Official git repository and contact information can be found at
// https://github.com/hrydgard/ppsspp and http://www.ppsspp.org/.

#include <algorithm>
#include <memory>

#include "Common/Render/DrawBuffer.h"
#include "Common/UI/View.h"
#include "Common/UI/ViewGroup.h"
#include "Common/UI/Context.h"
#include "Common/UI/UIScreen.h"
#include "Common/UI/PopupScreens.h"
#include "Common/UI/Notice.h"
#include "Common/GPU/thin3d.h"

#include "Common/Data/Text/I18n.h"
#include "Common/Data/Text/Parsers.h"
#include "Common/StringUtils.h"
#include "Common/System/OSD.h"
#include "Common/System/Request.h"
#include "Common/VR/PPSSPPVR.h"
#include "Common/UI/AsyncImageFileView.h"

#include "Core/KeyMap.h"
#include "Core/Reporting.h"
#include "Core/Dialog/PSPSaveDialog.h"
#include "Core/SaveState.h"
#include "Core/System.h"
#include "Core/Core.h"
#include "Core/Config.h"
#include "Core/RetroAchievements.h"
#include "Core/ELF/ParamSFO.h"
#include "Core/HLE/sceDisplay.h"
#include "Core/HLE/sceUmd.h"
#include "Core/HLE/sceNet.h"
#include "Core/HLE/sceNetInet.h"
#include "Core/HLE/sceNetAdhoc.h"
#include "Core/Util/GameDB.h"
#include "Core/HLE/NetAdhocCommon.h"

#include "GPU/GPUCommon.h"
#include "GPU/GPUState.h"

#include "UI/DevScreens.h"
#include "UI/EmuScreen.h"
#include "UI/PauseScreen.h"
#include "UI/LoadStateConfirmScreen.h"
#include "UI/GameSettingsScreen.h"
#include "UI/ReportScreen.h"
#include "UI/CwCheatScreen.h"
#include "UI/MainScreen.h"
#include "UI/GameScreen.h"
#include "UI/OnScreenDisplay.h"
#include "UI/GameInfoCache.h"
#include "UI/DisplayLayoutScreen.h"
#include "UI/RetroAchievementScreens.h"
#include "UI/TouchControlLayoutScreen.h"
#include "UI/BackgroundAudio.h"
#include "UI/MiscViews.h"
#include "UI/AdhocServerScreen.h"

// This is in an objective-C file.
#if PPSSPP_PLATFORM(IOS)
void copyDeepLinkForPath(std::string_view filePath);
#endif

void ShowMessageAfterSaveStateAction(SaveState::Status status, std::string_view message, std::string_view metadata) {
	if (!message.empty()) {
		g_OSD.Show(status == SaveState::Status::SUCCESS ? OSDType::MESSAGE_SUCCESS : OSDType::MESSAGE_ERROR,
			message, metadata, status == SaveState::Status::SUCCESS ? 2.0 : 5.0);
	}
}

class ScreenshotViewScreen : public UI::PopupScreen {
public:
	ScreenshotViewScreen(const Path &screenshotFilename, std::string_view saveStatePrefix, std::string_view title, int slot, Path gamePath)
		: PopupScreen(title), screenshotFilename_(screenshotFilename), saveStatePrefix_(saveStatePrefix), slot_(slot), gamePath_(gamePath), title_(title) {}

	int GetSlot() const {
		return slot_;
	}

	const char *tag() const override { return "ScreenshotView"; }

protected:
	bool FillVertical() const override { return false; }
	UI::Size PopupWidth() const override { return 500; }
	bool ShowButtons() const override { return true; }

	void CreatePopupContents(UI::ViewGroup *parent) override {
		using namespace UI;
		auto pa = GetI18NCategory(I18NCat::PAUSE);
		auto di = GetI18NCategory(I18NCat::DIALOG);

		ScrollView *scroll = new ScrollView(ORIENT_VERTICAL, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, 1.0f));
		LinearLayout *content = new LinearLayout(ORIENT_VERTICAL);
		Margins contentMargins(10, 0);
		content->Add(new AsyncImageFileView(screenshotFilename_, IS_KEEP_ASPECT, new LinearLayoutParams(480, 272, contentMargins)))->SetCanBeFocused(false);

		GridLayoutSettings gridsettings(235, 64, 10);
		gridsettings.fillCells = true;
		GridLayout *grid = content->Add(new GridLayoutList(gridsettings, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, Margins(10, 0))));

		const bool hasUndo = SaveState::HasUndoSaveInSlot(saveStatePrefix_, slot_);
		const bool undoEnabled = g_Config.bEnableStateUndo;

		Choice *undoButton = nullptr;
		if (undoEnabled || hasUndo) {
			// Show the undo button if state undo is enabled in settings, OR one is available. We can load it
			// even if making new undo states is not enabled.
			undoButton = new Choice(pa->T("Undo last save"));
			undoButton->SetEnabled(hasUndo);
		}

		grid->Add(new Choice(pa->T("Save State"), ImageID("I_FILE_SAVE")))->OnClick.Handle(this, &ScreenshotViewScreen::OnSaveState);
		// We can unconditionally show the load state button, because you can only pop this dialog up if a state exists.
		grid->Add(new Choice(pa->T("Load State"), ImageID("I_FOLDER_OPEN")))->OnClick.Handle(this, &ScreenshotViewScreen::OnLoadState);
		grid->Add(new Choice(di->T("Delete"), ImageID("I_TRASHCAN")))->OnClick.Handle(this, &ScreenshotViewScreen::OnDeleteState);
		if (undoButton) {
			grid->Add(undoButton)->OnClick.Handle(this, &ScreenshotViewScreen::OnUndoState);
		}
		grid->Add(new Choice(di->T("Back"), ImageID("I_NAVIGATE_BACK")))->OnClick.Handle<UIScreen>(this, &UIScreen::OnBack);

		scroll->Add(content);
		parent->Add(scroll);
	}

private:
	void OnSaveState(UI::EventParams &e);
	void OnLoadState(UI::EventParams &e);
	void OnUndoState(UI::EventParams &e);
	void OnDeleteState(UI::EventParams &e);

	Path screenshotFilename_;
	Path gamePath_;
	std::string saveStatePrefix_;
	std::string title_;
	int slot_;
};

void ScreenshotViewScreen::OnSaveState(UI::EventParams &e) {
	if (!NetworkWarnUserIfOnlineAndCantSavestate()) {
		g_Config.iCurrentStateSlot = slot_;
		SaveState::SaveSlot(saveStatePrefix_, slot_, &ShowMessageAfterSaveStateAction);
		TriggerFinish(DR_OK); //OK will close the pause screen as well
	}
}

void ScreenshotViewScreen::OnLoadState(UI::EventParams &e) {
	if (!NetworkWarnUserIfOnlineAndCantSavestate()) {
		g_Config.iCurrentStateSlot = slot_;
		if (g_Config.bConfirmLoadState) {
			screenManager()->push(new LoadStateConfirmScreen(saveStatePrefix_, slot_, [this](bool result) {
				if (result) {
					SaveState::LoadSlot(saveStatePrefix_, slot_, &ShowMessageAfterSaveStateAction);
					TriggerFinish(DR_OK);
				}
			}));
		} else {
			SaveState::LoadSlot(saveStatePrefix_, slot_, &ShowMessageAfterSaveStateAction);
			TriggerFinish(DR_OK);
		}
	}
}

void ScreenshotViewScreen::OnUndoState(UI::EventParams &e) {
	if (!NetworkWarnUserIfOnlineAndCantSavestate()) {
		SaveState::UndoSaveSlot(saveStatePrefix_, slot_);
		TriggerFinish(DR_YES);  // DR_YES signals that we need a refresh, but not to close the pause menu.
	}
}

void ScreenshotViewScreen::OnDeleteState(UI::EventParams &e) {
	auto di = GetI18NCategory(I18NCat::DIALOG);

	std::shared_ptr<GameInfo> info = g_gameInfoCache->GetInfo(NULL, gamePath_, GameInfoFlags::PARAM_SFO);

	std::string_view title = di->T("Delete");
	std::string message = std::string(di->T("DeleteConfirmSaveState")) + "\n\n" + info->GetTitle() + " (" + info->id + ")";
	message += "\n\n" + title_;

	// TODO: Also show the screenshot on the confirmation screen?

	screenManager()->push(new UI::MessagePopupScreen(title, message, di->T("Delete"), di->T("Cancel"), [this](bool result) {
		if (result) {
			SaveState::DeleteSlot(saveStatePrefix_, slot_);
			TriggerFinish(DR_YES);  // DR_YES signals that we need a refresh, but not to close the pause menu.
		}
	}));
}

// GMP Gameport: view auxiliar simples que só preenche seus bounds com uma
// cor sólida -- usada como scrim (faixa escura) atrás da data/botões dos
// cards de Save State, para dar legibilidade por cima da miniatura sem
// escurecer o texto/botões junto (que ficam por cima dela, inseridos depois
// na árvore).
class ScrimView : public UI::View {
public:
	ScrimView(uint32_t color, UI::LayoutParams *layoutParams) : UI::View(layoutParams), color_(color) {}
	void GetContentDimensions(const UIContext &dc, float &w, float &h) const override {
		w = 0; h = 0;
	}
	void Draw(UIContext &dc) override {
		dc.FillRect(UI::Drawable(color_), GetBounds());
	}
private:
	uint32_t color_;
};

class SaveSlotView : public UI::AnchorLayout {
public:
	SaveSlotView(std::string_view saveStatePrefix, int slot, UI::LayoutParams *layoutParams = nullptr);

	void GetContentDimensions(const UIContext &dc, float &w, float &h) const override {
		// GMP Gameport: card vertical estilo PS5 -- mais largo que alto ao
		// estilo "capa de jogo" (280x170), a miniatura ocupa o card inteiro
		// e número/data/botões ficam sobrepostos por cima dela.
		w = 280; h = 170;
	}

	void Draw(UIContext &dc) override;

	int GetSlot() const {
		return slot_;
	}

	Path GetScreenshotFilename() const {
		return screenshotFilename_;
	}

	std::string GetScreenshotTitle() const {
		return SaveState::GetSlotDateAsString(saveStatePrefix_, slot_);
	}

	UI::Event OnStateLoaded;
	UI::Event OnStateSaved;
	UI::Event OnScreenshotClicked;
	UI::Event OnSelected;
	UI::Event OnLoadRequested;

private:
	void OnSaveState(UI::EventParams &e);
	void OnLoadState(UI::EventParams &e);

	UI::Button *saveStateButton_ = nullptr;
	UI::Button *loadStateButton_ = nullptr;

	int slot_;
	std::string saveStatePrefix_;
	Path screenshotFilename_;
};

SaveSlotView::SaveSlotView(std::string_view saveStatePrefix, int slot, UI::LayoutParams *layoutParams) : UI::AnchorLayout(layoutParams), slot_(slot), saveStatePrefix_(saveStatePrefix) {
	using namespace UI;

	screenshotFilename_ = SaveState::GenerateSaveSlotPath(saveStatePrefix_, slot, SaveState::SCREENSHOT_EXTENSION);

	std::string number = StringFromFormat("%d", slot + 1);

	// GMP Gameport: o card inteiro é a miniatura (estilo capa de jogo do
	// PS5), em vez de um thumbnail pequeno ao lado de uma coluna de texto.
	// Dimensões absolutas (280x170) em vez de FILL_PARENT: como o próprio
	// card também tem tamanho fixo (ver LinearLayoutParams(280, 170, ...) em
	// CreateSavestateControls), FILL_PARENT aqui criava uma dependência
	// circular de medida dentro do ScrollView horizontal que colapsava o
	// card e sobrepunha o texto -- por isso os valores absolutos.
	AsyncImageFileView *fv = Add(new AsyncImageFileView(screenshotFilename_, IS_DEFAULT, new AnchorLayoutParams(280, 170, 0, 0, NONE, NONE)));

	auto pa = GetI18NCategory(I18NCat::PAUSE);
	auto sy = GetI18NCategory(I18NCat::SYSTEM);

	// GMP Gameport: scrim inserido ANTES do texto/botões (mas DEPOIS da
	// imagem) na árvore, então é desenhado por cima da miniatura e por baixo
	// do conteúdo da faixa inferior -- exatamente a ordem de camadas que dá
	// o efeito do card do PS5.
	Add(new ScrimView(0xD0000000, new AnchorLayoutParams(280, 78, 0, NONE, NONE, 0)));

	// TEMP HACK: use some other view, like a Choice, themed differently to enable keyboard access to selection.
	ClickableTextView *numberView = Add(new ClickableTextView(number, new AnchorLayoutParams(WRAP_CONTENT, WRAP_CONTENT, 10, 8, NONE, NONE)));
	numberView->SetBig(true);
	numberView->SetShadow(true);
	numberView->OnClick.Add([this](UI::EventParams &e) {
		e.v = this;
		OnSelected.Trigger(e);
	});

	fv->OnClick.Add([this](UI::EventParams &e) {
		e.v = this;
		OnScreenshotClicked.Trigger(e);
	});

	// GMP Gameport: faixa inferior sobreposta (data + botões), como os
	// cards de save do PS5 -- em vez de uma coluna de texto ao lado.
	LinearLayout *bottomBar = Add(new LinearLayout(ORIENT_VERTICAL, new AnchorLayoutParams(280, WRAP_CONTENT, 0, NONE, NONE, 0)));
	bottomBar->SetSpacing(4.0f);

	LinearLayout *buttons = new LinearLayout(ORIENT_HORIZONTAL, new LinearLayoutParams(WRAP_CONTENT, WRAP_CONTENT, Margins(8, 6, 8, 2)));
	buttons->SetSpacing(8.0f);
	bottomBar->Add(buttons);

	saveStateButton_ = buttons->Add(new Button(pa->T("Save State"), new LinearLayoutParams(0.0, Gravity::G_VCENTER)));
	saveStateButton_->OnClick.Handle(this, &SaveSlotView::OnSaveState);

	if (SaveState::HasSaveInSlot(saveStatePrefix_, slot_)) {
		if (!Achievements::HardcoreModeActive()) {
			loadStateButton_ = buttons->Add(new Button(pa->T("Load State"), new LinearLayoutParams(0.0, Gravity::G_VCENTER)));
			loadStateButton_->OnClick.Handle(this, &SaveSlotView::OnLoadState);
		}

		std::string dateStr = SaveState::GetSlotDateAsString(saveStatePrefix_, slot_);

		if (slot_ == g_Config.iAutoLoadSaveState - 3) {
			dateStr += " (" + std::string(sy->T("Auto load savestate")) + ")";
		}

		if (!dateStr.empty()) {
			TextView *dateView = new TextView(dateStr, new LinearLayoutParams(Margins(8, 0, 8, 6)));
			dateView->SetSmall(true);
			dateView->SetShadow(true);
			bottomBar->Add(dateView);
		}
	} else {
		fv->SetFilename(Path());
	}
}

void SaveSlotView::Draw(UIContext &dc) {
	if (g_Config.iCurrentStateSlot == slot_) {
		// GMP Gameport: usa o acento do tema ativo (lima no tema GMP Gameport)
		// em vez do preto/branco translúcido fixo do PPSSPP original, para que
		// o card selecionado reflita a identidade visual do app. Desenhado
		// antes dos filhos (a borda fica atrás da miniatura, só aparecendo
		// como um contorno ao redor do card por causa do Expand(3)).
		uint32_t accent = dc.GetTheme().itemFocusedStyle.background.color;
		dc.FillRect(UI::Drawable((accent & 0x00FFFFFF) | 0xFF000000), GetBounds().Expand(3));
	}
	UI::AnchorLayout::Draw(dc);
}

void SaveSlotView::OnLoadState(UI::EventParams &e) {
	if (!NetworkWarnUserIfOnlineAndCantSavestate()) {
		g_Config.iCurrentStateSlot = slot_;
		UI::EventParams e2{};
		e2.v = this;
		OnLoadRequested.Trigger(e2);
	}
}

void SaveSlotView::OnSaveState(UI::EventParams &e) {
	if (!NetworkWarnUserIfOnlineAndCantSavestate()) {
		g_Config.iCurrentStateSlot = slot_;
		SaveState::SaveSlot(saveStatePrefix_, slot_, &ShowMessageAfterSaveStateAction);
		UI::EventParams e2{};
		e2.v = this;
		OnStateSaved.Trigger(e2);
	}
}

void GamePauseScreen::update() {
	UpdateUIState(UISTATE_PAUSEMENU);

	if (!firstFrame_ && g_controlMapper.PollPauseTrigger()) {
		TriggerFinish(DR_BACK);
	}
	UIBaseDialogScreen::update();

	firstFrame_ = false;

	{
		std::lock_guard<std::mutex> lock(finishNextFrameMutex_);
		if (finishNextFrame_) {
			TriggerFinish(finishNextFrameResult_);
			finishNextFrame_ = false;
		}
	}

	const bool networkConnected = IsNetworkConnected();
	const InfraDNSConfig &dnsConfig = GetInfraDNSConfig();
	if (g_netInited != lastNetInited_ || netInetInited != lastNetInetInited_ || lastAdhocServerConnected_ != g_adhocServerConnected || lastOnline_ != networkConnected || lastDNSConfigLoaded_ != dnsConfig.loaded) {
		INFO_LOG(Log::sceNet, "Network status changed (or pause dialog just popped up), recreating views");
		RecreateViews();
		lastNetInetInited_ = netInetInited;
		lastNetInited_ = g_netInited;
		lastAdhocServerConnected_ = g_adhocServerConnected;
		lastOnline_ = networkConnected;
		lastDNSConfigLoaded_ = dnsConfig.loaded;
	}

	if (playButton_) {
		const bool mustRunBehind = MustRunBehind();
		playButton_->SetEnabled(!mustRunBehind);
	}

	SetVRAppMode(VRAppMode::VR_MENU_MODE);
}

GamePauseScreen::GamePauseScreen(const Path &filename, bool bootPending)
	: UIBaseDialogScreen(filename), bootPending_(bootPending) {
	// So we can tell if something blew up while on the pause screen.
	std::string assertStr = "PauseScreen: " + filename.GetFilename();
	SetExtraAssertInfo(assertStr.c_str());
	saveStatePrefix_ = SaveState::GetGamePrefix(g_paramSFO);
	SaveState::Rescan(saveStatePrefix_);
	g_controlMapper.AddListener(this);
	createdTime_ = time_now_d();
}

GamePauseScreen::~GamePauseScreen() {
	g_controlMapper.RemoveListener(this);
	__DisplaySetWasPaused();
}

void GamePauseScreen::DrawBackground(UIContext &ui) {
	// GMP Gameport: com isTransparent() retornando true (ver PauseScreen.h),
	// o ScreenManager continua desenhando a EmuScreen (o jogo pausado) por
	// trás desta tela. Aqui só precisamos de um véu escuro translúcido por
	// cima dele -- o suficiente para o menu continuar legível -- em vez do
	// preto sólido opaco que aparecia antes (que, na real, nem era um fundo
	// desenhado: era simplesmente o jogo não sendo renderizado).
	ui.FillRect(UI::Drawable(0xB0000000), ui.GetBounds());
}

void GamePauseScreen::OnVKey(VirtKey virtualKeyCode, bool down) {
	// Simple de-bounce using createdTime_, just to be safe.
	if (down && virtualKeyCode == VIRTKEY_PAUSE && time_now_d() > createdTime_ + 0.1) {
		std::lock_guard<std::mutex> lock(finishNextFrameMutex_);
		finishNextFrame_ = true;
		finishNextFrameResult_ = DR_BACK;
	}
}

void GamePauseScreen::CreateSavestateControls(UI::LinearLayout *leftColumnItems, UI::LinearLayout **extraRow) {
	auto pa = GetI18NCategory(I18NCat::PAUSE);

	using namespace UI;

	leftColumnItems->SetSpacing(10.0);

	// GMP Gameport: os 5 cards de save ficam num carrossel horizontal
	// dedicado (estilo PS5), em vez de empilhados verticalmente um embaixo
	// do outro como no PPSSPP original. Esse carrossel é adicionado como um
	// único item dentro do leftColumnItems (que continua vertical), então o
	// resto do conteúdo que também mora ali (avisos de rede, resumo de
	// conquistas, etc, adicionados por quem chama este método) continua
	// empilhado normalmente acima/abaixo dele.
	ScrollView *slotCarousel = leftColumnItems->Add(new ScrollView(ORIENT_HORIZONTAL, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT)));
	slotCarousel->SetShadows(false);
	LinearLayout *slotRow = slotCarousel->Add(new LinearLayout(ORIENT_HORIZONTAL, new LinearLayoutParams(WRAP_CONTENT, WRAP_CONTENT)));
	slotRow->SetSpacing(12.0f);

	for (int i = 0; i < g_Config.iSaveStateSlotCount; i++) {
		SaveSlotView *slot = slotRow->Add(new SaveSlotView(saveStatePrefix_, i, new LinearLayoutParams(280, 170, Gravity::G_TOPLEFT, Margins(0,0,0,0))));
		slot->OnStateLoaded.Handle(this, &GamePauseScreen::OnState);
		slot->OnStateSaved.Handle(this, &GamePauseScreen::OnState);
		slot->OnLoadRequested.Add([this](UI::EventParams &e) {
			SaveSlotView *v = static_cast<SaveSlotView *>(e.v);
			int slotNum = v->GetSlot();
			auto doLoad = [this, slotNum]() {
				SaveState::LoadSlot(saveStatePrefix_, slotNum, &ShowMessageAfterSaveStateAction);
				std::lock_guard<std::mutex> lock(finishNextFrameMutex_);
				finishNextFrame_ = true;
				finishNextFrameResult_ = DR_CANCEL;
			};
			if (g_Config.bConfirmLoadState) {
				screenManager()->push(new LoadStateConfirmScreen(saveStatePrefix_, slotNum, [doLoad](bool result) {
					if (result) doLoad();
				}));
			} else {
				doLoad();
			}
		});
		slot->OnScreenshotClicked.Add([this](UI::EventParams &e) {
			SaveSlotView *v = static_cast<SaveSlotView *>(e.v);
			int slot = v->GetSlot();
			g_Config.iCurrentStateSlot = v->GetSlot();
			if (SaveState::HasSaveInSlot(saveStatePrefix_, slot)) {
				Path fn = v->GetScreenshotFilename();
				std::string title = v->GetScreenshotTitle();
				Screen *screen = new ScreenshotViewScreen(fn, saveStatePrefix_, title, v->GetSlot(), gamePath_);
				screenManager()->push(screen);
			}
		});
		slot->OnSelected.Add([this](UI::EventParams &e) {
			SaveSlotView *v = static_cast<SaveSlotView *>(e.v);
			g_Config.iCurrentStateSlot = v->GetSlot();
			RecreateViews();
		});
	}

	*extraRow = nullptr;

	LinearLayout *buttonRow = new LinearLayout(ORIENT_HORIZONTAL, new LinearLayoutParams(Margins(10, 0, 0, 10)));
	if (g_Config.bEnableStateUndo && !Achievements::HardcoreModeActive() && NetworkAllowSaveState()) {
		UI::Choice *saveUndoButton = buttonRow->Add(new Choice(pa->T("Undo last save"), ImageID("I_NAVIGATE_BACK"), new LinearLayoutParams(WRAP_CONTENT, WRAP_CONTENT)));
		saveUndoButton->SetEnabled(SaveState::HasUndoLastSave(saveStatePrefix_));
		saveUndoButton->OnClick.Add([this](UI::EventParams &e) {
			SaveState::UndoLastSave(saveStatePrefix_);
			RecreateViews();
		});

		UI::Choice *loadUndoButton = buttonRow->Add(new Choice(pa->T("Undo last load"), ImageID("I_NAVIGATE_BACK"), new LinearLayoutParams(WRAP_CONTENT, WRAP_CONTENT)));
		loadUndoButton->SetEnabled(SaveState::HasUndoLoad(saveStatePrefix_));
		loadUndoButton->OnClick.Add([this](UI::EventParams &e) {
			SaveState::UndoLoad(saveStatePrefix_, &ShowMessageAfterSaveStateAction);
			TriggerFinish(DR_CANCEL);
		});
	}

	if (g_Config.iRewindSnapshotInterval > 0 && !Achievements::HardcoreModeActive() && NetworkAllowSaveState()) {
		UI::Choice *rewindButton = buttonRow->Add(new Choice(pa->T("Rewind"), ImageID("I_REWIND"), new LinearLayoutParams(WRAP_CONTENT, WRAP_CONTENT)));
		rewindButton->SetEnabled(SaveState::CanRewind());
		rewindButton->OnClick.Add([this](UI::EventParams &e) {
			SaveState::Rewind(&ShowMessageAfterSaveStateAction);
			TriggerFinish(DR_CANCEL);
		});
	}

	if (buttonRow->GetNumSubviews() == 0) {
		delete buttonRow;
	} else {
		*extraRow = buttonRow;
	}
}

UI::Margins GamePauseScreen::RootMargins() const {
	if (System_GetPropertyInt(SYSPROP_DEVICE_TYPE) == DEVICE_TYPE_MOBILE && GetDeviceOrientation() == DeviceOrientation::Landscape) {
		// Add some top margin on mobile so it isn't too close to the status bar, as we place buttons
		// very close to the top of the screen.
		return UI::Margins(0, 30, 0, 0);
	} else {
		return UI::Margins(0);
	}
}

void GamePauseScreen::CreateViews() {
	using namespace UI;

	Margins scrollMargins(0, 10, 0, 0);
	auto gr = GetI18NCategory(I18NCat::GRAPHICS);
	auto pa = GetI18NCategory(I18NCat::PAUSE);
	auto ac = GetI18NCategory(I18NCat::ACHIEVEMENTS);
	auto nw = GetI18NCategory(I18NCat::NETWORKING);
	auto di = GetI18NCategory(I18NCat::DIALOG);
	auto co = GetI18NCategory(I18NCat::CONTROLS);

	// GMP Gameport: layout sempre vertical (barra de botões embaixo, estilo
	// PS5), independente da orientação do aparelho -- antes, só o modo
	// retrato tinha os botões embaixo; paisagem colocava numa coluna lateral
	// de 320px, que é o que o usuário via ao jogar com o celular deitado.
	root_ = new LinearLayout(ORIENT_VERTICAL);
	((LinearLayout *)root_)->SetSpacing(0);

	// GMP Gameport: sempre mostra a barra de título (antes só no modo
	// retrato) -- como o layout agora é sempre vertical, sempre há o mesmo
	// espaço que o modo retrato já tinha para ela.
	{
		std::string title;
		std::vector<GameDBInfo> dbInfos;
		const bool inGameDB = g_gameDB.GetGameInfos(g_paramSFO.GetDiscID(), &dbInfos);
		if (inGameDB) {
			title = dbInfos[0].title;
		} else {
			title = g_paramSFO.GetValueString("TITLE");
		}
		TopBar *topBar = new TopBar(*screenManager()->getUIContext(), TopBarFlags::ContextMenuButton, title);
		root_->Add(topBar);

		topBar->OnContextMenuClick.Add([this](UI::EventParams &e) {
			UI::View *srcView = e.v;
			ShowContextMenu(srcView);
		});
	}

	ViewGroup *saveScrollContainer = new LinearLayout(ORIENT_VERTICAL, new LinearLayoutParams(1.0f, scrollMargins));
	root_->Add(saveScrollContainer);

	ScrollView *saveStateScroll = saveScrollContainer->Add(new ScrollView(ORIENT_VERTICAL, new LinearLayoutParams(FILL_PARENT, FILL_PARENT, 1.0f)));
	saveStateScroll->SetShadows(false);

	LinearLayout *saveDataScrollItems = new LinearLayoutList(ORIENT_VERTICAL, new LayoutParams(FILL_PARENT, WRAP_CONTENT));
	saveStateScroll->Add(saveDataScrollItems);

	saveDataScrollItems->SetSpacing(5.0f);
	if (Achievements::IsActive()) {
		// TODO: active subset?
		saveDataScrollItems->Add(new GameAchievementSummaryView(0));

		char buf[512];
		size_t sz = Achievements::GetRichPresenceMessage(buf, sizeof(buf));
		if (sz != (size_t)-1) {
			saveDataScrollItems->Add(new TextView(std::string_view(buf, sz), FLAG_WRAP_TEXT, true, new UI::LinearLayoutParams(Margins(5, 5))));
		}
	}

	if (IsNetworkConnected()) {
		const InfraDNSConfig &dnsConfig = GetInfraDNSConfig();
		LinearLayout *networkInfo = saveDataScrollItems->Add(new LinearLayout(ORIENT_VERTICAL, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, Margins(12, 0))));
		if (dnsConfig.loaded && __NetApctlConnected()) {
			networkInfo->Add(new NoticeView(NoticeLevel::SUCCESS, ApplySafeSubstitutions("%1: %2", nw->T("Network connected"), nw->T("Infrastructure")), ""));

			if (dnsConfig.state == InfraGameState::NotWorking) {
				networkInfo->Add(new NoticeView(NoticeLevel::WARN, nw->T("Some network functionality in this game is not working"), ""));
				if (!dnsConfig.workingIDs.empty()) {
					std::string str(nw->T("Other versions of this game that should work:"));
					for (auto &id : dnsConfig.workingIDs) {
						str.append("\n - ");
						str += id;
					}
					networkInfo->Add(new TextView(str));
				}
			} else if (dnsConfig.state == InfraGameState::Unknown) {
				networkInfo->Add(new NoticeView(NoticeLevel::WARN, nw->T("Network functionality in this game is not guaranteed"), ""));
			}
			if (!dnsConfig.revivalTeam.empty()) {
				networkInfo->Add(new TextView(std::string(nw->T("Infrastructure server provided by:"))));
				networkInfo->Add(new TextView(dnsConfig.revivalTeam));
				if (!dnsConfig.revivalTeamURL.empty()) {
					networkInfo->Add(new Button(dnsConfig.revivalTeamURL))->OnClick.Add([&dnsConfig](UI::EventParams &e) {
						if (!dnsConfig.revivalTeamURL.empty()) {
							System_LaunchUrl(LaunchUrlType::BROWSER_URL, dnsConfig.revivalTeamURL.c_str());
						}
					});
				}
			}
		} else if (NetAdhocctl_GetState() >= ADHOCCTL_STATE_CONNECTED) {
			// If we have it (thanks to the server list), add more metadata about the connected ad-hoc server here.
			AdhocServerListEntry entry{};
			if (AdhocGetServerByHost(g_Config.sProAdhocServer, &entry)) {
				networkInfo->Add(new NoticeView(NoticeLevel::SUCCESS, ApplySafeSubstitutions("%1: %2", nw->T("AdHoc server"), nw->T("Connected")), ""));
				Choice *status = networkInfo->Add(new Choice(ApplySafeSubstitutions(nw->T("Server status: %1"), entry.name), new LinearLayoutParams(WRAP_CONTENT, ITEM_HEIGHT)));
				status->SetIconLeft(ImageID("I_INFO"));
				status->OnClick.Add([this, entry](UI::EventParams &) {
					screenManager()->push(new AdhocServerInfoScreen(entry));
				});
			} else {
				// Awkwardly re-using a string here
				networkInfo->Add(new NoticeView(NoticeLevel::SUCCESS, ApplySafeSubstitutions("%1: %2 (%3)", nw->T("AdHoc server"), nw->T("Connected"), g_Config.sProAdhocServer), ""));
			}
		} else {
			// Hm, other kind of network connection, maybe plain sockets.
			networkInfo->Add(new NoticeView(NoticeLevel::SUCCESS, nw->T("Network connected"), ""));
		}
	}

	bool achievementsAllowSavestates = !Achievements::HardcoreModeActive() || g_Config.bAchievementsSaveStateInHardcoreMode;
	bool showSavestateControls = achievementsAllowSavestates;
	if (IsNetworkConnected() && !g_Config.bAllowSavestateWhileConnected) {
		showSavestateControls = false;
	}

	if (showSavestateControls) {
		if (PSP_CoreParameter().compat.flags().SaveStatesNotRecommended) {
			LinearLayout *horiz = new LinearLayout(ORIENT_HORIZONTAL);
			saveDataScrollItems->Add(horiz);
			horiz->Add(new NoticeView(NoticeLevel::WARN, pa->T("Using save states is not recommended in this game"), "", new LinearLayoutParams(1.0f)));
			horiz->Add(new Button(di->T("More info")))->OnClick.Add([](UI::EventParams &e) {
				System_LaunchUrl(LaunchUrlType::BROWSER_URL, "https://www.ppsspp.org/docs/troubleshooting/save-state-time-warps");
			});
		}
		LinearLayout *extraRow = nullptr;
		CreateSavestateControls(saveDataScrollItems, &extraRow);
		if (extraRow) {
			saveScrollContainer->Add(extraRow);
		}
	} else {
		// Let's show the active challenges.
		std::set<uint32_t> ids = Achievements::GetActiveChallengeIDs();
		if (!ids.empty()) {
			saveDataScrollItems->Add(new ItemHeader(ac->T("Active Challenges")));
			for (auto id : ids) {
				const rc_client_achievement_t *achievement = rc_client_get_achievement_info(Achievements::GetClient(), id);
				if (!achievement)
					continue;
				saveDataScrollItems->Add(new AchievementView(achievement));
			}
		}

		// And tack on an explanation for why savestate options are not available.
		if (!achievementsAllowSavestates) {
			saveDataScrollItems->Add(new NoticeView(NoticeLevel::INFO, ac->T("Save states not available in Hardcore Mode"), ""));
		}
	}

	// GMP Gameport: sempre a barra de botões embaixo (como já era só no modo
	// retrato) -- em vez de alternar para a coluna lateral de 320px que
	// existia no modo paisagem.
	LinearLayout *middleColumn = new LinearLayout(ORIENT_HORIZONTAL, new LinearLayoutParams(FILL_PARENT, ITEM_HEIGHT, Margins(10, 10, 10, 10)));
	root_->Add(middleColumn);

	ViewGroup *buttonColumn = new LinearLayout(ORIENT_VERTICAL, new LinearLayoutParams(FILL_PARENT, WRAP_CONTENT));
	root_->Add(buttonColumn);

	LinearLayout *rightColumnItems = new LinearLayout(ORIENT_VERTICAL);
	buttonColumn->Add(rightColumnItems);

	rightColumnItems->SetSpacing(0.0f);
	if (getUMDReplacePermit()) {
		rightColumnItems->Add(new Choice(pa->T("Switch UMD"), ImageID("I_UMD")))->OnClick.Add([this](UI::EventParams &) {
			screenManager()->push(new UmdReplaceScreen());
		});
	}

	// GMP Gameport: o "Continuar" agora sempre vem do exitRow mais abaixo
	// (que também é sempre usado, ver a mudança em root_ acima) -- este
	// segundo "Continuar" era exclusivo do modo paisagem antigo e ficaria
	// duplicado se mantido.

	if (g_paramSFO.IsValid() && g_Config.HasGameConfig(g_paramSFO.GetDiscID())) {
		rightColumnItems->Add(new Choice(pa->T("Game Settings"), ImageID("I_GEAR")))->OnClick.Handle(this, &GamePauseScreen::OnGameSettings);
		auto pa = GetI18NCategory(I18NCat::PAUSE);
		if (g_Config.HasGameConfig(g_paramSFO.GetValueString("DISC_ID"))) {
			Choice *delGameConfig = rightColumnItems->Add(new Choice(pa->T("Delete Game Config"), ImageID("I_TRASHCAN")));
			delGameConfig->OnClick.Handle(this, &GamePauseScreen::OnDeleteConfig);
			delGameConfig->SetEnabled(!bootPending_);
		}
	} else if (PSP_CoreParameter().fileType != IdentifiedFileType::PPSSPP_GE_DUMP) {
		rightColumnItems->Add(new Choice(pa->T("Settings"), ImageID("I_GEAR")))->OnClick.Handle(this, &GamePauseScreen::OnGameSettings);
		Choice *createGameConfig = rightColumnItems->Add(new Choice(pa->T("Create Game Config"), ImageID("I_GEAR_STAR")));
		createGameConfig->OnClick.Handle(this, &GamePauseScreen::OnCreateConfig);
		createGameConfig->SetEnabled(!bootPending_);
	}

	if (g_Config.bAchievementsEnable && Achievements::HasAchievementsOrLeaderboards()) {
		rightColumnItems->Add(new Choice(ac->T("Achievements"), ImageID("I_ACHIEVEMENT")))->OnClick.Add([this](UI::EventParams &e) {
			screenManager()->push(new RetroAchievementsListScreen(gamePath_));
		});
	}

	rightColumnItems->Add(new Choice(gr->T("Display layout & effects"), ImageID("I_DISPLAY")))->OnClick.Add([this](UI::EventParams &) -> void {
		screenManager()->push(new DisplayLayoutScreen(gamePath_));
	});
	if (g_Config.bShowTouchControls) {
		rightColumnItems->Add(new Choice(co->T("Edit touch control layout"), ImageID("I_CONTROLLER")))->OnClick.Add([this](UI::EventParams &) -> void {
			screenManager()->push(new TouchControlLayoutScreen(gamePath_));
		});
	}

	if (g_Config.bEnableCheats && PSP_CoreParameter().fileType != IdentifiedFileType::PPSSPP_GE_DUMP) {
		rightColumnItems->Add(new Choice(pa->T("Cheats"), ImageID("I_CHEAT")))->OnClick.Add([this](UI::EventParams &e) {
			screenManager()->push(new CwCheatScreen(gamePath_));
		});
	}

	// GMP Gameport: sempre mostra as opções extras (antes só em paisagem).
	AddExtraOptions(rightColumnItems);
	rightColumnItems->Add(new Spacer(20.0));
	Choice *exit;
	if (g_Config.bPauseMenuExitsEmulator) {
		auto di = GetI18NCategory(I18NCat::DIALOG);
		exit = new Choice(di->T("Exit"), ImageID("I_EXIT"));
	} else {
		exit = new Choice(pa->T("Exit to menu"), ImageID("I_EXIT"));
	}

	// GMP Gameport: sempre a linha "Sair" + "Continuar" lado a lado embaixo
	// (antes exclusiva do modo retrato; paisagem só tinha "Sair" sozinho, já
	// que "Continuar" vinha de outro lugar removido acima).
	UI::LinearLayout *exitRow = new UI::LinearLayout(ORIENT_HORIZONTAL, new UI::LinearLayoutParams(FILL_PARENT, WRAP_CONTENT, Margins(0, 0, 0, 0)));
	rightColumnItems->Add(exitRow);
	exitRow->Add(exit);
	exit->ReplaceLayoutParams(new UI::LinearLayoutParams(1.0f, Gravity::G_VCENTER));
	Choice *continueChoice = new Choice(pa->T("Continue"), ImageID("I_PLAY"));
	continueChoice->OnClick.Handle<UIScreen>(this, &UIScreen::OnBack);
	root_->SetDefaultFocusView(continueChoice);
	exitRow->Add(continueChoice);
	continueChoice->ReplaceLayoutParams(new UI::LinearLayoutParams(1.0f, Gravity::G_VCENTER));

	exit->OnClick.Handle(this, &GamePauseScreen::OnExit);
	exit->SetEnabled(!bootPending_);

	if (middleColumn) {
		middleColumn->SetSpacing(8.0f);
		playButton_ = middleColumn->Add(new Choice(g_Config.bRunBehindPauseMenu ? ImageID("I_PAUSE_LINE") : ImageID("I_PLAY_LINE"), new LinearLayoutParams(64, 64)));
		playButton_->OnClick.Add([this](UI::EventParams &e) {
			g_Config.bRunBehindPauseMenu = !g_Config.bRunBehindPauseMenu;
			playButton_->SetIconLeft(g_Config.bRunBehindPauseMenu ? ImageID("I_PAUSE_LINE") : ImageID("I_PLAY_LINE"));
		});

		bool mustRunBehind = MustRunBehind();
		playButton_->SetEnabled(!mustRunBehind);

		Choice *infoButton = middleColumn->Add(new Choice(ImageID("I_INFO"), new LinearLayoutParams(64, 64)));
		infoButton->OnClick.Add([this](UI::EventParams &e) {
			screenManager()->push(new GameScreen(gamePath_, true));
		});

		if (System_GetPropertyBool(SYSPROP_CAN_RESTRICT_ORIENTATION)) {
			AddRotationPicker(screenManager(), middleColumn, false);
		}

		// GMP Gameport: sempre mostra o botão de três pontos (antes só em
		// paisagem) -- agora que o layout é sempre o mesmo, não faz sentido
		// esse menu sumir dependendo da orientação do aparelho.
		Choice *menuButton = middleColumn->Add(new Choice("", ImageID("I_THREE_DOTS"), new LinearLayoutParams(64, 64)));
		menuButton->OnClick.Add([this, menuButton](UI::EventParams &e) {
			ShowContextMenu(menuButton);
		});
	} else {
		playButton_ = nullptr;
	}
}

void GamePauseScreen::ShowContextMenu(UI::View *menuButton) {
	using namespace UI;
	PopupCallbackScreen *contextMenu = new UI::PopupCallbackScreen([this](UI::ViewGroup *parent) {
		auto di = GetI18NCategory(I18NCat::DIALOG);
		parent->Add(new Choice(di->T("Reset"), ImageID("I_WARNING")))->OnClick.Add([this](UI::EventParams &e) {
			std::string confirmMessage = GetConfirmExitMessage();
			if (!confirmMessage.empty()) {
				auto di = GetI18NCategory(I18NCat::DIALOG);
				screenManager()->push(new UI::MessagePopupScreen(di->T("Reset"), confirmMessage, di->T("Reset"), di->T("Cancel"), [this](bool result) {
					if (result) {
						System_PostUIMessage(UIMessage::REQUEST_GAME_RESET);
						std::lock_guard<std::mutex> lock(finishNextFrameMutex_);
						finishNextFrame_ = true;
						finishNextFrameResult_ = DR_BACK;  // resume
					}
				}));
			} else {
				System_PostUIMessage(UIMessage::REQUEST_GAME_RESET);
				std::lock_guard<std::mutex> lock(finishNextFrameMutex_);
				finishNextFrameResult_ = DR_BACK;  // resume
				finishNextFrame_ = true;
			}
		});
		auto dev = GetI18NCategory(I18NCat::DEVELOPER);
		parent->Add(new Choice(dev->T("DevMenu"), ImageID("I_DEBUGGER")))->OnClick.Add([this](UI::EventParams &e) {
			screenManager()->push(new DevMenuScreen(gamePath_, I18NCat::DEVELOPER));
		});
		// GMP Gameport: AddExtraOptions() já é sempre exibida diretamente na
		// coluna de ações principal agora (era exclusiva do modo paisagem
		// antes) -- chamá-la aqui de novo duplicaria essas opções dentro do
		// menu de três pontos.
	}, menuButton);
	screenManager()->push(contextMenu);
}

void GamePauseScreen::AddExtraOptions(UI::ViewGroup *parent) {
	using namespace UI;
	// Add some other options that are removed from the main screen in portrait mode.
	if (Reporting::IsSupported() && g_paramSFO.GetValueString("DISC_ID").size()) {
		auto rp = GetI18NCategory(I18NCat::REPORTING);
		parent->Add(new Choice(rp->T("ReportButton", "Report Feedback")))->OnClick.Handle(this, &GamePauseScreen::OnReportFeedback);
	}
}

void GamePauseScreen::OnGameSettings(UI::EventParams &e) {
	screenManager()->push(new GameSettingsScreen(gamePath_));
}

void GamePauseScreen::OnState(UI::EventParams &e) {
	TriggerFinish(DR_CANCEL);
}

void GamePauseScreen::dialogFinished(const Screen *dialog, DialogResult dr) {
	std::string tag = dialog->tag();
	if (tag == "ScreenshotView") {
		if (dr == DR_OK) {
			std::lock_guard<std::mutex> lock(finishNextFrameMutex_);
			finishNextFrame_ = true;
		} else if (dr != DR_CANCEL && dr != DR_BACK) {
			// Just go back to the pause menu, but refresh the savestate thumbnails in case something changed.
			SaveState::Rescan(saveStatePrefix_);
			RecreateViews();
		}
	} else {
		if (tag == "Game") {
			g_BackgroundAudio.SetGame(Path());
		} else if (tag != "MessagePopupScreen" && tag != "Prompt" && tag != "ContextMenuPopup" && tag != "ContextMenuCallbackPopup" && tag != "Report" && tag != "listpopup") {
			// Maybe should invert the logic here, so many cases..
			// There may have been changes to our savestates, so let's recreate.
			SaveState::Rescan(saveStatePrefix_);
			RecreateViews();
		}
	}
}

int GetUnsavedProgressSeconds() {
	const double timeSinceSaveState = SaveState::SecondsSinceLastSavestate();
	const double timeSinceGameSave = SecondsSinceLastGameSave();

	return (int)std::min(timeSinceSaveState, timeSinceGameSave);
}

// If empty, no confirmation dialog should be shown.
std::string GetConfirmExitMessage() {
	std::string confirmMessage;

	if (!PSP_IsInited()) {
		return std::string();
	}

	int unsavedSeconds = GetUnsavedProgressSeconds();

	// If RAIntegration has dirty info, ask for confirmation.
	if (Achievements::RAIntegrationDirty()) {
		auto ac = GetI18NCategory(I18NCat::ACHIEVEMENTS);
		confirmMessage = ac->T("You have unsaved RAIntegration changes.");
		confirmMessage += '\n';
	}

	if (coreState == CORE_RUNTIME_ERROR) {
		// The game crashed, or similar. Don't bother checking for timeout or network.
		return confirmMessage;
	}

	if (IsNetworkConnected()) {
		auto nw = GetI18NCategory(I18NCat::NETWORKING);
		confirmMessage += nw->T("Network connected");
		confirmMessage += '\n';
	} else if (g_Config.iAskForExitConfirmationAfterSeconds > 0 && unsavedSeconds > g_Config.iAskForExitConfirmationAfterSeconds) {
		if (PSP_CoreParameter().fileType == IdentifiedFileType::PPSSPP_GE_DUMP) {
			// No need to ask for this type of confirmation for dumps.
			return confirmMessage;
		}
		auto di = GetI18NCategory(I18NCat::DIALOG);
		confirmMessage += ApplySafeSubstitutions(di->T("You haven't saved your progress for %1."), NiceTimeFormat((int)unsavedSeconds));
		confirmMessage += '\n';
	}

	return confirmMessage;
}

void GamePauseScreen::OnExit(UI::EventParams &e) {
	std::string confirmExitMessage = GetConfirmExitMessage();

	if (!confirmExitMessage.empty()) {
		auto di = GetI18NCategory(I18NCat::DIALOG);
		std::string_view title = di->T("Are you sure you want to exit?");
		screenManager()->push(new UI::MessagePopupScreen(title, confirmExitMessage, di->T("Exit"), di->T("Cancel"), [this](bool result) {
			if (result) {
				if (g_Config.bPauseMenuExitsEmulator) {
					System_ExitApp();
				} else {
					std::lock_guard<std::mutex> lock(finishNextFrameMutex_);
					finishNextFrameResult_ = DR_OK;  // exit game
					finishNextFrame_ = true;
				}
			}
		}));
	} else {
		if (g_Config.bPauseMenuExitsEmulator) {
			System_ExitApp();
		} else {
			TriggerFinish(DR_OK);
		}
	}
}

void GamePauseScreen::OnReportFeedback(UI::EventParams &e) {
	screenManager()->push(new ReportScreen(gamePath_));
}

void GamePauseScreen::OnCreateConfig(UI::EventParams &e) {
	std::shared_ptr<GameInfo> info = g_gameInfoCache->GetInfo(NULL, gamePath_, GameInfoFlags::PARAM_SFO);
	if (info->Ready(GameInfoFlags::PARAM_SFO)) {
		auto ga = GetI18NCategory(I18NCat::GAME);
		auto di = GetI18NCategory(I18NCat::DIALOG);
		screenManager()->push(new UI::MessagePopupScreen(info->GetTitle(), ga->T("Are you sure you want to create a game-specific config?"), di->T("Yes"), di->T("No"),
			[this, info](bool result) {
			if (result) {
				std::string gameId = info->id;
				g_Config.CreateGameConfig(gameId);
				g_Config.SaveGameConfig(gameId, info->GetTitle());
				if (info) {
					info->hasConfig = true;
				}
				RecreateViews();
			}
		}));
	}
}

void GamePauseScreen::OnDeleteConfig(UI::EventParams &e) {
	auto di = GetI18NCategory(I18NCat::DIALOG);
	const bool trashAvailable = System_GetPropertyBool(SYSPROP_HAS_TRASH_BIN);
	screenManager()->push(
		// TODO: This string should be changed to better match the one in OnCreateConfig.
		new UI::MessagePopupScreen(di->T("Delete"), di->T("DeleteConfirmGameConfig", "Do you really want to delete the settings for this game?"),
			trashAvailable ? di->T("Move to trash") : di->T("Delete"), di->T("Cancel"), [this](bool yes) {
		if (!yes) {
			return;
		}
		std::shared_ptr<GameInfo> info = g_gameInfoCache->GetInfo(NULL, gamePath_, GameInfoFlags::PARAM_SFO);
		if (info->Ready(GameInfoFlags::PARAM_SFO)) {
			g_Config.UnloadGameConfig();
			g_Config.DeleteGameConfig(info->id);
			info->hasConfig = false;
			screenManager()->RecreateAllViews();
		}
	}));
}
