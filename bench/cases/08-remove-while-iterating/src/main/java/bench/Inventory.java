package bench;

import java.time.LocalDate;
import java.util.List;

public final class Inventory {

    private Inventory() {}

    /** Removes the products that expired before `today`. A product that expires today stays. */
    public static void removeExpired(List<Product> products, LocalDate today) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).expires().isBefore(today)) {
                products.remove(i);
            }
        }
    }
}
