package bench;

import java.util.Locale;

public final class Slug {

    private Slug() {}

    /** Lowercase words joined by a single dash, with no dash at either end. */
    public static String of(String title) {
        return String.join("-", title.strip().toLowerCase(Locale.ROOT).split("\\s+"));
    }
}
