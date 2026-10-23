package shop;

/** 10% de descuento a partir de 50.000 (en centavos: 5.000.000). */
public class Pricing {

    static final long THRESHOLD = 5_000_000;
    static final int DISCOUNT_PERCENT = 10;

    public long applyDiscount(long subtotal) {
        if (subtotal >= THRESHOLD) {
            return subtotal - subtotal * DISCOUNT_PERCENT / 100;
        }
        return subtotal;
    }
}
