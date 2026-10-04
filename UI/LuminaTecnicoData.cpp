#include <algorithm>
#include <cctype>

#include "Common/File/FileUtil.h"
#include "Common/Log.h"
#include "Core/Config.h"
#include "Core/Util/PathUtil.h"

#include "UI/LuminaTecnicoData.h"

// TODO(preencher com os dados reais de MLPRO-main.zip, www/index.html):
// Esta é uma tabela de exemplo só pra deixar a tela funcional enquanto os dados
// reais não chegam. "texturesSubdir" e "coachHexCodes" aqui são placeholders —
// NÃO vão detectar nenhum patch de verdade até serem substituídos.
const std::vector<LuminaPatchDef> kLuminaPatches = {
	{ "lpfl27", "LPFL 27", "LPFL27", { /* TODO: hex codes do slot de técnico no LPFL 27 */ } },
	{ "mrgamer", "MR GAMER", "MRGAMER", { /* TODO: hex codes do slot de técnico no MR GAMER */ } },
};

// TODO(preencher com os 19 técnicos reais de www/index.html):
const std::vector<LuminaTecnicoDef> kLuminaTecnicos = {
	// { "id", "Nome do Técnico", "caminho/da/foto.png" },
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
	const Path texturesRoot = GetSysDirectory(DIRECTORY_TEXTURES);
	for (size_t i = 0; i < kLuminaPatches.size(); i++) {
		const LuminaPatchDef &patch = kLuminaPatches[i];
		const Path iniPath = texturesRoot / patch.texturesSubdir / "textures.ini";
		if (File::Exists(iniPath)) {
			INFO_LOG(Log::System, "Lumina/ML-PRO: patch detectado: %s (%s)", patch.displayName.c_str(), iniPath.c_str());
			return (int)i;
		}
	}
	return -1;
}

bool LuminaApplyTecnico(const LuminaPatchDef &patch, const LuminaTecnicoDef &tecnico, std::string *errorStr) {
	const Path iniPath = GetSysDirectory(DIRECTORY_TEXTURES) / patch.texturesSubdir / "textures.ini";

	std::string contents;
	if (!File::ReadTextFileToString(iniPath, &contents)) {
		*errorStr = "Não foi possível ler o textures.ini desse patch.";
		return false;
	}

	if (patch.coachHexCodes.empty()) {
		// GMP Gameport: enquanto os hex codes reais não são preenchidos em
		// LuminaTecnicoData.cpp, não há nada pra trocar -- avisa em vez de
		// regravar o arquivo sem fazer nada.
		*errorStr = "Este patch ainda não tem os códigos do técnico configurados.";
		return false;
	}

	// O textures.ini é feito de linhas "HEXCODE=caminho/da/imagem.png". Trocamos só
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
		*errorStr = "Nenhuma linha do técnico foi encontrada no textures.ini (os hex codes podem estar desatualizados).";
		return false;
	}

	std::string newContents;
	for (size_t i = 0; i < lines.size(); i++) {
		newContents += lines[i];
		if (i + 1 < lines.size())
			newContents += "\n";
	}

	if (!File::WriteStringToFile(true, newContents, iniPath)) {
		*errorStr = "Não foi possível salvar o textures.ini (verifique a permissão de armazenamento).";
		return false;
	}

	INFO_LOG(Log::System, "Lumina/ML-PRO: técnico '%s' aplicado ao patch '%s' (%d linha(s) trocada(s))", tecnico.displayName.c_str(), patch.displayName.c_str(), replacedCount);
	return true;
}
