package org.ppsspp.ppsspp;

import android.content.Context;

/**
 * ÚNICO ponto que escolhe de onde vem o catálogo. Hoje: o banco de teste local.
 *
 * Quando o banco real (Supabase) existir: crie SupabaseCatalogSource
 * implements CatalogSource e troque a linha abaixo. Nada mais no app muda.
 */
final class GmpCatalog {

    private GmpCatalog() {
    }

    static CatalogSource source(Context context) {
        return new LocalJsonCatalogSource(context.getApplicationContext());
    }
}
