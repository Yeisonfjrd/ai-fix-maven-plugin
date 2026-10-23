package bench;

import java.time.LocalDate;
import java.time.Period;

public final class Age {

    private Age() {}

    /** Full years lived on `today`. */
    public static int years(LocalDate birth, LocalDate today) {
        return Period.between(birth, today).getYears();
    }
}
