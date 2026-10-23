package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class InventoryTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    void twoExpiredInARow() {
        List<Product> shelf = new ArrayList<>(List.of(
                new Product("milk", TODAY.minusDays(2)),
                new Product("yogurt", TODAY.minusDays(1)),
                new Product("cheese", TODAY.plusDays(10))));
        Inventory.removeExpired(shelf, TODAY);
        assertEquals(List.of("cheese"), shelf.stream().map(Product::name).toList());
    }
}
