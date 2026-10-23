package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void splitsInThree() {
        assertArrayEquals(new long[] {34, 33, 33}, Money.split(100, 3));
    }
}
