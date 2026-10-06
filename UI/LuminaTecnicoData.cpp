#include <algorithm>
#include <cctype>

#include "Common/File/DirListing.h"
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

static bool EqualsNoCase(const std::string &a, const std::string &b) {
	if (a.size() != b.size())
		return false;
	for (size_t i = 0; i < a.size(); i++) {
		if (std::tolower((unsigned char)a[i]) != std::tolower((unsigned char)b[i]))
			return false;
	}
	return true;
}

// GMP Gameport: o armazenamento interno do Android normalmente é case-sensitive
// (diferente do Windows), então "THE BEST PATCH" vs "The Best Patch" vs
// "the best patch" são pastas DIFERENTES para o File::Exists() de baixo nível,
// mesmo que pareçam "a mesma coisa" pro usuário. Pra não depender de bater a
// grafia exata (maiúsculas, espaçamento) de cada pasta, resolvemos cada nível
// do caminho procurando, dentro do diretório pai, um item cujo nome bate
// ignorando caixa -- e usamos o nome REAL encontrado em disco pra montar o
// próximo passo. GETFILES_GETHIDDEN é necessário pra listar ".TEXTURES" (nome
// começando com ponto é tratado como oculto por padrão).
//
// Retorna o Path resolvido com a grafia real, ou um Path vazio (checar com
// resolved.empty() equivalente -- aqui usamos *found) se não encontrou nada
// parecido dentro do diretório pai.
static Path ResolveChildCaseInsensitive(const Path &parentDir, const std::string &wantedName, bool *found) {
	*found = false;
	std::vector<File::FileInfo> files;
	if (!File::GetFilesInDir(parentDir, &files, nullptr, File::GETFILES_GETHIDDEN)) {
		WARN_LOG(Log::System, "Lumina/ML-PRO: não consegui listar '%s' (pasta não existe ou sem permissão?)", parentDir.c_str());
		return Path();
	}
	for (const File::FileInfo &f : files) {
		if (EqualsNoCase(f.name, wantedName)) {
			*found = true;
			return f.fullName;
		}
	}
	WARN_LOG(Log::System, "Lumina/ML-PRO: '%s' não encontrado dentro de '%s'", wantedName.c_str(), parentDir.c_str());
	return Path();
}

