package org.ppsspp.ppsspp;

import android.content.Context;

/**
 * ÚNICO ponto que escolhe de onde vem o catálogo. Agora: o banco real do
 * projeto Supabase "TBP" (ver SupabaseCatalogSource).
 */
final class GmpCatalog {

    private GmpCatalog() {
    }

    static CatalogSource source(Context context) {
        return new SupabaseCatalogSource(context.getApplicationContext());
    }
}
