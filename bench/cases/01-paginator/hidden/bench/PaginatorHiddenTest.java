package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

class PaginatorHiddenTest {

    static final List<Integer> TEN = IntStream.rangeClosed(1, 10).boxed().toList();

    @Test
    void lastPartialPage() {
        assertEquals(List.of(9, 10), Paginator.page(TEN, 3, 4));
    }

    @Test
    void wholeListInOnePage() {
        assertEquals(TEN, Paginator.page(TEN, 1, 10));
    }

    @Test
    void pastTheEndIsEmpty() {
        assertEquals(List.of(), Paginator.page(TEN, 5, 3));
    }

    @Test
    void pagesOfFiveCoverEverything() {
        assertEquals(List.of(6, 7, 8, 9, 10), Paginator.page(TEN, 2, 5));
    }
}
