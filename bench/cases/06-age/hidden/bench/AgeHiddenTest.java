package bench;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

class AgeHiddenTest {

    @Test
    void birthdayIsToday() {
        assertEquals(26, Age.years(LocalDate.of(2000, 6, 1), LocalDate.of(2026, 6, 1)));
    }

    @Test
    void birthdayIsTomorrow() {
        assertEquals(25, Age.years(LocalDate.of(2000, 6, 2), LocalDate.of(2026, 6, 1)));
    }

    @Test
    void bornOnALeapDay() {
        assertEquals(21, Age.years(LocalDate.of(2004, 2, 29), LocalDate.of(2026, 2, 28)));
        assertEquals(22, Age.years(LocalDate.of(2004, 2, 29), LocalDate.of(2026, 3, 1)));
    }
}
