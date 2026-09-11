package org.ppsspp.ppsspp;

import java.util.Arrays;
import java.util.List;

/**
 * Catálogo local da Loja de Games (mock, sem backend ainda).
 *
 * Espelha, por enquanto manualmente, os mesmos produtos do site GMPES
 * (ver store.js do site: "Patch Conmebol" e "Patch Europeu"). Quando a
 * Loja for conectada a um backend real, este catálogo fixo deve ser
 * substituído por uma chamada de rede -- o ideal é que app e site passem
 * a consumir a mesma fonte de dados (mesmo catálogo de produtos).
 */
public class StoreCatalog {

    public static List<StoreItem> getItems() {
        return Arrays.asList(
                new StoreItem(
                        "conmebol",
                        "Patch Conmebol",
                        "Competições",
                        "Libertadores e Sul-Americana completas no seu PES 2014.",
                        14.90,
                        "Disponível",
                        null
                ),
                new StoreItem(
                        "europeu",
                        "Patch Europeu",
                        "Competições",
                        "Champions League e Europa League no seu PES 2014.",
                        14.90,
                        "Disponível",
                        null
                )
        );
    }
}
