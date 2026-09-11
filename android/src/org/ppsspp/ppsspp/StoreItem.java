package org.ppsspp.ppsspp;

/**
 * Representa um jogo/patch disponível na Loja (para baixar), em oposição a
 * GameItem, que representa um jogo já instalado no aparelho.
 *
 * FASE ATUAL: os itens vêm de uma lista fixa em StoreCatalog (mock local),
 * sem backend. Quando a Loja for ligada a um servidor de verdade (o mesmo
 * catálogo do site GMPES, por exemplo), essa classe deve passar a ser
 * alimentada pela resposta da API em vez do mock -- os campos abaixo já
 * foram pensados para bater com os mesmos dados que o site usa
 * (nome, categoria, preço, imagem, descrição).
 */
public class StoreItem {

    public final String id;
    public final String title;
    public final String category;
    public final String shortDescription;
    public final double price;
    public final String badge;
    public final String coverUrl;

    public StoreItem(String id, String title, String category, String shortDescription,
                      double price, String badge, String coverUrl) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.shortDescription = shortDescription;
        this.price = price;
        this.badge = badge;
        this.coverUrl = coverUrl;
    }

    public String getPriceLabel() {
        return String.format("R$ %.2f", price).replace('.', ',');
    }
}
