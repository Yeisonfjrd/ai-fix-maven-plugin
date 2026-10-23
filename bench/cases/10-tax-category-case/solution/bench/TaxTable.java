package bench;

import java.util.Locale;
import java.util.Map;

/** Tax rates per mille (105 = 10.5%). Categories are case-insensitive; unknown ones pay the general rate. */
public final class TaxTable {

    static final int GENERAL = 210;
    private static final Map<String, Integer> RATES = Map.of("FOOD", 105, "BOOKS", 0, "ELECTRONICS", 210);

    public int perMille(String category) {
        return RATES.getOrDefault(category.toUpperCase(Locale.ROOT), GENERAL);
    }
}
