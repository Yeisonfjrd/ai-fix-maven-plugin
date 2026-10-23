package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

class PaginatorTest {

    static final List<Integer> TEN = IntStream.rangeClosed(1, 10).boxed().toList();

    @Test
    void firstPage() {
        assertEquals(List.of(1, 2, 3), Paginator.page(TEN, 1, 3));
    }

    @Test
    void secondPage() {
        assertEquals(List.of(4, 5, 6), Paginator.page(TEN, 2, 3));
    }
}
