package bench;

public final class Versions {

    private Versions() {}

    /**
     * Compares dotted versions like 1.10.2 number by number. Missing parts count as 0, so 1.0 equals 1.0.0.
     * Negative if a is older, positive if a is newer, 0 if equal.
     */
    public static int compare(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }
}
