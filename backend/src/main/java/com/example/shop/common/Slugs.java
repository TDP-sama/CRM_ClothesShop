package com.example.shop.common;

import java.text.Normalizer;
import java.util.Locale;

/** URL slugs from Vietnamese names: "Áo khoác dạ" → "ao-khoac-da". */
public final class Slugs {

    private Slugs() {
    }

    public static String slugify(String text) {
        String ascii = Normalizer.normalize(text.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "item" : slug;
    }
}
