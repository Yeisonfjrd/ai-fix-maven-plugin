package shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CartTest {

    @Test
    void subtotalCountsQuantities() {
        Cart cart = new Cart(new Pricing());
        cart.add("yerba", 450_000, 2);
        cart.add("mate", 1_200_000, 1);
        assertEquals(2_100_000, cart.subtotal());
    }

    @Test
    void discountAppliesFromTheThreshold() {
        Cart cart = new Cart(new Pricing());
        cart.add("zapatillas", 2_500_000, 2);
        assertEquals(4_500_000, cart.total());
    }

    @Test
    void noDiscountBelowTheThreshold() {
        Cart cart = new Cart(new Pricing());
        cart.add("medias", 300_000, 3);
        assertEquals(900_000, cart.total());
    }

    @Test
    void rejectsZeroQuantity() {
        Cart cart = new Cart(new Pricing());
        assertThrows(IllegalArgumentException.class, () -> cart.add("nada", 100, 0));
    }
}
