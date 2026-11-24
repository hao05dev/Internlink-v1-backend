package com.internlink.core.application.system.impl;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PostSlugNormalizer {
    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern REPEATED_DASH = Pattern.compile("-+");
    private static final Pattern EDGE_DASH = Pattern.compile("^-|-$");

    private PostSlugNormalizer() {}

    public static String normalize(String input) {
        if (input == null) return "";
        String compact = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(compact, Normalizer.Form.NFD);
        String slug = DIACRITICS.matcher(normalized).replaceAll("");
        slug = slug.replace('đ', 'd').replace('Đ', 'D');
        slug = NON_LATIN.matcher(slug).replaceAll("");
        slug = REPEATED_DASH.matcher(slug).replaceAll("-");
        slug = EDGE_DASH.matcher(slug).replaceAll("");
        return slug.toLowerCase(Locale.ROOT);
    }
}
