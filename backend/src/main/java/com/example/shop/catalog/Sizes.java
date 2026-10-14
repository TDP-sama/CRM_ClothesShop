package com.example.shop.catalog;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Orders sizes the way shoppers expect: XS, S, M, L, XL, XXL, then numeric sizes, then anything else. */
final class Sizes {

    private static final List<String> LETTER_ORDER = List.of("XXS", "XS", "S", "M", "L", "XL", "XXL", "XXXL");

    static final Comparator<String> ORDER = Comparator
            .comparingInt(Sizes::rank)
            .thenComparing(Sizes::numeric)
            .thenComparing(s -> s.toUpperCase(Locale.ROOT));

    private Sizes() {
    }

    static List<String> sorted(Collection<String> sizes) {
        return sizes.stream().distinct().sorted(ORDER).toList();
    }

    private static int rank(String size) {
        int letter = LETTER_ORDER.indexOf(size.toUpperCase(Locale.ROOT));
        if (letter >= 0) {
            return letter;
        }
        return size.chars().allMatch(Character::isDigit) ? 100 : 200;
    }

    private static int numeric(String size) {
        return size.chars().allMatch(Character::isDigit) && !size.isEmpty() && size.length() < 9
                ? Integer.parseInt(size) : 0;
    }
}
