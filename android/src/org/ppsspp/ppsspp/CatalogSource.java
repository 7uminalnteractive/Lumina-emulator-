package org.ppsspp.ppsspp;

import java.io.IOException;
import java.util.List;

/**
 * De onde vem o catálogo e quem tem direito a quê.
 *
 * Existe para que trocar o banco de teste pelo banco real seja mexer em UMA
 * classe (uma nova implementação + a linha em GmpCatalog), sem tocar na
 * Biblioteca nem no instalador.
 *
 * Todos os métodos BLOQUEIAM (rede/disco): chame de uma thread de fundo.
 */
interface CatalogSource {

    /**
     * Itens que ESTE usuário pode baixar, já filtrados pelo acesso dele
     * (patch comprado ou plano ativo). Inclui os que já estão instalados;
     * quem chama decide o que mostrar.
     */
    List<CatalogGame> fetchDownloadableGames(String email) throws IOException;

    /**
     * Link para baixar o arquivo. Chamado a CADA tentativa de download, porque
     * links assinados do servidor expiram. Deve falhar se o usuário não tiver
     * acesso.
     */
    String resolveDownloadUrl(String email, CatalogGame game) throws IOException;
}
