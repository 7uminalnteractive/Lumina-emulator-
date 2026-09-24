package org.ppsspp.ppsspp;

import java.util.Set;

/**
 * A regra de acesso do GMP, em UM lugar: um item pode ser baixado por quem
 *   (a) comprou algum patch que o libera, OU
 *   (b) tem um plano ativo que o inclui.
 *
 * Esta regra roda hoje só no catálogo de teste (LocalJsonCatalogSource). Quando o
 * banco real existir, ela deve ser aplicada NO SERVIDOR (consulta com RLS do
 * Supabase ou função no banco), nunca só no app: um app no aparelho do usuário
 * pode ser alterado, e o servidor é o único lugar em que um bloqueio vale.
 *
 * Todos os ids devem chegar já normalizados (minúsculas, sem espaços).
 */
final class CatalogAccess {

    /** Em unlockedByPlans: qualquer plano ativo libera o item. */
    static final String ANY_PLAN = "*";

    private CatalogAccess() {
    }

    static boolean canDownload(CatalogGame game, Set<String> ownedProducts, String activePlan) {
        for (String product : game.unlockedByProducts) {
            if (ownedProducts.contains(product)) {
                return true;
            }
        }
        if (activePlan != null && !activePlan.isEmpty()) {
            return game.unlockedByPlans.contains(ANY_PLAN)
                    || game.unlockedByPlans.contains(activePlan);
        }
        return false;
    }
}
