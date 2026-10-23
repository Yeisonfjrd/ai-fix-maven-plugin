package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

class MoneyHiddenTest {

    @Test
    void twoExtraCents() {
        assertArrayEquals(new long[] {3, 3, 2, 2}, Money.split(10, 4));
    }

    @Test
    void exactSplit() {
        assertArrayEquals(new long[] {3, 3, 3}, Money.split(9, 3));
    }

    @Test
    void neverLosesACent() {
        assertEquals(1001, Arrays.stream(Money.split(1001, 7)).sum());
        assertEquals(99_999, Arrays.stream(Money.split(99_999, 13)).sum());
    }

    @Test
    void onePart() {
        assertArrayEquals(new long[] {57}, Money.split(57, 1));
    }
}
