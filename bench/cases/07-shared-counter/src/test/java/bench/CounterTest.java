package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CounterTest {

    @Test
    void eachCounterKeepsItsOwnCount() {
        Counter visits = new Counter();
        Counter likes = new Counter();
        visits.increment();
        visits.increment();
        likes.increment();
        assertEquals(2, visits.value());
        assertEquals(1, likes.value());
    }
}
