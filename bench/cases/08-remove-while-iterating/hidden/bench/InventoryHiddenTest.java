package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class InventoryHiddenTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    @Test
    void allExpired() {
        List<Product> shelf = new ArrayList<>(List.of(
                new Product("a", TODAY.minusDays(3)),
                new Product("b", TODAY.minusDays(2)),
                new Product("c", TODAY.minusDays(1))));
        Inventory.removeExpired(shelf, TODAY);
        assertTrue(shelf.isEmpty());
    }

    @Test
    void expiringTodayStays() {
        List<Product> shelf = new ArrayList<>(List.of(new Product("bread", TODAY), new Product("old", TODAY.minusDays(1))));
        Inventory.removeExpired(shelf, TODAY);
        assertEquals(List.of("bread"), shelf.stream().map(Product::name).toList());
    }

    @Test
    void nothingExpired() {
        List<Product> shelf = new ArrayList<>(List.of(new Product("x", TODAY.plusDays(1)), new Product("y", TODAY.plusDays(2))));
        Inventory.removeExpired(shelf, TODAY);
        assertEquals(2, shelf.size());
    }
}
