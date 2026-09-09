package com.promoteur.app.document;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * French spelling of a dinar amount (UX-06).
 *
 * <p>Standard French rules, not the Belgian ones: seventy is « soixante-dix », eighty is
 * « quatre-vingts ». « cent » and « vingt » take an s only when multiplied and final, and
 * « mille » never does.</p>
 */
@Service
public class AmountInWordsServiceImpl implements AmountInWordsService {

    /**
     * Millimes per dinar, and therefore the scale money is held at.
     */
    private static final int MILLIMES_PER_DINAR = 1000;

    private static final String[] UNITS = {
            "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
            "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize"
    };

    private static final String[] TENS = {
            "", "", "vingt", "trente", "quarante", "cinquante", "soixante", "", "quatre-vingt", ""
    };

    /**
     * Groups of a thousand, largest first; « mille » stands alone rather than « un mille ».
     */
    private static String integerWords(final long value) {
        if (value == 0) {
            return UNITS[0];
        }

        final StringBuilder words = new StringBuilder();
        long rest = value;

        final long billions = rest / 1_000_000_000L;
        rest %= 1_000_000_000L;
        if (billions > 0) {
            AmountInWordsServiceImpl.appendScale(words, billions, "milliard");
        }

        final long millions = rest / 1_000_000L;
        rest %= 1_000_000L;
        if (millions > 0) {
            AmountInWordsServiceImpl.appendScale(words, millions, "million");
        }

        final long thousands = rest / 1000L;
        rest %= 1000L;
        if (thousands == 1) {
            AmountInWordsServiceImpl.append(words, "mille");
        } else if (thousands > 1) {
            AmountInWordsServiceImpl.append(words, AmountInWordsServiceImpl.below1000(thousands) + " mille");
        }

        if (rest > 0) {
            AmountInWordsServiceImpl.append(words, AmountInWordsServiceImpl.below1000(rest));
        }
        return words.toString();
    }

    /**
     * « million » and « milliard » are nouns, so they take the plural; « mille » does not.
     */
    private static void appendScale(final StringBuilder words, final long count, final String scale) {
        final String spelled = count == 1
                ? "un " + scale
                : AmountInWordsServiceImpl.below1000(count) + " " + scale + "s";
        AmountInWordsServiceImpl.append(words, spelled);
    }

    private static void append(final StringBuilder words, final String part) {
        if (!words.isEmpty()) {
            words.append(' ');
        }
        words.append(part);
    }

    private static String below1000(final long value) {
        if (value < 100) {
            return AmountInWordsServiceImpl.below100((int) value);
        }
        final long hundreds = value / 100;
        final int rest = (int) (value % 100);
        // « cent » takes an s only when it is multiplied and nothing follows it.
        final String head = hundreds == 1
                ? "cent"
                : UNITS[(int) hundreds] + (rest == 0 ? " cents" : " cent");
        return rest == 0 ? head : head + " " + AmountInWordsServiceImpl.below100(rest);
    }

    private static String below100(final int value) {
        if (value < 17) {
            return UNITS[value];
        }
        if (value < 20) {
            return "dix-" + UNITS[value - 10];
        }
        if (value < 70) {
            final int tens = value / 10;
            final int unit = value % 10;
            if (unit == 0) {
                return TENS[tens];
            }
            // Twenty-one to sixty-one join with « et », the rest with a hyphen.
            return unit == 1 ? TENS[tens] + " et un" : TENS[tens] + "-" + UNITS[unit];
        }
        if (value < 80) {
            if (value == 71) {
                return "soixante et onze";
            }
            return "soixante-" + AmountInWordsServiceImpl.below100(value - 60);
        }
        if (value == 80) {
            return "quatre-vingts";
        }
        return "quatre-vingt-" + AmountInWordsServiceImpl.below100(value - 80);
    }

    @Override
    public String spell(final BigDecimal amount) {
        final BigDecimal normalized = (amount == null ? BigDecimal.ZERO : amount)
                .setScale(3, RoundingMode.HALF_UP)
                .abs();
        final long dinars = normalized.longValue();
        final long millimes = normalized.subtract(BigDecimal.valueOf(dinars))
                .multiply(BigDecimal.valueOf(MILLIMES_PER_DINAR))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        final StringBuilder spelled = new StringBuilder()
                .append(AmountInWordsServiceImpl.integerWords(dinars))
                .append(dinars == 1 ? " dinar" : " dinars");
        if (millimes > 0) {
            spelled.append(" et ")
                    .append(AmountInWordsServiceImpl.integerWords(millimes))
                    .append(millimes == 1 ? " millime" : " millimes");
        }
        return spelled.toString();
    }
}
