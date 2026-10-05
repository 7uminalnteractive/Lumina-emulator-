#include <algorithm>
#include <cctype>

#include "Common/File/FileUtil.h"
#include "Common/Log.h"
#include "Core/Config.h"

#include "UI/LuminaTecnicoData.h"

// GMP Gameport: o caminho real (confirmado pelo usuário) é
// <pasta do cartão de memória>/Sistema/.TEXTURES/<pasta do patch>/texture.ini
// -- note que é ".TEXTURES" (oculta, com ponto) dentro de "Sistema", e o
// arquivo é "texture.ini" no singular, diferente da convenção padrão do
// PPSSPP (pasta "TEXTURES" direto na raiz, arquivo "textures.ini" no
// plural). Por isso construímos o caminho a partir de g_Config.memStickDirectory
// na mão em vez de usar GetSysDirectory(DIRECTORY_TEXTURES).
static Path GmpTexturesRoot() {
	return g_Config.memStickDirectory / "Sistema" / ".TEXTURES";
}

// GMP Gameport: hex codes reais do slot "técnico de clube" no texture.ini de
// "The Best Patch" (recebidos do usuário, trecho "#ML PRO" do arquivo). São
// 5 linhas porque o jogo usa o mesmo retrato em vários lugares/resoluções --
// hoje todas apontam pro técnico padrão do patch (Fernando Diniz), e é
// exatamente isso que LuminaApplyTecnico() troca.
//
// Existe também um grupo de 3 hex codes apontando para "MLPRO/Kit Treino" no
// mesmo arquivo -- é outra coisa (kit de treino, não foto de técnico), por
// isso não entra em coachHexCodes. Se um dia "trocar kit de treino" for uma
// função à parte, esses 3 hex codes são:
//   0000000000000000a5a4d8a8, 000000000000000005559a4c, 00000000000000003201c284
static const std::vector<std::string> kTheBestPatchCoachHexCodes = {
	"0000000000000000515d4ee6",
	"0000000000000000ec964817",
	"0000000000000000ecfb38f5",
	"000000000000000059573bfe",
	"000000000000000020580ca4",
};

const std::vector<LuminaPatchDef> kLuminaPatches = {
	{ "the_best_patch", "The Best Patch", "THE BEST PATCH", kTheBestPatchCoachHexCodes },
};

// GMP Gameport: técnicos confirmados. O caminho da foto é relativo à pasta
// de texturas do próprio patch (Sistema/.TEXTURES/THE BEST PATCH/...).
//
// TODO(preencher com o restante dos 19 técnicos): falta nome + caminho da
// foto de cada um -- "Fernando Diniz" é o único confirmado até agora (é o
// valor padrão que já vem nos 5 hex codes acima).
const std::vector<LuminaTecnicoDef> kLuminaTecnicos = {
	{ "fernando_diniz", "Fernando Diniz", "MLPRO/Tecnicos/Técnico de Clube/Fernando Diniz/Diniz.png" },
};

static bool EqualsNoCase(const std::string &a, const std::string &b) {
	if (a.size() != b.size())
		return false;
	for (size_t i = 0; i < a.size(); i++) {
		if (std::tolower((unsigned char)a[i]) != std::tolower((unsigned char)b[i]))
			return false;
	}
	return true;
}

int LuminaDetectInstalledPatch() {
	const Path texturesRoot = GmpTexturesRoot();
	for (size_t i = 0; i < kLuminaPatches.size(); i++) {
		const LuminaPatchDef &patch = kLuminaPatches[i];
		const Path iniPath = texturesRoot / patch.texturesSubdir / "texture.ini";
		if (File::Exists(iniPath)) {
			INFO_LOG(Log::System, "Lumina/ML-PRO: patch detectado: %s (%s)", patch.displayName.c_str(), iniPath.c_str());
			return (int)i;
		}
	}
	return -1;
}

bool LuminaApplyTecnico(const LuminaPatchDef &patch, const LuminaTecnicoDef &tecnico, std::string *errorStr) {
	const Path iniPath = GmpTexturesRoot() / patch.texturesSubdir / "texture.ini";

	std::string contents;
	if (!File::ReadTextFileToString(iniPath, &contents)) {
		*errorStr = "Não foi possível ler o texture.ini desse patch.";
		return false;
	}

	if (patch.coachHexCodes.empty()) {
		// GMP Gameport: enquanto os hex codes reais não são preenchidos em
		// LuminaTecnicoData.cpp, não há nada pra trocar -- avisa em vez de
		// regravar o arquivo sem fazer nada.
		*errorStr = "Este patch ainda não tem os códigos do técnico configurados.";
		return false;
	}

	// O texture.ini é feito de linhas "HEXCODE=caminho/da/imagem.png". Trocamos só
	// as linhas cujo hex code (a chave, antes do "=") está na lista de hex codes do
	// técnico atual desse patch -- preservando todo o resto do arquivo (outras
	// texturas, comentários, opções) exatamente como estava.
	std::vector<std::string> lines;
	size_t start = 0;
	while (start <= contents.size()) {
		size_t end = contents.find('\n', start);
		if (end == std::string::npos) {
			lines.push_back(contents.substr(start));
			break;
		}
		lines.push_back(contents.substr(start, end - start));
		start = end + 1;
	}

	int replacedCount = 0;
	for (std::string &line : lines) {
		size_t eq = line.find('=');
		if (eq == std::string::npos)
			continue;
		// Aceita e preserva um eventual '\r' no fim da linha (CRLF).
		std::string key = line.substr(0, eq);
		while (!key.empty() && (key.back() == ' ' || key.back() == '\t'))
			key.pop_back();

		bool isCoachLine = false;
		for (const std::string &hex : patch.coachHexCodes) {
			if (EqualsNoCase(key, hex)) {
				isCoachLine = true;
				break;
			}
		}
		if (!isCoachLine)
			continue;

		std::string trailing;
		if (!line.empty() && line.back() == '\r') {
			trailing = "\r";
		}
		line = key + "=" + tecnico.photoRelativePath + trailing;
		replacedCount++;
	}

	if (replacedCount == 0) {
		*errorStr = "Nenhuma linha do técnico foi encontrada no texture.ini (os hex codes podem estar desatualizados).";
		return false;
	}

	std::string newContents;
	for (size_t i = 0; i < lines.size(); i++) {
		newContents += lines[i];
		if (i + 1 < lines.size())
			newContents += "\n";
	}

	if (!File::WriteStringToFile(true, newContents, iniPath)) {
		*errorStr = "Não foi possível salvar o texture.ini (verifique a permissão de armazenamento).";
		return false;
	}

	INFO_LOG(Log::System, "Lumina/ML-PRO: técnico '%s' aplicado ao patch '%s' (%d linha(s) trocada(s))", tecnico.displayName.c_str(), patch.displayName.c_str(), replacedCount);
	return true;
}
