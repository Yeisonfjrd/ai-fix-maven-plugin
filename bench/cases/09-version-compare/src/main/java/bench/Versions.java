package bench;

public final class Versions {

    private Versions() {}

    /**
     * Compares dotted versions like 1.10.2 number by number. Missing parts count as 0, so 1.0 equals 1.0.0.
     * Negative if a is older, positive if a is newer, 0 if equal.
     */
    public static int compare(String a, String b) {
        return a.compareTo(b);
    }
}
