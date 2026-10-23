package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class AgeTest {

    @Test
    void birthdayStillToCome() {
        assertEquals(25, Age.years(LocalDate.of(2000, 12, 31), LocalDate.of(2026, 6, 1)));
    }
}
