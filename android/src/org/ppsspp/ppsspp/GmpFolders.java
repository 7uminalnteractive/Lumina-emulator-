package org.ppsspp.ppsspp;

/**
 * Para qual pasta do GMP cada tipo de conteúdo vai. Quem decide o destino é o
 * APP, por esta lista fixa: o catálogo só informa o "tipo", nunca um caminho.
 * Assim um catálogo errado (ou adulterado) não consegue gravar fora do GMP.
 *
 * As pastas espelham as que a Biblioteca já cria (LibraryActivity):
 *   GMP/Jogo/Game, GMP/Jogo/Save, GMP/Textura, GMP/Sistema
 */
final class GmpFolders {

    private GmpFolders() {
    }

    /** Caminho relativo a GMP/, ou null se o tipo não é conhecido. */
    static String relativeFolderFor(String kind) {
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case "game":
                return "Jogo/Game";
            case "save":
                return "Jogo/Save";
            case "texture":
                return "Textura";
            case "system":
                return "Sistema";
            default:
                return null;
        }
    }
}
