// GMP Gameport — ML-PRO ("Contratar Técnico"), versão nativa.
//
// Isto substitui o app Capacitor/WebView separado do ML-PRO: em vez de pedir pro
// usuário escolher o patch manualmente numa lista, detectamos automaticamente qual
// patch de Master League está instalado (procurando a pasta de texturas + o
// texture.ini de cada patch conhecido dentro de Sistema/.TEXTURES), aí só pedimos pra
// escolher o técnico.
//
// TODO(preencher com os dados reais do MLPRO-main.zip — www/index.html):
//   - kLuminaPatches: por enquanto só tem "The Best Patch" (nome de pasta
//     confirmado); faltam os hex codes do slot "técnico" desse patch (os
//     mesmos hex codes que aplicarTecnico() troca via regex no JS original)
//     e os demais patches suportados, se houver mais de um.
//   - kLuminaTecnicos: a lista TECNICOS (19 técnicos) com nome e caminho da foto.
//   - kLuminaCoachPathPrefix: o PREFIXO_TECNICO original, se os caminhos de foto
//     usarem um prefixo fixo.
#pragma once

#include <string>
#include <vector>

#include "Common/File/Path.h"

// Um patch de Master League suportado (ex.: "The Best Patch").
struct LuminaPatchDef {
	std::string id;               // ex.: "lpfl27" — identificador curto, sem espaços.
	std::string displayName;      // ex.: "LPFL 27" — nome mostrado na tela.
	std::string texturesSubdir;   // nome da pasta dentro de Sistema/.TEXTURES que contém o texture.ini deste patch.
	std::vector<std::string> coachHexCodes; // hex codes (chaves do texture.ini) que representam a foto do técnico atual.
};

// Um técnico disponível para contratar.
struct LuminaTecnicoDef {
	std::string id;
	std::string displayName;
	std::string photoRelativePath; // caminho da foto, relativo à pasta de texturas do patch.
};

// TODO: substituir pelos patches reais (PATCHES de www/index.html).
extern const std::vector<LuminaPatchDef> kLuminaPatches;

// TODO: substituir pelos 19 técnicos reais (TECNICOS de www/index.html).
extern const std::vector<LuminaTecnicoDef> kLuminaTecnicos;

// Procura, dentro de Sistema/.TEXTURES, qual dos kLuminaPatches está instalado (ou seja,
// cuja pasta de texturas + texture.ini existem no cartão de memória do usuário).
// Retorna o índice em kLuminaPatches, ou -1 se nenhum patch reconhecido foi encontrado.
int LuminaDetectInstalledPatch();

// Lê o texture.ini do patch, troca cada hex code em patch.coachHexCodes para apontar
// pra foto do técnico escolhido, e regrava o arquivo. Retorna true em caso de sucesso;
// em caso de erro, preenche errorStr com uma mensagem pro usuário.
bool LuminaApplyTecnico(const LuminaPatchDef &patch, const LuminaTecnicoDef &tecnico, std::string *errorStr);
