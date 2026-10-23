package shop;

import java.util.ArrayList;
import java.util.List;

/** Un carrito: los montos van en centavos para no perder plata con decimales. */
public class Cart {

    public record Item(String name, long unitPrice, int quantity) {}

    private final List<Item> items = new ArrayList<>();
    private final Pricing pricing;

    public Cart(Pricing pricing) {
        this.pricing = pricing;
    }

    public void add(String name, long unitPrice, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        items.add(new Item(name, unitPrice, quantity));
    }

    public long subtotal() {
        long sum = 0;
        for (Item item : items) {
            sum += item.unitPrice();
        }
        return sum;
    }

    public long total() {
        return pricing.applyDiscount(subtotal());
    }
}
