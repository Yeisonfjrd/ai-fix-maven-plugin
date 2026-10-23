package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CounterHiddenTest {

    @Test
    void aNewCounterStartsAtZero() {
        new Counter().increment();
        assertEquals(0, new Counter().value());
    }

    @Test
    void manyIncrements() {
        Counter c = new Counter();
        for (int i = 0; i < 1000; i++) c.increment();
        assertEquals(1000, c.value());
    }
}
