package com.alkacode.flair.tag;

/**
 * {@code icon}/{@code itemsAdderId} sao opcionais (tags.yml categories.<id>.icon/itemsadder) -
 * quando ausentes, a aba da categoria cai no vidro generico (menus.yml flair_tags.categoria-*).
 */
public record TagCategory(String id, String display, int position, String icon, String itemsAdderId) {
    public boolean hasCustomIcon() {
        return (icon != null && !icon.isBlank()) || (itemsAdderId != null && !itemsAdderId.isBlank());
    }
}
