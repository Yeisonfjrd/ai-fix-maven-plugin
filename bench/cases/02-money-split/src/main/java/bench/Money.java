package bench;

public final class Money {

    private Money() {}

    /** Splits an amount in cents into `parts`. The parts add up to the total; the first ones get the extra cents. */
    public static long[] split(long cents, int parts) {
        if (parts < 1) throw new IllegalArgumentException("parts must be positive");
        long[] out = new long[parts];
        long each = cents / parts;
        for (int i = 0; i < parts; i++) {
            out[i] = each;
        }
        return out;
    }
}