// Resolve <memStickDirectory>/Sistema/.TEXTURES/<texturesSubdir>/texture.ini
// percorrendo nível por nível com ResolveChildCaseInsensitive, pra tolerar
// diferenças de maiúsculas/minúsculas em qualquer uma das pastas reais do
// usuário. Se algum nível não existir, retorna um Path vazio -- os logs de
// WARN_LOG acima dizem exatamente em qual nível a busca parou, o que ajuda a
// diagnosticar se o problema é o nome da pasta ou outra coisa (ex. permissão).
static Path ResolvePatchIniPath(const LuminaPatchDef &patch) {
	bool found = false;

	Path sistemaDir = ResolveChildCaseInsensitive(g_Config.memStickDirectory, "Sistema", &found);
	if (!found)
		return Path();

	Path texturesDir = ResolveChildCaseInsensitive(sistemaDir, ".TEXTURES", &found);
	if (!found)
		return Path();

	Path patchDir = ResolveChildCaseInsensitive(texturesDir, patch.texturesSubdir, &found);
	if (!found)
		return Path();

	Path iniPath = ResolveChildCaseInsensitive(patchDir, "texture.ini", &found);
	if (!found)
		return Path();

	return iniPath;
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

// GMP Gameport: achamos estes 14 hex codes no www/index.html original do
// ML-PRO (app Capacitor), para o segundo patch -- lá chamado "mrgamer" /
// "Brazukas 26". São o equivalente, nesse patch, aos 5 hex codes de
// "The Best Patch" acima (mesmo slot "técnico de clube", só que com hex
// codes diferentes porque o layout de texturas do patch é outro).
//
// NÃO foi adicionado um LuminaPatchDef pra ele ainda porque o nome de pasta
// real dentro de Sistema/.TEXTURES no GMP não está confirmado -- o app
// antigo usava "pastaRelativa: PSP/Textures/MRGAMER", mas esse mesmo campo
// pra "The Best Patch" dizia "PSP/Textures/LPFL27", que já se mostrou
// completamente errado pro GMP (o nome real é "THE BEST PATCH", convenção
// Sistema/.TEXTURES). Então não dá pra confiar em "MRGAMER" por analogia --
// só adicionamos esse patch na lista de detecção quando o usuário confirmar
// o nome real da pasta dele no GMP. Hex codes guardados aqui enquanto isso:
//   097222d02ba18ad320580ca4, 097223002ba18ad320580ca4, 0971fa309245a2a6ecfb38f5,
//   0971d1d0a039e953515d4ee6, 0971e620fe4a7825ec964817, 09720ec0eba094a559573bfe,
//   09720eb0eba094a559573bfe, 0971e610fe4a7825ec964817, 09720e80eba094a559573bfe,
//   097223102ba18ad320580ca4, 0971e5e0fe4a7825ec964817, 0971fa609245a2a6ecfb38f5,
//   0971d190a039e953515d4ee6, 0971d1c0a039e953515d4ee6

const std::vector<LuminaPatchDef> kLuminaPatches = {
	{ "the_best_patch", "The Best Patch", "THE BEST PATCH", kTheBestPatchCoachHexCodes },
};

// GMP Gameport: lista completa dos 19 técnicos (encontrada no www/index.html
// original do ML-PRO -- array TECNICOS). O caminho da foto é relativo à
// pasta de texturas do próprio patch (Sistema/.TEXTURES/<patch>/...), e é
// montado como PREFIXO_TECNICO ("MLPRO/Tecnicos/Técnico de Clube/") + pasta +
// "/" + foto, igual ao app original -- preservando os nomes de arquivo e
// acentos exatamente como estavam lá (ex.: pasta "Rogério Ceni" com acento,
// arquivo "Rogerio Ceni.png" sem acento).
const std::vector<LuminaTecnicoDef> kLuminaTecnicos = {
	{ "abel_ferreira",      "Abel Ferreira",      "MLPRO/Tecnicos/Técnico de Clube/Abel Ferreira/Abel.png" },
	{ "arthur_jorge",       "Arthur Jorge",        "MLPRO/Tecnicos/Técnico de Clube/Arthur Jorge/Arthur Jorge.png" },
	{ "cuca",               "Cuca",                "MLPRO/Tecnicos/Técnico de Clube/Cuca/Cuca.png" },
	{ "dorival_jr",         "Dorival Jr",          "MLPRO/Tecnicos/Técnico de Clube/Dorival Jr/Dorival.png" },
	{ "eduardo_dominguez",  "Eduardo Dominguez",   "MLPRO/Tecnicos/Técnico de Clube/Eduardo Dominguez/Eduardo Dominguez.png" },
	{ "f_carvalho",         "F.Carvalho",          "MLPRO/Tecnicos/Técnico de Clube/F.Carvalho/F.Carvalho.png" },
	{ "fernando_diniz",     "Fernando Diniz",      "MLPRO/Tecnicos/Técnico de Clube/Fernando Diniz/Diniz.png" },
	{ "fernando_seabra",    "Fernando Seabra",     "MLPRO/Tecnicos/Técnico de Clube/Fernando Seabra/Seabra.png" },
	{ "jair_ventura",       "Jair Ventura",        "MLPRO/Tecnicos/Técnico de Clube/Jair Ventura/Jair Ventura.png" },
	{ "luis_castro",        "Luís Castro",         "MLPRO/Tecnicos/Técnico de Clube/Luís Castro/Luis Castro.png" },
	{ "luis_zubeldia",      "Luís Zubeldia",       "MLPRO/Tecnicos/Técnico de Clube/Luís Zubeldia/Zubeldia.png" },
	{ "leo_jardim",         "Léo Jardim",          "MLPRO/Tecnicos/Técnico de Clube/Léo Jardim/Leonardo Jardim.png" },
	{ "odair_hellmann",     "Odair Hellmann",      "MLPRO/Tecnicos/Técnico de Clube/Odair Hellmann/Odair.png" },
	{ "paulo_pezzolano",    "Paulo Pezzolano",     "MLPRO/Tecnicos/Técnico de Clube/Paulo Pezzolano/Paulo.png" },
	{ "pedro_emanuel",      "Pedro Emanuel",       "MLPRO/Tecnicos/Técnico de Clube/Pedro Emanuel/Pedro Emanuel.png" },
	{ "rafael_guanaes",     "Rafael Guanaes",      "MLPRO/Tecnicos/Técnico de Clube/Rafael Guanaes/Guanaes.png" },
	{ "rafael_lacerda",     "Rafael Lacerda",      "MLPRO/Tecnicos/Técnico de Clube/Rafael Lacerda/Rafael Lacerda.png" },
	{ "rogerio_ceni",       "Rogério Ceni",        "MLPRO/Tecnicos/Técnico de Clube/Rogério Ceni/Rogerio Ceni.png" },
	{ "vagner_mancini",     "Vagner Mancini",      "MLPRO/Tecnicos/Técnico de Clube/Vagner Mancini/Vagner Mancini.png" },
};

int LuminaDetectInstalledPatch() {
	for (size_t i = 0; i < kLuminaPatches.size(); i++) {
		const LuminaPatchDef &patch = kLuminaPatches[i];
		const Path iniPath = ResolvePatchIniPath(patch);
		if (!iniPath.empty()) {
			INFO_LOG(Log::System, "Lumina/ML-PRO: patch detectado: %s (%s)", patch.displayName.c_str(), iniPath.c_str());
			return (int)i;
		}
	}
	return -1;
}

bool LuminaApplyTecnico(const LuminaPatchDef &patch, const LuminaTecnicoDef &tecnico, std::string *errorStr) {
	const Path iniPath = ResolvePatchIniPath(patch);
	if (iniPath.empty()) {
		// GMP Gameport: não achamos o texture.ini desse patch nem com a busca
		// tolerante a maiúsculas/minúsculas -- os WARN_LOG de ResolveChildCaseInsensitive
		// (ver logcat) dizem exatamente em qual nível da pasta a busca parou.
		*errorStr = "Não encontrei o texture.ini desse patch no cartão de memória (verifique se o patch ainda está instalado).";
		return false;
	}

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
